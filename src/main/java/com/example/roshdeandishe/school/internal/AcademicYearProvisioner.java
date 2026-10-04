package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.internal.SolarCalendar.SolarDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * سال تحصیلی را خودِ سیستم می‌سازد (openspec/changes/academic-year.md): نه
 * اپراتورِ پلتفرم و نه مدیرِ مدرسه. در کانفیگ، تاریخِ شمسیِ شروعِ سالِ تحصیلی
 * تعیین شده ({@code roshdeandishe.academic-year.start-month/start-day}، مثلاً ۱
 * مرداد)؛ از آن تاریخ به بعد هر سالِ شمسی، سالِ تحصیلیِ همان سال ساخته و در
 * دیتابیس ذخیره می‌شود - در ۱ مرداد ۱۴۰۵ سالِ «1405-1406». پیش از آن تاریخ در
 * هر سال، آخرین سالِ ساخته‌شده همان سالِ قبل است.
 * <p>
 * اجرا: یک‌بار هنگام بالا آمدن برنامه (برای سرور خاموش در روزِ مرزی) و هر شب
 * (cron) - idempotent است: سالِ موجود دوباره ساخته نمی‌شود و اگر برنامه مدتی
 * خاموش بوده، سال‌های جامانده یکی‌یکی و بدون حفره پر می‌شوند. چند instance
 * هم‌زمان خطر ندارد چون درج با {@code on conflict do nothing} است.
 * <p>
 * هیچ مدرسه‌ای با ساخته‌شدنِ سال تغییری نمی‌کند: «سال جاری» هر مدرسه همچنان
 * انتخابِ مدیرِ همان مدرسه است.
 */
@Component
class AcademicYearProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AcademicYearProvisioner.class);

    private final AcademicYearRepository repository;
    private final int startMonth;
    private final int startDay;
    private final ZoneId zone;

    AcademicYearProvisioner(
            AcademicYearRepository repository,
            @Value("${roshdeandishe.academic-year.start-month}") int startMonth,
            @Value("${roshdeandishe.academic-year.start-day}") int startDay,
            @Value("${roshdeandishe.academic-year.timezone}") String timezone
    ) {
        validate(startMonth, startDay);
        this.repository = repository;
        this.startMonth = startMonth;
        this.startDay = startDay;
        this.zone = ZoneId.of(timezone);
    }

    /**
     * ماه ۱ تا ۱۲؛ روز در بازه‌ی طولِ ماه (۱ تا ۱۲ ماهِ اولِ ۳۱ روزه، ۷ تا ۱۱ سی‌روزه،
     * اسفند ۲۹ روزه در سالِ غیرکبیسه) - تاریخی که در برخی سال‌ها وجود ندارد
     * سالِ تحصیلی را آن سال‌ها نمی‌ساخت، پس از همان ابتدا ردّ می‌شود.
     */
    static void validate(int month, int day) {
        int maxDay = month <= 6 ? 31 : (month <= 11 ? 30 : 29);
        if (month < 1 || month > 12 || day < 1 || day > maxDay) {
            throw new IllegalStateException("roshdeandishe.academic-year.start-month/start-day نامعتبر است: "
                    + month + "/" + day + " (ماه ۱ تا ۱۲، روز ۱ تا طولِ ماه؛ اسفند حداکثر ۲۹).");
        }
    }

    /** هنگام بالا آمدنِ برنامه (بعد از Flyway). */
    @Override
    public void run(ApplicationArguments args) {
        ensureAcademicYears(LocalDate.now(zone));
    }

    /** هر شب، کمی بعد از نیمه‌شب به وقتِ تهران (کانفیگ timezone). */
    @Scheduled(cron = "0 15 0 * * *", zone = "${roshdeandishe.academic-year.timezone}")
    void nightly() {
        ensureAcademicYears(LocalDate.now(zone));
    }

    /**
     * سال‌های تحصیلیِ جامانده تا «سالِ تحصیلیِ امروز» را می‌سازد و تعداد سالِ
     * تازه‌ساخته را برمی‌گرداند. «امروز» پارامتر است تا بدون ساعتِ واقعی قابل تست باشد.
     */
    int ensureAcademicYears(LocalDate today) {
        int target = currentAcademicStartYear(today);
        Integer latest = repository.findAllByOrderByStartYearAsc().stream()
                .map(AcademicYearEntity::getStartYear)
                .max(Integer::compare)
                .orElse(null);
        int from = latest == null ? target : latest + 1;
        int created = 0;
        for (int startYear = from; startYear <= target; startYear++) {
            String code = startYear + "-" + (startYear + 1);
            int inserted = repository.insertIfAbsent(code, startYear);
            if (inserted > 0) {
                log.info("سال تحصیلی {} ساخته شد.", code);
            }
            created += inserted;
        }
        return created;
    }

    /**
     * سالِ شروعِ سالِ تحصیلیِ جاری: اگر امروز به تاریخِ شروعِ همین سالِ شمسی رسیده،
     * خودِ سالِ شمسی؛ وگرنه سالِ قبل. (۱۴۰۵/۰۵/۰۱ و بعد → ۱۴۰۵؛ ۱۴۰۵/۰۴/۳۱ → ۱۴۰۴.)
     */
    int currentAcademicStartYear(LocalDate today) {
        SolarDate solar = SolarCalendar.fromGregorian(today);
        boolean started = solar.month() > startMonth
                || (solar.month() == startMonth && solar.day() >= startDay);
        return started ? solar.year() : solar.year() - 1;
    }

    /** زمان‌بندی (@Scheduled) فقط یک‌جا فعال می‌شود. */
    @Configuration
    @EnableScheduling
    static class SchedulingConfig {
    }
}
