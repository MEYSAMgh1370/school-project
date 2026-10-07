package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.notification.NotificationFacade;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.ReminderEvent;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * یادآوری رویدادهای برنامه‌ی سالانه (openspec/changes/event-reminders.md): هر ساعت، برای هر مدرسه‌ی
 * فعال که ساعتِ محلی‌اش به ساعتِ ارسال ({@code roshdeandishe.reminders.send-hour}) رسیده یا گذشته،
 * رویدادهای «سررسیدشده» را به گیرنده‌ها (اعضای فعال با نقشِ انتخاب‌شده، یا همه) پیام می‌دهد. ساعتِ
 * بعد از ساعتِ ارسال هم همان روز جبران می‌کند (catch-up).
 * <p>
 * بدون پیامِ تکراری: ردیفِ دفتر (EventReminderSentRepository.claim) «قبل از» ارسال درج می‌شود و فقط
 * با درجِ موفق پیام می‌رود؛ هر گیرنده برای هر رویداد یک‌بار تلاش می‌شود حتی اگر ارسال {@code false}
 * (بدونِ حسابِ بله یا شکست) برگردد. شکستِ یک ارسال یا یک مدرسه بقیه را متوقف نمی‌کند و
 * {@link #runOnce} هیچ‌وقت exception بیرون نمی‌دهد. {@code TenantContext} برای هر مدرسه ست و در finally
 * پاک می‌شود. متنِ پیام و اطلاعاتِ شخصی هرگز لاگ نمی‌شود (فقط شناسه‌ها و شمارش‌ها).
 * <p>
 * زمان‌بندی ({@code EnableScheduling}) فقط یک‌جا در school.internal.AcademicYearProvisioner.SchedulingConfig
 * اعلام شده است.
 */
@Component
class EventReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EventReminderJob.class);
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Tehran");

    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;
    private final NotificationFacade notificationFacade;
    private final EventReminderSentRepository ledger;
    private final boolean enabled;
    private final int sendHour;

    EventReminderJob(SchoolFacade schoolFacade, IamFacade iamFacade, NotificationFacade notificationFacade,
                     EventReminderSentRepository ledger,
                     @Value("${roshdeandishe.reminders.enabled:true}") boolean enabled,
                     @Value("${roshdeandishe.reminders.send-hour:8}") int sendHour) {
        validateSendHour(sendHour);
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
        this.notificationFacade = notificationFacade;
        this.ledger = ledger;
        this.enabled = enabled;
        this.sendHour = sendHour;
    }

    /** ساعتِ ارسال ۰ تا ۲۳؛ مقدارِ نامعتبر همان لحظه‌ی بالا آمدن fail می‌کند. */
    static void validateSendHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalStateException(
                    "roshdeandishe.reminders.send-hour نامعتبر است: " + hour + " (باید ۰ تا ۲۳ باشد).");
        }
    }

    /** هر ساعت، ۱۰ دقیقه بعد از سر ساعت. */
    @Scheduled(cron = "0 10 * * * *")
    void hourly() {
        runOnce(Instant.now());
    }

    /** یک دور اجرا؛ «اکنون» پارامتر است تا بدونِ ساعتِ واقعی قابل تست باشد. هرگز exception بیرون نمی‌دهد. */
    void runOnce(Instant now) {
        if (!enabled) {
            return;
        }
        try {
            for (SchoolInfo school : schoolFacade.listSchools()) {
                if (school.status() != SchoolStatus.ACTIVE) {
                    continue;
                }
                try {
                    remindSchool(school, now);
                } catch (RuntimeException e) {
                    log.warn("یادآوری رویدادها برای مدرسه {} شکست خورد: {}", school.id().value(),
                            e.getClass().getSimpleName());
                } finally {
                    TenantContext.clear();
                }
            }
        } catch (RuntimeException e) {
            log.warn("اجرای یادآوری رویدادها شکست خورد: {}", e.getClass().getSimpleName());
        }
    }

    private void remindSchool(SchoolInfo school, Instant now) {
        ZonedDateTime local = now.atZone(zoneOf(school.timezone()));
        if (local.getHour() < sendHour) {
            return;
        }
        LocalDate today = local.toLocalDate();
        SchoolId schoolId = school.id();
        TenantContext.set(schoolId.value());

        List<ReminderEvent> events = schoolFacade.dueReminderEvents(schoolId, today);
        if (events.isEmpty()) {
            return;
        }
        Set<Long> recipients = recipients(schoolId);

        int sent = 0;
        int notDelivered = 0;
        int failed = 0;
        for (ReminderEvent event : events) {
            String text = buildMessage(event.title(), (int) ChronoUnit.DAYS.between(today, event.day()),
                    schoolFacade.formatSolarDate(event.day()), school.name());
            for (Long userId : recipients) {
                try {
                    if (ledger.claim(event.id(), userId) == 0) {
                        continue;
                    }
                    if (notificationFacade.sendMessageToUser(userId, text)) {
                        sent++;
                    } else {
                        notDelivered++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("ارسال یادآوری رویداد {} به کاربر {} شکست خورد: {}", event.id(), userId,
                            e.getClass().getSimpleName());
                }
            }
        }
        log.info("یادآوری رویدادها برای مدرسه {}: رویداد={}, گیرنده={}, ارسال‌شده={}, ارسال‌نشده={}, خطا={}",
                schoolId.value(), events.size(), recipients.size(), sent, notDelivered, failed);
    }

    /** اعضای فعالِ مدرسه با نقشِ انتخاب‌شده (Optional.empty = همه‌ی نقش‌ها)، بدونِ تکرارِ کاربر. */
    private Set<Long> recipients(SchoolId schoolId) {
        Optional<Set<String>> roles = schoolFacade.reminderRoleCodes(schoolId);
        Set<Long> userIds = new LinkedHashSet<>();
        for (MembershipInfo m : iamFacade.findMembershipsBySchool(schoolId.value())) {
            if (m.active() && (roles.isEmpty() || roles.get().contains(m.roleCode()))) {
                userIds.add(m.userId().value());
            }
        }
        return userIds;
    }

    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(timezone.strip());
        } catch (DateTimeException e) {
            return FALLBACK_ZONE;
        }
    }

    /**
     * «یادآوری: رویداد «عنوان» امروز/فردا/N روز دیگر (تاریخِ شمسی) در مدرسه برگزار می‌شود.»
     * عددِ «N روز دیگر» با ارقامِ فارسی نوشته می‌شود (مثل تاریخِ شمسیِ SchoolFacade.formatSolarDate).
     */
    static String buildMessage(String title, int daysLeft, String solarDate, String schoolName) {
        String when = switch (daysLeft) {
            case 0 -> "امروز";
            case 1 -> "فردا";
            default -> persianDigits(daysLeft) + " روز دیگر";
        };
        return "یادآوری: رویداد «" + title + "» " + when + " (" + solarDate + ") در " + schoolName
                + " برگزار می‌شود.";
    }

    private static String persianDigits(int n) {
        StringBuilder out = new StringBuilder();
        for (char c : Integer.toString(n).toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }
}
