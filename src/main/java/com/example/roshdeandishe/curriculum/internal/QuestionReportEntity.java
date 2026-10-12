package com.example.roshdeandishe.curriculum.internal;

import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportStatus;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * گزارش مشکل یک سؤال (V25). {@code reporterUserId} و {@code schoolId} شناسه‌ی ساده‌اند (بدون کلید خارجی
 * به iam). این موجودیت هرگز خودِ سؤال را تغییر نمی‌دهد (D10).
 */
@Entity
@Table(name = "question_reports", schema = "curriculum")
@Getter
@Setter
@NoArgsConstructor
class QuestionReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    /** مدرسه‌ی گزارش‌دهنده. */
    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private ReportType reportType;

    @Column(columnDefinition = "text")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    QuestionReportEntity(Long questionId, Long reporterUserId, Long schoolId, ReportType reportType,
                         String comment, Instant now) {
        this.questionId = questionId;
        this.reporterUserId = reporterUserId;
        this.schoolId = schoolId;
        this.reportType = reportType;
        this.comment = comment;
        this.status = ReportStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
