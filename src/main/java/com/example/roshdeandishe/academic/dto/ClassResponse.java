package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.GradingScheme;

public record ClassResponse(Long id, Long schoolId, String name, Long teacherId, String section, int gradeLevel, GradingScheme gradingScheme,
                            String academicYear) {

    public ClassResponse(Long id, Long schoolId, String name, Long teacherId, String section, int gradeLevel,
                         GradingScheme gradingScheme) {
        this(id, schoolId, name, teacherId, section, gradeLevel, gradingScheme, null);
    }
}
