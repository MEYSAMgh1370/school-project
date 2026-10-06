package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavCatalog;
import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «غیبت‌های من» (/my-attendance، openspec/changes/attendance.md «Excuses») بدون Spring و Docker: ساختار صفحه،
 * اقدامِ اصلی در هدر، حالت‌های خالی/بارگذاری/خطا/اعتبارسنجی، modalِ عذر، لینکِ ناوبار و نبودنِ ویژگیِ
 * فیزیکیِ راست/چپ یا دیالوگِ مرورگر. رفتارِ Alpine در مرورگر راستی‌آزمایی می‌شود؛ پروژه تست JS ندارد.
 */
class MyAttendancePageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("والد نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("PARENT"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("my-attendance.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = MyAttendancePageRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void thePageHasTheRunButtonAsItsPrimaryActionInTheHeaderBeforeTheFilters() {
        String html = render();

        assertThat(html).contains("x-data=\"myAttendancePage()\"", "<title>غیبت‌های من");
        assertThat(html).contains("id=\"myAttendanceRun\"").contains("@click=\"load\">نمایش</button>");
        assertThat(html).contains(":class=\"{ 'is-loading': loading }\"");
        // اقدامِ اصلی در inline-end (level-right پس از عنوانِ level-left) و پیش از فیلترها
        assertThat(html.indexOf("level-left")).isLessThan(html.indexOf("level-right"));
        assertThat(html.indexOf("level-right")).isLessThan(html.indexOf("id=\"myAttendanceRun\""));
        assertThat(html.indexOf("id=\"myAttendanceRun\"")).isLessThan(html.indexOf("id=\"myAttFrom\""));
        // سرصفحه + «ارسال عذر» داخل modal (یک modal در هر لحظه)
        assertThat(count(html, "button is-link")).isEqualTo(2);
    }

    @Test
    void theRangeIsTwoTypedJalaliDateInputsWithInlineErrors() {
        String html = render();

        assertThat(html).contains("id=\"myAttFrom\"", "id=\"myAttTo\"", "x-model=\"fromText\"", "x-model=\"toText\"", "dir=\"ltr\"");
        assertThat(html).contains("x-show=\"fromError\"", "x-show=\"toError\"", "بازه حداکثر ۳۶۶ روز است");
    }

    @Test
    void eachStudentGetsATableWithDayStatusExcuseAndTheOwnTag() {
        String html = render();

        assertThat(html).contains("id=\"myAttendanceList\"", "x-for=\"student in students\"", "x-for=\"day in student.days\"");
        assertThat(html).contains(">خودم</span>", "x-show=\"student.own\"");
        assertThat(html).contains(">روز</th>", ">وضعیت</th>", ">عذر</th>");
        assertThat(html).contains("x-text=\"statusLabel(day.status)\"", ">موجه</span>", "x-show=\"day.excused\"");
        assertThat(html).contains("x-text=\"excuseLabel(day.excuse.status)\"", "x-text=\"day.excuse.note\"");
    }

    @Test
    void theExcuseButtonIsDrivenByStateAndOpensTheSingleModalWithTheNoteForm() {
        String html = render();

        assertThat(html).contains("x-show=\"canExcuse(student, day)\"", "@click=\"openExcuse(student, day)\"",
                "x-text=\"excuseActionLabel(day)\"");
        assertThat(html).contains("x-ref=\"excuseModal\"", "aria-labelledby=\"myAttExcuseTitle\"",
                "window.ModalFocus.trap($event, $el)", "/js/modal-focus.js");
        assertThat(html).contains("id=\"myAttNote\"", "<textarea", "x-model=\"excuse.note\"", "x-show=\"excuse.noteError\"",
                "x-show=\"excuse.error\"", "id=\"myAttNoteCounter\"");
        assertThat(html).contains("id=\"myAttExcuseSubmit\"").contains(">ارسال عذر</button>");
        assertThat(html).contains(":class=\"{ 'is-loading': saving }\"");
        // یک modal بیشتر نیست (modal در modal صفحه است)
        assertThat(count(html, "class=\"modal\"")).isEqualTo(1);
    }

    @Test
    void allFourStatesAreReachable() {
        String html = render();

        // خالی: راه را نشان می‌دهد
        assertThat(html).contains("در این بازه غیبت یا تأخیری ثبت نشده است").contains("بازه را بزرگ‌تر کنید");
        assertThat(HtmlUtils.htmlUnescape(html)).contains("loaded && !loading && !hasAnyDay()");
        // بارگذاری
        assertThat(html).contains("در حال بارگذاری");
        // خطا و پیامِ موفقیتِ مشترک (school-scope) و بستنِ بدنه با blocked
        assertThat(html).contains("x-show=\"error\"", "x-show=\"notice\"", "x-show=\"!blocked\"");
        // اعتبارسنجی: کنارِ فیلد
        assertThat(html).contains("is-danger", "help is-danger");
        assertThat(html).containsPattern("<template x-if=\"schoolId\">");
    }

    @Test
    void noControlIsHiddenByRoleOrPermission() {
        String html = render();
        String body = html.substring(html.indexOf("id=\"main-content\""));

        assertThat(body).doesNotContain("$store.nav").doesNotContain("selectedRole").doesNotContain("permissions.includes");
    }

    @Test
    void theScriptsLoadInOrder() {
        String html = render();

        assertThat(html).contains("/js/date-input.js", "/js/school-api.js", "/js/my-attendance.js");
        assertThat(html.indexOf("/js/calendar.js")).isLessThan(html.indexOf("/js/date-input.js"));
        assertThat(html.indexOf("/js/school-api.js")).isLessThan(html.indexOf("/js/my-attendance.js"));
    }

    @Test
    void theScriptTalksToTheMeEndpointsWithTheWireContractAndTheServerSideRules() throws IOException {
        String script = resource("/static/js/my-attendance.js");

        assertThat(script).contains("/api/me/schools/${this.schoolId}/attendance?from=${range.from}&to=${range.to}",
                "/api/me/schools/${schoolId}/attendance/excuses", "{ studentId: form.studentId, day: form.day, note }");
        assertThat(script).contains("'عذر ارسال شد.'", "'ارسال عذر'", "'ویرایش عذر'", "'در انتظار بررسی'", "'تأیید شد'", "'رد شد'");
        // دکمه‌ی عذر فقط روی ردیفِ فرزند، فقط غایب، و فقط تا پیش از تصمیم
        assertThat(script).contains("!student.own && day.status === 'ABSENT'", "day.excuse.status === 'PENDING'");
        assertThat(script).contains("MAX_DAYS = 366", "MAX_NOTE = 500");
    }

    @Test
    void theNavLinkAndTheShortcutAreForStudentsAndParentsOnly() {
        NavCatalog.Link link = NavCatalog.groups().stream().flatMap(g -> g.links().stream())
                .filter(l -> l.href().equals("/my-attendance")).findFirst().orElseThrow();

        assertThat(link.label()).isEqualTo("غیبت‌های من");
        assertThat(link.roles()).containsExactlyInAnyOrder("STUDENT", "PARENT");
        assertThat(link.shortcutRoles()).containsExactlyInAnyOrder("STUDENT", "PARENT");
        assertThat(link.permissions()).isEmpty();
        assertThat(NavCatalog.groups().stream().filter(g -> g.id().equals("education")).findFirst().orElseThrow().links())
                .contains(link);
        assertThat(NavCatalog.shortcuts()).contains(link);
    }

    @Test
    void noPhysicalLeftRightAndNoBrowserDialogs() throws IOException {
        assertThat(render())
                .doesNotContainPattern("(?i)(margin|padding|border)-(left|right)")
                .doesNotContainPattern("(?i)text-align:\\s*(left|right)")
                .doesNotContain("is-pulled-left").doesNotContain("is-pulled-right");
        String script = resource("/static/js/my-attendance.js");
        assertThat(script).doesNotContainPattern("(?i)\\b(margin|padding|border|text-align)-?(left|right)\\b")
                .doesNotContainPattern("\\balert\\(")
                .doesNotContainPattern("window\\.confirm\\(")
                .doesNotContainPattern("(?<![.\\w])confirm\\(");
    }
}
