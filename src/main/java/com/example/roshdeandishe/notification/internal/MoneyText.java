package com.example.roshdeandishe.notification.internal;

import java.math.BigDecimal;

/**
 * نمایش پول در پیام‌های ربات: ریالِ کامل، رقم فارسی، برچسب «ریال». واحد پول فقط از {@link #currencyOf} می‌آید.
 */
final class MoneyText {

    /** واحدهای پولِ قابل نمایش. */
    enum Currency { IRR }

    private MoneyText() {
    }

    /** تا زمانی که واحد پول به‌ازای مدرسه وجود ندارد، برای همه‌ی مدرسه‌ها ریال است. */
    static Currency currencyOf(Long schoolId) {
        return Currency.IRR;
    }

    /** IRR به «grouped(amount) ریال». */
    static String format(BigDecimal amount, Currency currency) {
        return switch (currency) {
            case IRR -> PersianText.grouped(amount) + " ریال";
        };
    }
}
