package com.example.roshdeandishe.finance;

import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.FinanceFacade.PaymentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.TuitionFeeInfo;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    private final FinanceFacade financeFacade;

    public FinanceController(FinanceFacade financeFacade) {
        this.financeFacade = financeFacade;
    }

    @PutMapping("/tuition-fee")
    @RequiresPermission("FINANCE_MANAGE")
    public TuitionFeeResponse setTuitionFee(@PathVariable Long schoolId, @RequestBody SetTuitionFeeRequest request) {
        TenantContext.requireMatches(schoolId);

        financeFacade.setTuitionFee(schoolId, request.amount());
        return new TuitionFeeResponse(schoolId, request.amount());
    }

    @GetMapping("/tuition-fee")
    @RequiresPermission("FINANCE_VIEW")
    public TuitionFeeResponse getTuitionFee(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        TuitionFeeInfo info = financeFacade.getTuitionFee(schoolId)
                .orElseThrow(() -> new FinanceException(new FinanceError.TuitionFeeNotSet(schoolId)));
        return new TuitionFeeResponse(info.schoolId(), info.amount());
    }

    @GetMapping("/invoices")
    @RequiresPermission("FINANCE_VIEW")
    public List<InvoiceResponse> listInvoices(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return financeFacade.listInvoices(schoolId).stream()
                .map(FinanceController::toResponse)
                .toList();
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

        return financeFacade.listPayments(schoolId, invoiceId).stream()
                .map(FinanceController::toResponse)
                .toList();
    }

    @PostMapping("/invoices/backfill")
    @RequiresPermission("FINANCE_MANAGE")
    public BackfillResponse backfillInvoices(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return new BackfillResponse(financeFacade.backfillInvoices(schoolId));
    }

    private static InvoiceResponse toResponse(InvoiceInfo info) {
        return new InvoiceResponse(info.id(), info.schoolId(), info.studentId(), info.amount(),
                info.amountPaid(), info.issuedAt(), info.dueDate(), info.status());
    }

    private static PaymentResponse toResponse(PaymentInfo info) {
        return new PaymentResponse(info.id(), info.invoiceId(), info.amount(), info.method(),
                info.note(), info.paidAt());
    }

    public record SetTuitionFeeRequest(BigDecimal amount) {
    }

    public record TuitionFeeResponse(Long schoolId, BigDecimal amount) {
    }

    public record InvoiceResponse(Long id, Long schoolId, Long studentId, BigDecimal amount,
                                  BigDecimal amountPaid, Instant issuedAt, Instant dueDate,
                                  InvoiceStatus status) {
    }

    public record RecordPaymentRequest(BigDecimal amount, PaymentMethod method, String note) {
    }

    public record PaymentResponse(Long id, Long invoiceId, BigDecimal amount, PaymentMethod method,
                                  String note, Instant paidAt) {
    }

    public record BackfillResponse(int issuedCount) {
    }
}
