package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.CalendarEntryInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlan;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanItem;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanRange;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanSource;
import com.example.roshdeandishe.school.dto.request.AddYearlyPlanEventRequest;
import com.example.roshdeandishe.school.dto.request.SetYearlyPlanRangeRequest;
import com.example.roshdeandishe.school.dto.response.AcademicYearResponse;
import com.example.roshdeandishe.school.dto.response.YearlyPlanItemResponse;
import com.example.roshdeandishe.school.dto.response.YearlyPlanRangeResponse;
import com.example.roshdeandishe.school.dto.response.YearlyPlanResponse;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * برنامه‌ی سالانه‌ی مدرسه (openspec/changes/yearly-plan.md، فاز A و B): رویدادهای تک‌روزه‌ی
 * یک سال تحصیلی (EVENT در تقویم) به‌علاوه‌ی تعطیلی‌های سراسری پلتفرم. هر عضو فعالِ مدرسه
 * می‌خواند (GET /api/me/..)؛ مدیر (مجوز YEARLY_PLAN_MANAGE) رویداد می‌افزاید و حذف می‌کند.
 * تعطیلیِ سراسری از اینجا قابل حذف نیست.
 * <p>
 * مدرسه ماژولِ entitlement ندارد (هسته‌ی پلتفرم است)، پس مثل تقویم فقط
 * {@code @RequiresPermission} دارد.
 */
@RestController
public class YearlyPlanController {

    private static final IamFacade.PermissionCode MANAGE_YEARS = new IamFacade.PermissionCode("ACADEMIC_YEAR_MANAGE");

    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    public YearlyPlanController(SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/me/schools/{schoolId}/yearly-plan")
    public YearlyPlanResponse myYearlyPlan(
            @PathVariable Long schoolId, @RequestParam(required = false) Long academicYearId) {
        // فقط عضو فعالِ همین مدرسه (هر نقشی)؛ مثل CalendarController.myCalendar
        IamFacade.UserId callerId = iamFacade.currentUserId();
        boolean activeMember = iamFacade.findMembershipsByUser(callerId).stream()
                .anyMatch(membership -> schoolId.equals(membership.schoolId()) && membership.active());
        if (!activeMember) {
            throw new IamException(new IamError.MembershipNotFound(callerId.value(), schoolId));
        }
        SchoolId school = new SchoolId(schoolId);
        Optional<AcademicYearInfo> current = schoolFacade.findCurrentAcademicYear(school);
        AcademicYearId year;
        if (academicYearId == null) {
            if (current.isEmpty()) {
                // مدرسه هنوز سالِ جاری ندارد؛ صفحه حالتِ خالی را نشان می‌دهد
                return new YearlyPlanResponse(null, null, List.of());
            }
            year = current.get().id();
        } else {
            // همان قاعده‌ی «چه کسی کدام سال را می‌بیند» (academic-year.md): مدیرِ سال (ACADEMIC_YEAR_MANAGE)
            // هر سالی را می‌بیند، بقیه فقط سالِ جاری؛ مدرسه‌ی بدون سالِ جاری محدود نیست
            year = new AcademicYearId(academicYearId);
            if (current.isPresent() && !current.get().id().equals(year) && !canSeeAnyYear(schoolId)) {
                throw new SchoolException(new SchoolError.AcademicYearNotAllowed(academicYearId));
            }
        }
        return toResponse(schoolFacade.yearlyPlan(school, year));
    }

    @PostMapping("/api/admin/schools/{schoolId}/yearly-plan")
    @RequiresPermission("YEARLY_PLAN_MANAGE")
    public YearlyPlanItemResponse add(@PathVariable Long schoolId, @RequestBody AddYearlyPlanEventRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry("سال، روز و عنوان الزامی است."));
        }
        AcademicYearId year = request.academicYearId() == null ? null : new AcademicYearId(request.academicYearId());
        CalendarEntryInfo created = schoolFacade.addYearlyPlanEvent(
                new SchoolId(schoolId), year, request.day(), request.title());
        return new YearlyPlanItemResponse(created.id(), created.day(), created.title(), YearlyPlanSource.SCHOOL.name());
    }

    @PutMapping("/api/admin/schools/{schoolId}/yearly-plan/range")
    @RequiresPermission("YEARLY_PLAN_MANAGE")
    public YearlyPlanRangeResponse setRange(
            @PathVariable Long schoolId, @RequestBody SetYearlyPlanRangeRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null) {
            throw new SchoolException(new SchoolError.InvalidYearRange("سال و تاریخ‌های شروع و پایان الزامی است."));
        }
        AcademicYearId year = request.academicYearId() == null ? null : new AcademicYearId(request.academicYearId());
        return toResponse(schoolFacade.setYearlyPlanRange(
                new SchoolId(schoolId), year, request.startDate(), request.endDate()));
    }

    @DeleteMapping("/api/admin/schools/{schoolId}/yearly-plan/{entryId}")
    @RequiresPermission("YEARLY_PLAN_MANAGE")
    public ResponseEntity<Void> remove(@PathVariable Long schoolId, @PathVariable Long entryId) {
        TenantContext.requireMatches(schoolId);
        schoolFacade.removeYearlyPlanEvent(new SchoolId(schoolId), entryId);
        return ResponseEntity.noContent().build();
    }

    private boolean canSeeAnyYear(Long schoolId) {
        return iamFacade.checkPermission(
                iamFacade.currentUserId(), MANAGE_YEARS, new AccessContext.SchoolContext(schoolId)).isGranted();
    }

    private static YearlyPlanResponse toResponse(YearlyPlan plan) {
        AcademicYearInfo year = plan.year();
        return new YearlyPlanResponse(
                new AcademicYearResponse(year.id().value(), year.code(), year.startDate(), year.endDate()),
                toResponse(plan.range()),
                plan.items().stream().map(YearlyPlanController::toResponse).toList());
    }

    private static YearlyPlanRangeResponse toResponse(YearlyPlanRange range) {
        return new YearlyPlanRangeResponse(range.startDate(), range.endDate(), range.custom());
    }

    private static YearlyPlanItemResponse toResponse(YearlyPlanItem item) {
        return new YearlyPlanItemResponse(item.id(), item.day(), item.title(), item.source().name());
    }
}
