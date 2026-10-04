package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.DashboardModel;
import com.example.roshdeandishe.web.model.MembershipRow;
import com.example.roshdeandishe.web.model.WardRow;
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
 * بخش «فرزندان من» داشبورد را مستقیم (بدون Spring/Docker) رندر می‌کند: وضعیت مالیِ
 * سه‌گانه، حالت خالی، غیبتِ بخش برای غیرِ والد، محدودشدن به نقشِ انتخاب‌شده، و
 * ترتیب بخش‌ها (دسترسی سریع ← فرزندان ← عضویت‌ها).
 */
class DashboardWardsRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مریم رضایی",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("PARENT"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(boolean parent, List<WardRow> wards) {
        DashboardModel model = new DashboardModel("مریم رضایی", "maryam@example.com", null,
                List.of(new MembershipRow(1L, "مدرسه‌ی نمونه", "PARENT", true)), List.of(), parent, wards);
        StringOutput output = new StringOutput();
        engine.render("dashboard.jte", Map.of("model", model, "navbar", NAVBAR), output);
        return output.toString();
    }

    private static WardRow ward(String name, int invoices, int overdue, int open) {
        return new WardRow(100L, 5L, name, "مدرسه‌ی نمونه", "پنجم", "الف", invoices, overdue, open);
    }

    @Test
    void showsNameSchoolClassAndSectionForEachWard() {
        String html = render(true, List.of(ward("آرین رضایی", 0, 0, 0), ward("نیکا رضایی", 0, 0, 0)));

        assertThat(html).contains("آرین رضایی", "نیکا رضایی", "مدرسه‌ی نمونه", "کلاس پنجم", "بخش الف");
        assertThat(html).contains("dashboard-wards-title");
    }

    @Test
    void eachWardLinksCarryStudentAndSchoolIdsInTheUrlNotInAClickHandler() {
        String html = HtmlUtils.htmlUnescape(render(true, List.of(ward("آرین رضایی", 0, 0, 0))));

        // مدرسه در خودِ URL است تا «باز کردن در تب جدید»/کلیک میانی هم درست کار کند
        assertThat(html).contains("href=\"/my-grades?student=100&school=5\"",
                "href=\"/my-invoices?student=100&school=5\"");
        assertThat(html).doesNotContain("rememberSchool");
        // نام فرزند نباید در URL برود
        assertThat(html).doesNotContain("student=آرین").doesNotContain("?name=").doesNotContain("&name=");
    }

    @Test
    void financeStateIsOverdueOpenSettledOrNoInvoices() {
        String overdue = render(true, List.of(ward("الف", 4, 2, 1)));
        assertThat(overdue).contains("tag is-danger").contains("فاکتور دیرکرد");
        assertThat(overdue).contains("Calendar.digits('2')");

        String open = render(true, List.of(ward("الف", 3, 0, 2)));
        assertThat(open).contains("tag is-warning").contains("فاکتور در انتظار پرداخت");
        assertThat(open).contains("Calendar.digits('2')").doesNotContain("فاکتور دیرکرد");

        String settled = render(true, List.of(ward("الف", 3, 0, 0)));
        assertThat(settled).contains("tag is-success").contains("همه‌ی فاکتورها تسویه‌شده");

        String none = render(true, List.of(ward("الف", 0, 0, 0)));
        assertThat(none).contains("فاکتوری صادر نشده").doesNotContain("tag is-danger");
    }

    @Test
    void aParentWithoutLinkedWardsSeesTheInvitationText() {
        String html = render(true, List.of());

        assertThat(html).contains("هنوز دانش‌آموزی به حساب شما لینک نشده است.");
        assertThat(html).contains("مدیر مدرسه می‌تواند از صفحه‌ی");
        assertThat(html).doesNotContain("app-ward");
    }

    @Test
    void theSectionIsAbsentForANonParent() {
        String html = render(false, List.of());

        assertThat(html).doesNotContain("dashboard-wards-title").doesNotContain("فرزندان من");
    }

    @Test
    void theSectionIsGatedByTheSelectedParentRoleAndHasItsOwnAlpineRoot() {
        String html = HtmlUtils.htmlUnescape(render(true, List.of(ward("الف", 0, 0, 0))));

        assertThat(html).containsPattern("<section[^>]*dashboard-wards-title[^>]* x-data[ >\\n]");
        assertThat(html).contains("x-show=\"['PARENT'].includes($store.nav.selectedRole)\"");
    }

    @Test
    void sectionsAreOrderedShortcutsWardsMemberships() {
        String html = render(true, List.of(ward("الف", 0, 0, 0)));

        int shortcuts = html.indexOf("dashboard-shortcuts-title");
        int wards = html.indexOf("dashboard-wards-title");
        int memberships = html.indexOf("<table");
        int phone = html.indexOf("phoneNumberField()");

        assertThat(shortcuts).isPositive();
        assertThat(shortcuts).isLessThan(wards);
        assertThat(wards).isLessThan(memberships);
        assertThat(memberships).isLessThan(phone);
    }

    @Test
    void jalaliHelperIsLoadedForTheDigitConversion() {
        assertThat(render(true, List.of())).contains("/js/jalali.js");
    }
}
