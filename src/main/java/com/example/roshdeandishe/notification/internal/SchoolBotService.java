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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ثبت، جایگزینی و حذفِ رباتِ بله‌ی مدرسه (بدون نگه‌داشتنِ تراکنش در طول تماس با بله). توکن فقط رمزشده ذخیره می‌شود و رازِ وب‌هوک فقط به‌صورت هش؛ هیچ‌کدام
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
    private final TransactionOperations tx;
    private final Clock clock;
    private final ConcurrentMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Autowired
    SchoolBotService(SchoolBotRepository bots, BaleLinkRepository links, BaleClient baleClient, TokenCipher cipher,
                     IamFacade iamFacade, RegistrationAttempts attempts, PlatformTransactionManager transactionManager,
                     @Value("${roshdeandishe.bale.webhook-base-url}") String webhookBaseUrl) {
        this(bots, links, baleClient, cipher, iamFacade, attempts, new TransactionTemplate(transactionManager),
                webhookBaseUrl, Clock.systemUTC());
    }

    SchoolBotService(SchoolBotRepository bots, BaleLinkRepository links, BaleClient baleClient, TokenCipher cipher,
                     IamFacade iamFacade, RegistrationAttempts attempts, TransactionOperations tx, String webhookBaseUrl, Clock clock) {
        this.bots = bots;
        this.links = links;
        this.baleClient = baleClient;
        this.cipher = cipher;
        this.iamFacade = iamFacade;
        this.attempts = attempts;
        this.webhookBaseUrl = stripTrailingSlashes(webhookBaseUrl == null ? "" : webhookBaseUrl.strip());
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolBotInfo> find(Long schoolId) {
        return bots.findBySchoolId(schoolId).map(this::toInfo);
    }

    /**
     * ثبت/جایگزینی. هیچ تراکنشی در طول تماس‌های HTTP با بله باز نیست: (۱) قفلِ حافظه‌ای به‌ازای مدرسه، (۲) اعتبارسنجی و
     * getMe بیرون از تراکنش، (۳) تراکنشِ کوتاهِ «رزرو» (ردیف با توکن و هشِ تازه)، (۴) setWebhook بیرون از تراکنش و در
     * صورت شکست «جبران» (بازگرداندنِ ردیفِ قبلی یا حذفِ ردیفِ تازه)، (۵) در صورت تغییرِ ربات حذفِ لینک‌های قدیمی.
     */
    @Override
    public SchoolBotInfo register(Long schoolId, String rawToken) {
        ReentrantLock lock = locks.computeIfAbsent(schoolId, id -> new ReentrantLock());
        lock.lock();
        try {
            return doRegister(schoolId, rawToken);
        } finally {
            lock.unlock();
        }
    }

    private SchoolBotInfo doRegister(Long schoolId, String rawToken) {
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
        Long actor = iamFacade.currentUserId().value();
        Reservation reservation;
        try {
            reservation = tx.execute(status -> reserve(schoolId, token, bot, secret, actor));
        } catch (DataIntegrityViolationException e) {
            // ثبتِ هم‌زمانِ همین ربات برای مدرسه‌ی دیگر
            throw new NotificationException(new NotificationError.BotAlreadyRegistered());
        }

        boolean webhookSet;
        try {
            webhookSet = baleClient.setWebhook(token, webhookBaseUrl + "/api/bale/webhook/" + secret);
        } catch (RuntimeException e) {
            tx.executeWithoutResult(status -> compensate(reservation));
            throw e;
        }
        if (!webhookSet) {
            tx.executeWithoutResult(status -> compensate(reservation));
            throw new NotificationException(new NotificationError.BaleUnavailable());
        }

        if (reservation.botChanged()) {
            // ربات تازه هنوز لینکی ندارد؛ همه‌ی لینک‌های ردیف متعلق به رباتِ قبلی‌اند
            tx.executeWithoutResult(status -> links.deleteByBotId(reservation.rowId()));
            // رباتِ قبلی دیگر برای این مدرسه نیست؛ وب‌هوکش را (بیرون از تراکنش و بی‌خطا) برمی‌داریم
            removeWebhookBestEffort(schoolId, reservation.previous().ciphertext(), reservation.previous().keyVersion());
        }
        log.info("school bot registered: school={}, bot=@{}, by user={}", schoolId, bot.username(), actor);
        return tx.execute(status -> bots.findById(reservation.rowId()).map(this::toInfo).orElseThrow());
    }

    /** تراکنش «رزرو»: ردیف را با ربات و توکن و هشِ تازه می‌نویسد و وضعیتِ قبلی را برای جبران برمی‌گرداند. */
    private Reservation reserve(Long schoolId, String token, BaleClient.BotInfo bot, String secret, Long actor) {
        Instant now = clock.instant();
        SchoolBotEntity entity = bots.findBySchoolId(schoolId).orElse(null);
        Snapshot previous = null;
        boolean botChanged = false;
        if (entity == null) {
            entity = new SchoolBotEntity();
            entity.setSchoolId(schoolId);
            entity.setBusinessEnabled(false);
            entity.setCreatedAt(now);
            entity.setCreatedBy(actor);
        } else {
            previous = Snapshot.of(entity);
            botChanged = entity.getBotUserId() != bot.id();
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
        SchoolBotEntity saved = bots.saveAndFlush(entity);
        return new Reservation(saved.getId(), previous, botChanged);
    }

    /** تراکنش «جبران»: ردیفِ قبلی را دقیقاً بازمی‌گرداند، یا ردیفِ تازه را حذف می‌کند. */
    private void compensate(Reservation r) {
        if (r.previous() == null) {
            bots.deleteById(r.rowId());
            return;
        }
        bots.findById(r.rowId()).ifPresent(e -> {
            r.previous().applyTo(e);
            bots.save(e);
        });
    }

    private record Reservation(Long rowId, Snapshot previous, boolean botChanged) {
    }

    /** وضعیتِ قبلیِ ردیف پیش از رزرو (فقط در حافظه). */
    private record Snapshot(Long botUserId, String username, String ciphertext, int keyVersion, String secretHash,
                            SchoolBotStatus status, String lastError, Instant lastErrorAt, Instant updatedAt,
                            Long updatedBy) {
        static Snapshot of(SchoolBotEntity e) {
            return new Snapshot(e.getBotUserId(), e.getBotUsername(), e.getTokenCiphertext(), e.getTokenKeyVersion(),
                    e.getWebhookSecretHash(), e.getStatus(), e.getLastError(), e.getLastErrorAt(), e.getUpdatedAt(),
                    e.getUpdatedBy());
        }

        void applyTo(SchoolBotEntity e) {
            e.setBotUserId(botUserId);
            e.setBotUsername(username);
            e.setTokenCiphertext(ciphertext);
            e.setTokenKeyVersion(keyVersion);
            e.setWebhookSecretHash(secretHash);
            e.setStatus(status);
            e.setLastError(lastError);
            e.setLastErrorAt(lastErrorAt);
            e.setUpdatedAt(updatedAt);
            e.setUpdatedBy(updatedBy);
        }
    }

    /** ردیف را در تراکنشِ کوتاه حذف می‌کند (لینک‌ها cascade)؛ برداشتنِ وب‌هوک بعد از commit و بیرون از تراکنش است. */
    @Override
    public void delete(Long schoolId) {
        record Doomed(String ciphertext, int keyVersion) {
        }
        Doomed doomed = tx.execute(status -> {
            SchoolBotEntity entity = bots.findBySchoolId(schoolId)
                    .orElseThrow(() -> new NotificationException(new NotificationError.SchoolBotNotFound(schoolId)));
            bots.delete(entity);
            return new Doomed(entity.getTokenCiphertext(), entity.getTokenKeyVersion());
        });
        removeWebhookBestEffort(schoolId, doomed.ciphertext(), doomed.keyVersion());
        log.info("school bot deleted: school={}, by user={}", schoolId, iamFacade.currentUserId().value());
    }

    /** ربات را (مثلاً پس از 401 بله) با به‌روزرسانیِ هدفمندِ status و خطا غیرفعال می‌کند. */
    void markDisabled(Long botId, String error) {
        bots.markStatus(botId, SchoolBotStatus.DISABLED, error, clock.instant());
    }

    private void removeWebhookBestEffort(Long schoolId, String ciphertext, int keyVersion) {
        try {
            baleClient.deleteWebhook(cipher.decrypt(ciphertext, keyVersion));
        } catch (RuntimeException e) {
            log.warn("برداشتن وب‌هوک ربات مدرسه {} انجام نشد: {}", schoolId, e.getClass().getSimpleName());
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
