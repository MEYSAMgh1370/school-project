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
 * فهرست ردیفیِ دانش‌آموزان (DESIGN.md §5a، الگوی دفتر نمرات): فقط ساختار قالب را می‌سنجد؛
 * جست‌وجو و مدیریت والدین در مرورگر راستی‌آزمایی شدند (پروژه تست JS ندارد).
 */
class StudentsPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("سارا احمدی",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("students.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void studentsAreListRowsWithASearchFieldNotATable() {
        String html = render();

        assertThat(html).contains("id=\"studentSearch\"").contains("x-model=\"filterText\"");
        assertThat(html).contains("x-for=\"student in visibleStudents()\"");
        assertThat(html).contains("class=\"app-list\"").contains("class=\"app-student-row\"");
        // هر دانش‌آموز ردیف است و جدولِ قدیمی دیگر نیست
        assertThat(html).doesNotContain("<table class=\"table is-fullwidth is-striped\">");
    }

    @Test
    void theGuardianPanelAndItsButtonKeepTheirBindings() {
        String html = render();

        assertThat(html).contains("@click=\"toggleGuardianPanel(student.studentId)\"");
        assertThat(html).contains("x-show=\"selectedStudentId\"");
        assertThat(html).contains("x-text=\"'والدین دانش‌آموز: ' + studentName(selectedStudentUserId())\"");
        assertThat(html).contains("@submit.prevent=\"linkGuardian\"");
    }
}
