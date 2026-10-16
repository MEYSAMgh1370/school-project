package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.school.shared.SchoolException;
import com.example.roshdeandishe.studentview.StudentSummary;
import com.example.roshdeandishe.studentview.StudentViewFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * پاسخِ پرسش‌های دانش‌آموزمحور: حضور امروز، خلاصه‌ی غیبت، نمره‌ها و امتحان‌ها. فقط از
 * {@link StudentViewFacade} می‌خواند و فقط درباره‌ی همان {@link BotTarget.Student} که مسیریاب از
 * {@link SubjectResolver} گرفته؛ هیچ شناسه‌ای از ورودی کاربر نمی‌آید. خطای دامنه‌ی facade به جمله‌ی «در دسترس
 * نیست» همان پرسش تبدیل و در سطح warn لاگ می‌شود (فقط شناسه‌ها، نه محتوا) و هرگز بیرون نمی‌رود.
 */
@Component
class StudentAnswers {

    private static final Logger log = LoggerFactory.getLogger(StudentAnswers.class);

    private final StudentViewFacade studentView;

    StudentAnswers(StudentViewFacade studentView) {
        this.studentView = studentView;
    }

    /** پاسخ {@code intent} برای {@code target}؛ خط اول نامِ دانش‌آموز و مدرسه است. */
    String answer(BotIntent intent, BotContext ctx, BotTarget target) {
        BotTarget.Student student = (BotTarget.Student) target;
        String header = BotTexts.studentHeader(student.name(), student.schoolName());
        String unavailable = switch (intent) {
            case GRADES -> BotTexts.GRADES_UNAVAILABLE;
            case EXAMS -> BotTexts.EXAMS_UNAVAILABLE;
            default -> BotTexts.ATTENDANCE_UNAVAILABLE;
        };
        String body;
        try {
            body = switch (intent) {
                case ATTENDANCE_TODAY -> BotTexts.attendanceToday(
                        studentView.todayAttendance(ctx.userId(), student.schoolId(), student.classId(),
                                student.studentId()),
                        !student.ward());
                case ATTENDANCE_SUMMARY -> summary(ctx, student);
                case GRADES -> grades(ctx, student);
                case EXAMS -> exams(ctx, student);
                default -> throw new IllegalArgumentException("intent " + intent + " is not a student intent");
            };
        } catch (AttendanceException | AcademicException | SchoolException e) {
            log.warn("Bot intent {} failed for school {} student {}: {}", intent, student.schoolId(),
                    student.studentId(), e.getClass().getSimpleName());
            body = unavailable;
        }
        return header + "\n" + body;
    }

    private String summary(BotContext ctx, BotTarget.Student student) {
        StudentSummary.AttendanceCount count = studentView.summary(ctx.userId(), student.schoolId(),
                student.studentId()).attendance();
        return count == null ? BotTexts.ATTENDANCE_UNAVAILABLE
                : BotTexts.attendanceSummary(count.absent(), count.excused(), count.late());
    }

    private String grades(BotContext ctx, BotTarget.Student student) {
        List<StudentSummary.GradeLine> grades = studentView.summary(ctx.userId(), student.schoolId(),
                student.studentId()).grades();
        if (grades.isEmpty()) {
            return BotTexts.GRADES_EMPTY;
        }
        List<String> lines = new ArrayList<>();
        grades.forEach(g -> lines.add(BotTexts.gradeLine(g.subject(), g.term(), g.value())));
        return String.join("\n", lines);
    }

    private String exams(BotContext ctx, BotTarget.Student student) {
        List<StudentSummary.ExamLine> exams = studentView.summary(ctx.userId(), student.schoolId(),
                student.studentId()).exams();
        if (exams.isEmpty()) {
            return BotTexts.EXAMS_EMPTY;
        }
        List<String> lines = new ArrayList<>();
        exams.forEach(e -> lines.add(BotTexts.examLine(e.title(), e.when())));
        return String.join("\n", lines);
    }
}
