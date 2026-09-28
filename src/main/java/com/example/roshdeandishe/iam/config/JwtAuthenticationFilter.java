package com.example.roshdeandishe.iam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * احراز هویت اپ موبایل از طریق هدر {@code Authorization: Bearer <jwt>} -
 * additive به مسیر session-cookie موجود (که کاملاً دست‌نخورده می‌ماند).
 * <p>
 * اگر هدر Authorization: Bearer نباشد، این فیلتر کاری نمی‌کند و درخواست را
 * دست‌نخورده رد می‌کند - یعنی لاگین وب مبتنی بر session هیچ تغییری نمی‌کند.
 * اگر توکن معتبر باشد، Authentication را فقط برای همین Request روی
 * SecurityContextHolder می‌گذارد (بدون ذخیره در HttpSession - یعنی JWT
 * auth کاملاً stateless می‌ماند، برخلاف مسیر لاگین وب). اگر توکن نامعتبر/
 * منقضی باشد، هیچ استثنایی پرتاب نمی‌شود - درخواست به همان مسیر
 * "احراز هویت‌نشده" می‌رود که امروز هم برای /api/** بدون هیچ هدری وجود دارد
 * (Http403ForbiddenEntryPoint در SecurityConfig).
 * <p>
 * عمداً {@code @Component} نیست: اگر این کلاس یک Spring-managed Filter bean
 * بود، Boot به‌طور خودکار آن را هم به‌عنوان یک servlet-container filter
 * سراسری ثبت می‌کرد (علاوه بر ثبت صریح آن در زنجیره‌ی Spring Security از
 * طریق addFilterBefore در SecurityConfig) - یعنی هر Request دوبار از آن رد
 * می‌شد. با ساختن دستی یک نمونه‌ی معمولی (نه bean) داخل
 * SecurityConfig.securityFilterChain و پاس دادن همان نمونه به
 * addFilterBefore، این فیلتر فقط دقیقاً یک‌بار، فقط در جای مشخص‌شده در
 * زنجیره‌ی Spring Security، اجرا می‌شود.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        extractBearerToken(request)
                .flatMap(jwtTokenProvider::validateAndExtractEmail)
                .ifPresent(email -> {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            email, null, Collections.emptyList()
                    );
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                });

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }
}
