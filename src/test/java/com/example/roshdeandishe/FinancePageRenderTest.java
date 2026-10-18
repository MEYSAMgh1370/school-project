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
 * صفحه‌ی مالیِ دانش‌آموز-محور (school-settings): دامنه‌ی «همه / فقط امسال / سال‌های پیش»، جست‌وجوی نام و پنل‌های فاکتور،
 * بدون Spring و Docker. سال، هشدار «بدون سال» و کلید اقساط به /settings رفته‌اند. رفتار سمت کلاینت را مرورگر نشان می‌دهد.
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
    void theStudentListHasScopeButtonsWithCountsAndNameSearchInputs() {
        String html = render();

        assertThat(html).contains("id=\"financeFirstName\"").contains("id=\"financeLastName\"").contains("id=\"financeSearch\"");
        assertThat(html).contains(">همه</span>").contains(">فقط امسال</span>").contains(">سال‌های پیش</span>");
        assertThat(html).contains("data-scope-chip").contains("fa(counts.all)").contains("fa(counts.current)").contains("fa(counts.previous)")
                .contains(":aria-pressed=\"scope === 'current' ? 'true' : 'false'\"");
        assertThat(html).contains("data-debt-chip").contains("بدهی پیشین").contains("مانده‌ی کل")
                .contains("loadMoreStudents()");
    }

    @Test
    void theYearSelectTheUnattributedBannerAndTheInstallmentsBoxMovedToSettings() {
        String html = render();

        assertThat(html).doesNotContain("id=\"financeYear\"").doesNotContain("financeYearBox")
                .doesNotContain("financeUnattributedBanner").doesNotContain("financeAttributeYear")
                .doesNotContain("financeInstallmentsSettingBox").doesNotContain("id=\"installmentsEnabled\"")
                .doesNotContain("onYearChange").doesNotContain("askAttribute");
    }

    @Test
    void theTuitionBoxAndTheBackfillButtonStayAndTargetTheCurrentYear() {
        String html = render();

        assertThat(html).contains("id=\"tuitionAmount\"").contains("@submit.prevent=\"setTuitionFee\"")
                .contains("ثبت شهریه");
        assertThat(html).contains("صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور").contains("@click=\"askBackfill\"");
        assertThat(html).contains("/js/finance-logic.js").contains("/js/date-input.js");
    }

    @Test
    void theInvoicePanelLinksToThatInvoicesAuditTrail() {
        String html = render();

        assertThat(html).contains("data-invoice-audit-link")
                .contains(":href=\"'/finance-audit?invoiceId=' + encodeURIComponent(invoice.id)\"")
                .contains(">سابقه‌ی این فاکتور</a>");
    }

    @Test
    void theInvoicePanelShowsTheSummaryTheDiscountFormAndTheInstallmentSections() {
        String html = render();

        // خلاصه بر پایه‌ی قابل‌پرداخت
        assertThat(html).contains("data-invoice-summary").contains("payable(invoice)").contains("remaining(invoice)")
                .contains("قابل پرداخت").contains("تخفیف:");
        // تخفیف: فرم با برچسب، راهنمای تومان به حروف و فهرست سابقه
        assertThat(html).contains("ثبت تخفیف").contains("@submit.prevent=\"applyDiscount(invoice)\"")
                .contains("'discountAmount-' + invoice.id").contains("Money.tomanWords(discountForm.amount)")
                .contains("x-for=\"discount in discounts\"");
        // تخفیف مشروط: چک‌باکس، تاریخ با برچسب و راهنما، ستون وضعیت و خطِ تخفیف‌های در انتظار
        assertThat(html).contains("تخفیف مشروط").contains("x-model=\"discountForm.conditional\"")
                .contains("'discountDeadline-' + invoice.id").contains("در صورت تسویه‌ی کامل تا تاریخ")
                .contains("اگر تا پایان این روز کل مبلغ قابل پرداخت تسویه نشود، تخفیف خودکار لغو می‌شود.")
                .contains("!discountDeadlineValid()").contains("data-discount-row").contains("data-payment-row")
                .doesNotContain("<table").doesNotContain("table-container")
                .contains("discountStatusLabel(discount)").contains("data-pending-discount")
                .contains("در صورت عدم تسویه‌ی کامل ساقط می‌شود").contains("در مبلغ قابل پرداخت حساب نمی‌شوند");
        // اقساط: تعریف / تغییر تعداد / ویرایش با شاخص جمع
        assertThat(html).contains("id=\"installmentsOffHint\"").contains("تعریف اقساط").contains("تغییر تعداد اقساط")
                .contains("فقط اقساط پرداخت‌نشده جایگزین می‌شوند")
                .contains("@submit.prevent=\"generateInstallments(invoice)\"")
                .contains("ویرایش مبالغ و تاریخ‌ها").contains("جمع اقساط:").contains("باید برابر باشد با")
                .contains("canSaveEdit(invoice)").contains("saveEditing(invoice)")
                .contains("اقساط تعریف‌شده فقط‌خواندنی است");
        // اقساط: فهرست انباشته (بدون جدول و اسکرول افقی)، نه جدول
        String installments = html.substring(html.indexOf("<h4 class=\"title is-6\">اقساط</h4>"));
        installments = installments.substring(0, installments.indexOf("@submit.prevent=\"generateInstallments(invoice)\""));
        assertThat(installments).contains("data-installment-row").doesNotContain("<table").doesNotContain("table-container");
        // هر ورودیِ ویرایشِ قسط برچسب دارد
        assertThat(html).contains("'installmentAmount-' + item.id").contains("'installmentDue-' + item.id");
        // قسطِ بدون سررسید: برچسبِ نمایش و راهنمای ورودیِ اختیاری، بدون صدا زدنِ formatDate روی null
        assertThat(html).contains("بدون سررسید").contains("FinanceLogic.isUndated(item)")
                .contains("اختیاری — تاریخ را بعداً هم می‌شود تعیین کرد")
                .contains("'installmentDueHelp-' + item.id");
    }
}
