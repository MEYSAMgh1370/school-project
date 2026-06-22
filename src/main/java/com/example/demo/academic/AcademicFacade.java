package com.example.roshdeandishe.academic;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Academic.
 */
public interface AcademicFacade {

    StudentInfo getStudentInfo(Long studentId);

    record StudentInfo(Long studentId, Long schoolId, String fullName, String gradeLevel) {
    }
}
