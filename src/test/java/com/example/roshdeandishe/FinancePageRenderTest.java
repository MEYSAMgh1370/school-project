package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * صفحه‌ی مالی به‌تفکیک سال (academic-year.md فاز ۲) بدون Spring و Docker: انتخاب سال، هشدارِ نسبت دادنِ اطلاعاتِ
 * بدون سال و برچسبِ «بدون سال». رفتارِ سمتِ کلاینت در مرورگر با APIهای شبیه‌سازی‌شده راستی‌آزمایی شد؛ پروژه تست JS ندارد.
 */
class FinancePageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر مالی",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("FINANCE_ADMIN"))));

    private String render() {
        StringOutput output = new StringOutput();
        TemplateEngine.createPrecompiled(ContentType.Html).render("finance.jte", Map.of("navbar", NAVBAR), output);
        String html = output.toString();
        // برای راستی‌آزمایی در مرورگر (فقط وقتی مسیرش داده شده)
        String dump = System.getProperty("finance.page.dump");
        if (dump != null) {
            try {
                Files.writeString(Path.of(dump), html);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return html;
    }

    @Test
    void theYearSelectIsHiddenUntilThereIsAListOfYears() {
        String html = render();

        assertThat(html).contains("id=\"financeYear\"").contains("x-model=\"year\"").contains("@change=\"onYearChange()\"");
        assertThat(html).contains("id=\"financeYearBox\"").contains("x-show=\"years.length > 0\"");
    }

    @Test
    void theUnattributedWarningOffersTheAttributionAndTheInvoiceRowsTagThem() {
        String html = render();

        assertThat(html).contains("id=\"financeUnattributedBanner\"").contains("x-show=\"hasUnattributed()\"");
        assertThat(html).contains("id=\"financeAttributeYear\"").contains("@click=\"attribute()\"");
        assertThat(html).contains("x-show=\"currentYear && !invoice.academicYear\"").contains(">بدون سال</span>");
        assertThat(html).contains("صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور");
    }
}
