package com.example.roshdeandishe.questionbox.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.*;
import com.example.roshdeandishe.questionbox.dto.request.SheetRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** برگه‌های چاپیِ ذخیره‌شده (openspec/changes/question-box.md)؛ {@code /resolved} ورودیِ صفحه‌ی چاپ است. */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/question-box/sheets")
@RequiresModule("QUESTION_BOX")
@RequiresPermission("QUESTION_BOX_MANAGE")
public class QuestionSheetController {

    private final QuestionBoxFacade facade;
    private final IamFacade iamFacade;

    public QuestionSheetController(QuestionBoxFacade facade, IamFacade iamFacade) {
        this.facade = facade;
        this.iamFacade = iamFacade;
    }

    @GetMapping
    public List<SheetSummary> list(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return facade.listSheets(schoolId, actor());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SheetInfo create(@PathVariable Long schoolId, @RequestBody SheetRequest request) {
        TenantContext.requireMatches(schoolId);
        return facade.createSheet(schoolId, actor(), toDraft(request));
    }

    @GetMapping("/{sheetId}")
    public SheetInfo get(@PathVariable Long schoolId, @PathVariable Long sheetId) {
        TenantContext.requireMatches(schoolId);
        return facade.getSheet(schoolId, actor(), sheetId);
    }

    @PutMapping("/{sheetId}")
    public SheetInfo save(@PathVariable Long schoolId, @PathVariable Long sheetId, @RequestBody SheetRequest request) {
        TenantContext.requireMatches(schoolId);
        return facade.saveSheet(schoolId, actor(), sheetId, toDraft(request));
    }

    @DeleteMapping("/{sheetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long schoolId, @PathVariable Long sheetId) {
        TenantContext.requireMatches(schoolId);
        facade.deleteSheet(schoolId, actor(), sheetId);
    }

    @GetMapping("/{sheetId}/resolved")
    public ResolvedSheet resolved(@PathVariable Long schoolId, @PathVariable Long sheetId) {
        TenantContext.requireMatches(schoolId);
        return facade.resolveSheet(schoolId, sheetId);
    }

    private Long actor() {
        return iamFacade.currentUserId().value();
    }

    private static SheetDraft toDraft(SheetRequest r) {
        List<SectionDraft> sections = r.sections() == null ? null : r.sections().stream()
                .map(s -> new SectionDraft(s.title(), s.instruction(), s.items() == null ? List.of()
                        : s.items().stream().map(i -> new ItemRef(i.source(), i.questionId())).toList()))
                .toList();
        return new SheetDraft(r.title(), r.headerId(), r.headerValues(),
                r.showEssayAnswerSpace() == null || r.showEssayAnswerSpace(),
                r.includeAnswerKey() != null && r.includeAnswerKey(), sections);
    }
}
