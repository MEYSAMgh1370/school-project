package com.example.roshdeandishe.exam.internal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * صدور/اعتبارسنجی JWT مخصوص جلسه‌ی آزمون‌دهی - پورت {@code azmoon/token.py}
 * (ExamTokenData/ExamTokenManager) موshavere. یک JWT سومِ کاملاً مستقل، با
 * کلید امضای جدا ({@code roshdeandishe.exam.token-secret}) - نه کوکی
 * سشن وب و نه JWT موبایل (iam.shared.JwtTokenProvider)، همان جداییِ صریحی
 * که MOSHAVERE_PORT_SPEC.md §5 خواسته.
 * <p>
 * در exam.internal می‌ماند، نه exam.shared: تنها مصرف‌کننده‌اش
 * ExamFacadeImpl است (همان ماژول)؛ ExamTakeController هرگز مستقیم این
 * کلاس را نمی‌بیند - رشته‌ی خام توکن را به متدهای ExamFacade پاس می‌دهد و
 * decode/expire/mismatch همه پشت Facade می‌ماند (رجوع به توضیح
 * ExamFacade.getExamForToken) - دقیقاً همان مکانیزم مجوز مستقلِ اشاره‌شده
 * در MOSHAVERE_PORT_SPEC.md §5، نه @RequiresPermission معمول.
 * <p>
 * انقضا claim بومی JWT است ({@code exp}) نه فیلد دستی - یعنی
 * {@code Jwts.parser()} خودش برای توکن منقضی‌شده {@link JwtException} پرتاب
 * می‌کند و decode() آن را هم مثل امضای نامعتبر Optional.empty() می‌کند؛
 * نیازی به مقایسه‌ی دستی زمان اینجا نیست.
 */
@Service
class ExamTokenService {

    private final SecretKey signingKey;

    ExamTokenService(@Value("${roshdeandishe.exam.token-secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    String generateToken(Long examId, Long studentId, Instant expireAt) {
        return Jwts.builder()
                .subject(studentId.toString())
                .claim("examId", examId)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expireAt))
                .signWith(signingKey)
                .compact();
    }

    Optional<TokenData> decode(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long examId = claims.get("examId", Long.class);
            Long studentId = Long.parseLong(claims.getSubject());
            return Optional.of(new TokenData(examId, studentId));
        } catch (JwtException | IllegalArgumentException e) {
            // امضای نامعتبر، فرمت بد، یا (به‌طور خودکار توسط Jwts.parser)
            // منقضی‌شده - در همه‌ی این حالت‌ها فقط «توکن معتبر نیست»
            // برمی‌گردد، همان قرارداد iam.shared.JwtTokenProvider.
            return Optional.empty();
        }
    }

    record TokenData(Long examId, Long studentId) {
    }
}
