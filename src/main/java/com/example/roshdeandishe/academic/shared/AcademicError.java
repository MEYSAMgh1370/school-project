package com.example.roshdeandishe.academic.shared;

/**
 * مدل خطای دامنه‌محور مختص ماژول academic.
 */
public sealed interface AcademicError {

    record StudentNotFound(Long studentId) implements AcademicError {
    }

    record ClassNotFound(Long classId) implements AcademicError {
    }
}
