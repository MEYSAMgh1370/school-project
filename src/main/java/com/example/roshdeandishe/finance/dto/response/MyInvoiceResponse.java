package com.example.roshdeandishe.finance.dto.response;

import com.example.roshdeandishe.finance.FinanceFacade.InstallmentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.FinanceFacade.PendingDiscountSummary;
import com.example.roshdeandishe.finance.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code studentName}: نامِ دانش‌آموزِ فاکتور (خالی اگر پیدا نشد؛ صفحه آن‌وقت شماره را نشان می‌دهد).
 * {@code installments}: طرحِ اقساطِ فاکتور؛ فهرستِ خالی = فاکتور طرحِ اقساط ندارد.
 * {@code pendingDiscount}: جمعِ تخفیف‌های مشروطِ باز (۰ اگر نیست) که با تسویه‌ی کامل تا
 * {@code pendingDiscountDeadline} (زودترین مهلت؛ خالی اگر نیست) می‌مانند.
 */
public record MyInvoiceResponse(Long id, Long studentId, String studentName, BigDecimal amount, BigDecimal amountPaid,
                                Instant issuedAt, Instant dueDate, InvoiceStatus status, String academicYear,
                                BigDecimal discountTotal, List<InstallmentResponse> installments,
                                BigDecimal pendingDiscount, LocalDate pendingDiscountDeadline) {
    public static MyInvoiceResponse from(InvoiceInfo info, List<InstallmentInfo> installments,
                                         PendingDiscountSummary pending, String studentName) {
        return new MyInvoiceResponse(info.id(), info.studentId(), studentName, info.amount(), info.amountPaid(),
                info.issuedAt(), info.dueDate(), info.status(), info.academicYear(), info.discountTotal(),
                installments.stream().map(InstallmentResponse::from).toList(),
                pending == null ? BigDecimal.ZERO : pending.amount(),
                pending == null ? null : pending.earliestDeadline());
    }
}
