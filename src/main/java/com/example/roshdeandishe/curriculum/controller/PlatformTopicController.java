package com.example.roshdeandishe.curriculum.controller;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicInfo;
import com.example.roshdeandishe.curriculum.dto.request.UpsertPlatformTopicRequest;
import com.example.roshdeandishe.curriculum.dto.response.TopicResponse;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import org.springframework.web.bind.annotation.*;

/**
 * نوشتنِ سرفصل‌های پلتفرمی (openspec/changes/moshavere-content-import.md): فقط مدیر پلتفرم، با همان گیتِ
 * PlatformHolidayController (مسیر پلتفرم: بدون TenantContext و بدون {@code @RequiresModule}، مجوز در
 * PlatformContext سنجیده می‌شود).
 */
@RestController
public class PlatformTopicController {

    private final CurriculumFacade curriculumFacade;

    public PlatformTopicController(CurriculumFacade curriculumFacade) {
        this.curriculumFacade = curriculumFacade;
    }

    @PostMapping("/api/platform/curriculum/topics")
    @RequiresPermission("PLATFORM_HOLIDAY_MANAGE")
    public TopicResponse upsert(@RequestBody UpsertPlatformTopicRequest request) {
        if (request == null) {
            throw new CurriculumException(new CurriculumError.InvalidTopic("کد منبع و نام سرفصل الزامی است."));
        }
        TopicInfo info = curriculumFacade.upsertPlatformTopic(
                request.sourceCode(), request.parentSourceCode(), request.name(),
                request.sortOrder() == null ? 0 : request.sortOrder()
        );
        return new TopicResponse(info.id().value(), info.schoolId(), info.parentId(), info.name(), info.sortOrder());
    }
}
