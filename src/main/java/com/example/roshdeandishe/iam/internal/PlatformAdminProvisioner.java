package com.example.roshdeandishe.iam.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * تنها نویسنده‌ی {@code iam.platform_admins} (openspec/changes/yearly-plan.md، فاز C): جدول را در
 * هر بالا آمدن با متغیر محیطی {@code PLATFORM_ADMIN_EMAILS} (property
 * {@code roshdeandishe.platform-admin.emails}) هم‌گام می‌کند. متغیر تنها منبع حقیقت است:
 * <ul>
 *   <li>فقط ایمیل‌هایی که با یک کاربر <b>موجود</b>، ACTIVE و فعال‌سازی‌شده (passwordHash غیر null)
 *       تطبیق دارند ردیف می‌گیرند؛ ایمیل ناشناخته نادیده گرفته می‌شود و هرگز کاربر ساخته نمی‌شود.</li>
 *   <li>ردیفِ کاربری که دیگر در فهرست نیست یا دیگر واجد شرایط نیست (مثلاً SUSPENDED) حذف می‌شود؛
 *       متغیر خالی/ست‌نشده یعنی جدول خالی.</li>
 *   <li>ایمیلی که فقط در حروف بزرگ/کوچک با چند کاربر تطبیق دارد مبهم است و رد می‌شود (fail closed):
 *       ثبت‌نام ایمیل را نرمال نمی‌کند، پس مهاجم نباید بتواند با ثبت نسخه‌ی case-دیگرِ ایمیلِ
 *       فهرست‌شده ادمین شود.</li>
 *   <li>عضویت و نقش را هرگز لمس نمی‌کند، ایمیل را لاگ نمی‌کند (فقط شمارش)، idempotent است و از مسیر
 *       ثبت‌نام/دعوت/OTP/seed صدا زده نمی‌شود.</li>
 * </ul>
 * ریسک عملیاتی (ثبت‌نام باز و بدون تأیید ایمیل): اپراتور باید اول حساب خودش را بسازد و بعد ایمیلش را
 * فهرست کند - رجوع به DEPLOYMENT.md.
 */
@Component
// بعد از IamSeedDataRunner (Order 10) و قبل از backfillِ ماژول school (Order 20)
@Order(15)
class PlatformAdminProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminProvisioner.class);

    private final UserRepository userRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final String configuredEmails;

    PlatformAdminProvisioner(
            UserRepository userRepository,
            PlatformAdminRepository platformAdminRepository,
            @Value("${roshdeandishe.platform-admin.emails:}") String configuredEmails
    ) {
        this.userRepository = userRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.configuredEmails = configuredEmails;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sync();
    }

    /** @return شمار ردیف‌های افزوده/حذف‌شده و شمار نهایی (فقط برای لاگ و تست؛ هیچ ایمیلی نیست) */
    @Transactional
    SyncResult sync() {
        Set<Long> eligible = new LinkedHashSet<>();
        for (String email : parseEmails(configuredEmails)) {
            List<UserEntity> matches = userRepository.findAllByEmailIgnoreCase(email);
            // صفر = کاربر ناشناخته (نادیده)، بیش از یک = مبهم (رد)
            if (matches.size() != 1) {
                continue;
            }
            UserEntity user = matches.get(0);
            boolean activated = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
            if (user.getStatus() == UserEntity.UserStatus.ACTIVE && activated) {
                eligible.add(user.getId());
            }
        }

        Set<Long> existing = new HashSet<>();
        platformAdminRepository.findAll().forEach(row -> existing.add(row.getUserId()));

        List<Long> toRemove = existing.stream().filter(id -> !eligible.contains(id)).toList();
        List<Long> toAdd = eligible.stream().filter(id -> !existing.contains(id)).toList();

        if (!toRemove.isEmpty()) {
            platformAdminRepository.deleteAllById(toRemove);
        }
        if (!toAdd.isEmpty()) {
            platformAdminRepository.saveAll(toAdd.stream().map(PlatformAdminEntity::new).toList());
        }

        SyncResult result = new SyncResult(toAdd.size(), toRemove.size(), eligible.size());
        // فقط شمارش؛ ایمیل هرگز لاگ نمی‌شود
        log.info("platform_admins synced: added={}, removed={}, total={}", result.added(), result.removed(), result.total());
        return result;
    }

    private static Set<String> parseEmails(String raw) {
        Set<String> emails = new LinkedHashSet<>();
        if (raw == null) {
            return emails;
        }
        for (String part : raw.split(",")) {
            String email = part.trim().toLowerCase(Locale.ROOT);
            if (!email.isEmpty()) {
                emails.add(email);
            }
        }
        return emails;
    }

    record SyncResult(int added, int removed, int total) {
    }
}
