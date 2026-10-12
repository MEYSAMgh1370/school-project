package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * بازیابی رمز عبور با ایمیل (openspec/changes/password-reset-by-email.md). جدا از {@link IamFacadeImpl}
 * تا آن کلاسِ بزرگ بزرگ‌تر نشود؛ فقط از طریق {@code IamFacade} صدا زده می‌شود.
 * <p>
 * درخواست هرگز نشان نمی‌دهد حسابی وجود دارد یا ایمیلی رفت: کاربرِ ناشناخته، کاربرِ بدونِ رمز، کاربرِ
 * معلق و کاربرِ داخلِ مهلت همه بی‌صدا برمی‌گردند. ایمیل در یک رشته‌ی جدا فرستاده می‌شود تا زمانِ
 * پاسخ هم وجودِ حساب را لو ندهد.
 */
@Service
class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final Duration TOKEN_TTL = Duration.ofHours(1);
    /** کمترین فاصله‌ی دو توکنِ یک کاربر؛ جلوی ایمیل‌بمباران یک قربانی را می‌گیرد. */
    static final Duration COOLDOWN = Duration.ofMinutes(5);
    private static final int TOKEN_BYTES = 32;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteEmailSender emailSender;
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepository;

    PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                         PasswordEncoder passwordEncoder, InviteEmailSender emailSender,
                         ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepository) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.sessionRepository = sessionRepository;
    }

    boolean deliveryAvailable() {
        return emailSender.deliveryAvailable();
    }

    void request(String email) {
        String normalized = email == null ? "" : email.trim();
        if (normalized.isEmpty()) {
            return;
        }
        Optional<UserEntity> found = userRepository.findByEmail(normalized);
        // بدونِ رمز = هنوز فعال‌نشده (لینکِ دعوت دارد)؛ معلق = ورودش ممنوع است. هیچ‌کدام توکن نمی‌گیرند.
        if (found.isEmpty() || found.get().getPasswordHash() == null
                || found.get().getStatus() != UserEntity.UserStatus.ACTIVE) {
            return;
        }
        UserEntity user = found.get();
        Instant now = Instant.now();
        Optional<PasswordResetTokenEntity> latest = tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent() && latest.get().getCreatedAt().plus(COOLDOWN).isAfter(now)) {
            return;
        }

        tokenRepository.invalidateOpenTokens(user.getId(), now);
        String token = newToken();
        tokenRepository.save(new PasswordResetTokenEntity(user.getId(), sha256Hex(token), now, now.plus(TOKEN_TTL)));

        String to = user.getEmail();
        String fullName = user.getFullName();
        CompletableFuture.runAsync(() -> {
            if (!emailSender.sendPasswordResetEmail(to, fullName, token, TOKEN_TTL.toMinutes())) {
                log.warn("لینک بازیابی رمز برای یک کاربر ساخته شد ولی ایمیلش ارسال نشد.");
            }
        });
    }

    @Transactional
    void confirm(String token, String newPassword) {
        // طولِ رمز پیش از لمس‌کردنِ توکن چک می‌شود، تا رمزِ کوتاه توکن را مصرف نکند.
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IamException(new IamError.InvalidPassword(
                    "رمز عبور باید حداقل " + MIN_PASSWORD_LENGTH + " کاراکتر باشد."));
        }
        Instant now = Instant.now();
        PasswordResetTokenEntity entity = token == null ? null
                : tokenRepository.findByTokenHash(sha256Hex(token)).orElse(null);
        if (entity == null || !entity.isUsable(now)) {
            throw new IamException(new IamError.PasswordResetTokenInvalid());
        }
        UserEntity user = userRepository.findById(entity.getUserId())
                .orElseThrow(() -> new IamException(new IamError.PasswordResetTokenInvalid()));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.invalidateOpenTokens(user.getId(), now);
        endWebSessions(user.getEmail());
    }

    /** نشست‌های وبِ باز (iam.spring_session) را می‌بندد، تا نشستِ دزدیده‌شده بعد از تغییر رمز نماند. */
    private void endWebSessions(String principalName) {
        sessionRepository.ifAvailable(repository -> deleteSessions(repository, principalName));
    }

    private static <S extends org.springframework.session.Session> void deleteSessions(
            FindByIndexNameSessionRepository<S> repository, String principalName) {
        repository.findByPrincipalName(principalName).keySet().forEach(repository::deleteById);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
