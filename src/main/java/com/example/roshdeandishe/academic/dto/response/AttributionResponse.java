package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.PromotionFacade;

public record AttributionResponse(String academicYear, int classes, int students) {
    public static AttributionResponse from(PromotionFacade.AttributionResult r) {
        return new AttributionResponse(r.academicYear(), r.classes(), r.students());
    }
}
