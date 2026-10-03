package com.example.roshdeandishe;

import com.example.roshdeandishe.platform.LocaleContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تست واحدِ {@link LocaleContext#textDirection()}: بدون Spring context و
 * بدون دیتابیس. نگهدارنده ThreadLocal است، پس بعد از هر تست پاک می‌شود.
 */
class LocaleTextDirectionTest {

    @AfterEach
    void clearLocale() {
        LocaleContext.clear();
    }

    @Test
    void persianIsRightToLeft() {
        LocaleContext.set(LocaleContext.DEFAULT_LOCALE);

        assertThat(LocaleContext.textDirection()).isEqualTo("rtl");
    }

    @Test
    void englishIsLeftToRight() {
        LocaleContext.set(LocaleContext.ENGLISH);

        assertThat(LocaleContext.textDirection()).isEqualTo("ltr");
    }

    @Test
    void unsetLocaleFallsBackToPersianRightToLeft() {
        LocaleContext.clear();

        assertThat(LocaleContext.isSet()).isFalse();
        assertThat(LocaleContext.textDirection()).isEqualTo("rtl");
    }
}
