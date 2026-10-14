package com.example.roshdeandishe.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record AuditEntryResponse(Long id, Instant occurredAt, String action, Long actorUserId, String actorName,
                                 Long studentId, String studentName, Long invoiceId, Long expenseId,
                                 BigDecimal amount, Map<String, Object> details) {
}
