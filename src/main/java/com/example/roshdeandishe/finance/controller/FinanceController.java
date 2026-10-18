package com.example.roshdeandishe.finance.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.DiscountInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.dto.request.ApplyDiscountRequest;
import com.example.roshdeandishe.finance.dto.request.GenerateInstallmentsRequest;
import com.example.roshdeandishe.finance.dto.request.SetInstallmentsRequest;
import com.example.roshdeandishe.finance.dto.request.UpdateInstallmentsRequest;
import com.example.roshdeandishe.finance.dto.response.InstallmentResponse;
import com.example.roshdeandishe.finance.dto.response.DiscountResponse;
import com.example.roshdeandishe.finance.dto.response.FinanceSettingsResponse;
import com.example.roshdeandishe.finance.FinanceFacade.PaymentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.TuitionFeeInfo;
import com.example.roshdeandishe.finance.dto.request.AttributeYearRequest;
import com.example.roshdeandishe.finance.dto.response.AttributionResponse;
import com.example.roshdeandishe.finance.dto.response.BackfillResponse;
import com.example.roshdeandishe.finance.dto.response.FinanceYearsResponse;
import com.example.roshdeandishe.finance.dto.response.UnattributedResponse;
import com.example.roshdeandishe.finance.dto.response.InvoiceResponse;
import com.example.roshdeandishe.finance.dto.response.PaymentResponse;
import com.example.roshdeandishe.finance.dto.response.StudentFinancePageResponse;
import com.example.roshdeandishe.finance.dto.response.StudentFinanceRowResponse;
import com.example.roshdeandishe.finance.dto.request.RecordPaymentRequest;
import com.example.roshdeandishe.finance.dto.request.SetTuitionFeeRequest;
import com.example.roshdeandishe.finance.dto.response.TuitionFeeResponse;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * مدیریت مالی یک مدرسه: تنظیم شهریه‌ی مصوب و مشاهده‌ی فاکتورها.
 * <p>
 * اولین کنترلری که هر دو لایه‌ی کنترل دسترسی §5 را کامل دارد:
 * {@code @RequiresModule("FINANCE")} در سطح کلاس (مدرسه اصلاً ماژول مالی
 * را خریده؟) و {@code @RequiresPermission} در سطح متد (نقش این کاربر این
 * عمل خاص را مجاز می‌داند؟). ماژول FINANCE برای مدرسه‌ی نمونه در
 * IamSeedDataRunner فعال seed می‌شود تا جریان dev از ابتدا کار کند.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}")
@RequiresModule("FINANCE")
public class FinanceController {

    private static final IamFacade.PermissionCode VIEW_ALL = new IamFacade.PermissionCode("FINANCE_VIEW_ALL");

    private final FinanceFacade financeFacade;
    private final IamFacade iamFacade;
    private final AcademicFacade academicFacade;
    private final SchoolFacade schoolFacade;

    public FinanceController(FinanceFacade financeFacade, IamFacade iamFacade, AcademicFacade academicFacade,
                             SchoolFacade schoolFacade) {
        this.financeFacade = financeFacade;
        this.iamFacade = iamFacade;
        this.academicFacade = academicFacade;
        this.schoolFacade = schoolFacade;
    }

    @PutMapping("/tuition-fee")
    @RequiresPermission("FINANCE_MANAGE")
    public TuitionFeeResponse setTuitionFee(@PathVariable Long schoolId, @RequestBody SetTuitionFeeRequest request,
                                            @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        // سالِ خالی = سالِ جاری (مدرسه‌ای که سالِ جاری ندارد: بدون سال، مثل پیش از فاز ۲)
        financeFacade.setTuitionFee(schoolId, academicYear, request.amount());
        TuitionFeeInfo info = financeFacade.getTuitionFee(schoolId, academicYear).orElseThrow();
        return new TuitionFeeResponse(schoolId, info.amount(), info.academicYear());
    }

    @GetMapping("/tuition-fee")
    @RequiresPermission("FINANCE_VIEW")
    public TuitionFeeResponse getTuitionFee(@PathVariable Long schoolId,
                                            @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        TuitionFeeInfo info = financeFacade.getTuitionFee(schoolId, academicYear)
                .orElseThrow(() -> new FinanceException(new FinanceError.TuitionFeeNotSet(schoolId)));
        return new TuitionFeeResponse(info.schoolId(), info.amount(), info.academicYear());
    }

    @GetMapping("/invoices")
    @RequiresPermission("FINANCE_VIEW")
    public List<InvoiceResponse> listInvoices(@PathVariable Long schoolId,
                                              @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        // ادمین/مدیر مالی سالِ دلخواه را می‌بیند (برای ثبتِ پرداخت روی فاکتورِ سال‌های قبل لازم است)؛
        // ولی فقط فاکتورهای فرزندانش را و فقط به شکلِ دیدِ خانواده (سالِ جاری)
        List<InvoiceInfo> invoices = hasSchoolWideFinanceVisibility(schoolId)
                ? financeFacade.listInvoices(schoolId, academicYear)
                : financeFacade.listInvoicesForStudents(schoolId, guardianWardStudentIds(schoolId));

        return invoices.stream()
                .map(FinanceController::toResponse)
                .toList();
    }

    /**
     * تصمیم صریح (CLAUDE.md §10، حل‌شده): دارنده‌ی FINANCE_VIEW بدون قید همه‌ی فاکتورهای مدرسه را
     * نمی‌بیند - PARENT (که FINANCE_VIEW دارد) فقط فاکتورهای فرزندانِ واقعاً لینک‌شده‌اش را. دیدِ کلِ
     * مدرسه با Permissionِ FINANCE_VIEW_ALL است، نه کدِ نقش، تا نقشِ سفارشیِ مالی (مثلاً «حسابدار») هم
     * کار کند (openspec/changes/finance-view-all-permission.md). checkPermission همه‌ی عضویت‌های فعال را
     * می‌سنجد، پس کاربری که هم PARENT است و هم نقشی با FINANCE_VIEW_ALL دارد دیدِ کامل می‌گیرد.
     */
    private boolean hasSchoolWideFinanceVisibility(Long schoolId) {
        return iamFacade.checkPermission(iamFacade.currentUserId(), VIEW_ALL,
                new AccessContext.SchoolContext(schoolId)).isGranted();
    }

    private List<Long> guardianWardStudentIds(Long schoolId) {
        Long userId = iamFacade.currentUserId().value();
        return academicFacade.listWards(userId, schoolId).stream()
                .map(ward -> ward.id().value())
                .toList();
    }

    private void requireInvoiceVisibleToCurrentUser(Long schoolId, Long invoiceId) {
        if (hasSchoolWideFinanceVisibility(schoolId)) {
            return;
        }
        boolean visible = financeFacade.listInvoicesForStudents(schoolId, guardianWardStudentIds(schoolId)).stream()
                .anyMatch(invoice -> invoice.id().equals(invoiceId));
        if (!visible) {
            throw new FinanceException(new FinanceError.InvoiceNotFound(invoiceId));
        }
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    @RequiresPermission("FINANCE_MANAGE")
    public PaymentResponse recordPayment(
            @PathVariable Long schoolId,
            @PathVariable Long invoiceId,
            @RequestBody RecordPaymentRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        PaymentInfo payment = financeFacade.recordPayment(
                schoolId, invoiceId, request.amount(), request.method(), request.note());
        return toResponse(payment);
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    @RequiresPermission("FINANCE_VIEW")
    public List<PaymentResponse> listPayments(@PathVariable Long schoolId, @PathVariable Long invoiceId) {
        TenantContext.requireMatches(schoolId);
        requireInvoiceVisibleToCurrentUser(schoolId, invoiceId);

        return financeFacade.listPayments(schoolId, invoiceId).stream()
                .map(FinanceController::toResponse)
                .toList();
    }

    /** تخفیف روی فاکتور؛ مثل ثبت پرداخت پاسخ 200 می‌دهد و مستقل از تنظیمِ اقساط است. */
    @PostMapping("/invoices/{invoiceId}/discounts")
    @RequiresPermission("FINANCE_MANAGE")
    public DiscountResponse applyDiscount(
            @PathVariable Long schoolId,
            @PathVariable Long invoiceId,
            @RequestBody ApplyDiscountRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        return toResponse(financeFacade.applyDiscount(
                schoolId, invoiceId, request.amount(), request.reason(), request.deadline()));
    }

    @GetMapping("/invoices/{invoiceId}/discounts")
    @RequiresPermission("FINANCE_VIEW")
    public List<DiscountResponse> listDiscounts(@PathVariable Long schoolId, @PathVariable Long invoiceId) {
        TenantContext.requireMatches(schoolId);
        requireInvoiceVisibleToCurrentUser(schoolId, invoiceId);

        return financeFacade.listDiscounts(schoolId, invoiceId).stream()
                .map(FinanceController::toResponse)
                .toList();
    }

    @GetMapping("/invoices/{invoiceId}/installments")
    @RequiresPermission("FINANCE_VIEW")
    public List<InstallmentResponse> listInstallments(@PathVariable Long schoolId, @PathVariable Long invoiceId) {
        TenantContext.requireMatches(schoolId);
        requireInvoiceVisibleToCurrentUser(schoolId, invoiceId);

        return financeFacade.listInstallments(schoolId, invoiceId).stream()
                .map(InstallmentResponse::from)
                .toList();
    }

    /** ساخت یا بازشمارشِ طرحِ اقساط؛ فقط تعداد را ادمین می‌دهد و تقسیم مساوی با سیستم است. */
    @PostMapping("/invoices/{invoiceId}/installments/generate")
    @RequiresPermission("FINANCE_MANAGE")
    public List<InstallmentResponse> generateInstallments(
            @PathVariable Long schoolId,
            @PathVariable Long invoiceId,
            @RequestBody GenerateInstallmentsRequest request
    ) {
        TenantContext.requireMatches(schoolId);
        if (request.count() == null) {
            throw new FinanceException(new FinanceError.InvalidInstallments("تعداد اقساط الزامی است."));
        }

        // فاصله‌ی خالی = ۱ ماه؛ تاریخِ خالی را خودِ facade فقط وقتی مانده‌ای هست رد می‌کند
        int interval = request.intervalMonths() == null ? 1 : request.intervalMonths();
        return financeFacade.generateInstallments(
                        schoolId, invoiceId, request.count(), request.firstDueDate(), interval).stream()
                .map(InstallmentResponse::from)
                .toList();
    }

    /** ویرایشِ دستیِ مبلغ/سررسیدِ اقساطِ پرداخت‌نشده؛ body فهرستِ کاملِ آن‌هاست. */
    @PutMapping("/invoices/{invoiceId}/installments")
    @RequiresPermission("FINANCE_MANAGE")
    public List<InstallmentResponse> updateInstallments(
            @PathVariable Long schoolId,
            @PathVariable Long invoiceId,
            @RequestBody UpdateInstallmentsRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        List<FinanceFacade.InstallmentEdit> edits = request.items() == null ? null : request.items().stream()
                .map(item -> item == null ? null
                        : new FinanceFacade.InstallmentEdit(item.id(), item.amount(), item.dueDate()))
                .toList();
        return financeFacade.updateInstallments(schoolId, invoiceId, edits).stream()
                .map(InstallmentResponse::from)
                .toList();
    }

    @GetMapping("/finance/settings")
    @RequiresPermission("FINANCE_VIEW")
    public FinanceSettingsResponse settings(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return new FinanceSettingsResponse(schoolFacade.isInstallmentsEnabled(new SchoolFacade.SchoolId(schoolId)));
    }

    /** ادمین مالی ACADEMIC_YEAR_MANAGE ندارد، پس تنظیمِ اقساط اینجا و با FINANCE_MANAGE است. */
    @PutMapping("/finance/settings/installments")
    @RequiresPermission("FINANCE_MANAGE")
    public FinanceSettingsResponse setInstallments(@PathVariable Long schoolId,
                                                   @RequestBody(required = false) SetInstallmentsRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null || request.enabled() == null) {
            throw new FinanceException(new FinanceError.InvalidSetting("مقدار روشن/خاموش الزامی است."));
        }
        financeFacade.setInstallmentsEnabled(schoolId, request.enabled());
        return new FinanceSettingsResponse(schoolFacade.isInstallmentsEnabled(new SchoolFacade.SchoolId(schoolId)));
    }

    @PostMapping("/invoices/backfill")
    @RequiresPermission("FINANCE_MANAGE")
    public BackfillResponse backfillInvoices(@PathVariable Long schoolId,
                                             @RequestParam(required = false) String academicYear) {
        TenantContext.requireMatches(schoolId);

        // «صدورِ فاکتورهای سال» (academic-year.md فاز ۲): سالِ خالی = سالِ جاری
        return new BackfillResponse(financeFacade.backfillInvoices(schoolId, academicYear));
    }

    /** سال‌های قابل‌انتخاب در صفحه‌ی مالی؛ مدیر مالی ACADEMIC_YEAR_MANAGE ندارد، پس از اینجا می‌گیرد. */
    @GetMapping("/finance/years")
    @RequiresPermission("FINANCE_VIEW")
    public FinanceYearsResponse years(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        List<String> codes = schoolFacade.listAcademicYears().stream()
                .map(SchoolFacade.AcademicYearInfo::code).toList();
        String current = schoolFacade.findCurrentAcademicYear(new SchoolFacade.SchoolId(schoolId))
                .map(SchoolFacade.AcademicYearInfo::code).orElse(null);
        return new FinanceYearsResponse(codes, current);
    }

    /** شهریه و فاکتورهایی که هنوز به سالی نسبت داده نشده‌اند (هشدارِ صفحه‌ی مالی). */
    @GetMapping("/finance/unattributed")
    @RequiresPermission("FINANCE_MANAGE")
    public UnattributedResponse unattributed(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        FinanceFacade.UnattributedSummary summary = financeFacade.unattributedSummary(schoolId);
        return new UnattributedResponse(summary.tuitionFee(), summary.invoices());
    }

    /** اقدامِ صریحِ ادمین: شهریه و فاکتورهای «بدون سال» را به یک سال نسبت می‌دهد. */
    @PostMapping("/finance/attribute-year")
    @RequiresPermission("FINANCE_MANAGE")
    public AttributionResponse attributeYear(@PathVariable Long schoolId, @RequestBody AttributeYearRequest request) {
        TenantContext.requireMatches(schoolId);

        FinanceFacade.AttributionResult result = financeFacade.attributeToYear(schoolId, request.academicYear());
        return new AttributionResponse(result.tuitionAttributed(), result.invoicesAttributed());
    }

    /**
     * صفحه‌ی مالیِ دانش‌آموزمحور (school-settings §4): scope=all|current|previous، debt=any|owing|overdue|settled،
     * فیلترِ نام/نام‌خانوادگی/متنِ آزاد، صفحه‌بندی (size ۱ تا ۱۰۰، پیش‌فرض ۵۰). مقدارِ نامعتبر: 400 invalid-filter.
     */
    @GetMapping("/finance/students")
    @RequiresPermission("FINANCE_MANAGE")
    public StudentFinancePageResponse listStudents(
            @PathVariable Long schoolId,
            @RequestParam(required = false, defaultValue = "all") String scope,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "any") String debt,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        TenantContext.requireMatches(schoolId);

        FinanceFacade.StudentFinancePage result = financeFacade.listStudentFinance(schoolId,
                new FinanceFacade.StudentFinanceQuery(
                        parseFilter(FinanceFacade.StudentScope.class, "scope", scope), firstName, lastName, q,
                        parseFilter(FinanceFacade.DebtFilter.class, "debt", debt), page, size));
        Map<String, Long> counts = new LinkedHashMap<>();
        result.counts().forEach((key, value) -> counts.put(key.name().toLowerCase(Locale.ROOT), value));
        return new StudentFinancePageResponse(
                result.items().stream().map(row -> new StudentFinanceRowResponse(row.studentId(), row.fullName(),
                        row.studentStatus(), row.currentStatus(), row.currentRemaining(), row.previousDebt(),
                        row.totalRemaining(), row.overdue())).toList(),
                result.total(), counts, result.page(), result.size());
    }

    /** همه‌ی فاکتورهای یک دانش‌آموز در همه‌ی سال‌ها (جدیدترین اول) برای باز شدنِ ردیف. */
    @GetMapping("/finance/students/{studentId}/invoices")
    @RequiresPermission("FINANCE_MANAGE")
    public List<InvoiceResponse> listStudentInvoices(@PathVariable Long schoolId, @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        return financeFacade.listInvoicesOfStudent(schoolId, studentId).stream()
                .map(FinanceController::toResponse)
                .toList();
    }

    private static <E extends Enum<E>> E parseFilter(Class<E> type, String name, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FinanceException(new FinanceError.InvalidFilter(
                    "مقدار پارامتر " + name + " نامعتبر است."));
        }
    }

    private static InvoiceResponse toResponse(InvoiceInfo info) {
        return new InvoiceResponse(info.id(), info.schoolId(), info.studentId(), info.amount(),
                info.amountPaid(), info.issuedAt(), info.dueDate(), info.status(), info.academicYear(),
                info.discountTotal());
    }

    private static DiscountResponse toResponse(DiscountInfo info) {
        return new DiscountResponse(info.id(), info.invoiceId(), info.amount(), info.reason(),
                info.createdByUserId(), info.createdAt(), info.deadline(), info.status(), info.resolvedAt());
    }

    private static PaymentResponse toResponse(PaymentInfo info) {
        return new PaymentResponse(info.id(), info.invoiceId(), info.amount(), info.method(),
                info.note(), info.paidAt());
    }
}
