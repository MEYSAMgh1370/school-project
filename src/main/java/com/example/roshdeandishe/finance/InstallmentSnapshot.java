package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * تصویرِ یک قسط در لحظه‌ی پیش یا پس از یک تغییرِ طرحِ اقساط (برای {@link InstallmentsChangedEvent} و audit).
 * {@code dueDate} خالی یعنی «بدون سررسید».
 */
public record InstallmentSnapshot(BigDecimal amount, BigDecimal amountPaid, LocalDate dueDate) {
}
