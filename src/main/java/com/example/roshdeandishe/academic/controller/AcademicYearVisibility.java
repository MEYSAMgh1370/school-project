package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.academic.shared.AcademicYearCodes;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * «چه کسی کدام سال را می‌بیند» (openspec/changes/academic-year.md، تصمیم‌های ۱، ۲ و ۵): دارنده‌ی
 * {@code ACADEMIC_YEAR_MANAGE} (مدیر مدرسه) هر سالِ فهرست را می‌بیند و اصلاح می‌کند؛ بقیه (معلم، دانش‌آموز،
 * ولی) فقط سالِ جاریِ مدرسه را. فرض: مدرسه‌ای که هنوز سالِ جاری انتخاب نکرده محدود نمی‌شود، وگرنه نمره‌های
 * همه‌ی مدرسه‌های موجود تا انتخابِ سال پنهان می‌شد؛ محدودیت از روزِ انتخابِ سال شروع می‌شود.
 */
@Component
public class AcademicYearVisibility {

    private static final IamFacade.PermissionCode MANAGE_YEARS = new IamFacade.PermissionCode("ACADEMIC_YEAR_MANAGE");

    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;

    public AcademicYearVisibility(IamFacade iamFacade, SchoolFacade schoolFacade) {
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
    }

    /** سالِ جاریِ مدرسه اگر کاربرِ فعلی فقط آن را می‌بیند؛ خالی = محدودیتی نیست. */
    public Optional<String> restrictedYear(Long schoolId) {
        Optional<AcademicYearInfo> current = schoolFacade.findCurrentAcademicYear(new SchoolId(schoolId));
        if (current.isEmpty()) {
            return Optional.empty();
        }
        AccessDecision decision = iamFacade.checkPermission(
                iamFacade.currentUserId(), MANAGE_YEARS, new AccessContext.SchoolContext(schoolId));
        return decision.isGranted() ? Optional.empty() : Optional.of(current.get().code());
    }

    /**
     * سالِ فیلترِ فهرستِ کلاس‌ها (academic-year.md فاز ۳). {@code requested} تهی = سالِ جاریِ مدرسه؛
     * {@code "all"} = همه‌ی سال‌ها (فقط کسی که هر سالی را می‌بیند)؛ سالِ مشخص = همان سال (برای بقیه فقط
     * سالِ جاری، وگرنه AcademicYearNotAllowed). خالی در پاسخ یعنی «همه‌ی سال‌ها»، که برای مدرسه‌ی بدون سالِ
     * جاری هم همین است.
     */
    public Optional<String> classListYear(Long schoolId, String requested) {
        Optional<String> restricted = restrictedYear(schoolId);
        if ("all".equals(requested)) {
            if (restricted.isPresent()) {
                throw new AcademicException(new AcademicError.AcademicYearNotAllowed(requested));
            }
            return Optional.empty();
        }
        if (requested == null || requested.isBlank()) {
            return schoolFacade.findCurrentAcademicYear(new SchoolId(schoolId)).map(AcademicYearInfo::code);
        }
        requireVisible(schoolId, requested);
        return Optional.of(AcademicYearCodes.canonical(requested));
    }

    /** AcademicYearNotAllowed (403) اگر کاربر این سال را نمی‌بیند. */
    public void requireVisible(Long schoolId, String academicYear) {
        if (academicYear == null || academicYear.isBlank()) {
            // سالِ خالی خطای خودِ facade است (400 «سال تحصیلی الزامی است»)، نه «دسترسی ممنوع» برای سالِ «null»
            return;
        }
        restrictedYear(schoolId).ifPresent(currentCode -> {
            if (!currentCode.equals(AcademicYearCodes.canonical(academicYear))) {
                throw new AcademicException(new AcademicError.AcademicYearNotAllowed(academicYear));
            }
        });
    }
}
