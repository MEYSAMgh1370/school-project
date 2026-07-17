package com.example.roshdeandishe.exam;

import com.example.roshdeandishe.exam.ExamFacade.ExamTakeInfo;
import com.example.roshdeandishe.exam.ExamFacade.SectionInfo;
import com.example.roshdeandishe.exam.ExamFacade.TakeQuestionInfo;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * جلسه‌ی زمان‌دارِ آزمون‌دهی - پورت {@code ExamTakeViewSet} موshavere.
 * عمداً بدون {@code @RequiresModule}/{@code @RequiresPermission} و بدون
 * X-School-ID: مکانیزم مجوز این کنترلر کاملاً مستقل است - همان توکن
 * جلسه‌ی آزمون (query param {@code token}, صادرشده توسط
 * {@code MyExamsController.startAttempt}) که examId/studentId/انقضا را
 * خودش حمل می‌کند و توسط ExamFacade در هر فراخوانی decode/اعتبارسنجی
 * می‌شود (رجوع به کلاس‌داک ExamFacade و MOSHAVERE_PORT_SPEC.md §5).
 * <p>
 * در {@code SecurityConfig} permitAll + از CSRF مستثناست، و در
 * {@code TenantResolverFilter} هم مستثناست (نیازی به X-School-ID ندارد) -
 * دقیقاً همان الگوی {@code BaleWebhookController}: فراخوان‌کننده یک سشن
 * مرورگر/JWT موبایل معمولی نیست که این دو زیرساخت برایش طراحی شده‌اند؛
 * schoolId خودش از examId داخل توکن resolve می‌شود، هیچ‌جا لازم نیست
 * صریح فرستاده شود.
 */
@RestController
@RequestMapping("/api/exam-take")
public class ExamTakeController {

    private final ExamFacade examFacade;

    public ExamTakeController(ExamFacade examFacade) {
        this.examFacade = examFacade;
    }

    @GetMapping("/exam")
    public ExamTakeResponse getExam(@RequestParam String token) {
        return toResponse(examFacade.getExamForToken(token));
    }

    @GetMapping("/sections/{sectionId}/questions")
    public List<TakeQuestionResponse> getSectionQuestions(@RequestParam String token, @PathVariable Long sectionId) {
        return examFacade.getSectionQuestions(token, sectionId).stream()
                .map(ExamTakeController::toResponse)
                .toList();
    }

    @PostMapping("/submit")
    public void submit(@RequestParam String token, @RequestBody SubmitRequest request) {
        examFacade.submitAnswer(token, request.sectionId(), request.questionId(), request.choice());
    }

    private static ExamTakeResponse toResponse(ExamTakeInfo info) {
        return new ExamTakeResponse(info.id().value(), info.title(), info.start(), info.end(),
                info.durationMinutes(), info.sections().stream().map(ExamTakeController::toResponse).toList());
    }

    private static SectionTakeResponse toResponse(SectionInfo info) {
        return new SectionTakeResponse(info.id().value(), info.topicId(), info.name(), info.questionCount());
    }

    private static TakeQuestionResponse toResponse(TakeQuestionInfo info) {
        return new TakeQuestionResponse(info.questionId(), info.text(), info.choice1(), info.choice2(),
                info.choice3(), info.choice4(), info.selectedChoice());
    }

    public record SubmitRequest(Long sectionId, Long questionId, Integer choice) {
    }

    public record ExamTakeResponse(Long id, String title, LocalDateTime start, LocalDateTime end,
                                   int durationMinutes, List<SectionTakeResponse> sections) {
    }

    public record SectionTakeResponse(Long id, Long topicId, String name, int questionCount) {
    }

    public record TakeQuestionResponse(Long questionId, String text, String choice1, String choice2,
                                       String choice3, String choice4, Integer selectedChoice) {
    }
}
