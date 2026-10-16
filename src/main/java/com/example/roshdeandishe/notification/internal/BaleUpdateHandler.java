package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleCallbackQuery;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleMessage;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * منطق پردازش یک Update ورودی از وب‌هوک بله. یک بات دستوری ساده است - نه
 * مکالمه‌ی آزاد و نه هیچ مدل زبانی‌ای ({@code CLAUDE_LEGACY.md} §10). لغو این قاعده برای مسیریاب
 * متن آزاد در {@code openspec/changes/bale-smart-bot.md} فقط پیشنهاد شده است؛ تا وقتی سند تصمیمش
 * (مرحله‌ی ۳) نوشته نشده، قاعده سر جایش است.
 * <p>
 * چون این کد در پاسخ به یک HTTP Request از سرور بله اجرا می‌شود - نه یک
 * درخواست مرورگر با سشن - هیچ‌کدام از {@code @RequiresModule}/
 * {@code @RequiresPermission} در دسترس/معنادار نیستند (به TenantContext/
 * SecurityContext یک session نیاز دارند که اینجا وجود ندارد). امنیت این
 * ماژول از الگوی دیگری می‌آید: هر پرس‌وجو فقط داده‌ی خودِ کاربرِ لینک‌شده
 * (از روی BaleLinkEntity) یا فرزندان *واقعاً لینک‌شده*‌ی او را می‌خواند -
 * دقیقاً همان الگوی اعتماد MeController («/api/me»، عمداً بدون آن دو
 * annotation، چون درباره‌ی خودِ کاربر است نه یک مدرسه‌ی خاص).
 */
@Component
class BaleUpdateHandler {

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "STUDENT", "دانش‌آموز",
            "TEACHER", "معلم",
            "PARENT", "والد",
            "SCHOOL_ADMIN", "مدیر مدرسه",
            "FINANCE_ADMIN", "مدیر مالی"
    );

    private final BaleLinkRepository baleLinkRepository;
    private final BotContextLoader botContextLoader;
    private final BaleClient baleClient;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final FinanceFacade financeFacade;

    BaleUpdateHandler(
            BaleLinkRepository baleLinkRepository,
            BaleClient baleClient,
            IamFacade iamFacade,
            SchoolFacade schoolFacade,
            FinanceFacade financeFacade,
            BotContextLoader botContextLoader
    ) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.financeFacade = financeFacade;
        this.botContextLoader = botContextLoader;
    }

    @Transactional
    void handle(BaleUpdate update) {
        BaleCallbackQuery callback = update.callbackQuery();
        if (callback != null) {
            handleCallback(callback);
            return;
        }

        BaleMessage message = update.message();
        if (message == null || message.chat() == null) {
            return; // آپدیت‌هایی غیر از پیام معمولی (مثلاً edited_message) فعلاً نادیده گرفته می‌شوند
        }

        Long chatId = message.chat().id();

        if (message.contact() != null) {
            handleContactShared(chatId, message.contact().phoneNumber());
            return;
        }

        String text = message.text() == null ? "" : message.text().trim();
        if ("/start".equals(text)) {
            baleClient.sendContactRequest(chatId, BotTexts.WELCOME_TEXT);
            return;
        }

        Optional<BotContext> context = botContextLoader.load(chatId);
        if (context.isEmpty()) {
            baleClient.sendMessage(chatId, BotTexts.NOT_LINKED_TEXT);
            return;
        }

        BotContext ctx = context.get();
        switch (text) {
            case "راهنما" -> baleClient.sendMessage(chatId, BotTexts.HELP_TEXT);
            case "مدرسه‌های من" -> baleClient.sendMessage(chatId, describeMemberships(ctx));
            case "فرزندان من" -> baleClient.sendMessage(chatId, describeWards(ctx));
            default -> baleClient.sendMessage(chatId, BotTexts.UNKNOWN_COMMAND_TEXT);
        }
    }

    /**
     * کلیک روی دکمه‌ی اینلاین: اول همیشه تأیید می‌شود (وگرنه بله برای
     * همیشه spinner نشان می‌دهد)، بعد مثل پیامی از همان chat رفتار می‌شود.
     * فعلاً intent مبتنی بر callback نداریم، پس کاربرِ لینک‌شده راهنما می‌گیرد.
     */
    private void handleCallback(BaleCallbackQuery callback) {
        baleClient.answerCallbackQuery(callback.id());
        if (callback.message() == null || callback.message().chat() == null) {
            return;
        }
        Long chatId = callback.message().chat().id();
        if (botContextLoader.load(chatId).isEmpty()) {
            baleClient.sendMessage(chatId, BotTexts.NOT_LINKED_TEXT);
            return;
        }
        baleClient.sendMessage(chatId, BotTexts.HELP_TEXT);
    }

    private void handleContactShared(Long chatId, String phoneNumber) {
        Optional<UserProfile> matchedUser = iamFacade.findUserByPhoneNumber(phoneNumber);
        if (matchedUser.isEmpty()) {
            baleClient.sendMessage(chatId,
                    "شماره‌ی به‌اشتراک‌گذاشته‌شده با هیچ حسابی در سامانه مطابقت ندارد. " +
                    "ابتدا از طریق پروفایل حساب کاربری‌تان در سایت، همین شماره را ثبت کنید.");
            return;
        }

        Long userId = matchedUser.get().id().value();
        // upsert بر اساس userId - اگر همین کاربر قبلاً از یک chat دیگر لینک
        // شده بود، آن لینک قدیمی جای‌گزین می‌شود (idempotent روی هر دو طرف)
        BaleLinkEntity link = baleLinkRepository.findByUserId(userId)
                .orElseGet(() -> new BaleLinkEntity(userId, chatId, Instant.now()));
        link.setChatId(chatId);
        link.setUserId(userId);
        baleLinkRepository.save(link);

        baleClient.sendMessage(chatId,
                matchedUser.get().fullName() + " عزیز، حساب شما با موفقیت متصل شد. برای راهنما «راهنما» را ارسال کنید.");
    }

    private String describeMemberships(BotContext ctx) {
        List<MembershipInfo> memberships = ctx.memberships();

        if (memberships.isEmpty()) {
            return "شما در حال حاضر عضو هیچ مدرسه‌ای نیستید.";
        }

        StringBuilder sb = new StringBuilder("مدرسه‌ها و نقش‌های شما:\n");
        for (MembershipInfo m : memberships) {
            String schoolName = schoolFacade.findSchool(new SchoolId(m.schoolId()))
                    .map(s -> s.name())
                    .orElse("مدرسه #" + m.schoolId());
            sb.append("- ").append(schoolName).append(" (")
                    .append(ROLE_LABELS.getOrDefault(m.roleCode(), m.roleCode())).append(")\n");
        }
        return sb.toString().stripTrailing();
    }

    private String describeWards(BotContext ctx) {
        if (!ctx.hasRole("PARENT")) {
            return "شما در هیچ مدرسه‌ای نقش والد ندارید.";
        }
        if (ctx.wards().isEmpty()) {
            return "هیچ دانش‌آموزی به شما لینک نشده است.";
        }

        StringBuilder sb = new StringBuilder();
        for (BotContext.Ward ward : ctx.wards()) {
            Long schoolId = ward.schoolId();
            String schoolName = schoolFacade.findSchool(new SchoolId(schoolId)).map(s -> s.name())
                    .orElse("مدرسه #" + schoolId);
            // همان دیدِ خانواده (سالِ جاری + «بدون سال»)، نه همه‌ی سال‌ها
            long invoiceCount = financeFacade.listInvoicesForStudents(schoolId, List.of(ward.studentId())).size();
            sb.append("- ").append(ward.fullName()).append(" (").append(schoolName).append("): ")
                    .append(invoiceCount).append(" فاکتور صادرشده\n");
        }
        return sb.toString().stripTrailing();
    }
}
