package com.example.roshdeandishe.academic.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @Order(0)}: باید قبل از {@code PlatformExceptionHandler}
 * ({@code @Order(Integer.MAX_VALUE)}) اجرا شود، وگرنه هر دو روی مقدار
 * پیش‌فرض {@code Ordered.LOWEST_PRECEDENCE} تساوی می‌خورند و نتیجه به
 * ترتیب نامعین ثبت beanها بستگی پیدا می‌کند.
 * <p>
 * ⚠️ نام Bean از طریق {@code @Component("...")} تنظیم شده، نه
 * {@code @RestControllerAdvice("...")}. {@code value()} در
 * {@code @ControllerAdvice} با {@code @AliasFor("basePackages")} به
 * {@code basePackages} (یک {@code String[]}) نگاشت می‌شود - پاس دادن رشته
 * به آن، Advice را به کنترلرهای زیرِ پکیجی به همان نام محدود می‌کند (که
 * وجود ندارد)، و این handler را برای همیشه غیرقابل‌اعمال بر هر کنترلر
 * واقعی می‌کند، صرف‌نظر از @Order. تأیید شده با اجرای واقعی HTTP.
 */
@RestControllerAdvice
@Component("academicGlobalExceptionHandler")
@Order(0)
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
            case AcademicError.GuardianNotEligible e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "guardian-not-eligible",
                    "کاربر واجد شرایط ولی نیست",
                    "کاربر " + e.guardianUserId() + " عضویت ولیِ فعالی در این مدرسه ندارد."
            );
        };
    }
}
