package com.example.roshdeandishe.finance.dto.request;

import com.example.roshdeandishe.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(LocalDate expenseDate, BigDecimal amount, Long categoryId, Long vendorId,
                             PaymentMethod paymentMethod, String description, String receiptNumber,
                             String invoiceNumber, String documentNumber) {
}
