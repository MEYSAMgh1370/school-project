package com.example.roshdeandishe.finance.dto.response;

import com.example.roshdeandishe.finance.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(Long id, Long schoolId, Long studentId, BigDecimal amount,
                              BigDecimal amountPaid, Instant issuedAt, Instant dueDate,
                              InvoiceStatus status, String academicYear) {
}
