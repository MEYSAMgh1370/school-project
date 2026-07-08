package com.example.roshdeandishe.iam.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.function.Supplier;

/**
 * احراز هویت وب همچنان session-based است (تصمیم Open Question #1 در
 * CLAUDE.md، بدون تغییر). علاوه بر آن، از اینجا به بعد یک مسیر دوم و
 * additive هم وجود دارد: JwtAuthenticationFilter، برای اپ موبایل Android
 * (Bearer token، stateless، بدون HttpSession) - رجوع به AuthController
 * (POST /api/auth/token) و JwtAuthenticationFilter. این پیکربندی *فقط*
 * احراز هویت را پوشش می‌دهد؛ مجوز عملیات (authorization) کماکان به‌طور کامل
 * توسط ModuleAccessAspect + PermissionAccessAspect انجام می‌شود، نه توسط
 * hasRole()/hasAuthority() این کلاس - برای هر دو مسیر (session و JWT)
 * یکسان، چون هر دو authorities را عمداً خالی می‌گذارند.
 * <p>
 * در iam.shared قرار دارد نه platform، چون به IamUserDetailsService (که
 * internal ماژول iam است) وابسته است - platform هرگز نباید به iam وابسته شود.
 * <p>
 * CSRF فعال است (پیش‌نیاز صریح UI مدیریتی - CLAUDE.md بند ۷ مورد ۶ گیت الف).
 * الگو: CookieCsrfTokenRepository با httpOnly=false، یعنی سرور توکن را در
 * کوکی XSRF-TOKEN می‌نویسد و جاوااسکریپت صفحه‌های JTE آن را خوانده و در
 * هدر X-XSRF-TOKEN با هر درخواست تغییردهنده (POST/PUT/...) برمی‌گرداند -
 * الگوی استاندارد مستندات Spring Security برای کلاینت‌های جاوااسکریپتی.
 * {@link SpaCsrfTokenRequestHandler} همان handler توصیه‌شده‌ی مستندات است:
 * برای مقدار هدر از resolve ساده استفاده می‌کند (کوکی خودش در برابر BREACH
 * حساس نیست) ولی رندر درون‌صفحه‌ای احتمالی آینده را XorشدهBREACH-safe نگه
 * می‌دارد، و با csrfToken.get()‌ در handle، نوشتن کوکیِ به‌تعویق‌افتاده را
 * در هر پاسخ تضمین می‌کند (بدون آن، کوکی هرگز ست نمی‌شد).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // وب‌هوک بله را از CSRF مستثنا می‌کند: فراخوان‌کننده سرور
                        // بله است، نه یک مرورگر با کوکی XSRF-TOKEN - امنیت این
                        // مسیر از طریق بخش مخفی در خودِ URL تأمین می‌شود
                        // (BaleWebhookController)، نه CSRF token.
                        .ignoringRequestMatchers("/api/bale/webhook/**")
                        // صدور توکن موبایل هم از CSRF مستثناست - برخلاف
                        // /api/auth/login (که عمداً CSRF می‌خواهد، برای جلوگیری
                        // از "login CSRF" روی مسیر cookie/session وب)، این
                        // endpoint هیچ کوکی‌ای برنمی‌گرداند؛ پاسخش یک JWT در
                        // بدنه‌ی JSON است که یک درخواست جعلی cross-origin اصلاً
                        // نمی‌تواند بخواند (same-origin policy) - پس مدل تهدید
                        // CSRF (سوءاستفاده از یک session از قبل موجود) اینجا
                        // اصلاً معنا ندارد. یک کلاینت موبایل هم راهی برای رقص
                        // کوکی/X-XSRF-TOKEN صفحه‌های JTE ندارد و نباید داشته باشد.
                        .ignoringRequestMatchers("/api/auth/token")
                        // درخواست‌های Bearer-authenticated (اپ موبایل) هم از CSRF
                        // مستثنا هستند: CSRF یک حمله‌ی auto-attach کوکی است - یک
                        // درخواست که باید هدر Authorization را دستی حمل کند
                        // (مرورگر هرگز این کار را خودکار نمی‌کند) از این نوع حمله
                        // در اصل مصون است. مسیر session/cookie وب هرگز این هدر را
                        // نمی‌فرستد، پس این استثنا آن مسیر را ضعیف نمی‌کند.
                        .ignoringRequestMatchers(request -> {
                            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                            return StringUtils.hasText(header) && header.startsWith("Bearer ");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/api/auth/**", "/actuator/**", "/error",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/api/bale/webhook/**",
                                "/activate", "/login", "/register", "/webjars/**",
                                "/css/**", "/js/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // صفحه‌های HTML (مثل /dashboard) بدون لاگین باید به /login
                // ری‌دایرکت شوند؛ APIهای JSON (/api/**) باید 403 بگیرند تا
                // fetchهای کلاینت پاسخ قابل‌تشخیص بگیرند. هر دو matcher صریح
                // ثبت شده‌اند چون اولین entry point ثبت‌شده، fallback پیش‌فرض
                // هم می‌شود - با فقط یکی، ریدایرکت به /api هم نشت می‌کرد.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new org.springframework.security.web.authentication.Http403ForbiddenEntryPoint(),
                                request -> request.getRequestURI().startsWith("/api/")
                        )
                        .defaultAuthenticationEntryPointFor(
                                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/login"),
                                request -> !request.getRequestURI().startsWith("/api/")
                        )
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(form -> form.disable())
                // بدون Authorization: Bearer، این فیلتر کاری نمی‌کند و درخواست
                // را دست‌نخورده رد می‌کند - مسیر session-cookie وب کاملاً
                // بدون تغییر می‌ماند. نمونه دستی ساخته می‌شود (نه یک @Component
                // bean) تا Boot آن را دوباره به‌عنوان یک servlet filter سراسری
                // ثبت نکند - رجوع به توضیح JwtAuthenticationFilter.
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * همان SpaCsrfTokenRequestHandler مستندات رسمی Spring Security
     * (بخش Single-Page Applications). دو کار انجام می‌دهد:
     * ۱) resolveCsrfTokenValue: اگر توکن از هدر آمده باشد (مسیر عادی
     * کلاینت‌های fetch ما) مقدار خام پذیرفته می‌شود؛ اگر روزی از پارامتر
     * فرم بیاید، مسیر Xorشده (محافظ BREACH) استفاده می‌شود.
     * ۲) handle: با فراخوانی csrfToken.get() بارگذاری به‌تعویق‌افتاده‌ی
     * توکن را مجبور می‌کند تا CookieCsrfTokenRepository کوکی XSRF-TOKEN
     * را واقعاً در پاسخ بنویسد.
     */
    private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
