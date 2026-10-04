package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.PromotionFacade;
import com.example.roshdeandishe.academic.PromotionOutcome;
import java.time.Instant;
import java.util.Map;

public record RunResponse(Long id, String fromYear, String toYear, String mode, Instant createdAt,
                          Instant undoneAt, Map<PromotionOutcome, Integer> counts, boolean undoable) {
    public static RunResponse from(PromotionFacade.RunInfo r) {
        return new RunResponse(r.id(), r.fromYear(), r.toYear(), r.mode(), r.createdAt(), r.undoneAt(),
                r.counts(), r.undoable());
    }
}
