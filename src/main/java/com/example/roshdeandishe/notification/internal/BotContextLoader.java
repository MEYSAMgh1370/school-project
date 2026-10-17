package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
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

    BotContextLoader(BaleLinkRepository baleLinkRepository, IamFacade iamFacade, SchoolBotRepository schoolBots) {
        this.baleLinkRepository = baleLinkRepository;
        this.iamFacade = iamFacade;
        this.schoolBots = schoolBots;
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

        Set<Long> schoolsWithActiveBot = bot.isShared() ? schoolBots.findActiveSchoolIds() : Set.of();
        List<MembershipInfo> memberships = iamFacade.findMembershipsByUser(userId).stream()
                .filter(MembershipInfo::active)
                .filter(m -> bot.isShared()
                        ? !schoolsWithActiveBot.contains(m.schoolId())
                        : m.schoolId().equals(bot.schoolId()))
                .toList();

        return Optional.of(new BotContext(chatId, userId, profile.get().fullName(), memberships));
    }
}
