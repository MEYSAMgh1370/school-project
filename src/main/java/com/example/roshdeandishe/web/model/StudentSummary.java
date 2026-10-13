package com.example.roshdeandishe.web.model;

import java.util.List;

/**
 * خلاصهٔ یک دانش‌آموز روی پیشخوان (خودِ دانش‌آموز یا هر فرزندِ والد): آخرین نمره‌ها، آزمون‌های پیش‌رو و
 * شمارِ غیبت در سالِ جاری. {@code attendance} تهی یعنی مدرسه ماژولِ حضور و غیاب را ندارد یا سالِ جاری انتخاب
 * نشده؛ ردیفش نشان داده نمی‌شود.
 *
 * @param attendance شمارِ روزهای ثبت‌شده؛ روزِ ثبت‌نشده هرگز غایب (یا حاضر) شمرده نمی‌شود
 */
public record StudentSummary(List<GradeLine> grades, List<ExamLine> exams, AttendanceCount attendance) {

    public StudentSummary {
        grades = List.copyOf(grades);
        exams = List.copyOf(exams);
    }

    /** {@code subject}: نامِ درس یا «نمره‌ی کل»؛ {@code value}: عدد یا سطحِ توصیفی. */
    public record GradeLine(String subject, String term, String value) {
    }

    /** {@code when}: تاریخِ شمسیِ شروع. */
    public record ExamLine(String title, String when) {
    }

    /** {@code absent}: غیبتِ بدونِ عذر؛ {@code excused}: غیبتِ موجه؛ {@code late}: دیرآمدن. */
    public record AttendanceCount(int absent, int excused, int late) {
    }

    public boolean hasAttendance() {
        return attendance != null;
    }

    public boolean hasGrades() {
        return !grades.isEmpty();
    }

    public boolean hasExams() {
        return !exams.isEmpty();
    }
}
