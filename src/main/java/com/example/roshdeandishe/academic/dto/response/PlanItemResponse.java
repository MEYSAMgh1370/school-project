package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.PromotionFacade;
import com.example.roshdeandishe.academic.PromotionOutcome;

public record PlanItemResponse(Long studentId, Long userId, Long classId, int gradeLevel,
                               PromotionOutcome defaultOutcome) {
    public static PlanItemResponse from(PromotionFacade.PlanItem item) {
        return new PlanItemResponse(item.studentId(), item.userId(), item.classId(), item.gradeLevel(),
                item.defaultOutcome());
    }
}
