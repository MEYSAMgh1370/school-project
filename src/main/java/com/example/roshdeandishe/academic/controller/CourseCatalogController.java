package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.dto.response.CourseCatalogResponse;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * فهرست درس‌های یک پایه برای انتخابگرِ درس در بانک سؤال، آزمون و برنامه‌ی مطالعه. فقط‌خواندنی و بدون
 * دانش‌آموز: همان مجوز CURRICULUM_VIEW که درختِ سرفصل را می‌خواند (SCHOOL_ADMIN و TEACHER).
 * {@code topicId} هر درس همان شناسه‌ای است که به {@code /topics/{topicId}/subtree} داده می‌شود.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/courses")
@RequiresModule("CURRICULUM")
public class CourseCatalogController {

    private final AcademicFacade academicFacade;

    public CourseCatalogController(AcademicFacade academicFacade) {
        this.academicFacade = academicFacade;
    }

    @GetMapping
    @RequiresPermission("CURRICULUM_VIEW")
    public List<CourseCatalogResponse> listCourses(@PathVariable Long schoolId,
                                                   @RequestParam int gradeLevel,
                                                   @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        return academicFacade.listCourseCatalog(schoolId, gradeLevel, academicYear).stream()
                .map(c -> new CourseCatalogResponse(c.id().value(), c.gradeLevel(), c.name(), c.track(), c.topicId()))
                .toList();
    }
}
