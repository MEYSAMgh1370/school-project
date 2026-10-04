package com.example.roshdeandishe.finance.dto;

import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MyInvoiceResponse(Long id, Long studentId, BigDecimal amount, BigDecimal amountPaid,
                                Instant issuedAt, Instant dueDate, InvoiceStatus status, String academicYear) {
    public static MyInvoiceResponse from(InvoiceInfo info) {
        return new MyInvoiceResponse(info.id(), info.studentId(), info.amount(), info.amountPaid(),
                info.issuedAt(), info.dueDate(), info.status(), info.academicYear());
    }
}
