package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.BotSettings;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.dto.request.SetBotSettingsRequest;
import com.example.roshdeandishe.school.dto.response.AcademicYearResponse;
import com.example.roshdeandishe.school.dto.response.SchoolSettingsResponse;
import com.example.roshdeandishe.school.dto.response.SchoolSettingsResponse.BotSection;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * نمای یکپارچه‌ی تنظیماتِ مدرسه (school-settings، بخش ۵ طراحی). خواندن هر بخش را فقط به دارنده‌ی مجوزِ همان بخش
 * نشان می‌دهد (بقیه null)؛ نوشتنِ هر تنظیم همچنان در endpointهای خودش است (سال تحصیلی، مالی، نقش‌های یادآوری)
 * و فقط تنظیماتِ ربات اینجا نوشته می‌شود (SCHOOL_SETTINGS_MANAGE).
 */
@RestController
public class SchoolSettingsController {

    private static final Logger log = LoggerFactory.getLogger(SchoolSettingsController.class);

    private static final String ACADEMIC_YEAR_MANAGE = "ACADEMIC_YEAR_MANAGE";
    private static final String FINANCE_MANAGE = "FINANCE_MANAGE";
    private static final String YEARLY_PLAN_MANAGE = "YEARLY_PLAN_MANAGE";
    private static final String SCHOOL_SETTINGS_MANAGE = "SCHOOL_SETTINGS_MANAGE";

    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    public SchoolSettingsController(SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/admin/schools/{schoolId}/settings")
    public SchoolSettingsResponse settings(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        boolean year = holds(schoolId, ACADEMIC_YEAR_MANAGE);
        boolean finance = holds(schoolId, FINANCE_MANAGE);
        boolean plan = holds(schoolId, YEARLY_PLAN_MANAGE);
        boolean bot = holds(schoolId, SCHOOL_SETTINGS_MANAGE);
        if (!(year || finance || plan || bot)) {
            // بدون هیچ‌کدام از چهار مجوز چیزی برای نشان‌دادن نیست
            throw new IamException(new IamError.PermissionDenied(SCHOOL_SETTINGS_MANAGE, schoolId,
                    "هیچ‌یک از مجوزهای تنظیمات مدرسه را ندارید."));
        }
        SchoolId school = new SchoolId(schoolId);
        AcademicYearResponse currentYear = !year ? null : schoolFacade.findCurrentAcademicYear(school)
                .map(info -> new AcademicYearResponse(
                        info.id().value(), info.code(), info.startDate(), info.endDate()))
                .orElse(null);
        Boolean autoPromotion = year ? schoolFacade.isAutoPromotion(school) : null;
        Boolean installments = finance ? schoolFacade.isInstallmentsEnabled(school) : null;
        // فهرستِ خالی = همه‌ی نقش‌ها (انتخابی ذخیره نشده)
        List<String> reminderRoles = !plan ? null : schoolFacade.reminderRoleCodes(school)
                .map(SchoolSettingsController::sorted).orElse(List.of());
        return new SchoolSettingsResponse(currentYear, autoPromotion, installments, reminderRoles,
                bot ? botSection(school) : null);
    }

    @PutMapping("/api/admin/schools/{schoolId}/settings/bot")
    @RequiresPermission(SCHOOL_SETTINGS_MANAGE)
    public BotSection setBot(@PathVariable Long schoolId, @RequestBody(required = false) SetBotSettingsRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null || request.enabled() == null) {
            throw new SchoolException(new SchoolError.InvalidSetting("مقدار روشن/خاموش الزامی است."));
        }
        Set<String> roles = SchoolRoleCodes.validate(iamFacade, schoolId,
                request.roles() == null ? List.<String>of() : request.roles());
        SchoolId school = new SchoolId(schoolId);
        schoolFacade.setBotSettings(school, request.enabled(), roles);
        // ثبت در لاگ (جدول audit فقط مالی است)؛ بدون متن کاربر
        log.info("Bot settings changed: schoolId={}, enabled={}, roles={}", schoolId, request.enabled(), sorted(roles));
        return botSection(school);
    }

    private BotSection botSection(SchoolId school) {
        BotSettings settings = schoolFacade.getBotSettings(school);
        return new BotSection(settings.enabled(), sorted(settings.roles()));
    }

    private static List<String> sorted(Set<String> codes) {
        return List.copyOf(new TreeSet<>(codes));
    }

    private boolean holds(Long schoolId, String code) {
        return iamFacade.checkPermission(iamFacade.currentUserId(), new IamFacade.PermissionCode(code),
                new AccessContext.SchoolContext(schoolId)).isGranted();
    }
}
