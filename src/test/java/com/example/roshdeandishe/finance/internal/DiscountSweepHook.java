package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.FinanceFacade;

/**
 * راهِ تست‌ها به جاروی تخفیف‌های مهلت‌گذشته بدونِ انتظار برای زمان‌بندی: جارو عمداً در {@link FinanceFacade}
 * نیست و فقط روی پیاده‌سازیِ package-private هست؛ این کلاس در همان package (در پوشه‌ی تست) است.
 */
public final class DiscountSweepHook {

    private DiscountSweepHook() {
    }

    /** @return تعداد تخفیف‌های LAPSEDشده */
    public static int sweep(FinanceFacade facade) {
        return ((FinanceFacadeImpl) facade).sweepExpiredDiscounts();
    }
}
