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
    void theCourseSelectIsDisabledUntilAClassIsChosenAndReloadsALoadedTable() {
        String html = render("grades.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains(":disabled=\"!filter.classId\"");
        assertThat(html).contains("@change=\"if (loaded) load()\"");
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
}
