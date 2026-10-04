package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.PromotionFacade;
import com.example.roshdeandishe.academic.PromotionOutcome;
import com.example.roshdeandishe.academic.dto.request.*;
import com.example.roshdeandishe.academic.dto.response.*;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * انتسابِ داده‌ی موجود به یک سال، ارتقای دانش‌آموزان، برگرداندنِ آن و جا‌دادنِ دانش‌آموزانِ ارتقایافته
 * (openspec/changes/academic-year.md، فاز ۳). همه‌ی اقدام‌ها با {@code ACADEMIC_YEAR_MANAGE} (مدیر مدرسه) گیت
 * شده‌اند: «گذار از سال» یک تصمیم اداری است و مجوزِ جدا ندارد.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}")
@RequiresModule("ACADEMIC")
@RequiresPermission("ACADEMIC_YEAR_MANAGE")
public class PromotionController {

    private final PromotionFacade promotionFacade;

    public PromotionController(PromotionFacade promotionFacade) {
        this.promotionFacade = promotionFacade;
    }

    @GetMapping("/academic-year/unattributed")
    public UnattributedResponse unattributed(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return UnattributedResponse.from(promotionFacade.unattributedSummary(schoolId));
    }

    @PostMapping("/academic-year/attribution")
    public AttributionResponse attribute(@PathVariable Long schoolId, @RequestBody AttributionRequest request) {
        TenantContext.requireMatches(schoolId);
        return AttributionResponse.from(promotionFacade.attributeExistingData(schoolId,
                request == null ? null : request.academicYear()));
    }

    @GetMapping("/promotion/preview")
    public List<PlanItemResponse> preview(@PathVariable Long schoolId, @RequestParam String fromYear) {
        TenantContext.requireMatches(schoolId);
        return promotionFacade.preview(schoolId, fromYear).stream().map(PlanItemResponse::from).toList();
    }

    @PostMapping("/promotion")
    public RunResponse run(@PathVariable Long schoolId, @RequestBody RunRequest request) {
        TenantContext.requireMatches(schoolId);
        Map<Long, PromotionOutcome> decisions = new HashMap<>();
        if (request.decisions() != null) {
            for (Decision d : request.decisions()) {
                if (d == null || d.studentId() == null || d.outcome() == null) {
                    throw new AcademicException(new AcademicError.InvalidRollover(
                            "هر تصمیم باید شناسه‌ی دانش‌آموز و نتیجه داشته باشد."));
                }
                decisions.put(d.studentId(), d.outcome());
            }
        }
        return RunResponse.from(promotionFacade.run(schoolId, request.fromYear(), decisions));
    }

    @GetMapping("/promotion/runs")
    public List<RunResponse> runs(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return promotionFacade.listRuns(schoolId).stream().map(RunResponse::from).toList();
    }

    @PostMapping("/promotion/runs/{runId}/undo")
    public ResponseEntity<Void> undo(@PathVariable Long schoolId, @PathVariable Long runId) {
        TenantContext.requireMatches(schoolId);
        promotionFacade.undo(schoolId, runId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/promotion/outcome")
    public ResponseEntity<Void> changeOutcome(@PathVariable Long schoolId, @RequestBody OutcomeRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null || request.studentId() == null || request.outcome() == null) {
            throw new AcademicException(new AcademicError.InvalidRollover(
                    "شناسه‌ی دانش‌آموز و نتیجه الزامی است."));
        }
        promotionFacade.changeOutcome(schoolId, request.studentId(), request.outcome());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/promotion/pending")
    public List<PendingResponse> pending(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return promotionFacade.listPending(schoolId).stream().map(PendingResponse::from).toList();
    }

    @GetMapping("/promotion/graduates")
    public List<PendingResponse> graduates(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return promotionFacade.listGraduates(schoolId).stream().map(PendingResponse::from).toList();
    }

    @PostMapping("/promotion/placements")
    public PlacementResponse place(@PathVariable Long schoolId, @RequestBody PlacementRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null || request.placements() == null) {
            throw new AcademicException(new AcademicError.InvalidRollover("فهرست جا‌دادن الزامی است."));
        }
        List<PromotionFacade.Placement> placements = request.placements().stream()
                .map(p -> new PromotionFacade.Placement(p.studentId(), p.classId()))
                .toList();
        return new PlacementResponse(promotionFacade.place(schoolId, placements));
    }
}
