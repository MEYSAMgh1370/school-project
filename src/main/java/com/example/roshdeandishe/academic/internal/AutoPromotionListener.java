package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.PromotionFacade;
import com.example.roshdeandishe.school.CurrentAcademicYearChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * ارتقای خودکار هنگام تعویضِ سالِ جاری (academic-year.md فاز ۳، بندِ ۹): اگر مدرسه تنظیمش را روشن کرده و سالِ
 * جدید بعد از سالِ قبلی است، قاعده‌ی پیش‌فرض برای همه‌ی دانش‌آموزان اجرا می‌شود. listener بعد از commitِ تعویضِ
 * سال و در thread جدا اجرا می‌شود؛ شکست فقط لاگ می‌شود و مدرسه در حالتِ دستی می‌ماند (مدیر همان صفحه‌ی ارتقا را
 * دستی اجرا می‌کند).
 */
@Component
class AutoPromotionListener {

    private static final Logger log = LoggerFactory.getLogger(AutoPromotionListener.class);

    private final PromotionFacade promotionFacade;
    private final EnrollmentLedger enrollmentLedger;

    AutoPromotionListener(PromotionFacade promotionFacade, EnrollmentLedger enrollmentLedger) {
        this.promotionFacade = promotionFacade;
        this.enrollmentLedger = enrollmentLedger;
    }

    @ApplicationModuleListener
    void on(CurrentAcademicYearChangedEvent event) {
        if (event.toStartYear() <= event.fromStartYear()) {
            return; // بازگشت به سالِ قبل (سؤال ۶) هرگز ارتقا نمی‌دهد
        }
        try {
            // اگر در این فاصله دوباره عوض شده، این رویداد کهنه است
            boolean stillCurrent = enrollmentLedger.currentYear(event.schoolId())
                    .map(y -> y.code().equals(event.toYear())).orElse(false);
            if (!stillCurrent) {
                return;
            }
            promotionFacade.runAutomatic(event.schoolId(), event.fromYear()).ifPresent(run ->
                    log.info("Automatic promotion for school {} from {} to {}: {}", event.schoolId(),
                            event.fromYear(), event.toYear(), run.counts()));
        } catch (RuntimeException e) {
            log.error("Automatic promotion failed for school {} ({} -> {}); the school stays in manual mode",
                    event.schoolId(), event.fromYear(), event.toYear(), e);
        }
    }
}
