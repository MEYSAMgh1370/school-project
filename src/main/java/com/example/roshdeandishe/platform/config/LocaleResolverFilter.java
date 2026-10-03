package com.example.roshdeandishe.platform.config;

import com.example.roshdeandishe.platform.LocaleContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * فیلتری که در ابتدای هر Request، هدر {@code Accept-Language} را می‌خواند و
 * زبان حل‌شده را در {@link LocaleContext} قرار می‌دهد.
 * <p>
 * قواعد (به ترتیب): هدر {@code Accept-Language} با مجموعه‌ی پشتیبانی‌شده
 * ({@code fa} و {@code en}) مطابقت داده می‌شود؛ در غیر این صورت زبان پیش‌فرض
 * (فارسی). هدر نامعتبر یا نبودِ هدر هم به زبان پیش‌فرض می‌رسد و هرگز خطا نمی‌دهد.
 * <p>
 * برخلاف {@link TenantResolverFilter}، هیچ مسیری مستثنا نیست (صفحه‌ها و فایل‌های
 * استاتیک هم زبان می‌گیرند) و این فیلتر هرگز Request را رد یا کوتاه نمی‌کند:
 * فقط resolve می‌کند، به زنجیره واگذار می‌کند، و پاک می‌کند.
 * <p>
 * {@code @Order(0)}: درست قبل از {@link TenantResolverFilter} ({@code @Order(1)}).
 * این دو فیلتر هیچ وابستگی‌ای به هم ندارند؛ اما چون این فیلتر هرگز رد نمی‌کند و
 * خودش پاک‌سازی می‌کند، قرارگرفتن قبل از فیلتر tenant تضمین می‌کند حتی Requestهایی
 * که فیلتر tenant با ۴۰۰ رد می‌کند هم زبان‌شان resolve شده باشد.
 */
@Component
@Order(0)
public class LocaleResolverFilter extends OncePerRequestFilter {

    public static final String LANGUAGE_HEADER = "Accept-Language";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            LocaleContext.set(resolve(request.getHeader(LANGUAGE_HEADER)));
            filterChain.doFilter(request, response);
        } finally {
            // پاک‌سازی همیشگی برای جلوگیری از نشتی Context بین Requestهای بعدی
            // که از همان Thread در connection pool استفاده می‌کنند.
            LocaleContext.clear();
        }
    }

    /**
     * هدر را با مجموعه‌ی پشتیبانی‌شده مطابقت می‌دهد؛ در هر حالت ناموفق
     * (نبود هدر، هدر نامعتبر، زبان پشتیبانی‌نشده) زبان پیش‌فرض را برمی‌گرداند.
     */
    static Locale resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return LocaleContext.DEFAULT_LOCALE;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(headerValue);
            Locale match = Locale.lookup(ranges, LocaleContext.SUPPORTED_LOCALES);
            return match != null ? match : LocaleContext.DEFAULT_LOCALE;
        } catch (IllegalArgumentException e) {
            // LanguageRange.parse روی هدر نامعتبر پرتاب می‌کند - این نباید ۴۰۰ شود
            return LocaleContext.DEFAULT_LOCALE;
        }
    }
}
