package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.PlatformHolidayInfo;
import com.example.roshdeandishe.school.dto.request.AddPlatformHolidayRequest;
import com.example.roshdeandishe.school.dto.response.AcademicYearResponse;
import com.example.roshdeandishe.school.dto.response.PlatformHolidayResponse;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * تعطیلی‌های سراسریِ پلتفرم (openspec/changes/yearly-plan.md، فاز C): فقط مدیر پلتفرم
 * (مجوز PLATFORM_HOLIDAY_MANAGE). این مسیرها «مسیر پلتفرم» هستند: TenantResolverFilter
 * هرگز TenantContext نمی‌گذارد، پس اینجا هیچ TenantContext و هیچ {@code @RequiresModule}
 * نیست (ModuleAccessAspect مفهومِ ماژولِ پلتفرم ندارد) و مجوز در PlatformContext سنجیده می‌شود.
 */
@RestController
public class PlatformHolidayController {

    private final SchoolFacade schoolFacade;

    public PlatformHolidayController(SchoolFacade schoolFacade) {
        this.schoolFacade = schoolFacade;
    }

    @GetMapping("/api/platform/academic-years")
    @RequiresPermission("PLATFORM_HOLIDAY_MANAGE")
    public List<AcademicYearResponse> years() {
        return schoolFacade.listAcademicYears().stream()
                .map(year -> new AcademicYearResponse(year.id().value(), year.code(), year.startDate(), year.endDate()))
                .toList();
    }

    @GetMapping("/api/platform/academic-years/{yearId}/holidays")
    @RequiresPermission("PLATFORM_HOLIDAY_MANAGE")
    public List<PlatformHolidayResponse> list(@PathVariable Long yearId) {
        return schoolFacade.listPlatformHolidays(new AcademicYearId(yearId)).stream()
                .map(PlatformHolidayController::toResponse)
                .toList();
    }

    @PostMapping("/api/platform/academic-years/{yearId}/holidays")
    @RequiresPermission("PLATFORM_HOLIDAY_MANAGE")
    public PlatformHolidayResponse add(@PathVariable Long yearId, @RequestBody AddPlatformHolidayRequest request) {
        if (request == null) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry("روز و عنوان الزامی است."));
        }
        return toResponse(schoolFacade.addPlatformHoliday(new AcademicYearId(yearId), request.day(), request.title()));
    }

    @DeleteMapping("/api/platform/holidays/{id}")
    @RequiresPermission("PLATFORM_HOLIDAY_MANAGE")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        schoolFacade.removePlatformHoliday(id);
        return ResponseEntity.noContent().build();
    }

    private static PlatformHolidayResponse toResponse(PlatformHolidayInfo info) {
        return new PlatformHolidayResponse(info.id(), info.day(), info.title());
    }
}
