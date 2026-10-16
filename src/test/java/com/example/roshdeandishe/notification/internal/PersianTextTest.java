package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** تبدیل رقم و گروه‌بندی مبلغ به فارسی. */
class PersianTextTest {

    @Test
    void digitsAreConvertedToPersian() {
        assertThat(PersianText.digits(1405)).isEqualTo("۱۴۰۵");
    }

    @Test
    void amountsAreGroupedWithPersianSeparator() {
        assertThat(PersianText.grouped(new BigDecimal("45000000.00"))).isEqualTo("۴۵٬۰۰۰٬۰۰۰");
    }

    @Test
    void localizeConvertsDigitsAndDecimalPoint() {
        assertThat(PersianText.localize("18.5")).isEqualTo("۱۸٫۵");
    }

    @Test
    void markdownCharactersAreReplacedWithInertLookalikes() {
        assertThat(PersianText.withoutMarkdown("*علی* _رضا_ [پیوند](x) ```y```"))
                .isEqualTo("∗علی∗ ˍرضاˍ ［پیوند](x) ˋˋˋyˋˋˋ");
    }

    @Test
    void plainPersianTextIsUnchanged() {
        String text = "حساب شما هنوز متصل نشده است. لطفاً ابتدا /start را ارسال کنید.";
        assertThat(PersianText.withoutMarkdown(text)).isEqualTo(text);
        assertThat(PersianText.withoutMarkdown(null)).isNull();
    }
}
