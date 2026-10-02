package com.example.roshdeandishe;

import com.example.roshdeandishe.web.dto.WardRef;
import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «نمره‌های من» (آکاردئونِ دانش‌آموز) و «فاکتورهای من» (فیلترِ ?student=) را
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
    void gradesPageIsAnAccordionWithAnAccessibleToggleAndPanelPerStudent() {
        String html = renderGrades(List.of());

        assertThat(html).contains("x-for=\"group in groups()\"");
        assertThat(html).contains(":aria-expanded=\"isOpen(group.studentId)\"")
                .contains(":aria-controls=\"'grades-panel-' + group.studentId\"")
                .contains("role=\"region\"")
                .contains(":aria-labelledby=\"'grades-toggle-' + group.studentId\"");
        assertThat(html).contains("@click=\"toggle(group.studentId)\"");
        assertThat(html).contains("/css/my-grades.css");
    }

    @Test
    void gradesPageNoLongerFiltersAndHasNoStudentColumn() {
        String html = renderGrades(List.of());

        assertThat(html).doesNotContain("studentFilter").doesNotContain("visibleGrades");
        assertThat(html).doesNotContain("<th scope=\"col\">دانش‌آموز</th>");
        // ستون‌های هر بخش
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
                new WardRef(101L, 6L, "نیکا \"کوچولو\" <b>رضایی</b>")));

        assertThat(html).contains("id=\"my-grades-wards\"");
        assertThat(html).contains("data-student-id=\"100\"", "data-school-id=\"5\"", "data-name=\"آرین رضایی\"");
        assertThat(html).contains("data-student-id=\"101\"", "data-school-id=\"6\"");
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
