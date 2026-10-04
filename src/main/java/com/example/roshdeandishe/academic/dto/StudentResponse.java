package com.example.roshdeandishe.academic.dto;

/**
 * {@code classId} برای دانش‌آموزِ PENDING/GRADUATED/LEFT تهی است؛ {@code status} همان
 * {@link com.example.roshdeandishe.academic.StudentStatus}.
 */
public record StudentResponse(Long studentId, Long userId, Long schoolId, Long classId,
                              com.example.roshdeandishe.academic.StudentStatus status) {

    public StudentResponse(Long studentId, Long userId, Long schoolId, Long classId) {
        this(studentId, userId, schoolId, classId, com.example.roshdeandishe.academic.StudentStatus.ACTIVE);
    }
}
