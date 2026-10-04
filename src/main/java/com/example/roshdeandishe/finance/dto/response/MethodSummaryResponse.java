package com.example.roshdeandishe.finance.dto.response;

import com.example.roshdeandishe.finance.PaymentMethod;

import java.math.BigDecimal;

public record MethodSummaryResponse(PaymentMethod method, BigDecimal total, long count) {
}
