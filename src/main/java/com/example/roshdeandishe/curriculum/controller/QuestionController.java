package com.example.roshdeandishe.curriculum.controller;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.NewQuestion;
import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionFilter;
import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionId;
import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionInfo;
import com.example.roshdeandishe.curriculum.QuestionDifficulty;
import com.example.roshdeandishe.curriculum.dto.CreateQuestionRequest;
import com.example.roshdeandishe.curriculum.dto.QuestionResponse;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * بانک سؤال یک مدرسه. همان تقسیم Permission که TopicController توضیح
 * می‌دهد. فیلترِ topicId شامل کل زیر درخت آن سرفصل است (سمنتیک
 * پورت‌شده از QuestionFilter در moshavere)؛ difficulty چندتایی است
 * (?difficulty=EASY&difficulty=MEDIUM).
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

        NewQuestion question = new NewQuestion(
                request.topicId(), request.text(),
                request.choice1(), request.choice2(), request.choice3(), request.choice4(),
                request.correctChoice() == null ? 0 : request.correctChoice(),
                request.answer(), request.difficulty(), request.discriminationIndex()
        );
        QuestionId questionId = curriculumFacade.createQuestion(schoolId, question);
        return new QuestionResponse(
                questionId.value(), schoolId, request.topicId(), request.text(),
                request.choice1(), request.choice2(), request.choice3(), request.choice4(),
                question.correctChoice(), request.answer(), request.difficulty(), request.discriminationIndex()
        );
    }

    @GetMapping
    @RequiresPermission("CURRICULUM_VIEW")
    public List<QuestionResponse> listQuestions(
            @PathVariable Long schoolId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Set<QuestionDifficulty> difficulty
    ) {
        TenantContext.requireMatches(schoolId);

        return curriculumFacade.listQuestions(schoolId, new QuestionFilter(topicId, difficulty)).stream()
                .map(QuestionController::toResponse)
                .toList();
    }

    private static QuestionResponse toResponse(QuestionInfo info) {
        return new QuestionResponse(
                info.id().value(), info.schoolId(), info.topicId(), info.text(),
                info.choice1(), info.choice2(), info.choice3(), info.choice4(),
                info.correctChoice(), info.answer(), info.difficulty(), info.discriminationIndex()
        );
    }
}
