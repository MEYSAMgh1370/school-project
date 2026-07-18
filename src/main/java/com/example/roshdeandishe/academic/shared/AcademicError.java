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
}
