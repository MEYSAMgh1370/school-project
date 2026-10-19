package com.example.roshdeandishe.finance.dto.response;

import com.example.roshdeandishe.finance.InvoiceStatus;

import java.math.BigDecimal;

/** یک ردیفِ دانش‌آموزمحور صفحه‌ی مالی؛ currentStatus تهی = فاکتورِ سالِ جاری ندارد. */
public record StudentFinanceRowResponse(Long studentId, String fullName, String studentStatus,
                                        InvoiceStatus currentStatus, BigDecimal currentRemaining,
                                        BigDecimal previousDebt, BigDecimal totalRemaining, boolean overdue) {
}
