package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** قالب نمایش پول در پیام‌های ربات. */
class MoneyTextTest {

    @Test
    void rialIsLabelled() {
        assertThat(MoneyText.format(new BigDecimal("1200"), MoneyText.Currency.IRR)).isEqualTo("۱٬۲۰۰ ریال");
    }

    @Test
    void everySchoolIsRialForNow() {
        assertThat(MoneyText.currencyOf(7L)).isEqualTo(MoneyText.Currency.IRR);
    }
}
