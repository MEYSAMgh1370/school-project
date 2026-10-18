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
    private final SchoolFacade schoolFacade;

    BotContextLoader(BaleLinkRepository baleLinkRepository, IamFacade iamFacade, SchoolFacade schoolFacade) {
        this.baleLinkRepository = baleLinkRepository;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
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

        List<MembershipInfo> active = iamFacade.findMembershipsByUser(userId).stream()
                .filter(MembershipInfo::active)
                .toList();
        // تنظیمات بات هر مدرسه (روشن/خاموش و نقش‌های مجاز) اینجا اعمال می‌شود؛ فرزندان و بقیه‌ی هدف‌ها
        // تنبل از همین عضویت‌ها می‌آیند (SubjectResolver)، پس عضویتِ حذف‌شده راهی به داده‌ی آن مدرسه ندارد.
        List<MembershipInfo> allowed = active.stream()
                .filter(m -> schoolFacade.isBotAllowed(new SchoolId(m.schoolId()), Set.of(m.roleCode())))
                .toList();
        boolean blocked = !active.isEmpty() && allowed.isEmpty();

        return Optional.of(new BotContext(chatId, userId, profile.get().fullName(), allowed, blocked));
    }
}
