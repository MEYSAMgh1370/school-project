package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.dto.AcademicYearResponse;
import com.example.roshdeandishe.school.dto.AcademicYearSettingsResponse;
import com.example.roshdeandishe.school.dto.MyAcademicYearResponse;
import com.example.roshdeandishe.school.dto.SetAcademicYearRequest;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * سال تحصیلی جاریِ مدرسه (openspec/changes/academic-year.md، فاز ۰).
 * <p>
 * دو دید، طبق تصمیم محصول: <b>هر عضو فعالِ مدرسه فقط سال جاری را می‌بیند</b>
 * (GET /api/me/..، بدون مجوزِ جداگانه، schoolId از path)؛ <b>مدیر</b> (مجوز
 * ACADEMIC_YEAR_MANAGE) فهرستِ همه‌ی سال‌ها را هم می‌بیند و سال جاری را از میان
 * سال‌های بازِ فهرست انتخاب می‌کند (GET/PUT /api/admin/schools/{id}/academic-year).
 * فهرستِ سال‌ها سراسری و فقط‌خواندنی است (اپراتور تعریف و باز می‌کند)؛ سال تاریخ
 * ندارد و شروع/پایانش همین انتخابِ مدیر است. هیچ داده‌ای با این انتخاب حذف یا
 * تغییر نمی‌کند.
 */
@RestController
public class AcademicYearController {

    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    public AcademicYearController(SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/me/schools/{schoolId}/academic-year")
    public MyAcademicYearResponse myAcademicYear(@PathVariable Long schoolId) {
        // مثل SchoolController.getSchool: فقط عضو فعالِ همین مدرسه؛ بیگانه نباید
        // بتواند تنظیمات مدرسه‌ی دیگری را بخواند
        IamFacade.UserId callerId = iamFacade.currentUserId();
        MembershipInfo membership = iamFacade.findMembership(callerId, schoolId)
                .orElseThrow(() -> new IamException(new IamError.MembershipNotFound(callerId.value(), schoolId)));
        if (!membership.active()) {
            throw new IamException(new IamError.MembershipNotFound(callerId.value(), schoolId));
        }
        return new MyAcademicYearResponse(current(schoolId));
    }

    @GetMapping("/api/admin/schools/{schoolId}/academic-year")
    @RequiresPermission("ACADEMIC_YEAR_MANAGE")
    public AcademicYearSettingsResponse settings(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return settingsFor(schoolId);
    }

    @PutMapping("/api/admin/schools/{schoolId}/academic-year")
    @RequiresPermission("ACADEMIC_YEAR_MANAGE")
    public AcademicYearSettingsResponse setCurrentAcademicYear(
            @PathVariable Long schoolId, @RequestBody SetAcademicYearRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null || request.academicYearId() == null) {
            throw new SchoolException(new SchoolError.AcademicYearNotFound(null));
        }
        schoolFacade.setCurrentAcademicYear(new SchoolId(schoolId), new AcademicYearId(request.academicYearId()));
        return settingsFor(schoolId);
    }

    private AcademicYearResponse current(Long schoolId) {
        return schoolFacade.findCurrentAcademicYear(new SchoolId(schoolId))
                .map(AcademicYearController::toResponse)
                .orElse(null);
    }

    private AcademicYearSettingsResponse settingsFor(Long schoolId) {
        List<AcademicYearResponse> years = schoolFacade.listAcademicYears().stream()
                .map(AcademicYearController::toResponse)
                .toList();
        return new AcademicYearSettingsResponse(current(schoolId), years);
    }

    private static AcademicYearResponse toResponse(AcademicYearInfo info) {
        return new AcademicYearResponse(info.id().value(), info.code(), info.open());
    }
}
