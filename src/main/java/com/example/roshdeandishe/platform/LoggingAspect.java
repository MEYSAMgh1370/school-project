package com.example.roshdeandishe.platform;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * لاگ‌گیری عمومی ورود/خروج/مدت‌زمان اجرا برای هر متدی که با {@link Loggable}
 * علامت‌گذاری شده (مستقیم روی متد یا روی کلاس).
 * <p>
 * عمداً در {@code platform} قرار دارد، نه در iam.shared - چون این aspect
 * هیچ دانشی از منطق دامنه‌ای هیچ ماژولی ندارد و باید بتواند روی متدهای
 * iam, academic, finance یکسان کار کند.
 * <p>
 * {@code @Order(1)} یعنی این aspect باید outermost باشد - یعنی قبل از هر
 * Aspect دیگری (مثل {@code ModuleAccessAspect} با Order(10)) اجرا شود و
 * دورتادور آن را بگیرد. این‌طوری حتی اگر یک درخواست با ModuleNotEntitled رد
 * شود، باز هم "ورود" و "خروج با خطا" برای آن لاگ می‌شود.
 * <p>
 * schoolId از {@link TenantContext} خوانده می‌شود (نه از پارامتر متد)، چون
 * این یک Cross-Cutting Concern است و method signatureها نباید برای لاگ‌گیری
 * تغییر کنند. اگر TenantContext مقداردهی نشده باشد (مثلاً درخواست
 * USER-scoped یا PLATFORM-scoped)، schoolId به‌صورت "-" لاگ می‌شود.
 */
@Aspect
@Component
@Order(1)
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("@annotation(com.example.roshdeandishe.platform.Loggable) || @within(com.example.roshdeandishe.platform.Loggable)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String signature = joinPoint.getSignature().toShortString();
        String schoolId = schoolIdOrDash();

        log.info("-> {} [schoolId={}]", signature, schoolId);
        long startedAt = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("<- {} [schoolId={}] OK ({} ms)", signature, schoolId, elapsedMs);
            return result;
        } catch (Throwable ex) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.warn(
                    "<- {} [schoolId={}] FAILED ({} ms): {}",
                    signature, schoolId, elapsedMs, ex.getMessage()
            );
            throw ex;
        }
    }

    private String schoolIdOrDash() {
        Long schoolId = TenantContext.getOrNull();
        return schoolId != null ? schoolId.toString() : "-";
    }
}
