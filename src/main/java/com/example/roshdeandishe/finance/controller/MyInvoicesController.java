package com.example.roshdeandishe.finance.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.dto.response.MyInvoiceResponse;
import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * «فاکتورهای من/فرزندانم در این مدرسه» - همان مدل اعتماد
 * {@code iam.MeController}/{@code academic.MyEnrollmentController}: عمداً
 * هیچ {@code @RequiresPermission}/{@code @RequiresModule} ندارد، چون داده
 * فقط درباره‌ی خودِ کاربر احراز هویت‌شده است - دانش‌آموزیِ خودش
 * (AcademicFacade.findMyEnrollment) به‌علاوه‌ی فرزندانِ واقعاً لینک‌شده‌اش
 * (AcademicFacade.listWards، رجوع به StudentGuardianEntity در CLAUDE.md §4).
 * هر بخش به نقشِ فعالِ خودش بسته است (دانش‌آموزیِ خودش: STUDENT، فرزندان: PARENT) و اجتماعِ هر دو برمی‌گردد -
 * یک کاربر می‌تواند هم‌زمان STUDENT و PARENT باشد.
 * <p>
 * تصمیم صریح (CLAUDE.md §10): endpoint ادمینیِ موجود
 * {@code GET /api/admin/schools/{id}/invoices} (همه‌ی فاکتورهای مدرسه برای
 * هر دارنده‌ی FINANCE_VIEW) دست‌نخورده می‌ماند؛ این endpoint مسیر جدا و
 * self-scoped برای والد/دانش‌آموز است، نه محدودسازیِ آن.
 * <p>
 * مسیر با پیشوند /api/me است، پس مثل بقیه‌ی /api/me/** در
 * TenantResolverFilter مستثناست - schoolId از خود path می‌آید (کاربر
 * چندمدرسه‌ای باید صریح بگوید کدام مدرسه)، نه از X-School-ID. در ماژول
 * finance (نه iam کنار MeController) چون iam نمی‌تواند به academic/finance
 * وابسته شود - همان دلیل MyEnrollmentController در academic.
 */
@RestController
public class MyInvoicesController {

    private final FinanceFacade financeFacade;
    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;

    public MyInvoicesController(FinanceFacade financeFacade, AcademicFacade academicFacade, IamFacade iamFacade) {
        this.financeFacade = financeFacade;
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/me/schools/{schoolId}/invoices")
    public List<MyInvoiceResponse> myInvoices(@PathVariable Long schoolId) {
        Long userId = iamFacade.currentUserId().value();
        // هر منبع به نقشِ فعالِ خودش بسته است: ثبت‌نام و لینکِ ولی با غیرفعال‌شدنِ عضویت پاک نمی‌شوند،
        // پس عضوِ سابق (یا کسی که فقط نقشِ دیگری دارد) آن بخش را نمی‌گیرد.
        java.util.Set<String> activeRoles = iamFacade.findActiveRoleCodes(new IamFacade.UserId(userId), schoolId);

        List<Long> studentIds = new ArrayList<>();
        if (activeRoles.contains("STUDENT")) {
            academicFacade.findMyEnrollment(userId, schoolId)
                    .ifPresent(enrollment -> studentIds.add(enrollment.studentId().value()));
        }
        if (activeRoles.contains("PARENT")) {
            academicFacade.listWards(userId, schoolId)
                    .forEach(ward -> studentIds.add(ward.id().value()));
        }
        if (studentIds.isEmpty()) {
            return List.of();
        }

        return financeFacade.listInvoicesForStudents(schoolId, studentIds).stream()
                .map(MyInvoiceResponse::from)
                .toList();
    }
}
