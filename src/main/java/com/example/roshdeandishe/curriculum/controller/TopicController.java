package com.example.roshdeandishe.curriculum.controller;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicId;
import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicInfo;
import com.example.roshdeandishe.curriculum.CurriculumFacade.TopicTreeNode;
import com.example.roshdeandishe.curriculum.dto.request.CreateTopicRequest;
import com.example.roshdeandishe.curriculum.dto.response.TopicResponse;
import com.example.roshdeandishe.curriculum.dto.response.TopicTreeResponse;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * مدیریت تاکسونومی سرفصل‌های یک مدرسه. تقسیم Permission طبق تصمیم صریح
 * new-module این ماژول: CURRICULUM_MANAGE (ساخت) به SCHOOL_ADMIN و
 * TEACHER هر دو داده می‌شود - تألیف بانک سؤال کار روزمره‌ی معلم است،
 * همان استدلال STUDENT_ENROLL، نه تصمیم اداری مثل CLASS_MANAGE.
 * CURRICULUM_VIEW (خواندن) هم فقط همین دو نقش - عمداً نه STUDENT/PARENT،
 * چون بانک سؤال گزینه‌ی درست و پاسخ تشریحی را حمل می‌کند؛ دسترسی
 * دانش‌آموز بعداً از طریق ماژول exam (با token مخصوص جلسه‌ی آزمون)
 * میانجی‌گری می‌شود، نه خواندن مستقیم بانک.
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

        TopicId topicId = curriculumFacade.createTopic(
                schoolId, request.parentId(), request.name(), request.sortOrder()
        );
        return new TopicResponse(
                topicId.value(), schoolId, request.parentId(), request.name(),
                request.sortOrder() == null ? 0 : request.sortOrder()
        );
    }

    @GetMapping
    @RequiresPermission("CURRICULUM_VIEW")
    public List<TopicResponse> listTopics(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return curriculumFacade.listTopics(schoolId).stream()
                .map(TopicController::toResponse)
                .toList();
    }

    /**
     * درخت تو در تو - جایگزین مسیرخوانی درخت pickle شده‌ی moshavere؛
     * children هر گره داخل خودش است، پس مسیر اجداد هم سمت کلاینت از همین
     * ساختار قابل استخراج است.
     */
    @GetMapping("/tree")
    @RequiresPermission("CURRICULUM_VIEW")
    public List<TopicTreeResponse> getTopicTree(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return curriculumFacade.getTopicTree(schoolId).stream()
                .map(TopicController::toTreeResponse)
                .toList();
    }

    private static TopicResponse toResponse(TopicInfo info) {
        return new TopicResponse(info.id().value(), info.schoolId(), info.parentId(), info.name(), info.sortOrder());
    }

    private static TopicTreeResponse toTreeResponse(TopicTreeNode node) {
        return new TopicTreeResponse(
                node.id().value(), node.name(), node.sortOrder(),
                node.children().stream().map(TopicController::toTreeResponse).toList()
        );
    }
}
