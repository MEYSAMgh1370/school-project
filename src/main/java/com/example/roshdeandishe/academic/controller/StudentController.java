package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.GuardianLinkId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.academic.dto.request.EnrollStudentRequest;
import com.example.roshdeandishe.academic.dto.response.GuardianLinkResponse;
import com.example.roshdeandishe.academic.dto.request.LinkGuardianRequest;
import com.example.roshdeandishe.academic.dto.response.StudentResponse;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ثبت‌نام دانش‌آموز در یک کلاس. طبق تصمیم صریح، این عمل هم برای
 * SCHOOL_ADMIN و هم TEACHER مجاز است (بر خلاف ساخت کلاس که فقط ادمین
 * مجاز است - رجوع کنید به ClassController).
 * <p>
 * {@code @RequiresModule("ACADEMIC")} در سطح کلاس: همان لایه‌ی اول
 * کنترل دسترسی دولایه - رجوع کنید به توضیح مشابه در ClassController.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/students")
@RequiresModule("ACADEMIC")
public class StudentController {

    private final AcademicFacade academicFacade;
    private final AcademicYearVisibility yearVisibility;

    public StudentController(AcademicFacade academicFacade, AcademicYearVisibility yearVisibility) {
        this.academicFacade = academicFacade;
        this.yearVisibility = yearVisibility;
    }

    @PostMapping
    @RequiresPermission("STUDENT_ENROLL")
    public StudentResponse enrollStudent(@PathVariable Long schoolId, @RequestBody EnrollStudentRequest request) {
        TenantContext.requireMatches(schoolId);

        StudentId studentId = academicFacade.enrollStudent(request.email(), request.fullName(), schoolId, request.classId(),
                request.track());
        StudentInfo info = academicFacade.getStudentInfo(schoolId, studentId.value());
        return toResponse(info);
    }

    @GetMapping("/{studentId}")
    @RequiresPermission("STUDENT_VIEW")
    public StudentResponse getStudent(@PathVariable Long schoolId, @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        return toResponse(academicFacade.getStudentInfo(schoolId, studentId));
    }

    /**
     * جدا از STUDENT_ENROLL (UX_ROADMAP.md §6.4، همان الگوی CLASS_VIEW/
     * CLASS_MANAGE در ClassController) - این نقش‌ها را قادر می‌کند فهرست
     * دانش‌آموزها را فقط بخوانند (مثلاً برای resolve کردن نام روی /finance)
     * بدون این‌که اجازه‌ی ثبت‌نام (نوشتن) داشته باشند.
     */
    @GetMapping
    @RequiresPermission("STUDENT_VIEW")
    public List<StudentResponse> listStudents(@PathVariable Long schoolId,
                                              @RequestParam(required = false) Long classId,
                                              @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        // با classId و سال: دانش‌آموزانِ آن کلاس در آن سال (دفتر نمرات - همان قاعده‌ی ثبت نمره).
        // با classId بدون سال: همه‌ی کسانی که هرگز در آن کلاس بوده‌اند (سازگاری با پیش از این).
        if (academicYear != null) {
            yearVisibility.requireVisible(schoolId, academicYear);
        }
        return (classId == null ? academicFacade.listStudents(schoolId)
                : academicYear != null
                ? academicFacade.listStudentsInClass(schoolId, classId, academicYear)
                : academicFacade.listStudentsInClass(schoolId, classId)).stream()
                .map(StudentController::toResponse)
                .toList();
    }


    /**
     * لینک کردن یک کاربر (که باید از قبل Membership فعالِ نقش PARENT در
     * همین مدرسه داشته باشد - AcademicFacade اعتبارسنجی می‌کند) به‌عنوان
     * ولیِ این دانش‌آموز. سطح حساسیتِ این عمل هم‌تراز مدیریت عضویت است، نه
     * ثبت‌نامِ روزمره - پس GUARDIAN_MANAGE عمداً فقط به SCHOOL_ADMIN داده
     * شده (بر خلاف STUDENT_ENROLL که TEACHER هم دارد).
     */
    @PostMapping("/{studentId}/guardians")
    @RequiresPermission("GUARDIAN_MANAGE")
    public GuardianLinkResponse linkGuardian(
            @PathVariable Long schoolId, @PathVariable Long studentId, @RequestBody LinkGuardianRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        GuardianLinkId linkId = academicFacade.linkGuardian(schoolId, studentId, request.guardianUserId());
        return new GuardianLinkResponse(linkId.value(), studentId, request.guardianUserId());
    }

    @GetMapping("/{studentId}/guardians")
    @RequiresPermission("GUARDIAN_MANAGE")
    public List<Long> listGuardians(@PathVariable Long schoolId, @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        return academicFacade.listGuardians(schoolId, studentId);
    }

    private static StudentResponse toResponse(StudentInfo info) {
        return new StudentResponse(info.id().value(), info.userId(), info.schoolId(),
                info.classId() == null ? null : info.classId().value(), info.status());
    }
}
