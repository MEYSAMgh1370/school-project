package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.dto.MyGradeResponse;
import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * «نمره‌های من/فرزندانم در این مدرسه» - همان مدل اعتماد
 * {@code finance.MyInvoicesController}/{@code academic.MyEnrollmentController}:
 * عمداً هیچ {@code @RequiresPermission}/{@code @RequiresModule} ندارد، چون
 * داده فقط درباره‌ی خودِ کاربر احراز هویت‌شده است - دانش‌آموزیِ خودش
 * (findMyEnrollment) به‌علاوه‌ی فرزندانِ واقعاً لینک‌شده‌اش (listWards).
 * هر بخش به نقشِ فعالِ خودش بسته است (دانش‌آموزیِ خودش: STUDENT، فرزندان: PARENT) و اجتماعِ هر دو برمی‌گردد -
 * یک کاربر می‌تواند هم‌زمان STUDENT و PARENT باشد.
 * <p>
 * مسیر با پیشوند /api/me است، پس مثل بقیه‌ی /api/me/** در
 * TenantResolverFilter مستثناست.
 */
@RestController
public class MyGradesController {

    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;
    private final AcademicYearVisibility yearVisibility;

    public MyGradesController(AcademicFacade academicFacade, IamFacade iamFacade,
                              AcademicYearVisibility yearVisibility) {
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
        this.yearVisibility = yearVisibility;
    }

    @GetMapping("/api/me/schools/{schoolId}/grades")
    public List<MyGradeResponse> myGrades(@PathVariable Long schoolId) {
        Long userId = iamFacade.currentUserId().value();
        // هر منبع به نقشِ فعالِ خودش بسته است: ثبت‌نام و لینکِ ولی با غیرفعال‌شدنِ عضویت پاک نمی‌شوند،
        // پس عضوِ سابق (یا کسی که فقط نقشِ دیگری دارد) آن بخش را نمی‌گیرد.
        java.util.Set<String> activeRoles = iamFacade.findActiveRoleCodes(new IamFacade.UserId(userId), schoolId);

        List<Long> studentIds = new ArrayList<>();
        if (activeRoles.contains("STUDENT")) {
            academicFacade.findMyEnrollment(userId, schoolId)
                    .ifPresent(enrollment -> studentIds.add(enrollment.studentId().value()));
        }
        if (activeRoles.contains("PARENT")) {
            academicFacade.listWards(userId, schoolId)
                    .forEach(ward -> studentIds.add(ward.id().value()));
        }
        if (studentIds.isEmpty()) {
            return List.of();
        }

        // غیرِ مدیر فقط نمره‌های سالِ جاریِ مدرسه را می‌بیند (academic-year.md فاز ۱، تصمیم ۵: کارنامه‌ی
        // سال‌های قبل را مدیر می‌دهد)؛ مدرسه‌ی بدونِ سالِ جاری محدود نمی‌شود
        java.util.Optional<String> onlyYear = yearVisibility.restrictedYear(schoolId);
        return academicFacade.listGradesForStudents(schoolId, studentIds).stream()
                .filter(grade -> onlyYear.isEmpty() || onlyYear.get().equals(grade.academicYear()))
                .map(MyGradeResponse::from)
                .toList();
    }
}
