package com.example.roshdeandishe;

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
 * قالب‌های حضور و غیاب و تقویم مدرسه (openspec/changes/attendance.md) بدون Spring و Docker: ساختار صفحه،
 * اقدامِ اصلی در هدر، حالت‌های خالی که راه را نشان می‌دهند، ورودی‌های تاریخ، modalها و نبودنِ ویژگیِ فیزیکیِ
 * راست/چپ. رفتارِ Alpine (بارگذاری، رقابتِ پاسخ‌ها، تبدیلِ جلالی) در مرورگر راستی‌آزمایی می‌شود؛ پروژه تست JS ندارد.
 */
class AttendancePagesRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String template) {
        StringOutput output = new StringOutput();
        engine.render(template, Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = AttendancePagesRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    // ------------------------------------------------------------------ /attendance

    @Test
    void theRecordingPageHasClassAndDayPickersAndTheRosterWithPerStudentControls() {
        String html = render("attendance.jte");

        assertThat(html).contains("x-data=\"attendancePage()\"");
        assertThat(html).contains("id=\"attClass\"", "id=\"attDay\"", "id=\"attToday\"", "id=\"attRoster\"");
        // حاضر (پیش‌فرض) / غایب / تأخیر، «موجه» فقط برای غایب، و یادداشتِ اختیاری
        assertThat(html).contains("x-for=\"status in statuses\"", "@click=\"setStatus(row, status.code)\"");
        assertThat(html).contains("x-model=\"row.excused\"", "موجه").contains("یادداشت (اختیاری)");
        assertThat(html).contains("x-show=\"row.status === 'ABSENT'\"", "x-show=\"row.status !== 'PRESENT'\"");
        // روز جلالیِ تایپ‌شده؛ ISO فقط در state/سیم (date-input.js) است، نه در فرم
        assertThat(html).contains("x-model=\"dayText\"", "dir=\"ltr\"");
    }

    @Test
    void thePrimaryActionIsTheOnlyLinkButtonAndSitsInTheHeaderBeforeTheForm() {
        String html = render("attendance.jte");

        assertThat(html).contains("id=\"attendanceSave\"").contains("ثبت حضور و غیاب</button>");
        assertThat(html).contains("@click=\"save\"").contains(":class=\"{ 'is-loading': saving }\"");
        assertThat(count(html, "button is-link")).isEqualTo(1);
        assertThat(html.indexOf("id=\"attendanceSave\"")).isLessThan(html.indexOf("id=\"attClass\""));
        // اقدامِ اصلی در inline-end (level-right، پس از عنوانِ level-left)
        assertThat(html.indexOf("level-left")).isLessThan(html.indexOf("level-right"));
        assertThat(html.indexOf("level-right")).isLessThan(html.indexOf("id=\"attendanceSave\""));
    }

    @Test
    void theRollStateIsAlwaysClear() {
        String html = render("attendance.jte");

        // گرفته شده / هنوز نه / روزِ غیردرسی / روزِ آینده
        assertThat(html).contains("id=\"attTaken\"", "id=\"attNotTaken\"", "id=\"attNotSchoolDay\"", "id=\"attFutureDay\"");
        assertThat(html).contains("حضور و غیابِ این کلاس در این روز ثبت شده است");
        assertThat(html).contains("هنوز ثبت نشده؛ تا غایب‌ها را علامت نزنید همه حاضر ثبت می‌شوند");
        assertThat(html).contains("روز درسیِ مدرسه نیست");
    }

    @Test
    void emptyStatesTeachWhatToDoNext() {
        String html = render("attendance.jte");

        assertThat(html).contains("کلاسی برای ثبت پیدا نشد").contains("href=\"/classes\"");
        assertThat(html).contains("در این کلاس هنوز دانش‌آموزی ثبت‌نام نشده است").contains("href=\"/students\"");
        assertThat(html).contains("در حال بارگذاری");
    }

    @Test
    void controlsAreNeverHiddenByPermissionAndErrorsGoToTheSharedNoticeArea() {
        String html = render("attendance.jte");

        // ناحیه‌ی خطا و پیامِ موفقیتِ مشترک (school-scope)
        assertThat(html).contains("x-show=\"error\"", "x-show=\"notice\"");
        // هیچ کنترلی به نقش/Permission وابسته نیست (design-page Step 3): ناوبار فقط پیش از بدنه‌ی صفحه می‌آید
        String body = html.substring(html.indexOf("id=\"main-content\""));
        assertThat(body).doesNotContain("$store.nav").doesNotContain("selectedRole").doesNotContain("permissions.includes");
        assertThat(html).containsPattern("<template x-if=\"schoolId\">");
    }

    @Test
    void theRecordingPageLoadsItsScriptsInOrderAndItsStyles() {
        String html = render("attendance.jte");

        assertThat(html).contains("/js/date-input.js", "/js/school-api.js", "/js/attendance.js", "/css/attendance.css");
        assertThat(html.indexOf("/js/calendar.js")).isLessThan(html.indexOf("/js/date-input.js"));
        assertThat(html.indexOf("/js/school-api.js")).isLessThan(html.indexOf("/js/attendance.js"));
    }

    // ------------------------------------------------------------------ /attendance/report

    @Test
    void theReportPageHasThreeTabsAndTheQueryButtonAsItsPrimaryAction() {
        String html = render("attendance-report.jte");

        assertThat(html).contains("x-data=\"attendanceReportPage()\"");
        assertThat(html).contains("id=\"attTabDay\"", "id=\"attTabRange\"", "id=\"attTabExcuses\"",
                "id=\"attPanelDay\"", "id=\"attPanelRange\"", "id=\"attPanelExcuses\"");
        assertThat(html).contains("غایبین روز", "گزارش بازه‌ای", "عذرخواهی‌ها");
        assertThat(html).contains("id=\"attendanceRun\"").contains("نمایش گزارش</button>");
        assertThat(count(html, "button is-link")).isEqualTo(1);
        assertThat(html.indexOf("id=\"attendanceRun\"")).isLessThan(html.indexOf("id=\"attTabDay\""));
        // تأیید/ردِ عذر می‌نویسد: ناحیه‌ی پیامِ موفقیتِ مشترک (کامپوننت `notice` دارد)
        assertThat(html).contains("x-show=\"notice\"");
    }

    @Test
    void theExcusesTabHasAStatusFilterOptionalDatesAndTheDecisionTable() {
        String html = render("attendance-report.jte");

        assertThat(html).contains("id=\"attExcStatus\"", "x-model=\"excStatus\"", "value=\"PENDING\"", "value=\"APPROVED\"",
                "value=\"REJECTED\"", "در انتظار", "تأیید شده", "رد شده");
        assertThat(html).contains("id=\"attExcFrom\"", "id=\"attExcTo\"", "x-model=\"excFromText\"", "x-model=\"excToText\"");
        assertThat(html).contains("id=\"attExcTable\"");
        assertThat(html).contains(">دانش‌آموز</th>", ">روزِ غیبت</th>", ">یادداشت</th>", ">وضعیت</th>");
        // «تأیید» و «رد» مستقیم و بدونِ modal، هر دو روی ردیفِ در انتظار، با is-loading؛ هیچ modalی در صفحه نیست
        assertThat(html).contains("@click=\"decide(row, 'APPROVED')\">تأیید</button>", "@click=\"decide(row, 'REJECTED')\">رد</button>");
        assertThat(html).contains("x-if=\"row.status === 'PENDING'\"", "'is-loading': isDeciding(row, 'APPROVED')");
        assertThat(html).doesNotContain("class=\"modal\"");
        // هنوز تنها یک دکمه‌ی اصلی (نمایش گزارش)
        assertThat(count(html, "button is-link")).isEqualTo(1);
        // حالت خالیِ هر وضعیت از state می‌آید و صفحه‌بندیِ ۲۵ ردیفی دارد
        assertThat(html).contains("id=\"attExcEmpty\"");
        assertThat(HtmlUtils.htmlUnescape(html)).contains("excusesLoaded && !excusesLoading && excuses.length === 0");
        assertThat(html).contains("id=\"attExcPagination\"", "x-show=\"excPaginated()\"");
    }

    @Test
    void theExcusesTabScriptSendsTheDecisionAndShowsTheSuccessWording() throws IOException {
        String script = resource("/static/js/attendance-report.js");

        assertThat(script).contains("/excuses/${row.id}/decision", "{ decision }", "'عذر تأیید شد.'", "'عذر رد شد.'",
                "عذر در انتظاری وجود ندارد.");
        assertThat(script).contains("status=${encodeURIComponent(status)}");
    }

    @Test
    void theDayTabShowsTheAdminFigureAndAClassCardWithTotals() {
        String html = render("attendance-report.jte");

        assertThat(html).contains("id=\"attSummaryBox\"", "id=\"attSummaryFigure\"", "id=\"attSummaryEmpty\"", "id=\"attSummaryClosed\"");
        assertThat(html).contains("class=\"progress is-primary\"").contains("درصد حضور");
        assertThat(html).contains("هنوز حضور و غیابی ثبت نشده است");
        assertThat(html).contains("id=\"attClassesWithoutRoll\"").contains("کلاس بدون ثبت");
        // کارتِ کلاس: ثبت شد / ثبت نشده، جمع‌ها، موجه
        assertThat(html).contains("id=\"attCards\"", "app-att-class", "ثبت شد", "ثبت نشده", "همه حاضر بودند.");
        assertThat(html).contains("غایب:", "موجه:", "تأخیر:");
        // روزِ غیردرسی پیامِ خودش را دارد و به تقویمِ مدرسه می‌برد
        assertThat(html).contains("روز درسیِ مدرسه نیست").contains("href=\"/calendar\"");
    }

    @Test
    void theRangeTabHasStartEndClassAndNameFiltersAndTheFiveColumnTable() {
        String html = render("attendance-report.jte");

        assertThat(html).contains("id=\"attFrom\"", "id=\"attTo\"", "id=\"attClassFilter\"", "id=\"attName\"");
        assertThat(html).contains("x-model=\"fromText\"", "x-model=\"toText\"");
        assertThat(html).contains("همه‌ی کلاس‌ها", "نام دانش‌آموز");
        assertThat(html).contains("id=\"attRangeTable\"");
        assertThat(html).contains(">دانش‌آموز</th>", ">کلاس</th>", ">روزهای غیبت</th>", ">روزهای تأخیر</th>", ">روزهای حضور</th>");
        // صفحه‌بندیِ سمت‌مرورگر برای بیش از ۲۵ ردیف
        assertThat(html).contains("id=\"attPagination\"", "x-show=\"paginated()\"");
    }

    @Test
    void anEmptyRangeSaysSoAndTeachesWhatToChange() {
        String html = render("attendance-report.jte");

        assertThat(html).contains("در این بازه حضور و غیابی ثبت نشده است");
        assertThat(html).contains("بازه را بزرگ‌تر کنید").contains("href=\"/attendance\"");
        assertThat(HtmlUtils.htmlUnescape(html)).contains("rangeLoaded && !rangeLoading && rows.length === 0");
    }

    @Test
    void theReportPageLoadsItsScriptsAndStyles() {
        // (بخشِ عذرخواهی‌ها هم از همین فایل‌ها می‌خواند و فایلِ تازه‌ای نمی‌خواهد)
        String html = render("attendance-report.jte");

        assertThat(html).contains("/js/date-input.js", "/js/school-api.js", "/js/attendance-report.js", "/css/attendance.css");
        assertThat(html.indexOf("/js/school-api.js")).isLessThan(html.indexOf("/js/attendance-report.js"));
    }

    // ------------------------------------------------------------------ /calendar

    @Test
    void theCalendarPageListsClosedDaysByMonthWithTheAddActionInTheHeader() {
        String html = render("calendar.jte");

        assertThat(html).contains("x-data=\"schoolCalendarPage()\"");
        assertThat(html).contains("id=\"calendarAdd\"").contains("افزودن روز تعطیل</button>");
        assertThat(count(html, "button is-link")).isEqualTo(2); // سرصفحه + «افزودن» داخل modal (یک modal در هر لحظه)
        assertThat(html.indexOf("id=\"calendarAdd\"")).isLessThan(html.indexOf("id=\"calendarMonth\""));
        assertThat(html).contains("id=\"calendarPrev\"", "id=\"calendarNext\"", "id=\"calendarMonth\"", "id=\"calendarYear\"");
        assertThat(html).contains("id=\"calendarTable\"", "id=\"calendarMonthTitle\"");
    }

    @Test
    void theCalendarEmptyStateTeachesAndTheDateInputIsATypedJalaliField() {
        String html = render("calendar.jte");

        assertThat(html).contains("در این ماه روز تعطیلی ثبت نشده است").contains("با «افزودن روز تعطیل»");
        assertThat(html).contains("id=\"calendarDate\"", "x-model=\"add.date\"", "dir=\"ltr\"");
        assertThat(html).contains("ارقام فارسی هم پذیرفته می‌شود");
        assertThat(html).contains("id=\"calendarTitleInput\"", "maxlength=\"128\"");
        // اعتبارسنجی: کنارِ فیلد و خلاصه در modal
        assertThat(html).contains("x-show=\"add.dateError\"", "x-show=\"add.titleError\"", "x-show=\"add.error\"");
    }

    @Test
    void removingNeedsAConfirmModalAndOnlyOneModalIsOpenAtATime() {
        String html = render("calendar.jte");

        assertThat(html).contains("x-ref=\"addModal\"", "x-ref=\"confirmModal\"");
        assertThat(html).contains("aria-labelledby=\"calendarAddTitle\"", "aria-labelledby=\"calendarConfirmTitle\"");
        assertThat(html).contains("id=\"calendarConfirmDelete\"").contains("حذف قطعی");
        assertThat(html).contains("window.ModalFocus.trap($event, $el)");
        assertThat(html).contains("/js/modal-focus.js", "/js/school-calendar.js", "/js/date-input.js");
    }

    // ------------------------------------------------------------------ RTL

    @Test
    void noPageOrStyleUsesPhysicalLeftRight() throws IOException {
        for (String template : List.of("attendance.jte", "attendance-report.jte", "calendar.jte", "my-attendance.jte")) {
            assertThat(render(template)).as(template)
                    .doesNotContainPattern("(?i)(margin|padding|border)-(left|right)")
                    .doesNotContainPattern("(?i)text-align:\\s*(left|right)")
                    .doesNotContain("is-pulled-left").doesNotContain("is-pulled-right");
        }
        assertThat(resource("/static/css/attendance.css")).doesNotContainPattern("(?i)\\b(left|right)\\b");
        for (String script : List.of("date-input", "school-api", "school-calendar", "attendance", "attendance-report")) {
            assertThat(resource("/static/js/" + script + ".js")).as(script)
                    .doesNotContainPattern("(?i)\\b(margin|padding|border|text-align)-?(left|right)\\b");
        }
    }

    @Test
    void theScriptsNeverUseBrowserDialogs() throws IOException {
        for (String script : List.of("school-calendar", "attendance", "attendance-report", "school-api")) {
            assertThat(resource("/static/js/" + script + ".js")).as(script)
                    .doesNotContainPattern("\\balert\\(")
                    .doesNotContainPattern("window\\.confirm\\(")
                    .doesNotContainPattern("(?<![.\\w])confirm\\(");
        }
    }
}
