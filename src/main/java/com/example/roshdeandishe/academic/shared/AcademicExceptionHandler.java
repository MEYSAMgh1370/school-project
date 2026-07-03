package com.example.roshdeandishe.academic.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice("academicGlobalExceptionHandler")
public class AcademicExceptionHandler {

    @ExceptionHandler(AcademicException.class)
    public ProblemDetail handleAcademicException(AcademicException ex) {
        return switch (ex.error()) {
            case AcademicError.StudentNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "student-not-found",
                    "دانش‌آموز یافت نشد",
                    "دانش‌آموزی با شناسه " + e.studentId() + " یافت نشد."
            );
            case AcademicError.ClassNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "class-not-found",
                    "کلاس یافت نشد",
                    "کلاسی با شناسه " + e.classId() + " یافت نشد."
            );
        };
    }
}
