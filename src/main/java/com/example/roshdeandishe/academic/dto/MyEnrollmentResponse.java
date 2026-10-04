package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.AcademicFacade.EnrollmentInfo;

/** فقط برای دانش‌آموزِ دارای کلاس (بدونِ کلاس: 204 در {@code MyEnrollmentController}). */
public record MyEnrollmentResponse(Long schoolId, Long classId, String className, String section,
                                   com.example.roshdeandishe.academic.StudentStatus status) {
    public static MyEnrollmentResponse from(EnrollmentInfo info) {
        return new MyEnrollmentResponse(info.schoolId(), info.classId() == null ? null : info.classId().value(),
                info.className(), info.section(), info.status());
    }
}
