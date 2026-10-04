package com.example.roshdeandishe.finance.dto.response;

import java.math.BigDecimal;

public record FinanceSummaryResponse(BigDecimal income, BigDecimal expense) {
}
