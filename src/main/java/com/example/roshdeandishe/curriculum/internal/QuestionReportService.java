package com.example.roshdeandishe.curriculum.internal;

import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionReportInfo;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportId;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportStatus;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportType;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * منطق گزارش مشکل سؤال (D8-D10) که {@link CurriculumFacadeImpl} به آن delegate می‌کند. هیچ مسیری ردیف سؤال را
 * تغییر نمی‌دهد: سؤال فقط خوانده می‌شود.
 */
@Service
class QuestionReportService {

    static final int MAX_TEXT_LENGTH = 1000;

    private final QuestionReportRepository reportRepository;
    private final QuestionRepository questionRepository;

    QuestionReportService(QuestionReportRepository reportRepository, QuestionRepository questionRepository) {
        this.reportRepository = reportRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    ReportId create(Long schoolId, Long reporterUserId, Long questionId, ReportType reportType, String comment) {
        if (reportType == null) {
            throw new CurriculumException(new CurriculumError.InvalidReport("نوع گزارش الزامی است."));
        }
        String cleanComment = clean(comment, "توضیح گزارش");
        QuestionEntity question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CurriculumException(new CurriculumError.QuestionNotFound(questionId)));
        // سؤال مدرسه‌ی دیگر از دید این مدرسه «یافت نشد» است (همان قرارداد TopicNotFound)
        if (question.getSchoolId() != null && !question.getSchoolId().equals(schoolId)) {
            throw new CurriculumException(new CurriculumError.QuestionNotFound(questionId));
        }
        QuestionReportEntity saved = reportRepository.save(
                new QuestionReportEntity(questionId, reporterUserId, schoolId, reportType, cleanComment, Instant.now()));
        return new ReportId(saved.getId());
    }

    @Transactional(readOnly = true)
    List<QuestionReportInfo> listForSchool(Long schoolId, ReportStatus status) {
        return toInfos(withStatus(reportRepository.findForSchool(schoolId), status));
    }

    @Transactional(readOnly = true)
    List<QuestionReportInfo> listByReporter(Long schoolId, Long userId) {
        return toInfos(reportRepository.findByReporter(schoolId, userId));
    }

    @Transactional(readOnly = true)
    List<QuestionReportInfo> listPlatform(ReportStatus status) {
        return toInfos(withStatus(reportRepository.findOnPlatformQuestions(), status));
    }

    private static List<QuestionReportEntity> withStatus(List<QuestionReportEntity> reports, ReportStatus status) {
        return status == null ? reports : reports.stream().filter(r -> r.getStatus() == status).toList();
    }

    @Transactional
    QuestionReportInfo updateForSchool(Long schoolId, Long reportId, ReportStatus newStatus, String note) {
        QuestionReportEntity report = requireReport(reportId);
        QuestionEntity question = questionRepository.findById(report.getQuestionId()).orElse(null);
        // فقط گزارشِ سؤالِ اختصاصیِ همین مدرسه؛ هر چیز دیگر از این صف «یافت نشد» است
        if (question == null || question.getSchoolId() == null || !question.getSchoolId().equals(schoolId)) {
            throw new CurriculumException(new CurriculumError.ReportNotFound(reportId));
        }
        return transition(report, question, newStatus, note);
    }

    @Transactional
    QuestionReportInfo updatePlatform(Long reportId, ReportStatus newStatus, String note) {
        QuestionReportEntity report = requireReport(reportId);
        QuestionEntity question = questionRepository.findById(report.getQuestionId()).orElse(null);
        if (question == null || question.getSchoolId() != null) {
            throw new CurriculumException(new CurriculumError.ReportNotFound(reportId));
        }
        return transition(report, question, newStatus, note);
    }

    private QuestionReportEntity requireReport(Long reportId) {
        return reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new CurriculumException(new CurriculumError.ReportNotFound(reportId)));
    }

    private QuestionReportInfo transition(QuestionReportEntity report, QuestionEntity question,
                                          ReportStatus newStatus, String note) {
        if (newStatus == null) {
            throw new CurriculumException(new CurriculumError.InvalidReport("وضعیت جدید الزامی است."));
        }
        String cleanNote = clean(note, "یادداشت نتیجه");
        if (!allowed(report.getStatus(), newStatus)) {
            throw new CurriculumException(new CurriculumError.InvalidReportTransition(
                    report.getStatus().name(), newStatus.name()));
        }
        report.setStatus(newStatus);
        if (cleanNote != null) {
            report.setResolutionNote(cleanNote);
        }
        report.setUpdatedAt(Instant.now());
        return toInfo(reportRepository.save(report), question);
    }

    private static boolean allowed(ReportStatus from, ReportStatus to) {
        return (from == ReportStatus.OPEN && to == ReportStatus.REVIEWED)
                || (from == ReportStatus.REVIEWED && (to == ReportStatus.FIXED || to == ReportStatus.CLOSED));
    }

    private static String clean(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > MAX_TEXT_LENGTH) {
            throw new CurriculumException(new CurriculumError.InvalidReport(
                    label + " نباید بیشتر از " + MAX_TEXT_LENGTH + " نویسه باشد."));
        }
        return stripped;
    }

    private List<QuestionReportInfo> toInfos(List<QuestionReportEntity> reports) {
        Map<Long, QuestionEntity> questions = questionRepository
                .findAllById(reports.stream().map(QuestionReportEntity::getQuestionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(QuestionEntity::getId, Function.identity()));
        return reports.stream().map(r -> toInfo(r, questions.get(r.getQuestionId()))).toList();
    }

    private static QuestionReportInfo toInfo(QuestionReportEntity r, QuestionEntity q) {
        return new QuestionReportInfo(
                new ReportId(r.getId()),
                r.getQuestionId(),
                q.getText(),
                q.getSchoolId() == null,
                r.getReporterUserId(),
                r.getSchoolId(),
                r.getReportType(),
                r.getComment(),
                r.getStatus(),
                r.getResolutionNote(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
