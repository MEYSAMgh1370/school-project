package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * قالب «تعطیلی‌های سراسری» (/platform/holidays) بدون Spring و Docker: اقدامِ اصلی در هدر، انتخاب‌گرِ سال، modalهای
 * افزودن/تأیید حذف، حالتِ خالیِ آموزنده، نبودنِ school-scope و X-School-ID (مدیر پلتفرم شاید هیچ مدرسه‌ای ندارد)،
 * ترتیبِ اسکریپت‌ها و نبودنِ ویژگیِ فیزیکیِ راست/چپ. رفتارِ Alpine در مرورگر راستی‌آزمایی می‌شود؛ منطقِ خالص در
 * src/test/js/platform-holidays-logic.test.js است.
 */
class PlatformHolidaysPageRenderTest {

    // مدیر پلتفرمِ بدونِ هیچ عضویتِ مدرسه‌ای
    private static final NavbarModel NAVBAR = new NavbarModel("مدیر پلتفرم", List.of(), Map.of(), true);

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("platform-holidays.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = PlatformHolidaysPageRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void theHeaderHasTheSinglePrimaryActionThenTheYearSelectorAndTheList() {
        String html = render();

        assertThat(html).contains("x-data=\"platformHolidaysPage()\"").contains("<title>تعطیلی‌های سراسری - ");
        assertThat(html).contains("این روزها در برنامه‌ی سالانه‌ی همه‌ی مدرسه‌ها می‌آیند و روز را در همه‌ی مدرسه‌ها تعطیل می‌کنند");
        assertThat(html).contains("id=\"platformHolidaysAdd\"").contains("افزودن تعطیلی</button>");
        // سرصفحه + «افزودن» داخل modal؛ اقدامِ اصلیِ صفحه فقط یکی است
        assertThat(count(html, "button is-link")).isEqualTo(2);
        // کنترل بر اساس Permission پنهان یا غیرفعال نمی‌شود
        assertThat(html).doesNotContain(":disabled=");
        int page = html.indexOf("x-data=\"platformHolidaysPage()\"");
        assertThat(html.indexOf("id=\"platformHolidaysAdd\"", page)).isLessThan(html.indexOf("id=\"platformHolidaysYearLine\""));
        assertThat(html.indexOf("id=\"platformHolidaysYearLine\"")).isLessThan(html.indexOf("id=\"platformHolidaysYear\""));
        assertThat(html.indexOf("id=\"platformHolidaysYear\"")).isLessThan(html.indexOf("id=\"platformHolidaysList\""));
        assertThat(html).contains("x-model.number=\"selectedYearId\"", "@change=\"onYearChange()\"", "yearOptionLabel(y)");
        // جدول: روزِ هفته در موبایل می‌افتد و جدول در table-container است
        assertThat(html).contains("th scope=\"col\" class=\"is-hidden-mobile\">روز</th>").contains("table-container");
        assertThat(html).contains("@click=\"askRemove(item)\"").contains(">حذف</button>");
    }

    @Test
    void theEmptyStatesTeachAndErrorsAndSuccessShareOneNoticeArea() {
        String html = render();

        assertThat(html).contains("تعطیلی‌های ملی و مذهبی را اینجا با «افزودن تعطیلی» ثبت کنید")
                .contains("هر مدرسه آن‌ها را خودکار در برنامه‌ی سالانه‌اش می‌بیند");
        // `&&` در صفت escape می‌شود؛ مرورگر قبل از Alpine دوباره decode می‌کند
        assertThat(html).contains("items.length === 0");
        assertThat(html).contains("هنوز هیچ سال تحصیلی‌ای تعریف نشده است");
        assertThat(html).contains("در حال بارگذاری...");
        assertThat(count(html, "x-show=\"error\"")).isEqualTo(1);
        assertThat(html).contains("notification is-danger\" role=\"alert\" x-cloak x-show=\"error\"")
                .contains("notification is-success\" role=\"status\" x-cloak x-show=\"notice\"");
    }

    @Test
    void addAndConfirmModalsTrapFocusAndValidateInlineAndInTheSummary() {
        String html = render();

        assertThat(html).contains("x-ref=\"addModal\"", "x-ref=\"confirmModal\"");
        assertThat(html).contains("aria-labelledby=\"platformHolidaysAddTitle\"", "aria-labelledby=\"platformHolidaysConfirmTitle\"");
        assertThat(count(html, "window.ModalFocus.trap($event, $el)")).isEqualTo(2);
        assertThat(html).contains("id=\"platformHolidaysDate\"", "x-model=\"add.date\"", "dir=\"ltr\"", "x-text=\"rangeHelp()\"");
        assertThat(html).contains("id=\"platformHolidaysTitleInput\"", "maxlength=\"128\"", "x-model=\"add.title\"");
        assertThat(html).contains("x-show=\"add.dateError\"", "x-show=\"add.titleError\"", "x-show=\"add.error\"");
        assertThat(html).contains("is-danger': add.dateError", "is-danger': add.titleError");
        assertThat(html).contains("id=\"platformHolidaysConfirmDelete\"").contains("حذف قطعی");
        assertThat(html).contains(":class=\"{ 'is-loading': saving }\"");
    }

    @Test
    void thePageIsPlatformScopedWithoutSchoolScopeOrSchoolHeader() throws IOException {
        String html = render();

        // layout/school-scope (هشدارِ «مدرسه انتخاب کنید» و x-if="schoolId") نیست
        assertThat(html).doesNotContain("schoolId").doesNotContain("x-if=\"schoolId\"")
                .doesNotContain("مدرسه‌ای انتخاب نشده").doesNotContain("از سوییچر بالا");
        assertThat(html).doesNotContain("/js/school-api.js");
        String js = resource("/static/js/platform-holidays.js");
        assertThat(js).doesNotContain("X-School-ID").doesNotContain("SchoolApi").doesNotContain("selectedSchoolId")
                .doesNotContain("school-changed");
        // CSRF روی نوشتن کم نشده و آدرس‌ها پلتفرمی‌اند
        assertThat(js).contains("'X-XSRF-TOKEN'").contains("method !== 'GET'")
                .contains("/api/platform").contains("/academic-years/${yearId}/holidays").contains("/holidays/${target.id}");
        assertThat(js).contains("فقط مدیر پلتفرم می‌تواند تعطیلی‌های سراسری را مدیریت کند.")
                .contains("endsWith('permission-denied')");
        assertThat(js).contains("'تعطیلی افزوده شد.'").contains("'تعطیلی حذف شد.'");
    }

    @Test
    void thePageRendersForAUserWithoutAnySchoolMembership() {
        String html = render();

        assertThat(html).contains("schoolNavbar({}, {})").contains("href=\"/platform/holidays\"");
        assertThat(html).doesNotContain("data-group=");
    }

    @Test
    void scriptsLoadInOrderAndNoPhysicalLeftRightOrBrowserDialogs() throws IOException {
        String html = render();

        assertThat(html.indexOf("/js/jalali.js")).isLessThan(html.indexOf("/js/calendar.js"));
        assertThat(html.indexOf("/js/calendar.js")).isLessThan(html.indexOf("/js/date-input.js"));
        assertThat(html.indexOf("/js/date-input.js")).isLessThan(html.indexOf("/js/yearly-plan-logic.js"));
        assertThat(html.indexOf("/js/yearly-plan-logic.js")).isLessThan(html.indexOf("/js/platform-holidays.js"));
        assertThat(html.indexOf("/js/platform-holidays.js")).isLessThan(html.indexOf("/webjars/alpinejs/"));
        assertThat(html).contains("/js/modal-focus.js").contains("/js/navbar.js");
        String page = html.substring(html.indexOf("x-data=\"platformHolidaysPage()\""));
        assertThat(page).doesNotContain("alert(").doesNotContainPattern("(?<![.\\w])confirm\\(");
        assertThat(html).doesNotContainPattern("(?i)(margin|padding|border)-(left|right)")
                .doesNotContainPattern("(?i)text-align:\\s*(left|right)")
                .doesNotContain("is-pulled-left").doesNotContain("is-pulled-right")
                .doesNotContain("<style");
        assertThat(resource("/static/js/platform-holidays.js"))
                .doesNotContainPattern("(?i)\\b(margin|padding|border|text-align)-?(left|right)\\b")
                .doesNotContainPattern("(?<![.\\w])alert\\(")
                .doesNotContainPattern("(?<![.\\w])confirm\\(");
    }
}
