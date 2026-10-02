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
 * صفحه‌های «نمره‌های من» و «فاکتورهای من» را مستقیم (بدون Spring/Docker) رندر
 * می‌کند تا قراردادِ فیلترِ یک فرزند (?student=…) قفل شود: لیست و حالت خالی از
 * فهرستِ فیلترشده می‌آیند، و اعلانِ فیلتر راه برگشت («نمایش همه») دارد. رفتارِ
 * خودِ JS در مرورگر راستی‌آزمایی شد، نه اینجا (پروژه تست JS ندارد).
 */
class MyPagesStudentFilterRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مریم رضایی",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("PARENT"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String template) {
        StringOutput output = new StringOutput();
        engine.render(template, Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void gradesPageIteratesTheFilteredListAndOffersToShowAll() {
        String html = render("my-grades.jte");

        assertThat(html).contains("studentFilter !== null");
        assertThat(html).contains("x-for=\"grade in visibleGrades()\"");
        // متنِ ثابتِ template بدون escape نوشته می‌شود (برخلاف پارامترها)
        assertThat(html).contains("visibleGrades().length > 0").doesNotContain("!loading && grades.length > 0");
        assertThat(html).contains("href=\"/my-grades\"").contains("نمایش همه");
    }

    @Test
    void invoicesPageIteratesTheFilteredListAndOffersToShowAll() {
        String html = render("my-invoices.jte");

        assertThat(html).contains("studentFilter !== null");
        assertThat(html).contains("x-for=\"invoice in visibleInvoices()\"");
        assertThat(html).contains("visibleInvoices().length > 0").doesNotContain("!loading && invoices.length > 0");
        assertThat(html).contains("href=\"/my-invoices\"").contains("نمایش همه");
    }

    @Test
    void theEmptyStateFollowsTheFilteredListToo() {
        assertThat(render("my-grades.jte")).contains("visibleGrades().length === 0");
        assertThat(render("my-invoices.jte")).contains("visibleInvoices().length === 0");
    }
}
