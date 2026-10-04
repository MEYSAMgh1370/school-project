package com.example.roshdeandishe.academic.shared;

import com.example.roshdeandishe.academic.GradingScheme;

/**
 * مدل خطای دامنه‌محور مختص ماژول academic.
 */
public sealed interface AcademicError {

    record StudentNotFound(Long studentId) implements AcademicError {
    }

    record ClassNotFound(Long classId) implements AcademicError {
    }

    /**
     * کاربری که قرار است به‌عنوان ولیِ یک دانش‌آموز لینک شود، عضویت PARENT
     * فعالی در همان مدرسه ندارد - وگرنه هر کاربری (مثلاً یک TEACHER) می‌توانست
     * به داده‌ی یک دانش‌آموز لینک شود.
     */
    record GuardianNotEligible(Long guardianUserId, Long schoolId) implements AcademicError {
    }

    /**
     * نمره برای دانش‌آموزی ثبت شده که در حال حاضر عضو این کلاس نیست (یا به
     * کلاس دیگری منتقل شده) - نمره باید برای همان کلاسی ثبت شود که
     * StudentEntity.classId او در همان لحظه به آن اشاره می‌کند.
     */
    record StudentNotInClass(Long studentId, Long classId) implements AcademicError {
    }

    /**
     * فیلد پرشده (descriptiveLevel/numericScore) با شِمای واقعیِ کلاس
     * (ClassEntity.gradingScheme) همخوانی ندارد - مثلاً نمره‌ی عددی برای
     * کلاسی که شِمای توصیفی دارد.
     */
    record GradingSchemeMismatch(Long classId, GradingScheme expectedScheme) implements AcademicError {
    }

    /**
     * سایر خطاهای اعتبارسنجیِ نمره که به عدم‌تطابقِ شِما مربوط نیستند (مثلاً
     * سال تحصیلی خالی یا نمره‌ی عددی خارج از بازه‌ی ۰ تا ۲۰).
     */
    record InvalidGrade(String reason) implements AcademicError {
    }

    record GradeNotFound(Long gradeId) implements AcademicError {
    }

    /** courseId ای که در فهرست سراسری درس‌ها وجود ندارد. */
    record CourseNotFound(Long courseId) implements AcademicError {
    }

    /**
     * درس برای پایه‌ی دیگری تعریف شده است: فهرست درس‌ها به‌تفکیک پایه است و
     * درسِ یک پایه برای کلاسِ پایه‌ی دیگر نمره‌پذیر نیست.
     */
    record CourseNotForClass(Long courseId, Long classId) implements AcademicError {
    }

    /** درس در برنامهٔ کلاسی هست ولی در کارنامه نمی‌آید ({@code graded = false}): نمره‌پذیر نیست. */
    record CourseNotGradable(Long courseId) implements AcademicError {
    }

    /** درس در بازه‌ی اعتبارش برای این سالِ تحصیلی ارائه نمی‌شود. */
    record CourseNotValidForYear(Long courseId, String academicYear) implements AcademicError {
    }

    /** کاربر فقط سالِ جاریِ مدرسه را می‌بیند (بدونِ ACADEMIC_YEAR_MANAGE) و سالِ دیگری خواسته است. */
    record AcademicYearNotAllowed(String academicYear) implements AcademicError {
    }

    /** کدِ سال تحصیلی در فهرستِ سراسریِ سال‌ها نیست. */
    record InvalidAcademicYear(String academicYear) implements AcademicError {
    }

    record PeriodNotFound(Long periodId) implements AcademicError {
    }

    /** برچسب/زمانِ زنگ نامعتبر (خالی، پایان نه بعد از شروع، ...). */
    record InvalidPeriod(String reason) implements AcademicError {
    }

    /** زنگ با زنگِ دیگرِ همان مدرسه و سال هم‌پوشانی دارد. */
    record PeriodOverlaps(Long otherPeriodId) implements AcademicError {
    }

    /** زنگی که خانه‌ی برنامه دارد حذف نمی‌شود. */
    record PeriodHasEntries(Long periodId) implements AcademicError {
    }

    record ActivityNotFound(Long activityId) implements AcademicError {
    }

    record ActivityNameTaken(String name) implements AcademicError {
    }

    /** پیش‌فرضِ پلتفرم قابل تغییر/حذف توسط مدرسه نیست. */
    record ActivityNotEditable(Long activityId) implements AcademicError {
    }

    /** فعالیتی که در یک خانه‌ی برنامه به کار رفته حذف نمی‌شود. */
    record ActivityInUse(Long activityId) implements AcademicError {
    }

    /** خانه‌ی برنامه نامعتبر (هر دو یا هیچ‌کدام از درس/فعالیت، نام خالی، ...). */
    record InvalidTimetableEntry(String reason) implements AcademicError {
    }

    /** معلمِ پیشنهادی عضوِ فعالِ TEACHER در این مدرسه نیست. */
    record TeacherNotEligible(Long teacherUserId, Long schoolId) implements AcademicError {
    }

    /** همان معلم در همان روز و زنگ در کلاسِ دیگری هست. */
    record TeacherDoubleBooked(Long teacherUserId, String weekday, Long periodId) implements AcademicError {
    }

    /**
     * schoolId ای که در createClass داده شده به هیچ SchoolEntity واقعی‌ای
     * اشاره ندارد - عملاً نباید رخ دهد (TenantContext از قبل schoolId را
     * تأیید کرده)، اما یک بررسی دفاعی است نه چیزی که در مسیر عادی رخ دهد.
     */
    record SchoolNotFound(Long schoolId) implements AcademicError {
    }

    /**
     * gradeLevel داده‌شده برای ساخت کلاس، در بازه‌ی مقطع تحصیلی مدرسه
     * (school.SchoolFacade.SchoolLevel) نیست - مثلاً پایه‌ی ۸ برای مدرسه‌ای
     * که مقطعش دبستان (پایه‌های ۱ تا ۶) است.
     */
    record InvalidGradeLevel(int gradeLevel, int minGrade, int maxGrade) implements AcademicError {
    }

    /** مدرسه سالِ جاری ندارد؛ انتسابِ داده‌ی موجود و ارتقا بدون آن معنا ندارد (هیچ سالی حدس زده نمی‌شود). */
    record YearNotChosen(Long schoolId) implements AcademicError {
    }

    /** درخواستِ انتساب/ارتقای نامعتبر (سالِ مبدأ بعد از جاری، دانش‌آموزی که ارتقا‌پذیر نیست، ...). */
    record InvalidRollover(String reason) implements AcademicError {
    }

    /** کلاس برای سالی است که دانش‌آموز در آن ثبت‌نام می‌شود نیست (فقط مدرسه‌ی دارای سالِ ثبت‌نام‌شده). */
    record ClassNotInYear(Long classId, String classYear, String expectedYear) implements AcademicError {
    }

    /** پایه‌ی کلاس با پایه‌ی هدفِ ارتقای دانش‌آموز یکی نیست. */
    record ClassGradeMismatch(Long classId, int classGrade, int expectedGrade) implements AcademicError {
    }

    record PromotionRunNotFound(Long runId) implements AcademicError {
    }

    /** اجرای ارتقا برگشت‌پذیر نیست (برگشته، اجرای جدیدتری هست، یا دانش‌آموزی از آن در کلاس جا داده شده). */
    record PromotionRunNotUndoable(Long runId, String reason) implements AcademicError {
    }
}
