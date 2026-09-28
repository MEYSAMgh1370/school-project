package com.example.roshdeandishe.finance.dto;

import java.math.BigDecimal;

public record CategorySummaryResponse(Long categoryId, String categoryName, BigDecimal total, long count) {
}
