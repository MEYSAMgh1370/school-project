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
}
