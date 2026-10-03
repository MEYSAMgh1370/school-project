package com.example.roshdeandishe.finance.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.dto.MyInvoiceResponse;
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
 * عمداً روی نقش شرط نمی‌گذارد و اجتماع هر دو را برمی‌گرداند - یک کاربر
 * می‌تواند هم‌زمان STUDENT و PARENT باشد.
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
        // عضوِ سابق (هیچ عضویتِ فعالی در این مدرسه) چیزی نمی‌گیرد؛ نقش مهم نیست
        if (!iamFacade.hasActiveMembership(new IamFacade.UserId(userId), schoolId)) {
            return List.of();
        }

        List<Long> studentIds = new ArrayList<>();
        academicFacade.findMyEnrollment(userId, schoolId)
                .ifPresent(enrollment -> studentIds.add(enrollment.studentId().value()));
        academicFacade.listWards(userId, schoolId)
                .forEach(ward -> studentIds.add(ward.id().value()));

        return financeFacade.listInvoicesForStudents(schoolId, studentIds).stream()
                .map(MyInvoiceResponse::from)
                .toList();
    }
}
