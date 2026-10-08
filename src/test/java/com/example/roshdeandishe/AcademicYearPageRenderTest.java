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
    void explainsThatChoosingDoesNotTouchDataAndThatYearsAreAddedAutomatically() {
        // متنِ راهنما در قالب چند خط است؛ فاصله‌ها یکی می‌شوند تا شکستنِ خط اهمیتی نداشته باشد
        String html = render().replaceAll("\\s+", " ");

        assertThat(html).contains("شروع و پایانِ هر سال با انتخابِ شما تعیین می‌شود")
                .contains("هیچ داده‌ای حذف یا تغییر نمی‌کند")
                .contains("خودکار")
                .doesNotContain("هنوز باز نشده");
    }

    @Test
    void theSaveButtonIsDisabledOnlyWhileSaving() {
        String html = render();

        // دکمه برای ورودیِ ناقص فعال می‌ماند و خودِ ذخیره توضیح می‌دهد (DESIGN.md §6)
        assertThat(html).contains(":disabled=\"saving\"").doesNotContain(":disabled=\"!canSave()\"");
    }

    @Test
    void theFirstChoiceWarningIsShownOnlyWhileTheSchoolHasNoCurrentYear() {
        StringOutput output = new StringOutput();
        TemplateEngine.createPrecompiled(ContentType.Html).render("academic-year.jte", Map.of("navbar", NAVBAR), output);
        String html = output.toString();

        // همان شرطِ پیامِ «هنوز سالی انتخاب نشده»؛ متنِ هشدار می‌گوید چه چیزی از غیرِ مدیر پنهان می‌شود
        assertThat(html).contains("id=\"academicYearFirstChoiceWarning\"").contains("x-show=\"!current\"");
        assertThat(html).contains("معلم، دانش‌آموز و ولی فقط اطلاعاتِ همان سال را می‌بینند")
                .contains("کارنامه‌ی سال‌های قبل را مدیر تحویل می‌دهد");
    }

    @Test
    void offersAttributionOfExistingDataAndTheAutomaticPromotionSetting() {
        String html = render().replaceAll("\\s+", " ");

        // بنرِ داده‌ی انتساب‌نشده فقط وقتی سالِ جاری هست و چیزی مانده؛ هیچ سالی پیش‌فرض نیست (فقط گزینه‌ی راهنما)
        assertThat(html).contains("id=\"academicYearUnattributed\"").contains("x-show=\"hasUnattributed()\"")
                .contains("x-for=\"year in attributableYears\"").contains("id=\"academicYearAttributeButton\"")
                .contains(":disabled=\"attributing\"");
        // ارتقای خودکار: انتخابگرِ روشن/خاموش و لینک به صفحه‌ی دستی
        assertThat(html).contains("id=\"academicYearAutoPromotionToggle\"").contains("askAutoPromotion($event)")
                .contains("href=\"/promotion\"").contains("آخرین پایه‌ی مدرسه فارغ‌التحصیل می‌شود");
    }
}
