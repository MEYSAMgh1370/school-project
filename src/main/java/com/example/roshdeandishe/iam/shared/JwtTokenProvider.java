package com.example.roshdeandishe.iam.shared;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * صدور/اعتبارسنجی JWT برای احراز هویت اپ موبایل - additive به session
 * cookie (که همچنان بدون تغییر برای وب استفاده می‌شود).
 * <p>
 * فقط email را در توکن حمل می‌کند، نه userId: دقیقاً همان چیزی که session
 * auth هم دارد - {@code Authentication.getName()} همیشه email بوده
 * (IamUserDetailsService.loadUserByUsername) و IamFacadeImpl.currentUserId()
 * از قبل userId را هر بار از روی همان email از UserRepository resolve
 * می‌کند. پس نیازی به یک وابستگی جدید به UserRepository/iam.internal اینجا
 * نیست - این کلاس در iam.shared می‌ماند، کنار تنها دو مصرف‌کننده‌اش
 * (AuthController برای صدور، JwtAuthenticationFilter برای اعتبارسنجی).
 */
@Service
class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    JwtTokenProvider(
            @Value("${roshdeandishe.jwt.secret}") String secret,
            @Value("${roshdeandishe.jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationSeconds = expirationSeconds;
    }

    long expirationSeconds() {
        return expirationSeconds;
    }

    String generateToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationSeconds, ChronoUnit.SECONDS)))
                .signWith(signingKey)
                .compact();
    }

    Optional<String> validateAndExtractEmail(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // امضای نامعتبر، توکن منقضی‌شده، یا فرمت بد - در همه‌ی این
            // حالت‌ها فقط "توکن معتبر نیست" برمی‌گردد، نه استثنا؛
            // JwtAuthenticationFilter این را به «درخواست احراز هویت‌نشده»
            // ترجمه می‌کند، دقیقاً مثل نبودِ هدر Authorization.
            return Optional.empty();
        }
    }
}
