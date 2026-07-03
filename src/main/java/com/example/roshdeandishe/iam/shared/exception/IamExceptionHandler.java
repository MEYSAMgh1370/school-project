package com.example.roshdeandishe.iam.shared.exception;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * فقط مسئول routing است: IamException را به IamProblemResolver می‌دهد.
 * (قبلاً GlobalExceptionHandler نام داشت؛ برای رفع تداخل نام Bean بین
 * ماژول‌ها، به IamExceptionHandler تغییر نام یافت.)
 * منطق نگاشت خطا -> ProblemDetail اینجا نیست (در IamProblemResolver است)
 * تا این کلاس کوچک و قابل‌تست بماند و resolver بدون نیاز به MVC context
 * مستقلاً تست شود.
 */
@RestControllerAdvice("iamGlobalExceptionHandler")
@RequiredArgsConstructor
public class IamExceptionHandler {

    private final IamProblemResolver resolver;

    @ExceptionHandler(IamException.class)
    public ProblemDetail handleIamException(IamException ex) {
        return resolver.resolve(ex.error());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        // معمولاً به این معنی است که TenantContext مقداردهی نشده (هدر X-School-ID نبوده)
        return ProblemDetailFactory.problem(
                HttpStatus.BAD_REQUEST, "tenant-context-missing",
                "Context نامعتبر", ex.getMessage()
        );
    }
}
