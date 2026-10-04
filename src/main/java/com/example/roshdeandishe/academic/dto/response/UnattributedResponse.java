package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.PromotionFacade;

public record UnattributedResponse(String currentYear, long classes, long students) {
    public static UnattributedResponse from(PromotionFacade.UnattributedSummary s) {
        return new UnattributedResponse(s.currentYear(), s.classes(), s.students());
    }
}
