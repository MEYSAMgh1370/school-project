package com.example.roshdeandishe.curriculum;

import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicId;
import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicInfo;
import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicNode;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * مدیریت سرفصل‌های درسی یک مدرسه. طبق تصمیم صریح (پاس new-module ماژول
 * curriculum)، نوشتن (CURRICULUM_MANAGE) به SCHOOL_ADMIN و TEACHER هر دو
 * داده می‌شود - معلم‌ها صاحب سرفصل/سوال‌های خودشان هستند
 * (MOSHAVERE_PORT_SPEC.md §2) - و خواندن (CURRICULUM_VIEW) هم فعلاً به همان
 * دو نقش؛ جدایی VIEW از MANAGE (الگوی CLASS_VIEW/CLASS_MANAGE) برای این است
 * که یک نقش سفارشی بتواند فقط-خواندنی ساخته شود، نه اینکه grant سیستمی
 * متفاوتی وجود داشته باشد.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/topics")
@RequiresModule("CURRICULUM")
public class TopicController {

    private final CurriculumFacade curriculumFacade;

    public TopicController(CurriculumFacade curriculumFacade) {
        this.curriculumFacade = curriculumFacade;
    }

    @PostMapping
    @RequiresPermission("CURRICULUM_MANAGE")
    public TopicResponse createTopic(@PathVariable Long schoolId, @RequestBody CreateTopicRequest request) {
        TenantContext.requireMatches(schoolId);

        TopicId topicId = curriculumFacade.createTopic(schoolId, request.parentId(), request.name(), request.order());
        return new TopicResponse(topicId.value(), schoolId, request.parentId(), request.name(),
                request.order() == null ? 0 : request.order());
    }

    @GetMapping
    @RequiresPermission("CURRICULUM_VIEW")
    public List<TopicResponse> listTopics(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return curriculumFacade.listTopics(schoolId).stream()
                .map(TopicController::toResponse)
                .toList();
    }

    @GetMapping("/tree")
    @RequiresPermission("CURRICULUM_VIEW")
    public List<TopicTreeResponse> getTopicTree(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return curriculumFacade.getTopicTree(schoolId).stream()
                .map(TopicController::toTreeResponse)
                .toList();
    }

    private static TopicResponse toResponse(TopicInfo info) {
        return new TopicResponse(info.id().value(), info.schoolId(), info.parentId(), info.name(), info.order());
    }

    private static TopicTreeResponse toTreeResponse(TopicNode node) {
        return new TopicTreeResponse(
                node.id().value(), node.name(), node.order(),
                node.children().stream().map(TopicController::toTreeResponse).toList()
        );
    }

    public record CreateTopicRequest(String name, Long parentId, Integer order) {
    }

    public record TopicResponse(Long id, Long schoolId, Long parentId, String name, int order) {
    }

    public record TopicTreeResponse(Long id, String name, int order, List<TopicTreeResponse> children) {
    }
}
