package com.example.roshdeandishe.platform.config;

import com.example.roshdeandishe.platform.TenantContext;
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
            "/actuator", "/api/auth", "/login", "/error", "/css", "/js", "/favicon.ico",
            "/swagger-ui", "/v3/api-docs", "/activate", "/register", "/webjars",
            // «من» و داشبورد درباره‌ی خودِ کاربرند، نه یک مدرسه‌ی خاص -
            // schoolId ای وجود ندارد که در هدر بیاید
            "/api/me", "/dashboard",
            // صفحه‌ی مدیریتیِ /people خودش یک route است نه یک API؛ فراخوانی‌های
            // fetch آن (به /api/admin/schools/{id}/memberships) هدر را خودشان
            // می‌فرستند - این استثنا فقط بارگذاری خودِ صفحه را شامل می‌شود
            "/people",
            // همان استدلال /people برای صفحه‌ی نقش‌ها و دسترسی‌ها
            "/roles",
            // همان استدلال /people برای صفحه‌ی مدیریت کلاس‌ها
            "/classes",
            // همان استدلال /people برای صفحه‌ی سرفصل‌ها و بانک سؤال
            "/curriculum",
            // همان استدلال /people برای صفحه‌ی برنامه‌های مطالعه
            "/study-plans",
            // همان استدلال /people برای صفحه‌ی مدیریت آزمون‌ها
            "/exams",
            // «آزمون‌های من»: مثل /my-invoices خودِ صفحه یک route است؛
            // فراخوانی‌های fetch آن زیرمجموعه‌ی پیشوند /api/me هستند
            "/my-exams",
            // «برنامه‌ی مطالعه‌ی من»: همان استدلال /my-exams - فراخوانی‌های
            // fetch آن به GET/PATCH /api/me/schools/{id}/study-intervals
            // می‌روند که خودشان زیرمجموعه‌ی پیشوند /api/me هستند
            "/my-study-plan",
            // صفحه‌ی آزمون‌دهی (نه پیشوند REST آن که از قبل مستثناست): هیچ
            // X-School-ID ای در دسترس این صفحه نیست - همان استدلال
            // /api/exam-take بالاتر
            "/exam-take",
            // همان استدلال /people برای صفحه‌ی ثبت‌نام دانش‌آموز
            "/students",
            // همان استدلال /people برای صفحه‌ی امور مالی
            "/finance",
            // همان استدلال /people برای صفحه‌ی هزینه‌های مدرسه
            "/expenses",
            // صفحه‌ی «فاکتورهای من»: مثل /finance خودِ صفحه یک route است؛
            // فراخوانی fetch آن به GET /api/me/schools/{id}/invoices می‌رود که
            // خودش هم زیرمجموعه‌ی پیشوند /api/me است و بدون X-School-ID کار
            // می‌کند (schoolId از path می‌آید، نه هدر)
            "/my-invoices",
            // همان استدلال /people برای دفترچه‌نمره‌ی ادمین/معلم
            "/grades",
            // صفحه‌ی «نمره‌های من»: مثل /my-invoices خودِ صفحه یک route است؛
            // فراخوانی fetch آن به GET /api/me/schools/{id}/grades می‌رود که
            // خودش هم زیرمجموعه‌ی پیشوند /api/me است و بدون X-School-ID کار
            // می‌کند (schoolId از path می‌آید، نه هدر)
            "/my-grades",
            // صفحه‌ی ثبت مدرسه: مدرسه هنوز وجود ندارد که schoolId داشته باشد
            "/schools/new",
            // درِ ورودی برنددار مدرسه (/s/{slug}): صفحه‌ی عمومی، بازدیدکننده
            // هنوز لاگین نکرده که هدر بفرستد - schoolId از خودِ slug در مسیر
            // resolve می‌شود. (با "/students" تداخل ندارد: آن مسیر با "/s"
            // شروع می‌شود ولی نه با "/s/".)
            "/s/",
            // وب‌هوک بله: فراخوان‌کننده سرور بله است، نه یک مرورگر که بتواند
            // X-School-ID بفرستد - مدرسه‌ی هدف از روی chatId/userId لینک‌شده
            // در خودِ ماژول notification تشخیص داده می‌شود
            "/api/bale/webhook",
            // جلسه‌ی آزمون‌دهی: مجوز و schoolId هر دو از توکن جلسه‌ی آزمون
            // (query param token) resolve می‌شوند، نه از این هدر - همان
            // استدلال وب‌هوک بله (رجوع به exam.ExamTakeController)
            "/api/exam-take"
    };

    /**
     * SchoolController.createSchool (POST) و getSchool (GET) عمداً بدون
     * schoolId کار می‌کنند - ساخت مدرسه ذاتاً قبل از وجود schoolId اتفاق
     * می‌افتد، و مسیر مشاهده‌ی یک مدرسه از خودِ path variable استفاده
     * می‌کند، نه TenantContext. اما زیرمسیرهای عمیق‌تر مثل
     * /api/admin/schools/{id}/memberships واقعاً به X-School-ID نیاز دارند
     * (برای @RequiresPermission). یک prefix ساده نمی‌تواند این دو را از هم
     * جدا کند، پس این الگو دقیقاً فقط "/api/admin/schools" یا
     * "/api/admin/schools/{عدد}" را match می‌کند - نه هیچ زیرمسیر دیگری.
     */
    private static final java.util.regex.Pattern SCHOOL_SELF_SERVICE_PATH =
            java.util.regex.Pattern.compile("^/api/admin/schools(/\\d+)?$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        // "/" (صفحه‌ی اول عمومی) جداگانه و با equals چک می‌شود - داخل
        // EXCLUDED_PATHS نمی‌رود چون startsWith("/") همه‌چیز را مستثنا می‌کرد.
        boolean excluded = path.equals("/")
                || java.util.Arrays.stream(EXCLUDED_PATHS).anyMatch(path::startsWith)
                || SCHOOL_SELF_SERVICE_PATH.matcher(path).matches();

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
