package com.example.roshdeandishe.academic.dto.request;

import com.example.roshdeandishe.academic.PromotionOutcome;

public record Decision(Long studentId, PromotionOutcome outcome) {
}
