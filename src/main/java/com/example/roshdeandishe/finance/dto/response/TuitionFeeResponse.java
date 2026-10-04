package com.example.roshdeandishe.finance.dto.response;

import java.math.BigDecimal;

public record TuitionFeeResponse(Long schoolId, BigDecimal amount, String academicYear) {
}
