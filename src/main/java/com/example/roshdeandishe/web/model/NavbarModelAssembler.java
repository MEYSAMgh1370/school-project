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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        Map<Long, List<String>> rolesBySchool = new LinkedHashMap<>();
        // کدِ همه‌ی نقش‌های هر مدرسه (فعال و غیرفعال): جدولِ داشبورد هم برچسبِ نقش‌های غیرفعال را نشان می‌دهد
        Map<Long, Set<String>> allRolesBySchool = new LinkedHashMap<>();
        for (MembershipInfo membership : memberships) {
            allRolesBySchool.computeIfAbsent(membership.schoolId(), id -> new LinkedHashSet<>())
                    .add(membership.roleCode());
            if (!membership.active()) {
                continue;
            }
            rolesBySchool.computeIfAbsent(membership.schoolId(), id -> new ArrayList<>())
                    .add(membership.roleCode());
        }
        List<NavbarModel.SchoolOption> options = new ArrayList<>();
        rolesBySchool.forEach((schoolId, roles) -> {
            Optional<SchoolFacade.SchoolInfo> school = schoolFacade.findSchool(new SchoolId(schoolId));
            options.add(new NavbarModel.SchoolOption(
                    schoolId, schoolName(school, schoolId), roles, iamFacade.findPermissionCodesByRole(schoolId, roles),
                    // مدرسه‌ی نیافته فقط یک گزینه‌ی نمایشی است؛ اجرای قاعده‌ی هفته با سرور است
                    school.map(SchoolFacade.SchoolInfo::weekStart).orElse(SchoolFacade.WeekStart.SATURDAY),
                    schoolFacade.findCurrentAcademicYear(new SchoolId(schoolId))
                            .map(SchoolFacade.AcademicYearInfo::code).orElse(null)));
        });
        Map<Long, Map<String, String>> roleNames = new LinkedHashMap<>();
        allRolesBySchool.forEach((schoolId, roles) -> {
            Map<String, String> names = iamFacade.findCustomRoleDisplayNames(schoolId, roles);
            if (!names.isEmpty()) {
                roleNames.put(schoolId, names);
            }
        });
        return new NavbarModel(profile.fullName(), List.copyOf(options), roleNames);
    }

    /**
     * اگر مدرسه‌ای پیدا نشد (نباید رخ دهد، ولی iam فقط یک Long مات نگه
     * می‌دارد) به جای شکستن صفحه، شناسه را نمایش می‌دهیم.
     */
    private static String schoolName(Optional<SchoolFacade.SchoolInfo> school, Long schoolId) {
        return school.map(SchoolFacade.SchoolInfo::name).orElse("مدرسه #" + schoolId);
    }
}
