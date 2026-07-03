package com.example.roshdeandishe.academic;

import java.math.BigDecimal;
import java.time.Instant;


public record InvoiceIssuedEvent(
        Long schoolId,
        Long studentId,
        BigDecimal amount,
        Long actorUserId,
        Instant occurredAt
) {
}