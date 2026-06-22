package com.example.roshdeandishe.iam.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * فیلتری که در ابتدای هر Request، هدر {@code X-School-ID} را می‌خواند و در
 * {@link TenantContext} قرار می‌دهد تا در طول پردازش همان Request (Controller,
 * Service, Repository) بدون نیاز به پاس دادن schoolId به‌صورت پارامتر، در
 * دسترس باشد.
 * <p>
 * Order با مقدار پایین (اجرای زود) تنظیم شده تا قبل از فیلترهای Security
 * (که ممکن است به schoolId برای تشخیص نقش‌ها نیاز داشته باشند) اجرا شود؛
 * در صورت نیاز این مقدار را با توجه به SecurityFilterChain واقعی پروژه تنظیم کنید.
 */
@Component
@Order(1)
public class TenantResolverFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-School-ID";

    /**
     * مسیرهایی که نیاز به schoolId ندارند (مثل health check یا صفحه لاگین عمومی).
     */
    private static final String[] EXCLUDED_PATHS = {
            "/actuator", "/login", "/error", "/css", "/js", "/favicon.ico"
    };

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean excluded = java.util.Arrays.stream(EXCLUDED_PATHS).anyMatch(path::startsWith);

        try {
            if (!excluded) {
                String headerValue = request.getHeader(TENANT_HEADER);
                if (headerValue == null || headerValue.isBlank()) {
                    response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "هدر " + TENANT_HEADER + " الزامی است."
                    );
                    return;
                }
                try {
                    TenantContext.set(Long.parseLong(headerValue));
                } catch (NumberFormatException e) {
                    response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "مقدار هدر " + TENANT_HEADER + " باید عددی باشد."
                    );
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // پاک‌سازی همیشگی برای جلوگیری از نشتی Context بین Requestهای بعدی
            // که از همان Thread در connection pool استفاده می‌کنند.
            TenantContext.clear();
        }
    }
}
