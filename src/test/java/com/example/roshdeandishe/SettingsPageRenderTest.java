package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * صفحه‌ی «تنظیمات مدرسه» (school-settings): ساختار قالب بدون Spring و Docker؛ رفتار سمت کلاینت را مرورگر نشان می‌دهد.
 */
class SettingsPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private String render() {
        StringOutput output = new StringOutput();
        TemplateEngine.createPrecompiled(ContentType.Html).render("settings.jte", Map.of("navbar", NAVBAR), output);
        String html = output.toString();
        // برای راستی‌آزمایی در مرورگر (فقط وقتی مسیرش داده شده)
        String dump = System.getProperty("settings.page.dump");
        if (dump != null) {
            try {
                Files.writeString(Path.of(dump), html);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return html;
    }

    /** متنِ یک بخش: از id آن تا id بخش بعدی (یا پایان). */
    private static String section(String html, String id) {
        List<String> ids = List.of("settingsYear", "settingsFinance", "settingsReminders", "settingsBot");
        int start = html.indexOf("id=\"" + id + "\"");
        assertThat(start).as("section " + id).isGreaterThanOrEqualTo(0);
        int end = html.length();
        for (String other : ids) {
            int at = html.indexOf("id=\"" + other + "\"", start + 1);
            if (at > 0 && at < end) {
                end = at;
            }
        }
        // مودال‌ها بعد از آخرین بخش می‌آیند
        int modal = html.indexOf("<div class=\"modal\"", start);
        if (modal > 0 && modal < end) {
            end = modal;
        }
        return html.substring(start, end);
    }

    @Test
    void hasFourSectionsEachShownOnlyWhenItsPartOfTheResponseIsPresent() {
        String html = render();

        assertThat(section(html, "settingsYear")).contains("x-show=\"hasYear\"");
        assertThat(section(html, "settingsFinance")).contains("x-show=\"hasFinance\"");
        assertThat(section(html, "settingsReminders")).contains("x-show=\"hasReminders\"");
        assertThat(section(html, "settingsBot")).contains("x-show=\"hasBot\"");
        assertThat(html).contains("x-data=\"settingsPage()\"");
    }

    @Test
    void everySectionHasItsOwnSaveButtonErrorAreaAndSuccessNote() {
        String html = render();

        for (String id : List.of("settingsYear", "settingsFinance", "settingsReminders", "settingsBot")) {
            String part = section(html, id);
            assertThat(part).as(id + " save").contains("type=\"submit\"");
            assertThat(part).as(id + " alert").contains("id=\"" + id + "Error\"").contains("role=\"alert\"");
            assertThat(part).as(id + " status").contains("id=\"" + id + "Notice\"").contains("role=\"status\"");
        }
        assertThat(html).contains("<p role=\"status\" x-show=\"loading\"");
    }

    @Test
    void theRoleGroupsAreFieldsetsWithLegends() {
        String html = render();

        for (String id : List.of("settingsReminders", "settingsBot")) {
            assertThat(section(html, id)).as(id).contains("<fieldset").contains("<legend");
        }
        assertThat(section(html, "settingsBot"))
                .contains("اگر هیچ نقشی انتخاب نشود، همه‌ی نقش‌ها می‌توانند از بات استفاده کنند.");
    }

    @Test
    void theYearSectionCarriesTheYearSelectWarningAutoPromotionAndBothAttributionNotices() {
        String year = section(render(), "settingsYear").replaceAll("\\s+", " ");

        assertThat(year).contains("id=\"settingsYearSelect\"").contains("x-for=\"year in years\"")
                .contains("id=\"settingsYearFirstChoiceWarning\"").contains("x-show=\"!current\"")
                .contains("id=\"settingsAutoPromotionToggle\"").contains("askAutoPromotion($event)")
                .contains("href=\"/promotion\"");
        assertThat(year).contains("id=\"settingsYearUnattributed\"").contains("x-show=\"hasUnattributed()\"")
                .contains("x-for=\"year in attributableYears\"").contains("id=\"settingsYearAttributeButton\"")
                .contains("id=\"settingsFinanceUnattributed\"").contains("x-show=\"hasFinanceUnattributed()\"")
                .contains("id=\"settingsFinanceAttributeButton\"");
    }

    @Test
    void theFinanceSectionHasTheInstallmentsCheckboxWithItsHelpText() {
        String finance = section(render(), "settingsFinance").replaceAll("\\s+", " ");

        assertThat(finance).contains("id=\"settingsInstallmentsEnabled\"").contains("x-model=\"installmentsEnabled\"")
                .contains("تقسیط شهریه در این مدرسه فعال باشد")
                .contains("فقط تعریف یا ویرایشِ اقساط بسته می‌شود");
    }

    @Test
    void theBotSectionHasAnOnOffSwitchAndRoleCheckboxes() {
        String bot = section(render(), "settingsBot");

        assertThat(bot).contains("id=\"settingsBotEnabled\"").contains("role=\"switch\"")
                .contains("x-for=\"(role, index) in roles\"").contains("x-model=\"botRoles\"")
                .contains("id=\"settingsBotAllRoles\"");
        assertThat(section(render(), "settingsReminders")).contains("x-model=\"reminderRoles\"")
                .contains("id=\"settingsRemindersAllRoles\"");
    }

    @Test
    void theBotSectionHasASeparateNotificationsSwitchWithItsOwnLabelAndHelp() {
        String bot = section(render(), "settingsBot");

        assertThat(bot).contains("پرسش و پاسخ").contains("اعلان‌ها")
                .contains("id=\"settingsBotNotificationsEnabled\"").contains("x-model=\"botNotificationsEnabled\"")
                .contains("for=\"settingsBotNotificationsEnabled\"")
                .contains("id=\"settingsBotNotificationsHelp\"").contains("aria-describedby=\"settingsBotNotificationsHelp\"")
                .contains("یادآوری رویداد، اعلان غیبت و فاکتور جدید");
        // دو سوئیچ، دو مدل جدا؛ سوئیچِ پرسش و پاسخ همان مدل قبلی را دارد
        assertThat(bot).contains("x-model=\"botEnabled\"");
        assertThat(bot.indexOf("x-model=\"botEnabled\"")).isNotEqualTo(bot.indexOf("x-model=\"botNotificationsEnabled\""));
    }

    @Test
    void hasTheAutoPromotionAndTheTwoAttributionConfirmModals() {
        String html = render();

        assertThat(html).contains("aria-labelledby=\"settingsAutoConfirmTitle\"")
                .contains("id=\"settingsAutoConfirmButton\"")
                .contains("aria-labelledby=\"settingsAttributeConfirmTitle\"")
                .contains("id=\"settingsAttributeConfirm\"")
                .contains("aria-labelledby=\"settingsFinanceAttributeConfirmTitle\"")
                .contains("id=\"settingsFinanceAttributeConfirm\"");
        assertThat(html).contains("ModalFocus.trap($event, $el)");
        Matcher modals = Pattern.compile("class=\"modal\" role=\"dialog\" aria-modal=\"true\"").matcher(html);
        int count = 0;
        while (modals.find()) {
            count++;
        }
        assertThat(count).isEqualTo(3);
    }

    @Test
    void loadsTheScriptsInOrderWithoutInlineStyleOrNativeDialogs() {
        String html = render();

        assertThat(html).contains("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/modal-focus.js",
                "/js/settings-logic.js", "/js/settings.js");
        assertThat(html.indexOf("/js/jalali.js")).isLessThan(html.indexOf("/js/calendar.js"));
        assertThat(html.indexOf("/js/settings-logic.js")).isLessThan(html.indexOf("/js/settings.js"));
        assertThat(html).doesNotContain("style=\"").doesNotContain("alert(").doesNotContain("confirm(");
    }
}
