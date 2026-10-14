package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * قالبِ «برگه‌ی حضور و غیابِ دانش‌آموز» (openspec/changes/attendance-reports.md) بدون Spring و Docker: کنترل‌های
 * پنهان در چاپ، ساختارِ برگه، حالت‌ها، ترتیبِ اسکریپت‌ها و نبودنِ ویژگیِ فیزیکیِ راست/چپ. رفتارِ چاپ در مرورگر دیده می‌شود.
 */
class AttendanceSheetPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("attendance-sheet.jte", Map.of("navbar", NAVBAR, "studentId", 41L), output);
        return output.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = AttendanceSheetPageRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theControlsAreHiddenInPrintAndThePrimaryActionIsThePrintButton() {
        String html = render();

        assertThat(html).contains("x-data=\"attendanceSheetPage()\"", "data-student-id=\"41\"");
        assertThat(html).contains("id=\"sheetPrint\"", "@click=\"printSheet\"", "چاپ / ذخیره PDF</button>");
        assertThat(html).contains("id=\"sheetBack\"", "href=\"/attendance/report\"", "بازگشت به گزارش");
        assertThat(html).contains("بازه از صفحه‌ی گزارش می‌آید");
        assertThat(html.split("button is-link", -1).length - 1).isEqualTo(1);
        // نوار کنترل و پیام‌های صفحه با no-print از چاپ حذف می‌شوند؛ خودِ برگه نه
        assertThat(html).contains("app-sheet-toolbar no-print", "app-sheet-notice no-print");
        assertThat(html.substring(html.indexOf("id=\"attendanceSheet\""))).doesNotContain("no-print");
        // بدون ناوبار: فقط نامِ مدرسه‌ها برای سربرگ
        assertThat(html).doesNotContain("class=\"navbar\"");
        assertThat(html).contains("data-school-id=\"1\"", "data-school-name=\"مدرسه‌ی نمونه\"");
    }

    @Test
    void theSheetShowsTitleStudentRangePercentClassSummaryAndTheDatedList() {
        String html = render();

        assertThat(html).contains("<h1 class=\"title is-4 mb-1\">برگه‌ی حضور و غیاب</h1>");
        assertThat(html).contains("x-text=\"schoolName\"", "x-text=\"sheet.studentName\"", "x-text=\"rangeText()\"");
        // درصد: نوارِ native + عددِ متنی
        assertThat(html).contains("<progress class=\"progress is-primary\" max=\"100\"", "x-text=\"percentText(sheet.presentPercent)\"");
        assertThat(html).contains("id=\"sheetClassTable\"", ">روزهای غیبت</th>", ">روزهای تأخیر</th>", ">روزهای حضور</th>", ">درصد حضور</th>");
        assertThat(html).contains("id=\"sheetDaysTable\"", ">تاریخ</th>", ">روز هفته</th>", ">کلاس</th>", ">وضعیت</th>", ">یادداشت</th>");
        assertThat(html).contains("x-show=\"item.excused\">موجه</span>");
        assertThat(html).contains("تاریخ چاپ:", "x-text=\"printDate()\"");
    }

    @Test
    void loadingErrorAndEmptyStatesExist() {
        String html = render();

        assertThat(html).contains("id=\"sheetLoading\"", "در حال بارگذاری");
        assertThat(html).contains("id=\"sheetError\"", "role=\"alert\"", "x-text=\"error\"");
        assertThat(html).contains("id=\"sheetEmpty\"", "در این بازه غیبت یا تأخیری ثبت نشده است.");
        assertThat(html).contains("id=\"sheetNoPercent\"");
    }

    @Test
    void theScriptsLoadInDependencyOrderAndOnlyItsOwnStyle() {
        String html = render();

        assertThat(html).contains("/js/attendance-sheet-logic.js", "/js/attendance-sheet-print.js", "/css/attendance-print.css");
        assertThat(html).doesNotContain("question-box.css");
        assertThat(html.indexOf("/js/jalali.js")).isLessThan(html.indexOf("/js/calendar.js"));
        assertThat(html.indexOf("/js/calendar.js")).isLessThan(html.indexOf("/js/date-input.js"));
        assertThat(html.indexOf("/js/school-api.js")).isLessThan(html.indexOf("/js/attendance-sheet-print.js"));
        assertThat(html.indexOf("/js/attendance-sheet-logic.js")).isLessThan(html.indexOf("/js/attendance-sheet-print.js"));
    }

    @Test
    void theSheetStylesheetHasPrintRulesAndNoPhysicalLeftRight() throws IOException {
        String html = render();
        assertThat(html).doesNotContainPattern("(?i)(margin|padding|border)-(left|right)")
                .doesNotContainPattern("(?i)text-align:\\s*(left|right)")
                .doesNotContain("is-pulled-left").doesNotContain("is-pulled-right");

        String css = resource("/static/css/attendance-print.css");
        assertThat(css).contains("@media print", "size: A4", ".no-print", "break-inside: avoid");
        assertThat(css).doesNotContainPattern("(?i)\\b(left|right)\\b");
        assertThat(css).doesNotContainPattern("\\d+px");

        for (String script : List.of("attendance-sheet-logic", "attendance-sheet-print")) {
            assertThat(resource("/static/js/" + script + ".js")).as(script)
                    .doesNotContainPattern("(?i)\\b(margin|padding|border|text-align)-?(left|right)\\b")
                    .doesNotContainPattern("\\balert\\(")
                    .doesNotContainPattern("(?<![.\\w])confirm\\(");
        }
        assertThat(resource("/static/js/attendance-sheet-print.js")).contains("window.print()");
    }
}
