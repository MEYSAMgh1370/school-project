package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.AcademicFacade.EnrollmentInfo;

public record MyEnrollmentResponse(Long schoolId, Long classId, String className, String section) {
    public static MyEnrollmentResponse from(EnrollmentInfo info) {
        return new MyEnrollmentResponse(info.schoolId(), info.classId().value(), info.className(), info.section());
    }
}
