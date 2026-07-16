package com.example.roshdeandishe.studyplan.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * همان الگوی CurriculumExceptionHandler: {@code @Order(0)} تا قبل از
 * PlatformExceptionHandler اجرا شود، نام Bean از طریق
 * {@code @Component("...")}.
 */
@RestControllerAdvice
@Component("studyPlanExceptionHandler")
@Order(0)
public class StudyPlanExceptionHandler {

    @ExceptionHandler(StudyPlanException.class)
    public ProblemDetail handleStudyPlanException(StudyPlanException ex) {
        return switch (ex.error()) {
            case StudyPlanError.PlanNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "plan-not-found",
                    "برنامه یافت نشد",
                    "برنامه‌ای با شناسه " + e.planId() + " در این مدرسه یافت نشد."
            );
            case StudyPlanError.InvalidPlan e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-plan",
                    "برنامه نامعتبر",
                    e.reason()
            );
            case StudyPlanError.TopicNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "topic-not-found",
                    "سرفصل یافت نشد",
                    "سرفصلی با شناسه " + e.topicId() + " در این مدرسه یافت نشد."
            );
            case StudyPlanError.StudentNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "student-not-found",
                    "دانش‌آموز یافت نشد",
                    "دانش‌آموزی با شناسه " + e.studentId() + " در این مدرسه یافت نشد."
            );
            case StudyPlanError.PlanNotAssigned e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "plan-not-assigned",
                    "برنامه‌ای تخصیص داده نشده",
                    "دانش‌آموز " + e.studentId() + " هیچ برنامه‌ی مطالعاتی‌ای ندارد."
            );
            case StudyPlanError.InvalidWeekCourse e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-week-course",
                    "درسِ هفته نامعتبر",
                    e.reason()
            );
            case StudyPlanError.NotSaturday e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "not-saturday",
                    "تاریخ شروع هفته نامعتبر",
                    "تاریخ " + e.date() + " شنبه نیست؛ هفته باید از شنبه شروع شود."
            );
            case StudyPlanError.InvalidSchedule e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-schedule",
                    "برنامه‌ی زمانی نامعتبر",
                    e.reason()
            );
            case StudyPlanError.ScheduleTooSmall e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "schedule-too-small",
                    "زمان آزاد ناکافی",
                    "زمان کل آزادِ هفته (" + e.totalMinutes() + " دقیقه) باید حداقل ۱۸۰ دقیقه باشد."
            );
            case StudyPlanError.WeekNotEmpty e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "week-not-empty",
                    "هفته خالی نیست",
                    "هفته‌ی شروع‌شده از " + e.weekStartDate() + " از قبل بازه‌ی مطالعه دارد."
            );
            case StudyPlanError.StudyIntervalNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "study-interval-not-found",
                    "بازه‌ی مطالعه یافت نشد",
                    "بازه‌ی مطالعه‌ای با شناسه " + e.intervalId() + " یافت نشد."
            );
        };
    }
}
