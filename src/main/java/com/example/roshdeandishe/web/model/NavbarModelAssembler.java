package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ترکیب داده‌ی ناوبار مشترک (عضویت‌ها از iam + نام مدرسه از school) در یک
 * جا، تا هر page controller جدیدِ ماژول web این منطق را دوباره ننویسد.
 */
@Component
public class NavbarModelAssembler {

    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;

    NavbarModelAssembler(IamFacade iamFacade, SchoolFacade schoolFacade) {
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
    }

    /**
     * برای صفحه‌هایی که جز ناوبار داده‌ی دیگری از iam لازم ندارند.
     */
    public NavbarModel forCurrentUser() {
        UserId userId = iamFacade.currentUserId();
        UserProfile profile = iamFacade.findUser(userId).orElseThrow();
        return assemble(profile, iamFacade.findMembershipsByUser(userId));
    }

    /**
     * برای صفحه‌هایی (مثل داشبورد) که خودشان profile و memberships را
     * برای محتوای صفحه گرفته‌اند - از واکشی دوباره جلوگیری می‌کند.
     */
    public NavbarModel assemble(UserProfile profile, List<MembershipInfo> memberships) {
        // گزینه‌های سوییچر: فقط عضویت‌های فعال، گروه‌شده per-school با
        // فهرست نقش‌های فعالِ همان مدرسه (برای دراپ‌داون نقش).
        Map<Long, NavbarModel.SchoolOption> options = new LinkedHashMap<>();
        for (MembershipInfo membership : memberships) {
            if (!membership.active()) {
                continue;
            }
            options.computeIfAbsent(membership.schoolId(), id ->
                            new NavbarModel.SchoolOption(id, schoolName(id), new ArrayList<>()))
                    .roles().add(membership.roleCode());
        }
        return new NavbarModel(profile.fullName(), List.copyOf(options.values()));
    }

    /**
     * اگر مدرسه‌ای پیدا نشد (نباید رخ دهد، ولی iam فقط یک Long مات نگه
     * می‌دارد) به جای شکستن صفحه، شناسه را نمایش می‌دهیم.
     */
    String schoolName(Long schoolId) {
        return schoolFacade.findSchool(new SchoolId(schoolId))
                .map(SchoolFacade.SchoolInfo::name)
                .orElse("مدرسه #" + schoolId);
    }
}
