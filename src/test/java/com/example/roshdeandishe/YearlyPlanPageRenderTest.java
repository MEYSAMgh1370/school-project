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
 * قالب «برنامه‌ی سالانه» (openspec/changes/yearly-plan.md) بدون Spring و Docker: اقدامِ اصلی در هدر، حالت‌های
 * خالیِ آموزنده، modalهای افزودن/تأیید حذف، کلیدِ حلقه‌ها و نبودنِ ویژگیِ فیزیکیِ راست/چپ. رفتارِ Alpine در
 * مرورگر راستی‌آزمایی می‌شود؛ منطقِ خالص در src/test/js/yearly-plan-logic.test.js است.
 */
class YearlyPlanPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("yearly-plan.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = YearlyPlanPageRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void thePageHasTheAddActionInTheHeaderAndTheYearLineBeforeTheList() {
        String html = render();

        assertThat(html).contains("x-data=\"yearlyPlanPage()\"").contains("برنامه‌ی سالانه");
        assertThat(html).contains("رویدادهای مدرسه و تعطیلی‌های سراسری سال تحصیلی");
        assertThat(html).contains("id=\"yearlyPlanAdd\"").contains("افزودن رویداد</button>");
        assertThat(count(html, "button is-link")).isEqualTo(2); // سرصفحه + «افزودن» داخل modal
        assertThat(html.indexOf("id=\"yearlyPlanAdd\"")).isLessThan(html.indexOf("id=\"yearlyPlanYearLine\""));
        assertThat(html.indexOf("id=\"yearlyPlanYearLine\"")).isLessThan(html.indexOf("id=\"yearlyPlanList\""));
        // انتخاب‌گرِ سال فقط با بیش از یک سال
        assertThat(html).contains("id=\"yearlyPlanYear\"", "x-show=\"years.length > 1\"");
    }

    @Test
    void bothEmptyStatesTeachAndPlatformHolidaysAreReadOnlyTaggedRows() {
        String html = render();

        assertThat(html).contains("برای این مدرسه هنوز سال تحصیلی جاری انتخاب نشده است.").contains("href=\"/academic-year\"");
        assertThat(html).contains("هفته‌های امتحان، مراسم، جلسه‌های اولیا و اردوها").contains("«افزودن رویداد»")
                .contains("تعطیلی‌های سراسری هم خودکار");
        assertThat(html).contains("تعطیلی سراسری</span>").contains("item.source === 'PLATFORM'");
        // حذف فقط برای رویداد مدرسه؛ کلیدِ حلقه source:id است (id دو منبع می‌تواند یکی باشد)
        assertThat(html).contains("item.source === 'SCHOOL'").contains(":key=\"itemKey(item)\"");
    }

    @Test
    void addAndConfirmModalsTrapFocusAndValidateInlineAndInTheSummary() {
        String html = render();

        assertThat(html).contains("x-ref=\"addModal\"", "x-ref=\"confirmModal\"");
        assertThat(html).contains("aria-labelledby=\"yearlyPlanAddTitle\"", "aria-labelledby=\"yearlyPlanConfirmTitle\"");
        assertThat(html).contains("window.ModalFocus.trap($event, $el)");
        assertThat(html).contains("id=\"yearlyPlanDate\"", "x-model=\"add.date\"", "dir=\"ltr\"", "x-text=\"rangeHelp()\"");
        assertThat(html).contains("id=\"yearlyPlanTitleInput\"", "maxlength=\"128\"");
        assertThat(html).contains("x-show=\"add.dateError\"", "x-show=\"add.titleError\"", "x-show=\"add.error\"");
        assertThat(html).contains("id=\"yearlyPlanConfirmDelete\"").contains("حذف قطعی");
        assertThat(html).contains(":class=\"{ 'is-loading': saving }\"");
    }

    @Test
    void scriptsLoadInOrderAndNoPhysicalLeftRightOrBrowserDialogs() throws IOException {
        String html = render();

        assertThat(html.indexOf("/js/jalali.js")).isLessThan(html.indexOf("/js/calendar.js"));
        assertThat(html.indexOf("/js/calendar.js")).isLessThan(html.indexOf("/js/date-input.js"));
        assertThat(html.indexOf("/js/school-api.js")).isLessThan(html.indexOf("/js/yearly-plan.js"));
        assertThat(html.indexOf("/js/yearly-plan-logic.js")).isLessThan(html.indexOf("/js/yearly-plan.js"));
        assertThat(html).contains("/js/modal-focus.js");
        assertThat(html).doesNotContainPattern("(?i)(margin|padding|border)-(left|right)")
                .doesNotContainPattern("(?i)text-align:\\s*(left|right)")
                .doesNotContain("is-pulled-left").doesNotContain("is-pulled-right");
        for (String script : List.of("yearly-plan", "yearly-plan-logic")) {
            assertThat(resource("/static/js/" + script + ".js")).as(script)
                    .doesNotContainPattern("(?i)\\b(margin|padding|border|text-align)-?(left|right)\\b")
                    .doesNotContainPattern("\\balert\\(")
                    .doesNotContainPattern("(?<![.\\w])confirm\\(");
        }
    }
}
