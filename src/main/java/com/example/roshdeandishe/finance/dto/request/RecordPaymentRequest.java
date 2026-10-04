package com.example.roshdeandishe.finance.dto.request;

import com.example.roshdeandishe.finance.PaymentMethod;

import java.math.BigDecimal;

public record RecordPaymentRequest(BigDecimal amount, PaymentMethod method, String note) {
}
