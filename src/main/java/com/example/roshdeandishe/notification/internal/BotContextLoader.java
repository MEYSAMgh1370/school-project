package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * BotContext را از روی chat.id می‌سازد: کاربر فقط از BaleLinkEntity پیدا
 * می‌شود، عضویت‌ها فقط از iam (فرزندان تنبل در SubjectResolver) - همان الگوی
 * اعتماد BaleUpdateHandler.
 */
@Component
class BotContextLoader {

    private final BaleLinkRepository baleLinkRepository;
    private final IamFacade iamFacade;

    BotContextLoader(BaleLinkRepository baleLinkRepository, IamFacade iamFacade) {
        this.baleLinkRepository = baleLinkRepository;
        this.iamFacade = iamFacade;
    }

    /** خالی: این chat لینک نشده، یا کاربرِ لینک‌شده دیگر وجود ندارد. */
    Optional<BotContext> load(Long chatId) {
        Optional<BaleLinkEntity> link = baleLinkRepository.findByChatId(chatId);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        UserId userId = new UserId(link.get().getUserId());
        Optional<UserProfile> profile = iamFacade.findUser(userId);
        if (profile.isEmpty()) {
            return Optional.empty();
        }

        List<MembershipInfo> memberships = iamFacade.findMembershipsByUser(userId).stream()
                .filter(MembershipInfo::active)
                .toList();

        return Optional.of(new BotContext(chatId, userId, profile.get().fullName(), memberships));
    }
}
