package com.example.roshdeandishe.exam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * پاسخ انتخابیِ یک دانش‌آموز به یک {@link SectionQuestionEntity} - پورت
 * {@code Submit} موshavere. {@code choice} بین ۱ تا ۴ (همان
 * CheckConstraint منبع)؛ حذف پاسخ (choice=null در درخواست) یعنی حذف کامل
 * همین ردیف، نه مقداردهی null - رجوع به ExamFacadeImpl.submitAnswer.
 */
@Entity
@Table(name = "submits", schema = "exam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"section_question_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
class SubmitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_question_id", nullable = false)
    private Long sectionQuestionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private int choice;

    SubmitEntity(Long sectionQuestionId, Long studentId, int choice) {
        this.sectionQuestionId = sectionQuestionId;
        this.studentId = studentId;
        this.choice = choice;
    }
}
