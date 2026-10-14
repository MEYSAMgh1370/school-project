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
        assertThat(html).contains("id=\"financeAttributeYear\"").contains("@click=\"askAttribute()\"");
        assertThat(html).contains("x-show=\"currentYear && !invoice.academicYear\"").contains(">بدون سال</span>");
        assertThat(html).contains("صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور");
    }

    @Test
    void theInstallmentsSwitchAndItsScriptsAreOnThePage() {
        String html = render();

        assertThat(html).contains("id=\"financeInstallmentsSettingBox\"").contains("id=\"installmentsEnabled\"")
                .contains("setInstallmentsEnabled($event.target.checked, $event.target)")
                .contains("اقساط شهریه");
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
                .contains("!discountDeadlineValid()").contains("<th scope=\"col\">وضعیت</th>")
                .contains("discountStatusLabel(discount)").contains("data-pending-discount")
                .contains("در صورت عدم تسویه‌ی کامل ساقط می‌شود").contains("در مبلغ قابل پرداخت حساب نمی‌شوند");
        // اقساط: تعریف / تغییر تعداد / ویرایش با شاخص جمع
        assertThat(html).contains("id=\"installmentsOffHint\"").contains("تعریف اقساط").contains("تغییر تعداد اقساط")
                .contains("فقط اقساط پرداخت‌نشده جایگزین می‌شوند")
                .contains("@submit.prevent=\"generateInstallments(invoice)\"")
                .contains("ویرایش مبالغ و تاریخ‌ها").contains("جمع اقساط:").contains("باید برابر باشد با")
                .contains("canSaveEdit(invoice)").contains("saveEditing(invoice)")
                .contains("اقساط تعریف‌شده فقط‌خواندنی است");
        // هر ورودیِ ویرایشِ قسط برچسب دارد
        assertThat(html).contains("'installmentAmount-' + item.id").contains("'installmentDue-' + item.id");
        // قسطِ بدون سررسید: برچسبِ نمایش و راهنمای ورودیِ اختیاری، بدون صدا زدنِ formatDate روی null
        assertThat(html).contains("بدون سررسید").contains("FinanceLogic.isUndated(item)")
                .contains("اختیاری — تاریخ را بعداً هم می‌شود تعیین کرد")
                .contains("'installmentDueHelp-' + item.id");
    }
}
