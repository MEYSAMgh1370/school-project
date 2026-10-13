package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * دکمه‌ی باز کردنِ منو روی موبایل (burger) و دکمه‌ی بستنِ کشو باید هدفِ لمسِ حداقل ۴۴ پیکسلی و خطوطِ خوانا
 * داشته باشند (DESIGN.md §7). Bulma آن‌ها را ۴۰ و ۲۰ پیکسل و با خطوطِ ۱۶ پیکسلی می‌ساخت؛ اینجا فقط اطمینان
 * می‌دهیم override در app.css هست و کوچک‌تر از آستانه نیست (بدون Spring و مرورگر).
 */
class MobileMenuControlsCssTest {

    private static final Path CSS = Path.of("src/main/resources/static/css/app.css");

    private String block(String selector) throws Exception {
        String css = Files.readString(CSS);
        Matcher m = Pattern.compile(Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
        assertThat(m.find()).as("rule for " + selector).isTrue();
        return m.group(1);
    }

    private static double rem(String body, String property) {
        Matcher m = Pattern.compile(Pattern.quote(property) + ":\\s*([0-9.]+)rem").matcher(body);
        assertThat(m.find()).as(property).isTrue();
        return Double.parseDouble(m.group(1));
    }

    @Test
    void theMenuBurgerIsAtLeastAFortyFourPixelTouchTargetWithLongerBars() throws Exception {
        String body = block("button.navbar-burger");

        assertThat(rem(body, "inline-size") * 16).isGreaterThanOrEqualTo(44);
        assertThat(rem(body, "block-size") * 16).isGreaterThanOrEqualTo(44);
        // خطوطِ بزرگ‌تر از ۱۶ پیکسلِ پیش‌فرض و ضخیم‌تر از ۲ پیکسل
        assertThat(rem(body, "--bulma-burger-item-width") * 16).isGreaterThan(16);
        assertThat(body).contains("--bulma-burger-item-height: 3px");
    }

    @Test
    void theDrawerCloseButtonIsAtLeastAFortyFourPixelTouchTarget() throws Exception {
        String body = block(".app-sidebar-close");

        assertThat(rem(body, "--bulma-delete-dimensions") * 16).isGreaterThanOrEqualTo(44);
    }
}
