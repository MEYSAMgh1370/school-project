package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.PromotionFacade;
import com.example.roshdeandishe.academic.PromotionOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** قراردادِ JSON انتساب و ارتقا (openspec/changes/academic-year.md، فاز ۳). */
public final class PromotionDtos {

    private PromotionDtos() {
    }

    public record UnattributedResponse(String currentYear, long classes, long students) {
        public static UnattributedResponse from(PromotionFacade.UnattributedSummary s) {
            return new UnattributedResponse(s.currentYear(), s.classes(), s.students());
        }
    }

    public record AttributionRequest(String academicYear) {
    }

    public record AttributionResponse(String academicYear, int classes, int students) {
        public static AttributionResponse from(PromotionFacade.AttributionResult r) {
            return new AttributionResponse(r.academicYear(), r.classes(), r.students());
        }
    }

    public record PlanItemResponse(Long studentId, Long userId, Long classId, int gradeLevel,
                                   PromotionOutcome defaultOutcome) {
        public static PlanItemResponse from(PromotionFacade.PlanItem item) {
            return new PlanItemResponse(item.studentId(), item.userId(), item.classId(), item.gradeLevel(),
                    item.defaultOutcome());
        }
    }

    public record Decision(Long studentId, PromotionOutcome outcome) {
    }

    /** {@code decisions} اختیاری: دانش‌آموزانِ نیامده قاعده‌ی پیش‌فرض را می‌گیرند. */
    public record RunRequest(String fromYear, List<Decision> decisions) {
    }

    public record RunResponse(Long id, String fromYear, String toYear, String mode, Instant createdAt,
                              Instant undoneAt, Map<PromotionOutcome, Integer> counts, boolean undoable) {
        public static RunResponse from(PromotionFacade.RunInfo r) {
            return new RunResponse(r.id(), r.fromYear(), r.toYear(), r.mode(), r.createdAt(), r.undoneAt(),
                    r.counts(), r.undoable());
        }
    }

    public record OutcomeRequest(Long studentId, PromotionOutcome outcome) {
    }

    public record PendingResponse(Long studentId, Long userId, int gradeLevel, Integer previousGradeLevel) {
        public static PendingResponse from(PromotionFacade.PendingStudent p) {
            return new PendingResponse(p.studentId(), p.userId(), p.gradeLevel(), p.previousGradeLevel());
        }
    }

    public record PlacementItem(Long studentId, Long classId) {
    }

    public record PlacementRequest(List<PlacementItem> placements) {
    }

    public record PlacementResponse(int placed) {
    }
}
