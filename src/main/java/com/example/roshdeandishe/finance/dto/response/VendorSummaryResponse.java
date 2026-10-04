package com.example.roshdeandishe.finance.dto.response;

import java.math.BigDecimal;

public record VendorSummaryResponse(Long vendorId, String vendorName, BigDecimal total, long count) {
}
