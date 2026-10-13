package com.example.roshdeandishe.questionbox.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.NewQuestion;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.QuestionListFilter;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.QuestionType;
import com.example.roshdeandishe.questionbox.dto.request.QuestionRequest;
import com.example.roshdeandishe.questionbox.dto.response.QuestionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * سؤال‌های باکس (openspec/changes/question-box.md). همه‌ی endpointها {@code QUESTION_BOX} + {@code QUESTION_BOX_MANAGE}
 * می‌خواهند؛ قاعده‌ی ویرایشِ سازنده/مدیر/سازنده‌ی جداشده در facade اعمال می‌شود.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/question-box/questions")
@RequiresModule("QUESTION_BOX")
@RequiresPermission("QUESTION_BOX_MANAGE")
public class QuestionBoxController {

    private final QuestionBoxFacade facade;
    private final IamFacade iamFacade;

    public QuestionBoxController(QuestionBoxFacade facade, IamFacade iamFacade) {
        this.facade = facade;
        this.iamFacade = iamFacade;
    }

    @GetMapping
    public List<QuestionResponse> list(@PathVariable Long schoolId,
                                       @RequestParam(required = false) String search,
                                       @RequestParam(required = false) QuestionType type,
                                       @RequestParam(required = false) Long topicId) {
        TenantContext.requireMatches(schoolId);
        return facade.listQuestions(schoolId, actor(), new QuestionListFilter(search, type, topicId))
                .stream().map(QuestionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse create(@PathVariable Long schoolId, @RequestBody QuestionRequest request) {
        TenantContext.requireMatches(schoolId);
        return QuestionResponse.from(facade.createQuestion(schoolId, actor(), toNew(request)));
    }

    @GetMapping("/{questionId}")
    public QuestionResponse get(@PathVariable Long schoolId, @PathVariable Long questionId) {
        TenantContext.requireMatches(schoolId);
        return QuestionResponse.from(facade.getQuestion(schoolId, actor(), questionId));
    }

    @PutMapping("/{questionId}")
    public QuestionResponse update(@PathVariable Long schoolId, @PathVariable Long questionId,
                                   @RequestBody QuestionRequest request) {
        TenantContext.requireMatches(schoolId);
        return QuestionResponse.from(facade.updateQuestion(schoolId, actor(), questionId, toNew(request)));
    }

    @DeleteMapping("/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long schoolId, @PathVariable Long questionId) {
        TenantContext.requireMatches(schoolId);
        facade.deleteQuestion(schoolId, actor(), questionId);
    }

    private Long actor() {
        return iamFacade.currentUserId().value();
    }

    private static NewQuestion toNew(QuestionRequest r) {
        return new NewQuestion(r.type(), r.contentMode(), r.topicId(), r.text(), r.choices(), r.correctChoice(),
                r.answer(), r.suggestedScore());
    }
}
