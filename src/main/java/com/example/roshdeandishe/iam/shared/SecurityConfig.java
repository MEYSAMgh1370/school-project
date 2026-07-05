package com.example.roshdeandishe.iam.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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
 * Session-based authentication (تصمیم صریح - نه JWT، طبق تصمیم فعلی روی
 * Open Question #1 در CLAUDE.md). این پیکربندی *فقط* احراز هویت را پوشش
 * می‌دهد؛ مجوز عملیات (authorization) کماکان به‌طور کامل توسط
 * ModuleAccessAspect + PermissionAccessAspect انجام می‌شود، نه توسط
 * hasRole()/hasAuthority() این کلاس.
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**", "/actuator/**", "/error",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/activate", "/login", "/register", "/webjars/**"
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
                .formLogin(form -> form.disable());

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
