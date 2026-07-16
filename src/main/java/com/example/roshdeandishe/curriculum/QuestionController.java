package com.example.roshdeandishe.curriculum;

import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionId;
import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionInfo;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * بانک سوال چهارگزینه‌ای یک مدرسه. همان تقسیم دسترسی TopicController:
 * CURRICULUM_MANAGE برای ثبت، CURRICULUM_VIEW برای خواندن - هیچ‌کدام به
 * STUDENT/PARENT داده نمی‌شود، چون پاسخ صحیح (correctChoice) در همین
 * پاسخ‌ها برمی‌گردد و دیدن بانک سوال برای دانش‌آموز یعنی لو رفتن آزمون؛
 * مسیر دانش‌آموز به سوالات، بعداً از توکن آزمونِ ماژول exam می‌گذرد
 * (MOSHAVERE_PORT_SPEC.md §5)، نه از این کنترلر.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/questions")
@RequiresModule("CURRICULUM")
public class QuestionController {

    private final CurriculumFacade curriculumFacade;

    public QuestionController(CurriculumFacade curriculumFacade) {
        this.curriculumFacade = curriculumFacade;
    }

    @PostMapping
    @RequiresPermission("CURRICULUM_MANAGE")
    public QuestionResponse createQuestion(@PathVariable Long schoolId, @RequestBody CreateQuestionRequest request) {
        TenantContext.requireMatches(schoolId);

        QuestionId questionId = curriculumFacade.createQuestion(
                schoolId, request.topicId(), request.text(),
                request.choice1(), request.choice2(), request.choice3(), request.choice4(),
                request.correctChoice() == null ? 0 : request.correctChoice(),
                request.difficulty(), request.discriminationIndex()
        );
        return new QuestionResponse(questionId.value(), schoolId, request.topicId(), request.text(),
                request.choice1(), request.choice2(), request.choice3(), request.choice4(),
                request.correctChoice(), request.difficulty(), request.discriminationIndex());
    }

    @GetMapping
    @RequiresPermission("CURRICULUM_VIEW")
    public List<QuestionResponse> listQuestions(
            @PathVariable Long schoolId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Integer difficulty
    ) {
        TenantContext.requireMatches(schoolId);

        return curriculumFacade.listQuestions(schoolId, topicId, difficulty).stream()
                .map(QuestionController::toResponse)
                .toList();
    }

    private static QuestionResponse toResponse(QuestionInfo info) {
        return new QuestionResponse(
                info.id().value(), info.schoolId(), info.topicId().value(), info.text(),
                info.choice1(), info.choice2(), info.choice3(), info.choice4(),
                info.correctChoice(), info.difficulty(), info.discriminationIndex()
        );
    }

    public record CreateQuestionRequest(Long topicId, String text,
                                        String choice1, String choice2, String choice3, String choice4,
                                        Integer correctChoice, Integer difficulty, Double discriminationIndex) {
    }

    public record QuestionResponse(Long id, Long schoolId, Long topicId, String text,
                                   String choice1, String choice2, String choice3, String choice4,
                                   Integer correctChoice, Integer difficulty, Double discriminationIndex) {
    }
}
