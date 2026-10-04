package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.ClassInfo;
import com.example.roshdeandishe.academic.dto.ClassResponse;
import com.example.roshdeandishe.academic.dto.CreateClassRequest;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * مدیریت کلاس‌های یک مدرسه. طبق تصمیم صریح، ساخت کلاس یک تصمیم
 * اداری/چیدمانی است و فقط SCHOOL_ADMIN مجاز است (بر خلاف ثبت‌نام
 * دانش‌آموز که TEACHER هم مجاز است - رجوع کنید به StudentController).
 * <p>
 * فهرست کلاس‌ها (GET) عمداً Permission جدایی دارد: CLASS_VIEW، نه
 * CLASS_MANAGE. بدون این جدایی، TEACHER (که STUDENT_ENROLL دارد ولی
 * CLASS_MANAGE ندارد) نمی‌توانست فهرست کلاس‌ها را برای پر کردن دراپ‌داون
 * انتخاب کلاس در صفحه‌ی ثبت‌نام (/students) ببیند - یک باگ واقعی که در
 * بازبینی بعد از ساخت آن صفحه پیدا شد. CLASS_VIEW به SCHOOL_ADMIN و
 * TEACHER هر دو داده می‌شود؛ CLASS_MANAGE (ساخت کلاس) فقط SCHOOL_ADMIN.
 * <p>
 * {@code @RequiresModule("ACADEMIC")} در سطح کلاس: همه‌ی این عملیات فقط
 * وقتی معنا دارند که مدرسه ماژول ACADEMIC را خریده باشد (لایه‌ی اول از
 * کنترل دسترسی دولایه؛ لایه‌ی دوم همان @RequiresPermission روی هر متد است).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/classes")
@RequiresModule("ACADEMIC")
public class ClassController {

    private final AcademicFacade academicFacade;
    private final AcademicYearVisibility yearVisibility;

    public ClassController(AcademicFacade academicFacade, AcademicYearVisibility yearVisibility) {
        this.academicFacade = academicFacade;
        this.yearVisibility = yearVisibility;
    }

    @PostMapping
    @RequiresPermission("CLASS_MANAGE")
    public ClassResponse createClass(@PathVariable Long schoolId, @RequestBody CreateClassRequest request) {
        TenantContext.requireMatches(schoolId);

        ClassId classId = academicFacade.createClass(
                schoolId, request.name(), request.teacherId(), request.section(), request.gradeLevel(),
                request.gradingScheme(), request.academicYear());
        return toResponse(academicFacade.listClasses(schoolId).stream()
                .filter(c -> c.id().equals(classId)).findFirst().orElseThrow());
    }

    @GetMapping
    @RequiresPermission("CLASS_VIEW")
    public List<ClassResponse> listClasses(@PathVariable Long schoolId,
                                           @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        // پیش‌فرض سالِ جاری (به‌علاوه‌ی کلاس‌های بدون سال)؛ "all" فقط برای مدیر (academic-year.md فاز ۳)
        return yearVisibility.classListYear(schoolId, academicYear)
                .map(year -> academicFacade.listClasses(schoolId, year))
                .orElseGet(() -> academicFacade.listClasses(schoolId)).stream()
                .map(ClassController::toResponse)
                .toList();
    }


    private static ClassResponse toResponse(ClassInfo info) {
        return new ClassResponse(info.id().value(), info.schoolId(), info.name(), info.teacherId(), info.section(),
                info.gradeLevel(), info.gradingScheme(), info.academicYear());
    }
}
