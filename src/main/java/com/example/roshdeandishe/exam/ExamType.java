package com.example.roshdeandishe.exam;

/**
 * نوع آزمون - جایگزینِ زنجیره‌ی وراثت مدل جنگو در منبع moshavere
 * ({@code Exam} → {@code PersonalExam} → {@code WeeklyExam} /
 * {@code CollectiveExam}) با یک enum روی یک entity تخت، مطابق ترجیح
 * این codebase برای enum صریح به‌جای زنجیره‌ی وراثت (همان تصمیم
 * {@code ModuleScope}/{@code IamError}، MOSHAVERE_PORT_SPEC.md §5).
 * <p>
 * WEEKLY یعنی آزمونی خودکار و شخصی برای یک دانش‌آموز مشخص (ساخته‌شده از
 * سرفصل‌های مطالعه‌شده‌ی اخیرش - {@code StudyPlanFacade.getRecentTopics});
 * COLLECTIVE یعنی آزمونی که یک معلم/ادمین برای گروهی از دانش‌آموزان
 * تألیف می‌کند. {@code ExamEntity.studentId}/{@code teacherId} بسته به
 * این نوع پر می‌شوند - رجوع به ExamEntity.
 */
public enum ExamType {
    WEEKLY,
    COLLECTIVE
}
