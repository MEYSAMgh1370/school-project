package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleCallbackQuery;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleMessage;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
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
    private final BotTokens botTokens;

    BaleUpdateHandler(
            BaleLinkRepository baleLinkRepository,
            BaleClient baleClient,
            IamFacade iamFacade,
            SchoolFacade schoolFacade,
            FinanceFacade financeFacade,
            BotContextLoader botContextLoader,
            SubjectResolver subjectResolver,
            BotRouter botRouter,
            BotTokens botTokens
    ) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.financeFacade = financeFacade;
        this.botContextLoader = botContextLoader;
        this.subjectResolver = subjectResolver;
        this.botRouter = botRouter;
        this.botTokens = botTokens;
    }

    @Transactional
    void handle(BotRef bot, BaleUpdate update) {
        BaleCallbackQuery callback = update.callbackQuery();
        if (callback != null) {
            handleCallback(bot, callback);
            return;
        }

        BaleMessage message = update.message();
        if (message == null || message.chat() == null) {
            return; // آپدیت‌هایی غیر از پیام معمولی (مثلاً edited_message) فعلاً نادیده گرفته می‌شوند
        }

        Long chatId = message.chat().id();

        // فقط گفتگوی خصوصی: در گروه/کانال (یا نوع نامشخص) نه داده‌ای داده می‌شود نه لینکی ساخته می‌شود
        if (!isPrivate(message)) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.PRIVATE_CHAT_ONLY);
            return;
        }

        if (message.contact() != null) {
            handleContactShared(bot, chatId, message);
            return;
        }

        String text = message.text() == null ? "" : message.text().trim();
        if ("/start".equals(text)) {
            baleClient.sendContactRequest(botTokens.tokenFor(bot), chatId, BotTexts.WELCOME_TEXT);
            return;
        }

        Optional<BotContext> context = botContextLoader.load(bot, chatId);
        if (context.isEmpty()) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.NOT_LINKED_TEXT);
            return;
        }

        BotContext ctx = context.get();
        // «راهنما» همیشه کار می‌کند: متن ثابت و منو از عضویت‌های فیلترشده، بدون داده‌ی مدرسه
        if (ctx.blockedBySchoolSettings() && !BotTexts.BTN_HELP.equals(text)) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.BOT_DISABLED_FOR_SCHOOL);
            return;
        }
        switch (text) {
            case BotTexts.BTN_HELP -> baleClient.sendMenu(botTokens.tokenFor(bot), chatId, botRouter.help(ctx), botRouter.menu(ctx));
            case BotTexts.CMD_MY_SCHOOLS -> baleClient.sendMessage(botTokens.tokenFor(bot), chatId, describeMemberships(ctx));
            case BotTexts.CMD_MY_WARDS -> baleClient.sendMessage(botTokens.tokenFor(bot), chatId, describeWards(ctx));
            default -> {
                BotRouter.Reply reply = botRouter.onText(ctx, text);
                if (reply == null) {
                    baleClient.sendMenu(botTokens.tokenFor(bot), chatId, BotTexts.UNKNOWN_TEXT, botRouter.menu(ctx));
                } else {
                    send(bot, chatId, reply);
                }
            }
        }
    }

    private void send(BotRef bot, Long chatId, BotRouter.Reply reply) {
        if (reply.choices().isEmpty()) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, reply.text());
        } else {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, reply.text(), reply.choices());
        }
    }

    /**
     * کلیک روی دکمه‌ی اینلاین: اول همیشه تأیید می‌شود (وگرنه بله برای همیشه spinner نشان می‌دهد)، بعد
     * پاسخ از {@link BotRouter#onCallback}؛ userId فقط از لینکِ chat می‌آید و شناسه‌ی callback فقط یک
     * انتخاب است که با هدف‌های همان کاربر سنجیده می‌شود.
     */
    private void handleCallback(BotRef bot, BaleCallbackQuery callback) {
        baleClient.answerCallbackQuery(botTokens.tokenFor(bot), callback.id());
        if (callback.message() == null || callback.message().chat() == null) {
            return;
        }
        Long chatId = callback.message().chat().id();
        if (!isPrivate(callback.message())) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.PRIVATE_CHAT_ONLY);
            return;
        }
        Optional<BotContext> context = botContextLoader.load(bot, chatId);
        if (context.isEmpty()) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.NOT_LINKED_TEXT);
            return;
        }
        if (context.get().blockedBySchoolSettings()) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.BOT_DISABLED_FOR_SCHOOL);
            return;
        }
        send(bot, chatId, botRouter.onCallback(context.get(), callback.data()));
    }

    private static boolean isPrivate(BaleMessage message) {
        return "private".equals(message.chat().type());
    }

    /**
     * لینک فقط وقتی ساخته می‌شود که مخاطب، شماره‌ی خودِ فرستنده باشد (contact.user_id == from.id)؛ بله
     * اجازه می‌دهد هر مخاطبِ دفترچه‌تلفن فرستاده شود. این بررسی پیش از جست‌وجوی شماره است تا مخاطب
     * بیگانه هرگز معلوم نکند شماره‌ای ثبت شده یا نه؛ نبودنِ user_id هم رد می‌شود (fail closed).
     */
    private void handleContactShared(BotRef bot, Long chatId, BaleMessage message) {
        Long contactUserId = message.contact().userId();
        if (contactUserId == null || message.from() == null || !contactUserId.equals(message.from().id())) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.CONTACT_NOT_OWN);
            return;
        }

        Optional<UserProfile> matchedUser = iamFacade.findUserByPhoneNumber(message.contact().phoneNumber());
        if (matchedUser.isEmpty()) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.CONTACT_NOT_MATCHED);
            return;
        }

        UserId id = matchedUser.get().id();
        Long userId = id.value();

        // ربات مدرسه فقط عضو فعالِ همان مدرسه (یا مدیر پلتفرم) را لینک می‌کند؛ ربات مشترک بدون این شرط
        if (!bot.isShared() && !mayLinkToSchoolBot(bot, id)) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, BotTexts.NOT_A_MEMBER);
            return;
        }

        // upsert بر اساس (ربات، userId) - اگر همین کاربر قبلاً در همین ربات از یک chat دیگر لینک
        // شده بود، آن لینک قدیمی جای‌گزین می‌شود؛ لینک ربات‌های دیگر دست‌نخورده می‌ماند
        BaleLinkEntity link = baleLinkRepository.findByUser(bot, userId)
                .orElseGet(() -> new BaleLinkEntity(bot.botId(), userId, chatId, Instant.now()));
        link.setChatId(chatId);
        link.setUserId(userId);
        baleLinkRepository.save(link);

        String confirmation = BotTexts.linkSuccess(matchedUser.get().fullName());
        sendAfterCommit(() -> sendLinkConfirmation(bot, chatId, confirmation));
    }

    private boolean mayLinkToSchoolBot(BotRef bot, UserId id) {
        boolean member = iamFacade.findMembershipsByUser(id).stream()
                .anyMatch(m -> m.active() && bot.schoolId().equals(m.schoolId()));
        return member || iamFacade.isPlatformAdmin(id);
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

    private void sendLinkConfirmation(BotRef bot, Long chatId, String confirmation) {
        List<List<String>> menu = List.of();
        try {
            menu = botContextLoader.load(bot, chatId).map(botRouter::menu).orElse(List.of());
        } catch (RuntimeException e) {
            log.warn("Building the menu after linking failed: chatId={} error={}",
                    chatId, e.getClass().getSimpleName());
        }
        if (menu.isEmpty()) {
            baleClient.sendMessage(botTokens.tokenFor(bot), chatId, confirmation);
        } else {
            baleClient.sendMenu(botTokens.tokenFor(bot), chatId, confirmation, menu);
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
