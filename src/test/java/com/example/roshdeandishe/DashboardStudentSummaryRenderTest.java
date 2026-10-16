package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.DashboardModel;
import com.example.roshdeandishe.web.model.MembershipRow;
import com.example.roshdeandishe.web.model.NavbarModel;
import com.example.roshdeandishe.web.model.OwnSummaryRow;
import com.example.roshdeandishe.studentview.StudentSummary;
import com.example.roshdeandishe.studentview.TodayAttendance;
import com.example.roshdeandishe.web.model.WardRow;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «خلاصه‌ی من» دانش‌آموز و خلاصه‌ی هر فرزند روی پیشخوان (changes/student-summary.md). */
class DashboardStudentSummaryRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("آرین",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("STUDENT"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private static StudentSummary full() {
        return new StudentSummary(
                List.of(new StudentSummary.GradeLine("ریاضی", "نوبت اول", "18.5")),
                List.of(new StudentSummary.ExamLine("آزمون هفتگی", "۱۴ مهر ۱۴۰۵")),
                new StudentSummary.AttendanceCount(2, 1, 3));
    }

    private String render(List<OwnSummaryRow> own, List<WardRow> wards) {
        DashboardModel model = new DashboardModel("آرین", "a@example.com", null,
                List.of(new MembershipRow(1L, "مدرسه‌ی نمونه", "STUDENT", true)), List.of(), !wards.isEmpty(), wards,
                List.of(), List.of(), List.of(), "", own);
        StringOutput output = new StringOutput();
        engine.render("dashboard.jte", Map.of("model", model, "navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void theStudentSeesGradesExamsAndAbsenceCountsWithNoAverage() {
        String html = render(List.of(new OwnSummaryRow("مدرسه‌ی نمونه", full())), List.of());

        assertThat(html).contains("id=\"dashboardStudentSummary\"", "خلاصه‌ی من", "ریاضی", "نوبت اول",
                "Calendar.digits('18.5')", "آزمون هفتگی", "۱۴ مهر ۱۴۰۵", "href=\"/my-grades\">همه‌ی نمره‌ها</a>");
        assertThat(html).contains("Calendar.digits('2')", "روز غایب", "موجه", "دیرآمدن");
        assertThat(html).doesNotContain("میانگین");
    }

    @Test
    void emptySectionsSayNothingWasRecordedAndAMissingAttendanceModuleHidesTheRow() {
        StudentSummary empty = new StudentSummary(List.of(), List.of(), null);
        String html = render(List.of(new OwnSummaryRow("مدرسه‌ی نمونه", empty)), List.of());

        assertThat(html).contains("نمره‌ای ثبت نشده است.", "آزمون نزدیکی ثبت نشده است.");
        assertThat(html).doesNotContain("غیبت امسال");
    }

    @Test
    void noSummarySectionForAUserWithoutOne() {
        assertThat(render(List.of(), List.of())).doesNotContain("dashboardStudentSummary");
    }

    @Test
    void eachWardCardCarriesThatChildsSummaryWithAGradesLinkForThatChild() {
        WardRow ward = new WardRow(100L, 5L, "نیکا", "مدرسه‌ی نمونه", "پنجم", "الف", 0, 0, 0,
                TodayAttendance.UNAVAILABLE, full());
        String html = org.springframework.web.util.HtmlUtils.htmlUnescape(render(List.of(), List.of(ward)));

        assertThat(html).contains("آخرین نمره‌ها:", "آزمون‌های پیش‌رو:", "غیبت امسال:",
                "href=\"/my-grades?student=100&school=5\">همه‌ی نمره‌ها</a>");
    }
}
