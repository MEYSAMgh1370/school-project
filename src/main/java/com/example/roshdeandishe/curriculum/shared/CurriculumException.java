package com.example.roshdeandishe.curriculum.shared;

public class CurriculumException extends RuntimeException {

    private final CurriculumError error;

    public CurriculumException(CurriculumError error) {
        super(error.toString());
        this.error = error;
    }

    public CurriculumError error() {
        return error;
    }
}
