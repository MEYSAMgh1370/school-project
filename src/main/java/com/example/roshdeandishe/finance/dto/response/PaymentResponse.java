package com.example.roshdeandishe.finance.dto.response;

import com.example.roshdeandishe.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long invoiceId, BigDecimal amount, PaymentMethod method,
                              String note, Instant paidAt) {
}
