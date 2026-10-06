package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.CalendarEntryInfo;
import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.dto.request.AddCalendarEntryRequest;
import com.example.roshdeandishe.school.dto.response.CalendarEntryResponse;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * تقویم مدرسه (openspec/changes/attendance.md، فاز ۰): روزهای تعطیل (و بعداً رویدادهای
 * برنامه‌ی سالانه). مدیر (مجوز CALENDAR_MANAGE) می‌افزاید، حذف می‌کند و فهرست می‌گیرد؛
 * هر عضو فعالِ مدرسه فقط فهرستِ بازه را می‌بیند (GET /api/me/.., بدون مجوزِ جداگانه).
 * <p>
 * مدرسه ماژولِ entitlement ندارد (هسته‌ی پلتفرم است)، پس مثل «سال تحصیلی» فقط
 * {@code @RequiresPermission} دارد.
 */
@RestController
public class CalendarController {

    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    public CalendarController(SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    @PostMapping("/api/admin/schools/{schoolId}/calendar")
    @RequiresPermission("CALENDAR_MANAGE")
    public CalendarEntryResponse add(@PathVariable Long schoolId, @RequestBody AddCalendarEntryRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry("روز، نوع و عنوان الزامی است."));
        }
        return toResponse(schoolFacade.addCalendarEntry(
                new SchoolId(schoolId), request.day(), parseKind(request.kind()), request.title()));
    }

    @DeleteMapping("/api/admin/schools/{schoolId}/calendar/{entryId}")
    @RequiresPermission("CALENDAR_MANAGE")
    public ResponseEntity<Void> remove(@PathVariable Long schoolId, @PathVariable Long entryId) {
        TenantContext.requireMatches(schoolId);
        schoolFacade.removeCalendarEntry(new SchoolId(schoolId), entryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/schools/{schoolId}/calendar")
    @RequiresPermission("CALENDAR_MANAGE")
    public List<CalendarEntryResponse> list(
            @PathVariable Long schoolId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        TenantContext.requireMatches(schoolId);
        return listFor(schoolId, from, to);
    }

    @GetMapping("/api/me/schools/{schoolId}/calendar")
    public List<CalendarEntryResponse> myCalendar(
            @PathVariable Long schoolId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        // فقط عضو فعالِ همین مدرسه (هر نقشی)؛ هر عضویت مستقلاً فعال/غیرفعال می‌شود، پس
        // همه‌ی عضویت‌های کاربر بررسی می‌شود (مثل AcademicYearController.myAcademicYear)
        IamFacade.UserId callerId = iamFacade.currentUserId();
        boolean activeMember = iamFacade.findMembershipsByUser(callerId).stream()
                .anyMatch(membership -> schoolId.equals(membership.schoolId()) && membership.active());
        if (!activeMember) {
            throw new IamException(new IamError.MembershipNotFound(callerId.value(), schoolId));
        }
        return listFor(schoolId, from, to);
    }

    private List<CalendarEntryResponse> listFor(Long schoolId, LocalDate from, LocalDate to) {
        return schoolFacade.listCalendar(new SchoolId(schoolId), from, to).stream()
                .map(CalendarController::toResponse)
                .toList();
    }

    private static CalendarKind parseKind(String kind) {
        try {
            return CalendarKind.valueOf(kind == null ? "" : kind.trim());
        } catch (IllegalArgumentException e) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry("نوع ورودی تقویم باید CLOSED یا EVENT باشد."));
        }
    }

    private static CalendarEntryResponse toResponse(CalendarEntryInfo info) {
        return new CalendarEntryResponse(info.id(), info.day(), info.kind().name(), info.title());
    }
}
