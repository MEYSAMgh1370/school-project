package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * هدف‌هایی که کاربرِ یک {@link BotContext} برای یک {@link BotIntent} مجاز است درباره‌شان بپرسد؛ فقط از
 * داده‌ی سرور (عضویت‌های فعال و facadeها) و بدون خواندنِ هیچ id از ورودیِ کاربر. فرزندان و ثبت‌نامِ خودِ
 * دانش‌آموز فقط وقتی دیده می‌شوند که عضویتِ فعالِ {@code PARENT}/{@code STUDENT} همان مدرسه وجود داشته باشد.
 * ترتیب پایدار است: بر اساس مدرسه، و داخل هر مدرسه به همان ترتیبِ facade (فرزندان) و سپس ثبت‌نامِ خود.
 */
@Component
class SubjectResolver {

    private final AcademicFacade academicFacade;
    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    SubjectResolver(AcademicFacade academicFacade, SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.academicFacade = academicFacade;
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    /** هدف‌هایی که این کاربر برای این intent می‌تواند بپرسد، ساخته‌شده فقط از داده‌ی سرور. */
    List<BotTarget> targets(BotContext ctx, BotIntent intent) {
        List<MembershipInfo> active = ctx.memberships().stream().filter(MembershipInfo::active).toList();
        Map<Long, String> schoolNames = new HashMap<>();
        return switch (intent.targetKind()) {
            case STUDENT_TARGET -> students(ctx, active, schoolNames, true);
            case WARD_TARGET -> students(ctx, active, schoolNames, false);
            case TEACHER_SCHOOL -> schools(active, "TEACHER", schoolNames);
            case MEMBER_SCHOOL -> schools(active, null, schoolNames);
        };
    }

    /**
     * آیا کاربر دست‌کم یک هدف از این نوع دارد؟ برای ساختن منو: در اولین برخورد متوقف می‌شود و نام‌ها را
     * نمی‌خواند (findUser/findSchool صدا زده نمی‌شود)؛ با {@link #targets} هم‌معنا است.
     */
    boolean hasAny(BotContext ctx, BotIntent.TargetKind kind) {
        List<MembershipInfo> active = ctx.memberships().stream().filter(MembershipInfo::active).toList();
        return switch (kind) {
            case TEACHER_SCHOOL -> active.stream().anyMatch(m -> "TEACHER".equals(m.roleCode()));
            case MEMBER_SCHOOL -> !active.isEmpty();
            case STUDENT_TARGET, WARD_TARGET -> {
                boolean includeOwn = kind == BotIntent.TargetKind.STUDENT_TARGET;
                for (Long schoolId : active.stream().map(MembershipInfo::schoolId).distinct().sorted().toList()) {
                    if (hasRole(active, schoolId, "PARENT")
                            && !academicFacade.listWards(ctx.userId().value(), schoolId).isEmpty()) {
                        yield true;
                    }
                    if (includeOwn && hasRole(active, schoolId, "STUDENT")
                            && academicFacade.findMyEnrollment(ctx.userId().value(), schoolId).isPresent()) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    private List<BotTarget> students(BotContext ctx, List<MembershipInfo> active,
                                     Map<Long, String> schoolNames, boolean includeOwn) {
        List<Long> schoolIds = active.stream().map(MembershipInfo::schoolId).distinct().sorted().toList();
        List<BotTarget> result = new ArrayList<>();
        for (Long schoolId : schoolIds) {
            boolean parent = hasRole(active, schoolId, "PARENT");
            boolean student = includeOwn && hasRole(active, schoolId, "STUDENT");
            if (!parent && !student) {
                continue;
            }
            String schoolName = schoolName(schoolId, schoolNames);
            if (parent) {
                for (StudentInfo ward : academicFacade.listWards(ctx.userId().value(), schoolId)) {
                    String name = iamFacade.findUser(new UserId(ward.userId()))
                            .map(UserProfile::fullName)
                            .orElse(BotTexts.studentFallback(ward.userId()));
                    Long classId = ward.classId() == null ? null : ward.classId().value();
                    result.add(new BotTarget.Student(schoolId, ward.id().value(), ward.userId(), classId,
                            name, schoolName, true));
                }
            }
            if (student) {
                academicFacade.findMyEnrollment(ctx.userId().value(), schoolId).ifPresent(e -> {
                    Long classId = e.classId() == null ? null : e.classId().value();
                    result.add(new BotTarget.Student(schoolId, e.studentId().value(), ctx.userId().value(),
                            classId, ctx.fullName(), schoolName, false));
                });
            }
        }
        return result;
    }

    private List<BotTarget> schools(List<MembershipInfo> active, String roleCode, Map<Long, String> schoolNames) {
        return active.stream()
                .filter(m -> roleCode == null || roleCode.equals(m.roleCode()))
                .map(MembershipInfo::schoolId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .<BotTarget>map(id -> new BotTarget.School(id, schoolName(id, schoolNames)))
                .toList();
    }

    private static boolean hasRole(List<MembershipInfo> active, Long schoolId, String roleCode) {
        return active.stream().anyMatch(m -> schoolId.equals(m.schoolId()) && roleCode.equals(m.roleCode()));
    }

    private String schoolName(Long schoolId, Map<Long, String> cache) {
        return cache.computeIfAbsent(schoolId, id -> schoolFacade.findSchool(new SchoolId(id))
                .map(s -> s.name())
                .orElse(BotTexts.schoolFallback(id)));
    }
}
