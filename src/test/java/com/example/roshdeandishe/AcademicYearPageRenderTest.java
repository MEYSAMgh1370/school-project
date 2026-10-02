package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * صفحه‌ی «سال تحصیلی» (openspec/changes/academic-year.md): ساختار قالب، نه رفتارِ
 * Alpine (در مرورگر راستی‌آزمایی شد؛ پروژه تست JS ندارد).
 */
class AcademicYearPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("academic-year.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void showsTheCurrentYearOrAnEmptyStateAndASelectOfYears() {
        String html = render();

        assertThat(html).contains("id=\"academicYearCurrent\"").contains("id=\"academicYearNone\"");
        assertThat(html).contains("هنوز سال تحصیلی‌ای برای این مدرسه انتخاب نشده است.");
        assertThat(html).contains("id=\"academicYearSelect\"").contains("x-for=\"year in years\"");
        assertThat(html).contains("/js/academic-year.js").contains("/js/jalali.js");
    }

    @Test
    void theYearBoxIsShownOnlyAfterASuccessfulLoad() {
        String html = render();

        // با شکست بارگذاری (مثلاً بعد از عوض‌کردن مدرسه) وضعیتِ مدرسه‌ی قبلی یا
        // «سالی انتخاب نشده» نشان داده نمی‌شود
        assertThat(html).contains("x-show=\"!loading && loaded\"");
    }

    @Test
    void closedYearsAreVisibleButNotSelectable() {
        String html = render();

        assertThat(html).contains(":disabled=\"!year.open\"").contains("(هنوز باز نشده)");
    }

    @Test
    void explainsThatChoosingDoesNotTouchDataAndThatTheAdminSetsStartAndEnd() {
        // متنِ راهنما در قالب چند خط است؛ فاصله‌ها یکی می‌شوند تا شکستنِ خط اهمیتی نداشته باشد
        String html = render().replaceAll("\\s+", " ");

        assertThat(html).contains("شروع و پایانِ هر سال با انتخابِ شما تعیین می‌شود")
                .contains("هیچ داده‌ای حذف یا تغییر نمی‌کند");
    }

    @Test
    void theSaveButtonIsDisabledUnlessAnotherYearIsSelected() {
        String html = render();

        assertThat(html).contains(":disabled=\"!canSave()\"");
    }
}
