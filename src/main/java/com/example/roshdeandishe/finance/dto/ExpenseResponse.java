package com.example.roshdeandishe.finance.dto;

import com.example.roshdeandishe.finance.ExpenseStatus;
import com.example.roshdeandishe.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(Long id, LocalDate expenseDate, BigDecimal amount, Long categoryId,
                              Long vendorId, PaymentMethod paymentMethod, ExpenseStatus status,
                              String description, String receiptNumber, String invoiceNumber,
                              String documentNumber, Instant createdAt, Instant updatedAt) {
}
