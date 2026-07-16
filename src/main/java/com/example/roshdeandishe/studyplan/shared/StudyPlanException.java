package com.example.roshdeandishe.studyplan.shared;

public final class StudyPlanException extends RuntimeException {

    private final StudyPlanError error;

    public StudyPlanException(StudyPlanError error) {
        super(describe(error));
        this.error = error;
    }

    public StudyPlanError error() {
        return error;
    }

    private static String describe(StudyPlanError error) {
        return switch (error) {
            case StudyPlanError.PlanNotFound e -> "برنامه‌ای با شناسه " + e.planId() + " یافت نشد.";
            case StudyPlanError.StudentNotFound e -> "دانش‌آموزی با شناسه " + e.studentId() + " در این مدرسه یافت نشد.";
            case StudyPlanError.TopicNotFound e -> "سرفصلی با شناسه " + e.topicId() + " در این مدرسه یافت نشد.";
            case StudyPlanError.InvalidPlan e -> e.detail();
            case StudyPlanError.InvalidWeekCourse e -> e.detail();
            case StudyPlanError.DuplicateWeekCourse e ->
                    "برای این برنامه در هفته‌ی " + e.weekStart() + " سرفصل " + e.courseTopicId() + " قبلاً ثبت شده است.";
            case StudyPlanError.InvalidSchedule e -> e.detail();
            case StudyPlanError.InvalidWeekStart e -> e.detail();
            case StudyPlanError.WeekNotEmpty e -> "هفته‌ی " + e.weekStart() + " خالی نیست.";
            case StudyPlanError.ScheduleTooSmall e ->
                    "مجموع زمان آزاد برنامه (" + e.totalMinutes() + " دقیقه) کمتر از ۱۸۰ دقیقه است.";
            case StudyPlanError.PlanNotAssigned e ->
                    "دانش‌آموز " + e.studentId() + " هنوز به هیچ برنامه‌ای تخصیص داده نشده است.";
            case StudyPlanError.IntervalNotFound e -> "بازه‌ای با شناسه " + e.intervalId() + " در این مدرسه یافت نشد.";
            case StudyPlanError.IntervalOverlaps e -> e.detail();
            case StudyPlanError.InvalidInterval e -> e.detail();
        };
    }
}
