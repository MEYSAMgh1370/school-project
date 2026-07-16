package com.example.roshdeandishe.studyplan.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * نگاشت StudyPlanError به ProblemDetail - ۱۴ case از حد «چند مورد ساده»ی
 * CLAUDE.md §6 می‌گذرد، پس مثل Iam/FinanceProblemResolver در یک @Component
 * مستقلِ قابل unit-test بدون MVC جدا شده و StudyPlanExceptionHandler فقط
 * routing است.
 */
@Component
public class StudyPlanProblemResolver {

    public ProblemDetail resolve(StudyPlanError error) {
        return switch (error) {
            case StudyPlanError.PlanNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "plan-not-found",
                    "برنامه یافت نشد",
                    "برنامه‌ای با شناسه " + e.planId() + " در این مدرسه وجود ندارد."
            );
            case StudyPlanError.StudentNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "student-not-found",
                    "دانش‌آموز یافت نشد",
                    "دانش‌آموزی با شناسه " + e.studentId() + " در این مدرسه وجود ندارد."
            );
            case StudyPlanError.TopicNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "topic-not-found",
                    "سرفصل یافت نشد",
                    "سرفصلی با شناسه " + e.topicId() + " در این مدرسه وجود ندارد."
            );
            case StudyPlanError.InvalidPlan e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-plan",
                    "برنامه نامعتبر",
                    e.detail()
            );
            case StudyPlanError.InvalidWeekCourse e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-week-course",
                    "سرفصل هفتگی نامعتبر",
                    e.detail()
            );
            case StudyPlanError.DuplicateWeekCourse e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "duplicate-week-course",
                    "سرفصل هفتگی تکراری",
                    "برای این برنامه در هفته‌ی " + e.weekStart() + " سرفصل " + e.courseTopicId()
                            + " قبلاً ثبت شده است."
            );
            case StudyPlanError.InvalidSchedule e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-schedule",
                    "برنامه‌ی زمانی نامعتبر",
                    e.detail()
            );
            case StudyPlanError.InvalidWeekStart e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-week-start",
                    "شروع هفته نامعتبر",
                    e.detail()
            );
            case StudyPlanError.WeekNotEmpty e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "week-not-empty",
                    "هفته خالی نیست",
                    "برای هفته‌ی " + e.weekStart() + " بازه‌ی مطالعه از قبل ثبت شده است - اول هفته را خالی کنید."
            );
            case StudyPlanError.ScheduleTooSmall e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "schedule-too-small",
                    "زمان آزاد برنامه کافی نیست",
                    "مجموع زمان آزاد برنامه (" + e.totalMinutes() + " دقیقه) کمتر از ۱۸۰ دقیقه است."
            );
            case StudyPlanError.PlanNotAssigned e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "plan-not-assigned",
                    "برنامه‌ای تخصیص داده نشده",
                    "دانش‌آموز " + e.studentId() + " هنوز به هیچ برنامه‌ای تخصیص داده نشده است."
            );
            case StudyPlanError.IntervalNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "interval-not-found",
                    "بازه‌ی مطالعه یافت نشد",
                    "بازه‌ای با شناسه " + e.intervalId() + " در این مدرسه وجود ندارد."
            );
            case StudyPlanError.IntervalOverlaps e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "interval-overlaps",
                    "هم‌پوشانی بازه‌ی مطالعه",
                    e.detail()
            );
            case StudyPlanError.InvalidInterval e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-interval",
                    "بازه‌ی مطالعه نامعتبر",
                    e.detail()
            );
        };
    }
}
