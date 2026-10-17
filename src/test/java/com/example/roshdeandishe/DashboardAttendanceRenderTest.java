package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.AttendanceTodayRow;
import com.example.roshdeandishe.web.model.DashboardModel;
import com.example.roshdeandishe.web.model.MembershipRow;
import com.example.roshdeandishe.web.model.NavbarModel;
import com.example.roshdeandishe.web.model.TuitionRow;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * بخش «حضور و غیاب امروز» پیشخوانِ مدیر را مستقیم (بدون Spring/Docker) رندر می‌کند: رقم‌ها، حالت‌های «هنوز ثبت
 * نشده» و «روزِ غیردرسی»، قفلِ SCHOOL_ADMIN/مدرسه‌ی انتخاب‌شده و سازگاریِ سازنده‌های قدیمی. گاردِ entitlement در
 * AttendanceTodayAssemblerTest است.
 */
class DashboardAttendanceRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر",
            List.of(new NavbarModel.SchoolOption(7L, "دبستان مهر", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(List<TuitionRow> tuition, List<AttendanceTodayRow> attendance) {
        DashboardModel model = new DashboardModel("مدیر", "admin@example.com", null,
                List.of(new MembershipRow(7L, "دبستان مهر", "SCHOOL_ADMIN", true)), List.of(), false, List.of(),
                tuition, attendance);
        StringOutput output = new StringOutput();
        engine.render("dashboard.jte", Map.of("model", model, "navbar", NAVBAR), output);
        return output.toString();
    }

    private static AttendanceTodayRow row(boolean schoolDay, int absent, Integer percent, int taken, int total) {
        return new AttendanceTodayRow(7L, "دبستان مهر", schoolDay, absent, percent, taken, total);
    }

    @Test
    void showsTheAbsentCountPresentPercentAsAProgressBarAndClassesWithoutARoll() {
        String html = render(List.of(), List.of(row(true, 4, 92, 6, 8)));

        assertThat(html).contains("حضور و غیاب امروز", "دبستان مهر");
        assertThat(html).contains("Calendar.digits('4')", "Calendar.digits('92')").contains("غایب</strong>").contains("٪ حضور");
        assertThat(html).contains("<progress class=\"progress is-primary\" value=\"92\" max=\"100\"");
        // ۸ کلاس، ۶ ثبت‌شده → ۲ کلاس بدون ثبت
        assertThat(html).contains("Calendar.digits('2')").contains("کلاس بدون ثبت");
        assertThat(html).contains("href=\"/attendance/report\">غایبین امروز</a>");
    }

    @Test
    void isShownOnlyForTheSelectedAdminRoleAndSchool() {
        String html = render(List.of(), List.of(row(true, 0, 100, 1, 1)));

        assertThat(html).contains("$store.nav.permissions.includes('ATTENDANCE_VIEW_ALL')")
                .contains("$store.nav.selectedSchoolId === 7");
        assertThat(html).containsPattern("<section[^>]*dashboard-attendance-title-7[^>]* x-data[ >\\n]");
    }

    @Test
    void whenEveryClassHasARollNoUntakenLineIsShown() {
        String html = render(List.of(), List.of(row(true, 1, 96, 8, 8)));

        assertThat(html).doesNotContain("کلاس بدون ثبت");
    }

    @Test
    void noRollYetSaysSoInsteadOfAZeroPercent() {
        String html = render(List.of(), List.of(row(true, 0, null, 0, 8)));

        assertThat(html).contains("هنوز حضور و غیابی ثبت نشده است").doesNotContain("<progress");
        assertThat(html).contains("غایبین امروز");
    }

    @Test
    void aNonSchoolDayShowsNoFigure() {
        String html = render(List.of(), List.of(row(false, 0, null, 0, 8)));

        assertThat(html).contains("امروز روز درسیِ مدرسه نیست").doesNotContain("<progress");
    }

    @Test
    void usersWithoutAnAttendanceRowGetNoBox() {
        // غیرمدیر یا مدرسه‌ای که ماژول حضور و غیاب را ندارد: ردیفی ساخته نمی‌شود
        assertThat(render(List.of(), List.of())).doesNotContain("حضور و غیاب امروز").doesNotContain("غایبین امروز");
    }

    @Test
    void sitsNextToTheTuitionBoxAfterIt() {
        String html = render(List.of(new TuitionRow(7L, "دبستان مهر", 3, 72)), List.of(row(true, 4, 92, 6, 8)));

        assertThat(html.indexOf("وضعیت شهریه")).isPositive().isLessThan(html.indexOf("حضور و غیاب امروز"));
    }

    @Test
    void theRowDerivesItsFiguresAndClampsThem() {
        assertThat(row(true, 4, 92, 6, 8).hasRoll()).isTrue();
        assertThat(row(true, 0, null, 0, 8).hasRoll()).isFalse();
        assertThat(row(false, 0, null, 0, 8).hasRoll()).isFalse();
        assertThat(row(true, 4, 92, 6, 8).classesWithoutRoll()).isEqualTo(2);
        // هرگز منفی نیست
        assertThat(row(true, 0, 100, 9, 8).classesWithoutRoll()).isZero();
        assertThat(row(true, 0, 130, 1, 1).percentValue()).isEqualTo(100);
        assertThat(row(true, 0, null, 0, 1).percentValue()).isZero();
    }

    @Test
    void theOlderDashboardModelConstructorsStillWork() {
        DashboardModel seven = new DashboardModel("م", "a@b.c", null, List.of(), List.of(), false, List.of());
        DashboardModel eight = new DashboardModel("م", "a@b.c", null, List.of(), List.of(), false, List.of(), List.of());

        assertThat(seven.attendanceToday()).isEmpty();
        assertThat(seven.tuition()).isEmpty();
        assertThat(eight.attendanceToday()).isEmpty();
    }
}
