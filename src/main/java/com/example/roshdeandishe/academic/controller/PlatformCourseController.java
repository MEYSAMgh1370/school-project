package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.CourseInfo;
import com.example.roshdeandishe.academic.dto.request.UpsertPlatformCourseRequest;
import com.example.roshdeandishe.academic.dto.response.CourseResponse;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import org.springframework.web.bind.annotation.*;

/**
 * نوشتنِ درس‌های سراسریِ پلتفرمی (openspec/changes/moshavere-content-import.md): فقط مدیر پلتفرم، با همان گیتِ
 * PlatformHolidayController (مسیر پلتفرم: بدون TenantContext و بدون {@code @RequiresModule}).
 */
@RestController
public class PlatformCourseController {

    private final AcademicFacade academicFacade;

    public PlatformCourseController(AcademicFacade academicFacade) {
        this.academicFacade = academicFacade;
    }

    @PostMapping("/api/platform/academic/courses")
    @RequiresPermission("PLATFORM_HOLIDAY_MANAGE")
    public CourseResponse upsert(@RequestBody UpsertPlatformCourseRequest request) {
        if (request == null) {
            throw new AcademicException(new AcademicError.InvalidCourse("پایه و نام درس الزامی است."));
        }
        CourseInfo info = academicFacade.upsertPlatformCourse(
                request.gradeLevel(), request.name(), request.track(), request.topicId());
        return new CourseResponse(info.id().value(), info.gradeLevel(), info.name());
    }
}
