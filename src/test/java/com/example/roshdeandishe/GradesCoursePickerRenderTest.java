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
 * انتخابگر «درس» در دفتر نمرات و ستون «درس» در «نمره‌های من»
 * (openspec/changes/grades-by-course.md). فقط ساختار قالب‌ها را می‌سنجد -
 * رفتار Alpine (بارگذاری درس‌ها با عوض‌شدن کلاس، بارگذاری مجدد با عوض‌شدن
 * درس) در مرورگر راستی‌آزمایی شد؛ پروژه تست JS ندارد.
 */
class GradesCoursePickerRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("معلم نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("TEACHER"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String template, Map<String, Object> params) {
        StringOutput output = new StringOutput();
        engine.render(template, params, output);
        return output.toString();
    }

    @Test
    void gradesPageHasACourseSelectWhoseDefaultIsTheOverallGrade() {
        String html = render("grades.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("<label class=\"label\" for=\"gradeCourse\">درس</label>");
        assertThat(html).contains("id=\"gradeCourse\"").contains("x-model=\"filter.courseId\"");
        // گزینه‌ی پیش‌فرض خالی = «نمره‌ی کل» (رفتار پیش از نمره‌ی هر درس)
        assertThat(html).contains("<option value=\"\">نمره‌ی کل</option>");
        assertThat(html).contains("x-for=\"course in courses\"");
    }

    @Test
    void theCourseSelectIsDisabledUntilAClassIsChosen() {
        String html = render("grades.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains(":disabled=\"!filter.classId\"");
        // بارگذاریِ دوباره‌ی جدول با تغییرِ درس از $watch در JS است (با حفاظت از نمره‌ی ثبت‌نشده)
    }

    @Test
    void anEmptyCatalogForTheGradeLevelIsExplainedNotSilent() {
        String html = render("grades.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("id=\"gradeCourseHelp\"")
                .contains("filter.classId && courses.length === 0")
                .contains("برای پایه‌ی این کلاس هنوز درسی تعریف نشده است؛ فقط «نمره‌ی کل» قابل ثبت است.");
        assertThat(html).contains("aria-describedby=\"gradeCourseHelp\"");
    }

    @Test
    void theTableShowsWhichCourseTheRowsBelongTo() {
        String html = render("grades.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("id=\"gradeCourseLabel\"").contains("x-text=\"courseLabel()\"");
    }

    @Test
    void myGradesShowsACourseColumnWithOverallAsTheFallback() {
        String html = render("my-grades.jte", Map.of("navbar", NAVBAR, "wards", List.of()));

        assertThat(html).contains("<th scope=\"col\">درس</th>");
        assertThat(html).contains("x-text=\"grade.courseName || 'نمره‌ی کل'\"");
        // ستون درس بین نوبت و نمره است
        assertThat(html.indexOf("<th scope=\"col\">نوبت</th>"))
                .isLessThan(html.indexOf("<th scope=\"col\">درس</th>"));
        assertThat(html.indexOf("<th scope=\"col\">درس</th>"))
                .isLessThan(html.indexOf("<th scope=\"col\">نمره</th>"));
    }

    @Test
    void theYearIsASelectOfListedYearsWithATextFallbackWhenThereAreNone() {
        String html = render("grades.jte", Map.of("navbar", NAVBAR));

        // با فهرستِ سال‌ها: select؛ بدونِ هیچ سالی: فیلدِ متنی (هر دو به filter.academicYear وصل‌اند)
        assertThat(html).contains("id=\"gradeYear\"").contains("x-for=\"year in years\"");
        assertThat(html).contains("id=\"gradeYearText\"").contains("x-show=\"years.length === 0\"");
        assertThat(html).contains("<label class=\"label\" for=\"gradeYear\">سال تحصیلی</label>");
    }

    // ورود سریعِ نمرات (DESIGN.md §5a): ساختارِ قالب. رفتارِ Alpine در مرورگر راستی‌آزمایی می‌شود
    // (پروژه تست JS ندارد)؛ اینجا فقط چیزهایی قفل می‌شوند که با تغییرِ قالب بی‌صدا می‌شکنند.
    @Test
    void quickEntryHasChipsFocusModeAndOneFilledPrimaryActionInEachView() {
        String html = render("grades.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("/css/grades.css");
        // chip ها: aria-pressed و شمارش
        assertThat(html).contains("x-for=\"item in chips()\"").contains(":aria-pressed=");
        // نمای تمرکز: نامِ مقصد روی دکمه‌ی اصلی، و نوار روی کیبورد
        assertThat(html).contains("x-text=\"nextLabel()\"").contains("keyboardInset");
        // خلاصه‌ی مرور برای صفحه‌خوان
        assertThat(html).contains("role=\"alert\" x-show=\"reviewSummary\"");
        // نمره ورودیِ متنی با inputmode اعشاری است، نه number (رقم‌های فارسی را رد می‌کرد)
        assertThat(html).contains("inputmode=\"decimal\"").doesNotContain("type=\"number\"");
        // یک دکمه‌ی پُر در هر نما: نوار فهرست و نوار تمرکز (هر کدام «ثبت همه»/«اصلاح»/«بعدی»).
        // دکمه‌ی «بارگذاری» پیش از بارگذاری پُر است و بعد فرعی می‌شود، پس با کلاس پویا نوشته شده و اینجا نمی‌آید.
        assertThat(html.split("class=\"button is-primary\"", -1).length - 1).isEqualTo(2);
        // «ثبت» هر ردیف فرعی است
        assertThat(html).doesNotContain("button is-small is-link").doesNotContain("button is-link");
        // برچسب سطح ارزشیابی از منبع مشترک می‌آید، نه کپی در هر صفحه
        assertThat(html).contains("/js/grade-labels.js");
        // تغییر درس با نمره‌ی ثبت‌نشده از watch گرفته می‌شود، نه از @change که جدول را بی‌صدا عوض می‌کرد
        assertThat(html).doesNotContain("@change=\"if (loaded || loading) load()\"");
    }
}
