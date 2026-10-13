package com.example.roshdeandishe.finance.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code deadline} اختیاری و به شکل ISO (yyyy-MM-dd)؛ خالی = تخفیفِ بی‌قیدوشرط. */
public record ApplyDiscountRequest(BigDecimal amount, String reason, LocalDate deadline) {
}
