package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.notification.SchoolBotFacade;
import com.example.roshdeandishe.notification.shared.NotificationError;
import com.example.roshdeandishe.notification.shared.NotificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * ثبت، جایگزینی و حذفِ رباتِ بله‌ی مدرسه. توکن فقط رمزشده ذخیره می‌شود و رازِ وب‌هوک فقط به‌صورت هش؛ هیچ‌کدام
 * لاگ یا برگردانده نمی‌شوند. تغییر وضعیتِ ناشی از کانال ارسال ({@link #markDisabled}) فقط با به‌روزرسانیِ هدفمند
 * انجام می‌شود تا تغییر هم‌زمانِ مدیر برنگردد.
 */
@Service
class SchoolBotService implements SchoolBotFacade {

    private static final Logger log = LoggerFactory.getLogger(SchoolBotService.class);

    private final SchoolBotRepository bots;
    private final BaleLinkRepository links;
    private final BaleClient baleClient;
    private final TokenCipher cipher;
    private final IamFacade iamFacade;
    private final RegistrationAttempts attempts;
    private final String webhookBaseUrl;
    private final Clock clock;

    @Autowired
    SchoolBotService(SchoolBotRepository bots, BaleLinkRepository links, BaleClient baleClient, TokenCipher cipher,
                     IamFacade iamFacade, RegistrationAttempts attempts,
                     @Value("${roshdeandishe.bale.webhook-base-url}") String webhookBaseUrl) {
        this(bots, links, baleClient, cipher, iamFacade, attempts, webhookBaseUrl, Clock.systemUTC());
    }

    SchoolBotService(SchoolBotRepository bots, BaleLinkRepository links, BaleClient baleClient, TokenCipher cipher,
                     IamFacade iamFacade, RegistrationAttempts attempts, String webhookBaseUrl, Clock clock) {
        this.bots = bots;
        this.links = links;
        this.baleClient = baleClient;
        this.cipher = cipher;
        this.iamFacade = iamFacade;
        this.attempts = attempts;
        this.webhookBaseUrl = stripTrailingSlashes(webhookBaseUrl == null ? "" : webhookBaseUrl.strip());
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolBotInfo> find(Long schoolId) {
        return bots.findBySchoolId(schoolId).map(this::toInfo);
    }

    @Override
    @Transactional
    public SchoolBotInfo register(Long schoolId, String rawToken) {
        if (!attempts.tryAcquire(schoolId)) {
            throw new NotificationException(new NotificationError.TooManyAttempts());
        }
        String token = rawToken == null ? "" : rawToken.strip();
        if (token.isEmpty()) {
            throw new NotificationException(new NotificationError.BotTokenInvalid());
        }
        if (!cipher.available()) {
            throw new NotificationException(new NotificationError.BotEncryptionUnavailable());
        }
        BaleClient.BotInfo bot = switch (baleClient.getMe(token)) {
            case BaleClient.BotInfoResult.Found found -> found.bot();
            case BaleClient.BotInfoResult.InvalidToken invalid ->
                    throw new NotificationException(new NotificationError.BotTokenInvalid());
            case BaleClient.BotInfoResult.Unreachable unreachable ->
                    throw new NotificationException(new NotificationError.BaleUnavailable());
        };
        Optional<SchoolBotEntity> holder = bots.findByBotUserId(bot.id());
        if (holder.isPresent() && !schoolId.equals(holder.get().getSchoolId())) {
            throw new NotificationException(new NotificationError.BotAlreadyRegistered());
        }

        String secret = WebhookSecrets.newSecret();
        if (!baleClient.setWebhook(token, webhookBaseUrl + "/api/bale/webhook/" + secret)) {
            throw new NotificationException(new NotificationError.BaleUnavailable());
        }

        Instant now = clock.instant();
        Long actor = iamFacade.currentUserId().value();
        SchoolBotEntity entity = bots.findBySchoolId(schoolId).orElse(null);
        if (entity == null) {
            entity = new SchoolBotEntity();
            entity.setSchoolId(schoolId);
            entity.setBusinessEnabled(false);
            entity.setCreatedAt(now);
            entity.setCreatedBy(actor);
        } else if (entity.getBotUserId() != bot.id()) {
            // ربات دیگری جایگزین شد: لینک‌های ربات قبلی بی‌معنی‌اند
            links.deleteByBotId(entity.getId());
        }
        TokenCipher.Encrypted encrypted = cipher.encrypt(token);
        entity.setBotUserId(bot.id());
        entity.setBotUsername(bot.username());
        entity.setTokenCiphertext(encrypted.ciphertext());
        entity.setTokenKeyVersion(encrypted.keyVersion());
        entity.setWebhookSecretHash(WebhookSecrets.hash(secret));
        entity.setStatus(SchoolBotStatus.ACTIVE);
        entity.setLastError(null);
        entity.setLastErrorAt(null);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);
        SchoolBotEntity saved = bots.save(entity);

        log.info("school bot registered: school={}, bot=@{}, by user={}", schoolId, bot.username(), actor);
        return toInfo(saved);
    }

    @Override
    @Transactional
    public void delete(Long schoolId) {
        SchoolBotEntity entity = bots.findBySchoolId(schoolId)
                .orElseThrow(() -> new NotificationException(new NotificationError.SchoolBotNotFound(schoolId)));
        removeWebhookBestEffort(entity);
        bots.delete(entity);
        log.info("school bot deleted: school={}, by user={}", schoolId, iamFacade.currentUserId().value());
    }

    /** ربات را (مثلاً پس از 401 بله) با به‌روزرسانیِ هدفمندِ status و خطا غیرفعال می‌کند. */
    @Transactional
    void markDisabled(Long botId, String error) {
        bots.markStatus(botId, SchoolBotStatus.DISABLED, error, clock.instant());
    }

    private void removeWebhookBestEffort(SchoolBotEntity entity) {
        try {
            String token = cipher.decrypt(entity.getTokenCiphertext(), entity.getTokenKeyVersion());
            baleClient.deleteWebhook(token);
        } catch (RuntimeException e) {
            log.warn("برداشتن وب‌هوک ربات مدرسه {} انجام نشد: {}", entity.getSchoolId(), e.getClass().getSimpleName());
        }
    }

    private SchoolBotInfo toInfo(SchoolBotEntity e) {
        return new SchoolBotInfo(e.getBotUsername(),
                e.getStatus() == SchoolBotStatus.ACTIVE ? SchoolBotState.ACTIVE : SchoolBotState.DISABLED,
                links.countByBotId(e.getId()), e.getLastUpdateAt(), e.getLastError(), e.getLastErrorAt());
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
