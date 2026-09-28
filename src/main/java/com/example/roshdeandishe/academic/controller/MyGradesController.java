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
 * عمداً روی نقش شرط نمی‌گذارد و اجتماع هر دو را برمی‌گرداند - یک کاربر
 * می‌تواند هم‌زمان STUDENT و PARENT باشد.
 * <p>
 * مسیر با پیشوند /api/me است، پس مثل بقیه‌ی /api/me/** در
 * TenantResolverFilter مستثناست.
 */
@RestController
public class MyGradesController {

    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;

    public MyGradesController(AcademicFacade academicFacade, IamFacade iamFacade) {
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/me/schools/{schoolId}/grades")
    public List<MyGradeResponse> myGrades(@PathVariable Long schoolId) {
        Long userId = iamFacade.currentUserId().value();

        List<Long> studentIds = new ArrayList<>();
        academicFacade.findMyEnrollment(userId, schoolId)
                .ifPresent(enrollment -> studentIds.add(enrollment.studentId().value()));
        academicFacade.listWards(userId, schoolId)
                .forEach(ward -> studentIds.add(ward.id().value()));

        return academicFacade.listGradesForStudents(schoolId, studentIds).stream()
                .map(MyGradeResponse::from)
                .toList();
    }
}
