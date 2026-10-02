package com.example.roshdeandishe;

import com.example.roshdeandishe.web.dto.DashboardModel;
import com.example.roshdeandishe.web.model.NavCatalog;
import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * قالب‌های برنامهٔ کلاسی (openspec/changes/class-timetable.md) بدون Spring: ساختار
 * صفحه‌ی مدیر، صفحه‌ی «برنامه‌ی کلاسی من»، بخش «برنامه‌ی امروز» در داشبورد و لینک‌های
 * NavCatalog. رفتارِ Alpine (بارگذاری، رقابتِ پاسخ‌ها، modal) در مرورگر راستی‌آزمایی
 * شد؛ پروژه تست JS ندارد.
 */
class TimetablePagesRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String template, Map<String, Object> params) {
        StringOutput output = new StringOutput();
        engine.render(template, params, output);
        return output.toString();
    }

    @Test
    void theAdminPageHasYearTabsGridPeriodsAndActivities() {
        String html = render("timetable.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("x-data=\"timetablePage()\"");
        assertThat(html).contains("id=\"ttYear\"").contains("@change=\"onYearChange()\"");
        assertThat(html).contains("id=\"ttTabGrid\"", "id=\"ttTabPeriods\"", "id=\"ttTabActivities\"");
        assertThat(html).contains("id=\"ttGrid\"", "id=\"ttPeriodsTable\"", "id=\"ttActivitiesTable\"");
        assertThat(html).contains("id=\"ttClass\"").contains("@change=\"onClassChange()\"");
        // مدیر هر سالی را می‌بیند ولی فقط سالِ جاری برای بقیه نمایش داده می‌شود
        assertThat(html).contains("id=\"ttYearOther\"").contains("فقط برنامه‌ی سالِ تحصیلیِ جاری را می‌بینند");
    }

    @Test
    void theAdminPageLoadsItsScriptsAndStyles() {
        String html = render("timetable.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("/js/timetable-grid.js", "/js/timetable.js", "/js/modal-focus.js",
                "/css/timetable.css");
        assertThat(html.indexOf("/js/timetable-grid.js")).isLessThan(html.indexOf("/js/timetable.js"));
    }

    @Test
    void everyEmptyStateTeachesWhatToDoNext() {
        String html = render("timetable.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("هنوز کلاسی ندارید. از صفحه‌ی <a href=\"/classes\">کلاس‌ها</a>");
        assertThat(html).contains("id=\"ttNoPeriods\"").contains("زنگ‌ها را بسازید");
        assertThat(html).contains("با فرمِ بالا اولین زنگ را بسازید");
        assertThat(html).contains("با فرمِ بالا اولین فعالیتِ فوق‌برنامه را بسازید");
    }

    @Test
    void theCellEditorChoosesACourseOrAnActivityAndAnOptionalTeacher() {
        String html = render("timetable.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("id=\"ttCellCourse\"", "id=\"ttCellActivity\"", "id=\"ttCellTeacher\"");
        assertThat(html).contains("<option value=\"\">بدون معلم</option>");
        assertThat(html).contains("id=\"ttCellSave\"", "id=\"ttCellClear\"");
        // یک modal در هر لحظه: ویرایشگر خانه و تأیید حذف جدا هستند و هر دو focus-trap دارند
        assertThat(html).contains("x-ref=\"cellModal\"", "x-ref=\"confirmModal\"");
        assertThat(html).contains("aria-labelledby=\"ttCellTitle\"", "aria-labelledby=\"ttConfirmTitle\"");
    }

    @Test
    void periodTimesAreTimeInputsInLtrAndPlatformActivitiesAreMarkedReadOnly() {
        String html = render("timetable.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("id=\"ttPeriodStart\" type=\"time\" dir=\"ltr\"");
        assertThat(html).contains("پیش‌فرض پلتفرم");
        assertThat(html).contains("x-show=\"!activity.platformDefault\"");
    }

    @Test
    void myTimetableIsAReadOnlyGridWithAPickerForSeveralViews() {
        String html = render("my-timetable.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("x-data=\"myTimetablePage()\"").contains("id=\"myTtGrid\"");
        assertThat(html).contains("x-show=\"views.length > 1\"").contains("id=\"tt-picker-list\"");
        // خواندنی است: هیچ دکمه‌ی ویرایشی ندارد
        assertThat(html).doesNotContain("app-tt-cell").doesNotContain("saveCell");
        assertThat(html).contains("id=\"myTtNoYear\"", "id=\"myTtNoPeriods\"", "id=\"myTtNoEntries\"");
        assertThat(html).contains("برنامه‌ای برای شما یا فرزندانتان در این مدرسه ثبت نشده است.");
        assertThat(html).contains("/css/my-grades.css", "/css/timetable.css");
    }

    @Test
    void theDashboardShowsTodaysTimetableOnlyForTheStudentRoleWithALinkToTheWeek() {
        DashboardModel model = new DashboardModel("دانش‌آموز نمونه", "student@example.com", null,
                List.of(), List.of(), false, List.of());
        String html = render("dashboard.jte", Map.of("model", model, "navbar", NAVBAR));

        assertThat(html).contains("id=\"dashboardTimetable\"").contains("x-data=\"dashboardTimetable()\"");
        assertThat(html).contains("x-show=\"isStudent()\"");
        assertThat(html).contains("برنامه‌ی امروز").contains("href=\"/my-timetable\">برنامه‌ی کل هفته</a>");
        // کم‌اثر نبودنِ شکست: هر وضعیتِ خالی پیامِ خودش را دارد
        assertThat(html).contains("id=\"dashboardTimetableNoYear\"", "id=\"dashboardTimetableHoliday\"",
                "id=\"dashboardTimetableNone\"");
        assertThat(html.indexOf("/js/timetable-grid.js")).isLessThan(html.indexOf("/js/dashboard-timetable.js"));
    }

    @Test
    void navCatalogGivesTheAdminAManagePageAndStudentParentTeacherAViewPage() {
        NavCatalog.Link manage = link("/timetable");
        NavCatalog.Link mine = link("/my-timetable");

        assertThat(manage.roles()).containsExactly("SCHOOL_ADMIN");
        assertThat(manage.shortcutRoles()).containsExactly("SCHOOL_ADMIN");
        assertThat(mine.roles()).containsExactlyInAnyOrder("STUDENT", "PARENT", "TEACHER");
        assertThat(mine.shortcutRoles()).containsExactlyInAnyOrder("STUDENT", "PARENT", "TEACHER");
    }

    private static NavCatalog.Link link(String href) {
        return NavCatalog.groups().stream().flatMap(g -> g.links().stream())
                .filter(l -> l.href().equals(href)).findFirst().orElseThrow();
    }
}
