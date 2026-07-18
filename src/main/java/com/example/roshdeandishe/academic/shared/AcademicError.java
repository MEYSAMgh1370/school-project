package com.example.roshdeandishe.academic.shared;

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
}
