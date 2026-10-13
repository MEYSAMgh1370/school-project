package com.example.roshdeandishe.finance.dto.response;

import com.example.roshdeandishe.finance.DiscountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** {@code deadline} خالی = تخفیفِ بی‌قیدوشرط؛ {@code resolvedAt} لحظه‌ی EARNED/LAPSED شدن. */
public record DiscountResponse(Long id, Long invoiceId, BigDecimal amount, String reason,
                               Long createdByUserId, Instant createdAt,
                               LocalDate deadline, DiscountStatus status, Instant resolvedAt) {
}
