package com.example.roshdeandishe.studyplan.shared;

public class StudyPlanException extends RuntimeException {

    private final StudyPlanError error;

    public StudyPlanException(StudyPlanError error) {
        super(error.toString());
        this.error = error;
    }

    public StudyPlanError error() {
        return error;
    }
}
