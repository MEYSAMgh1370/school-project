package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BotContext را از روی chat.id می‌سازد: کاربر فقط از BaleLinkEntity پیدا
 * می‌شود، نقش‌ها و فرزندان فقط از facadeهای iam/academic - همان الگوی
 * اعتماد BaleUpdateHandler.
 */
@Component
class BotContextLoader {

    private final BaleLinkRepository baleLinkRepository;
    private final IamFacade iamFacade;
    private final AcademicFacade academicFacade;

    BotContextLoader(BaleLinkRepository baleLinkRepository, IamFacade iamFacade, AcademicFacade academicFacade) {
        this.baleLinkRepository = baleLinkRepository;
        this.iamFacade = iamFacade;
        this.academicFacade = academicFacade;
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

        List<BotContext.Ward> wards = new ArrayList<>();
        for (MembershipInfo membership : memberships) {
            if (!"PARENT".equals(membership.roleCode())) {
                continue;
            }
            Long schoolId = membership.schoolId();
            for (StudentInfo student : academicFacade.listWards(userId.value(), schoolId)) {
                String wardName = iamFacade.findUser(new UserId(student.userId()))
                        .map(UserProfile::fullName)
                        .orElse("دانش‌آموز #" + student.userId());
                Long classId = student.classId() == null ? null : student.classId().value();
                wards.add(new BotContext.Ward(
                        schoolId, student.id().value(), student.userId(), classId, wardName));
            }
        }

        return Optional.of(new BotContext(chatId, userId, profile.get().fullName(), memberships, List.copyOf(wards)));
    }
}
