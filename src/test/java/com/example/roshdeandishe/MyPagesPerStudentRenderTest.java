package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.WardRef;
import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «نمره‌های من» (انتخابگرِ دانش‌آموز) و «فاکتورهای من» (فیلترِ ?student=) را
 * مستقیم و بدون Spring/Docker رندر می‌کند تا قرارداد قفل شود. رفتارِ خودِ JS در
 * مرورگر راستی‌آزمایی شد، نه اینجا (پروژه تست JS ندارد).
 */
class MyPagesPerStudentRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مریم رضایی",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("PARENT"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String template, Map<String, Object> params) {
        StringOutput output = new StringOutput();
        engine.render(template, params, output);
        return output.toString();
    }

    private String renderGrades(List<WardRef> wards) {
        return render("my-grades.jte", Map.of("navbar", NAVBAR, "wards", wards));
    }

    @Test
    void gradesPageHasAnAccessibleStudentPickerWithAListOfOptions() {
        String html = renderGrades(List.of());

        assertThat(html).contains("class=\"app-picker-toggle\"")
                .contains("aria-controls=\"grades-picker-list\"")
                .contains(":aria-expanded=\"pickerOpen\"")
                .contains("aria-labelledby=\"grades-picker-label grades-picker-current\"");
        assertThat(html).contains("id=\"grades-picker-list\"").contains("x-show=\"pickerOpen\"");
        assertThat(html).contains("x-for=\"group in groups()\"")
                .contains("@click=\"select(group.studentId)\"")
                .contains(":aria-current=\"isSelected(group.studentId) ? 'true' : null\"");
        // بستن با کلیکِ بیرون و Escape (Escape فوکوس را به دکمه برمی‌گرداند)
        assertThat(html).contains("@click.outside=\"pickerOpen = false\"").contains("@keydown.escape=\"closePicker(true)\"")
                .contains("x-ref=\"pickerToggle\"");
        assertThat(html).contains("/css/my-grades.css");
    }

    @Test
    void aLoadFailureDoesNotShowTheGradePanelAsIfNoGradesWereRecorded() {
        String html = renderGrades(List.of(new WardRef(100L, 5L, "آرین رضایی")));

        assertThat(html).contains("x-show=\"!loading && !error && groups().length > 0\"");
    }

    @Test
    void thePickerIsHiddenForASingleStudentWhoseNameIsShownInstead() {
        String html = renderGrades(List.of());

        assertThat(html).contains("x-show=\"groups().length > 1\"");
        assertThat(html).contains("x-show=\"groups().length === 1\"");
    }

    @Test
    void gradesPageShowsOnlyTheSelectedStudentsGradesAndHasNoLeftoverFilterOrAccordion() {
        String html = renderGrades(List.of());

        assertThat(html).contains("selectedGroup().grades");
        assertThat(html).doesNotContain("studentFilter").doesNotContain("visibleGrades")
                .doesNotContain("app-accordion").doesNotContain("toggle(group.studentId)");
        assertThat(html).doesNotContain("<th scope=\"col\">دانش‌آموز</th>");
        // ستون‌های جدول
        assertThat(html).contains("سال تحصیلی", "نوبت", "نمره", "تاریخ ثبت");
    }

    @Test
    void theEmptyStateFollowsTheGroups() {
        assertThat(renderGrades(List.of())).contains("groups().length === 0");
    }

    @Test
    void wardNamesReachThePageAsEscapedAttributesNotAHandBuiltJson() {
        String html = renderGrades(List.of(
                new WardRef(100L, 5L, "آرین رضایی"),
                new WardRef(101L, 6L, "نیکا \"کوچولو\" <b>رضایی</b>"),
                new WardRef(102L, 5L, "نمره‌های من", true)));

        assertThat(html).contains("id=\"my-grades-wards\"");
        assertThat(html).contains("data-student-id=\"100\"", "data-school-id=\"5\"", "data-name=\"آرین رضایی\"");
        assertThat(html).contains("data-student-id=\"101\"", "data-school-id=\"6\"");
        // خودِ کاربر (دانش‌آموزِ بدون نمره) هم با own=true می‌آید
        // (JTE برای boolean=false خودِ attribute را حذف می‌کند؛ my-grades.js فقط 'true' را own می‌شناسد)
        assertThat(html).contains("data-student-id=\"102\"", "data-own=\"true\"");
        assertThat(html.split("data-own=").length - 1).isEqualTo(1);
        // نام با کوتیشن و تگ، escape می‌شود و attribute را نمی‌شکند
        assertThat(html).doesNotContain("<b>رضایی</b>").doesNotContain("data-name=\"نیکا \"کوچولو\"");
    }

    @Test
    void wardNamesLiveOutsideTheSchoolScopeTemplateSoTheyExistWhenTheScriptInitialises() {
        String html = renderGrades(List.of(new WardRef(100L, 5L, "آرین رضایی")));

        // محتوای school-scope داخل <template x-if="schoolId"> است و هنگام init در DOM نیست
        assertThat(html.indexOf("id=\"my-grades-wards\"")).isPositive();
        assertThat(html.indexOf("id=\"my-grades-wards\"")).isLessThan(html.indexOf("<template x-if=\"schoolId\">"));
    }

    @Test
    void invoicesPageStillIteratesTheFilteredListAndOffersToShowAll() {
        String html = render("my-invoices.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("studentFilter !== null");
        assertThat(html).contains("x-for=\"invoice in visibleInvoices()\"");
        // متنِ ثابتِ template بدون escape نوشته می‌شود (برخلاف پارامترها)
        assertThat(html).contains("visibleInvoices().length > 0").doesNotContain("!loading && invoices.length > 0");
        assertThat(html).contains("href=\"/my-invoices\"").contains("نمایش همه");
        assertThat(html).contains("visibleInvoices().length === 0");
    }
}
