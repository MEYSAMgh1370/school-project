package com.example.roshdeandishe.finance.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * جاروی ساعتیِ تخفیف‌های مشروطِ مهلت‌گذشته (openspec/changes/tuition-installments.md، ضمیمه‌ی «تخفیف مشروط»):
 * هر تخفیفِ PENDING که روزِ مهلتش (به وقتِ مدرسه) گذشته LAPSED می‌شود. کارِ اصلی (قفلِ فاکتور، تراکنشِ جدا برای هر
 * فاکتور، گرفتن و لاگِ شکستِ تک‌تک فاکتورها) در {@link FinanceFacadeImpl#sweepExpiredDiscounts()} است تا تست‌ها بدونِ
 * انتظار برای زمان‌بندی صدایش بزنند؛ همان برداشتنِ تنبل زیر قفل هم در هر نوشتن روی فاکتور انجام می‌شود، پس این job
 * فقط فاکتورهای بی‌حرکت را به‌روز نگه می‌دارد. {@code EnableScheduling} فقط یک‌جا در
 * school.internal.AcademicYearProvisioner.SchedulingConfig اعلام شده است. هرگز exception بیرون نمی‌دهد.
 */
@Component
class ExpiredDiscountSweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpiredDiscountSweeper.class);

    private final FinanceFacadeImpl financeFacade;
    private final boolean enabled;

    ExpiredDiscountSweeper(FinanceFacadeImpl financeFacade,
                           @Value("${roshdeandishe.finance.discount-sweep.enabled:true}") boolean enabled) {
        this.financeFacade = financeFacade;
        this.enabled = enabled;
    }

    /** هر ساعت، ۲۰ دقیقه بعد از سر ساعت. */
    @Scheduled(cron = "0 20 * * * *")
    void hourly() {
        if (!enabled) {
            return;
        }
        try {
            int lapsed = financeFacade.sweepExpiredDiscounts();
            if (lapsed > 0) {
                log.info("تخفیف‌های مشروطِ مهلت‌گذشته برداشته شد: {}", lapsed);
            }
        } catch (RuntimeException e) {
            log.warn("جاروی تخفیف‌های مهلت‌گذشته شکست خورد: {}", e.getClass().getSimpleName());
        }
    }
}
