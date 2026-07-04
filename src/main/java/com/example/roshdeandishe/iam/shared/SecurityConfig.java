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
 * ⚠️ CSRF عمداً غیرفعال شده. این API فقط JSON برمی‌گرداند و صفحه‌ی تک HTML
 * موجود (GET /activate، فعال‌سازی حساب) هیچ session cookie ضمنی‌ای را
 * به‌عنوان اعتبارنامه استفاده نمی‌کند - اعتبارنامه‌ی واقعی خودِ invite token
 * در بدنه‌ی درخواست است (دقیقاً هم‌راستا با مدل اعتماد /api/auth/login که
 * خودش هم عمداً بدون احراز هویت است)، پس ریسک CSRF کلاسیک (بهره‌برداری از
 * یک session معتبرِ از پیش موجود در مرورگر قربانی) برای این یک صفحه صدق
 * نمی‌کند. اگر در آینده یک وب‌کلاینت مبتنی بر *session cookie* در برابر
 * REST APIهای ادمین (MembershipController، ClassController،
 * StudentController و...) ساخته شود - که هنوز اصلاً scope نشده - این
 * تصمیم باید در آن نقطه بازبینی شود.
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
                .csrf(AbstractHttpConfigurer::disable) // بازبینی لازم فقط اگر یک کلاینت *session-cookie-authenticated* در برابر REST ادمین ساخته شود؛ نه برای /activate
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**", "/actuator/**", "/error",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/activate", "/login", "/webjars/**"
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
}
