package com.example.roshdeandishe.finance.dto.response;

import java.util.List;

public record AuditPageResponse(int page, int size, long totalItems, List<AuditEntryResponse> items) {
}
