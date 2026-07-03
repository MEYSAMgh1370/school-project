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
 * ⚠️ CSRF عمداً غیرفعال شده برای این فاز (API فقط JSON، بدون فرم HTML).
 * اگر در آینده یک وب‌کلاینت مبتنی بر مرورگر (مثلاً Thymeleaf با فرم واقعی،
 * یا یک SPA که از همان session cookie استفاده می‌کند) اضافه شد، این تصمیم
 * باید بازبینی شود - CSRF غیرفعال یعنی endpointهای mutating در برابر
 * Cross-Site Request Forgery محافظت نمی‌شوند.
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
    public DaoAuthenticationProvider authenticationProvider(
            org.springframework.security.core.userdetails.UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // TODO: قبل از افزودن هر کلاینت مبتنی بر مرورگر، بازبینی شود
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**", "/actuator/**", "/error",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(form -> form.disable());

        return http.build();
    }
}
