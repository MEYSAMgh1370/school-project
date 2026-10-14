package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * صفحه‌ی «سابقه‌ی تغییرات مالی» بدون Spring و Docker: فیلترها (با برچسب)، جدول با جزئیاتِ بازشدنی، صفحه‌بندی،
 * حالت خالی، فقط‌خواندنی بودن، و ترتیبِ اسکریپت‌ها. رفتار داده‌محورِ سمتِ کلاینت در finance-audit-logic.test.js
 * قفل شده است.
 */
class FinanceAuditPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر مالی",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("FINANCE_ADMIN"))));

    private String render() {
        StringOutput output = new StringOutput();
        TemplateEngine.createPrecompiled(ContentType.Html).render("finance-audit.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void everyFilterControlHasALabelAndTheDatesAreTextInputsLikeDateInput() {
        String html = render();

        assertThat(html).contains("for=\"auditAction\"").contains("id=\"auditAction\"")
                .contains("for=\"auditFrom\"").contains("id=\"auditFrom\"")
                .contains("for=\"auditTo\"").contains("id=\"auditTo\"")
                .contains("for=\"auditInvoice\"").contains("id=\"auditInvoice\"");
        // تاریخ Jalali تایپی (DateInput) و نه date input مرورگر؛ ارقام روی سیم ASCII می‌شود
        assertThat(html).contains(":placeholder=\"exampleDate()\"").doesNotContain("type=\"date\"");
        assertThat(html).contains("اعمال فیلتر").contains("پاک کردن فیلترها").contains("@submit.prevent=\"apply()\"");
        assertThat(html).contains("help is-danger").contains("x-show=\"errors.from\"");
    }

    @Test
    void eachEntryIsACardWithTheDetailsToggleFirstAndAnExpandableDetailBlock() {
        String html = render();

        assertThat(html).contains("data-audit-card").contains("class=\"box\"");
        assertThat(html).doesNotContain("<table").doesNotContain("table-container");
        // دکمه‌ی «جزئیات» اولین عنصرِ کارت (سمتِ شروعِ RTL) است، پیش از زمان و مجری
        assertThat(html.indexOf("@click=\"toggle(item)\"")).isLessThan(html.indexOf("actor(item)"));
        assertThat(html).contains("دانش‌آموز:").contains("item.amount != null");
        assertThat(html).contains("data-audit-row").contains("data-audit-detail")
                .contains(":aria-expanded=\"isExpanded(item) ? 'true' : 'false'\"").contains("@click=\"toggle(item)\"");
        // اقساط: «قبل» و «بعد» زیر هم (سطرهای چیده‌شده)؛ بقیه کلید/مقدار
        assertThat(html).contains("'قبل'").contains("'بعد'").contains("dueLabel(row)")
                .contains("x-for=\"entry in detailEntries(item)\"");
    }

    @Test
    void displayGoesThroughTheFormattersAndPaginationIsServerSide() {
        String html = render();

        assertThat(html).contains("whenDate(item)").contains("whenTime(item)").contains("amountText(item)")
                .contains("fa(item.invoiceId)");
        assertThat(html).contains("id=\"financeAuditPagination\"").contains("@click=\"goTo(page - 1)\"")
                .contains("@click=\"goTo(page + 1)\"").contains("pagination-previous").contains("pagination-next");
    }

    @Test
    void theEmptyStateTeachesAndThePageIsReadOnly() {
        // `&&` در پارامترِ show از partial escape می‌شود؛ مرورگر قبل از Alpine دوباره decode می‌کند
        String html = HtmlUtils.htmlUnescape(render());

        assertThat(html).contains("در این بازه تغییر مالی‌ای ثبت نشده است. هر پرداخت، تخفیف و تغییر اقساط اینجا ثبت می‌شود.");
        assertThat(html).contains("x-show=\"!loading && !error && items.length === 0\"").contains("x-if=\"!blocked\"");
        // هیچ فرمِ نوشتنی در صفحه نیست: تنها فرمِ محتوا فیلتر است (فقط‌خواندنی بودنِ اسکریپت در finance-audit-logic.test.js)
        assertThat(html.split("id=\"financeAuditFilters\"", -1).length - 1).isEqualTo(1);
        assertThat(html).doesNotContain("@submit.prevent=\"save").doesNotContain("@submit.prevent=\"create");
    }

    @Test
    void scriptsLoadInDependencyOrderAndTheLogicComesBeforeThePage() {
        String html = render();

        int jalali = html.indexOf("/js/jalali.js");
        int calendar = html.indexOf("/js/calendar.js");
        int logic = html.indexOf("/js/finance-audit-logic.js");
        int page = html.indexOf("/js/finance-audit.js");

        assertThat(jalali).isPositive();
        assertThat(jalali).isLessThan(calendar);
        assertThat(html).contains("/js/money.js").contains("/js/digits.js").contains("/js/date-input.js");
        assertThat(logic).isPositive();
        assertThat(logic).isLessThan(page);
        assertThat(html).contains("x-data=\"financeAuditPage()\"");
    }
}
