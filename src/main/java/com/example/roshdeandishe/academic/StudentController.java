package com.example.roshdeandishe.academic;

import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

/**
 * ثبت‌نام دانش‌آموز در یک کلاس. طبق تصمیم صریح، این عمل هم برای
 * SCHOOL_ADMIN و هم TEACHER مجاز است (بر خلاف ساخت کلاس که فقط ادمین
 * مجاز است - رجوع کنید به ClassController).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/students")
public class StudentController {

    private final AcademicFacade academicFacade;

    public StudentController(AcademicFacade academicFacade) {
        this.academicFacade = academicFacade;
    }

    @PostMapping
    @RequiresPermission("STUDENT_ENROLL")
    public StudentResponse enrollStudent(@PathVariable Long schoolId, @RequestBody EnrollStudentRequest request) {
        TenantContext.requireMatches(schoolId);

        StudentId studentId = academicFacade.enrollStudent(request.email(), request.fullName(), schoolId, request.classId());
        StudentInfo info = academicFacade.getStudentInfo(studentId.value());
        return toResponse(info);
    }

    @GetMapping("/{studentId}")
    @RequiresPermission("STUDENT_ENROLL")
    public StudentResponse getStudent(@PathVariable Long schoolId, @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        return toResponse(academicFacade.getStudentInfo(studentId));
    }


    private static StudentResponse toResponse(StudentInfo info) {
        return new StudentResponse(info.id().value(), info.userId(), info.schoolId(), info.classId().value());
    }

    public record EnrollStudentRequest(String email, String fullName, Long classId) {
    }

    public record StudentResponse(Long studentId, Long userId, Long schoolId, Long classId) {
    }
}
