package com.example.roshdeandishe.school.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * مدیریت اکسپشن‌های مختص ماژول school. نام Bean صریح است (مطابق همان
 * قراردادی که برای IamExceptionHandler/AcademicExceptionHandler استفاده شد)
 * تا تداخل نام Bean بین ماژول‌ها رخ ندهد.
 */
@RestControllerAdvice("schoolExceptionHandler")
public class SchoolExceptionHandler {

    @ExceptionHandler(SchoolException.class)
    public ProblemDetail handleSchoolException(SchoolException ex) {
        return switch (ex.error()) {
            case SchoolError.SchoolNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "school-not-found",
                    "مدرسه یافت نشد",
                    "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد."
            );
        };
    }
}
