package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * کد یک‌بارمصرف پیامکی برای ورود با شماره تلفن (فاکتور دوم لاگین، نه یک
 * مسیر ثبت‌نام جدید - رجوع به IamFacade.requestOtp/verifyOtp). همان شکل
 * one-time-use/TTL/nullable-consumed-marker که {@link InviteTokenEntity}
 * دارد، نه OneToOne موشاوره: کاربر باید بتواند قبل از منقضی‌شدن کد قبلی،
 * دوباره کد بخواهد - هر درخواست یک ردیف تازه می‌سازد (برخلاف
 * issueInviteToken که یک توکن معتبر موجود را دوباره برمی‌گرداند)، و
 * verifyOtp همیشه فقط آخرین ردیف مصرف‌نشده‌ی کاربر را معتبر می‌داند - پس
 * درخواست یک کد تازه عملاً کدهای قدیمی‌تر را بی‌اثر می‌کند.
 */
@Entity
@Table(name = "verification_codes", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
class VerificationCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    VerificationCodeEntity(Long userId, String code, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.code = code;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
