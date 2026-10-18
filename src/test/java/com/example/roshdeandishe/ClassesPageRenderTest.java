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
 * صفحه‌ی «کلاس‌ها» بدون انتخابگرِ سال: کلاس‌ها در سالِ جاریِ مدرسه ساخته و فهرست می‌شوند (سمت سرور)؛ سالِ هر کلاس
 * فقط به‌صورت برچسب روی ردیف می‌ماند. بدون Spring و Docker.
 */
class ClassesPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private String render() {
        StringOutput output = new StringOutput();
        TemplateEngine.createPrecompiled(ContentType.Html).render("classes.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void thereIsNoYearSelectInTheCreateFormOrAboveTheList() {
        String html = render();

        assertThat(html).doesNotContain("classYear").doesNotContain("classYearFilter")
                .doesNotContain("form.academicYear").doesNotContain("yearFilter")
                .doesNotContain("x-for=\"year in years\"");
    }

    @Test
    void eachClassRowStillShowsItsYearLabel() {
        assertThat(render()).contains("x-text=\"yearLabel(classItem.academicYear)\"");
    }
}
