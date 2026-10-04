package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.PromotionFacade;

public record PendingResponse(Long studentId, Long userId, int gradeLevel, Integer previousGradeLevel) {
    public static PendingResponse from(PromotionFacade.PendingStudent p) {
        return new PendingResponse(p.studentId(), p.userId(), p.gradeLevel(), p.previousGradeLevel());
    }
}
