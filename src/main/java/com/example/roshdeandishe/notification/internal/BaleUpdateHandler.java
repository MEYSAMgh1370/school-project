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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(BaleUpdateHandler.class);

    private final BaleLinkRepository baleLinkRepository;
    private final BotContextLoader botContextLoader;
    private final BaleClient baleClient;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final FinanceFacade financeFacade;
    private final SubjectResolver subjectResolver;
    private final BotRouter botRouter;

    BaleUpdateHandler(
            BaleLinkRepository baleLinkRepository,
            BaleClient baleClient,
            IamFacade iamFacade,
            SchoolFacade schoolFacade,
            FinanceFacade financeFacade,
            BotContextLoader botContextLoader,
            SubjectResolver subjectResolver,
            BotRouter botRouter
    ) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.financeFacade = financeFacade;
        this.botContextLoader = botContextLoader;
        this.subjectResolver = subjectResolver;
        this.botRouter = botRouter;
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
            case BotTexts.BTN_HELP -> baleClient.sendMenu(chatId, BotTexts.HELP_TEXT, botRouter.menu(ctx));
            case BotTexts.CMD_MY_SCHOOLS -> baleClient.sendMessage(chatId, describeMemberships(ctx));
            case BotTexts.CMD_MY_WARDS -> baleClient.sendMessage(chatId, describeWards(ctx));
            default -> {
                BotRouter.Reply reply = botRouter.onText(ctx, text);
                if (reply == null) {
                    baleClient.sendMenu(chatId, BotTexts.UNKNOWN_TEXT, botRouter.menu(ctx));
                } else {
                    send(chatId, reply);
                }
            }
        }
    }

    private void send(Long chatId, BotRouter.Reply reply) {
        if (reply.choices().isEmpty()) {
            baleClient.sendMessage(chatId, reply.text());
        } else {
            baleClient.sendMessage(chatId, reply.text(), reply.choices());
        }
    }

    /**
     * کلیک روی دکمه‌ی اینلاین: اول همیشه تأیید می‌شود (وگرنه بله برای همیشه spinner نشان می‌دهد)، بعد
     * پاسخ از {@link BotRouter#onCallback}؛ userId فقط از لینکِ chat می‌آید و شناسه‌ی callback فقط یک
     * انتخاب است که با هدف‌های همان کاربر سنجیده می‌شود.
     */
    private void handleCallback(BaleCallbackQuery callback) {
        baleClient.answerCallbackQuery(callback.id());
        if (callback.message() == null || callback.message().chat() == null) {
            return;
        }
        Long chatId = callback.message().chat().id();
        Optional<BotContext> context = botContextLoader.load(chatId);
        if (context.isEmpty()) {
            baleClient.sendMessage(chatId, BotTexts.NOT_LINKED_TEXT);
            return;
        }
        send(chatId, botRouter.onCallback(context.get(), callback.data()));
    }

    private void handleContactShared(Long chatId, String phoneNumber) {
        Optional<UserProfile> matchedUser = iamFacade.findUserByPhoneNumber(phoneNumber);
        if (matchedUser.isEmpty()) {
            baleClient.sendMessage(chatId, BotTexts.CONTACT_NOT_MATCHED);
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

        String confirmation = BotTexts.linkSuccess(matchedUser.get().fullName());
        sendAfterCommit(() -> sendLinkConfirmation(chatId, confirmation));
    }

    /** تأییدیه فقط پس از commitِ لینک فرستاده می‌شود (خطای منو نباید تراکنش را rollback-only کند). */
    private static void sendAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void sendLinkConfirmation(Long chatId, String confirmation) {
        List<List<String>> menu = List.of();
        try {
            menu = botContextLoader.load(chatId).map(botRouter::menu).orElse(List.of());
        } catch (RuntimeException e) {
            log.warn("Building the menu after linking failed: chatId={} error={}",
                    chatId, e.getClass().getSimpleName());
        }
        if (menu.isEmpty()) {
            baleClient.sendMessage(chatId, confirmation);
        } else {
            baleClient.sendMenu(chatId, confirmation, menu);
        }
    }

    private String describeMemberships(BotContext ctx) {
        List<MembershipInfo> memberships = ctx.memberships();

        if (memberships.isEmpty()) {
            return BotTexts.NO_MEMBERSHIPS;
        }

        StringBuilder sb = new StringBuilder(BotTexts.MEMBERSHIPS_HEADER);
        for (MembershipInfo m : memberships) {
            String schoolName = schoolFacade.findSchool(new SchoolId(m.schoolId()))
                    .map(s -> s.name())
                    .orElse(BotTexts.schoolFallback(m.schoolId()));
            sb.append(BotTexts.membershipLine(schoolName, m.roleCode()));
        }
        return sb.toString().stripTrailing();
    }

    private String describeWards(BotContext ctx) {
        if (!ctx.hasRole("PARENT")) {
            return BotTexts.NOT_A_PARENT;
        }
        // INVOICES = فقط فرزندان (هدف‌ها از داده‌ی سرور، نه از ورودی کاربر)
        List<BotTarget> wards = subjectResolver.targets(ctx, BotIntent.INVOICES);
        if (wards.isEmpty()) {
            return BotTexts.NO_WARDS;
        }

        StringBuilder sb = new StringBuilder();
        for (BotTarget target : wards) {
            BotTarget.Student ward = (BotTarget.Student) target;
            // همان دیدِ خانواده (سالِ جاری + «بدون سال»)، نه همه‌ی سال‌ها
            long invoiceCount = financeFacade.listInvoicesForStudents(ward.schoolId(), List.of(ward.studentId())).size();
            sb.append(BotTexts.wardLine(ward.name(), ward.schoolName(), invoiceCount));
        }
        return sb.toString().stripTrailing();
    }
}
