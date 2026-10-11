package com.example.roshdeandishe.curriculum.controller;

import com.example.roshdeandishe.curriculum.MoshavereImportFacade;
import com.example.roshdeandishe.curriculum.MoshavereImportFacade.Bundle;
import com.example.roshdeandishe.curriculum.MoshavereImportFacade.ImportSummary;
import com.example.roshdeandishe.curriculum.dto.request.MoshavereBundleRequest;
import com.example.roshdeandishe.curriculum.dto.response.MoshavereImportResponse;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ایمپورتِ پلتفرمیِ بسته‌ی مشاوره (openspec/changes/moshavere-content-import.md §6): فقط مدیر پلتفرم، با همان گیتِ
 * PlatformHolidayController. {@code dryRun=true} همه‌چیز را اجرا و rollback می‌کند.
 */
@RestController
public class PlatformMoshavereImportController {

    private final MoshavereImportFacade importFacade;

    public PlatformMoshavereImportController(MoshavereImportFacade importFacade) {
        this.importFacade = importFacade;
    }

    @PostMapping("/api/platform/moshavere/import")
    @RequiresPermission("PLATFORM_HOLIDAY_MANAGE")
    public MoshavereImportResponse importBundle(@RequestBody MoshavereBundleRequest request,
                                                @RequestParam(defaultValue = "false") boolean dryRun) {
        if (request == null) {
            throw new CurriculumException(new CurriculumError.InvalidMoshavereBundle("بدنه‌ی درخواست خالی است."));
        }
        List<MoshavereImportFacade.TopicEntry> topics = request.topics() == null ? null
                : request.topics().stream()
                .map(t -> t == null ? null
                        : new MoshavereImportFacade.TopicEntry(t.sourceCode(), t.parentSourceCode(), t.name(), t.sortOrder()))
                .toList();
        List<MoshavereImportFacade.CourseEntry> courses = request.courses() == null ? null
                : request.courses().stream()
                .map(c -> c == null ? null
                        : new MoshavereImportFacade.CourseEntry(c.gradeLevel(), c.name(), c.track(), c.topicSourceCode()))
                .toList();
        ImportSummary s = importFacade.importBundle(new Bundle(request.format(), request.source(), topics, courses), dryRun);
        return new MoshavereImportResponse(s.topicsCreated(), s.topicsUpdated(), s.coursesCreated(),
                s.coursesUpdated(), s.coursesUnlinked(), s.dryRun());
    }
}
