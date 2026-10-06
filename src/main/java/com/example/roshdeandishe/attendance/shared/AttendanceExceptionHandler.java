package com.example.roshdeandishe.attendance.shared;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * همان الگوی FinanceExceptionHandler: {@code @Order(0)} تا قبل از {@code PlatformExceptionHandler}
 * اجرا شود، و نام Bean صریح و یکتا از طریق {@code @Component("...")} است (نه
 * {@code @RestControllerAdvice("...")} که value آن به basePackages نگاشت می‌شود و handler را بی‌اثر می‌کند).
 * فقط routing است؛ نگاشت در {@link AttendanceProblemResolver}.
 */
@RestControllerAdvice
@Component("attendanceExceptionHandler")
@Order(0)
public class AttendanceExceptionHandler {

    private final AttendanceProblemResolver resolver;

    public AttendanceExceptionHandler(AttendanceProblemResolver resolver) {
        this.resolver = resolver;
    }

    @ExceptionHandler(AttendanceException.class)
    public ProblemDetail handleAttendanceException(AttendanceException ex) {
        return resolver.resolve(ex.error());
    }
}
