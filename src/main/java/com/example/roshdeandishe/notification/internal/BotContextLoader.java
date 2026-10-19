package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BotContext را از روی chat.id می‌سازد: کاربر فقط از BaleLinkEntity پیدا
 * می‌شود، عضویت‌ها فقط از iam (فرزندان تنبل در SubjectResolver) - همان الگوی
 * اعتماد BaleUpdateHandler.
 */
@Component
class BotContextLoader {


    private final BaleLinkRepository baleLinkRepository;
    private final IamFacade iamFacade;
    private final SchoolBotRepository schoolBots;
    private final SchoolFacade schoolFacade;

    BotContextLoader(BaleLinkRepository baleLinkRepository, IamFacade iamFacade, SchoolBotRepository schoolBots,
                     SchoolFacade schoolFacade) {
        this.baleLinkRepository = baleLinkRepository;
        this.iamFacade = iamFacade;
        this.schoolBots = schoolBots;
        this.schoolFacade = schoolFacade;
    }

    /**
     * خالی: این chat در این ربات لینک نشده، یا کاربرِ لینک‌شده دیگر وجود ندارد. عضویت‌ها به دامنه‌ی ربات
     * محدود می‌شوند: ربات مدرسه فقط عضویت‌های همان مدرسه را می‌بیند؛ ربات مشترک مدرسه‌هایی را که ربات فعالِ
     * خودشان را دارند کنار می‌گذارد (مدرسه‌ای که رباتش غیرفعال است همچنان در ربات مشترک سرویس می‌گیرد).
     * عضویتِ پایان‌یافته هیچ‌گاه داده‌ای نشان نمی‌دهد.
     */
    Optional<BotContext> load(BotRef bot, Long chatId) {
        Optional<BaleLinkEntity> link = baleLinkRepository.findByChat(bot, chatId);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        UserId userId = new UserId(link.get().getUserId());
        Optional<UserProfile> profile = iamFacade.findUser(userId);
        if (profile.isEmpty()) {
            return Optional.empty();
        }

        // ۱) دامنه‌ی ربات (کدام ربات، ربات این chat است) - قبل از تنظیمات مدرسه.
        // Make schoolBots result tolerant (may be null from mocks). Treat null as empty set.
        Set<Long> schoolsWithActiveBot = bot.isShared()
                ? Optional.ofNullable(schoolBots.findActiveSchoolIds()).orElse(Set.of())
                : Set.of();
        List<MembershipInfo> inScope = iamFacade.findMembershipsByUser(userId).stream()
                .filter(MembershipInfo::active)
                .filter(m -> bot.isShared()
                        ? !schoolsWithActiveBot.contains(m.schoolId())
                        : m.schoolId().equals(bot.schoolId()))
                .toList();
        // ۲) تنظیمات بات هر مدرسه (روشن/خاموش و نقش‌های مجاز) اینجا اعمال می‌شود؛ فرزندان و بقیه‌ی هدف‌ها
        // تنبل از همین عضویت‌ها می‌آیند (SubjectResolver)، پس عضویتِ حذف‌شده راهی به داده‌ی آن مدرسه ندارد.
        // روی هر رباتی که مدرسه را سرویس می‌دهد (ربات خودِ مدرسه یا ربات مشترک) یکسان اعمال می‌شود.
        List<MembershipInfo> allowed = inScope.stream()
                .filter(m -> schoolFacade.isBotAllowed(new SchoolId(m.schoolId()), Set.of(m.roleCode())))
                .toList();
        // «مسدود با تنظیمات» فقط وقتی که عضویتِ فعالِ داخل دامنه‌ی این ربات داشته و همه را تنظیمات حذف کرده؛
        // کاربری که در دامنه‌ی این ربات اصلاً عضویتی ندارد رفتار قبلیِ «بدون عضویت» را می‌گیرد.
        boolean blocked = !inScope.isEmpty() && allowed.isEmpty();

        return Optional.of(new BotContext(chatId, userId, profile.get().fullName(), allowed, blocked));
    }
}
