package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavCatalog;
import com.example.roshdeandishe.web.model.NavCatalog.Group;
import com.example.roshdeandishe.web.model.NavCatalog.Link;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * منبع واحدِ لینک‌ها (سایدبار + «دسترسی سریع» پیشخوان) را بدون Spring و
 * Docker قفل می‌کند: ثبت لینکِ تکراری یا کارتی برای نقشی که لینکش را
 * نمی‌بیند، همین‌جا می‌شکند - نه در مرورگر.
 */
class NavCatalogTest {

    private static List<Link> allLinks() {
        return NavCatalog.groups().stream().flatMap(group -> group.links().stream()).toList();
    }

    @Test
    void hrefsAndGroupIdsAreUnique() {
        Set<String> hrefs = new HashSet<>();
        allLinks().forEach(link -> assertThat(hrefs.add(link.href())).as(link.href()).isTrue());

        Set<String> ids = new HashSet<>();
        NavCatalog.groups().forEach(group -> assertThat(ids.add(group.id())).as(group.id()).isTrue());
    }

    @Test
    void everyLinkHasLabelDescriptionAndAtLeastOneRole() {
        allLinks().forEach(link -> {
            assertThat(link.href()).startsWith("/");
            assertThat(link.label()).isNotBlank();
            assertThat(link.description()).isNotBlank();
            assertThat(link.roles()).as(link.href()).isNotEmpty();
        });
    }

    @Test
    void shortcutRolesAreASubsetOfSidebarRoles() {
        allLinks().forEach(link ->
                assertThat(link.roles()).as(link.href()).containsAll(link.shortcutRoles()));
    }

    @Test
    void shortcutsListsExactlyTheLinksWithShortcutRoles() {
        assertThat(NavCatalog.shortcuts())
                .containsExactlyElementsOf(allLinks().stream().filter(l -> !l.shortcutRoles().isEmpty()).toList());
    }

    @Test
    void everyRoleThatSeesTheSidebarHasAtLeastOneShortcut() {
        // وگرنه پیشخوانِ آن نقش بخش «دسترسی سریع» خالی نشان می‌دهد
        Set<String> roles = new HashSet<>();
        allLinks().forEach(link -> roles.addAll(link.roles()));
        roles.forEach(role -> assertThat(NavCatalog.shortcuts().stream()
                .anyMatch(link -> link.shortcutRoles().contains(role))).as(role).isTrue());
    }

    @Test
    void groupIsVisibleForTheUnionOfItsLinkRoles() {
        Group school = NavCatalog.groups().stream().filter(g -> g.id().equals("school")).findFirst().orElseThrow();
        assertThat(school.visibleWhen("selectedRole"))
                .isEqualTo("['SCHOOL_ADMIN', 'TEACHER', 'FINANCE_ADMIN'].includes(selectedRole)");
    }

    @Test
    void anyShortcutExpressionCoversEveryShortcutRole() {
        String expression = NavCatalog.anyShortcutVisibleWhen("$store.nav.selectedRole");
        assertThat(expression).endsWith("].includes($store.nav.selectedRole)")
                .contains("'SCHOOL_ADMIN'", "'TEACHER'", "'STUDENT'", "'PARENT'", "'FINANCE_ADMIN'");
    }

    @Test
    void shortcutCardForACustomRoleNeedsAllLinkPermissionsAndSystemRolesStayRoleOnly() {
        NavCatalog.Link finance = NavCatalog.shortcuts().stream().filter(l -> l.href().equals("/finance")).findFirst().orElseThrow();
        NavCatalog.Link mine = NavCatalog.shortcuts().stream().filter(l -> l.href().equals("/my-grades")).findFirst().orElseThrow();

        assertThat(finance.shortcutVisibleWhen("role", "perms")).isEqualTo(
                "['SCHOOL_ADMIN', 'FINANCE_ADMIN'].includes(role) || ['FINANCE_MANAGE', 'FINANCE_VIEW']"
                        + ".every(p => (!['SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'FINANCE_ADMIN']"
                        + ".includes(role) ? perms : []).includes(p))"
                        + " || ['FINANCE_VIEW', 'FINANCE_VIEW_ALL']"
                        + ".every(p => (!['SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'FINANCE_ADMIN']"
                        + ".includes(role) ? perms : []).includes(p))");
        // کارت‌های «من» Permission ندارند: همان شرطِ نقش
        assertThat(mine.shortcutVisibleWhen("role", "perms")).isEqualTo(mine.shortcutVisibleWhen("role"));
        assertThat(NavCatalog.anyShortcutVisibleWhen("role", "perms"))
                .startsWith(NavCatalog.anyShortcutVisibleWhen("role").replace(")", ")"))
                .contains("|| ['TIMETABLE_MANAGE'].every(p => (!");
    }

    @Test
    void selfServiceLinksAreRoleOnlyAndManagementLinksNameTheirManagePermission() {
        allLinks().forEach(link -> {
            if (link.href().startsWith("/my-") || link.href().equals("/yearly-plan")) {
                // «من» و «برنامه‌ی سالانه» (خواندنش برای همه‌ی اعضاست): بدونِ Permission -
                // GRADES_VIEW/FINANCE_VIEW را نقش‌های دیگر هم دارند
                assertThat(link.permissions()).as(link.href()).isEmpty();
            } else {
                assertThat(link.permissions()).as(link.href()).isNotEmpty();
            }
        });
        assertThat(permissionsOf("/timetable")).containsExactly("TIMETABLE_MANAGE");
    }

    @Test
    void aPageWhoseFirstLoadNeedsAnotherPermissionRequiresBothSoACustomRoleIsNotShownADeadPage() {
        // Permissionها مستقل انتخاب می‌شوند: بدونِ خواندنِ مسدودکننده، صفحه با اولین 403 بسته می‌شود
        assertThat(permissionsOf("/finance")).containsExactlyInAnyOrder("FINANCE_MANAGE", "FINANCE_VIEW");
        assertThat(permissionsOf("/students")).containsExactlyInAnyOrder("STUDENT_ENROLL", "STUDENT_VIEW");
        assertThat(permissionsOf("/grades")).containsExactlyInAnyOrder("GRADES_EDIT", "CLASS_VIEW");
    }

    @Test
    void attendanceAndCalendarPagesJoinTheirGroupsWithTheRightRolesAndPermissions() {
        Link record = linkOf("/attendance");
        Link report = linkOf("/attendance/report");
        Link calendar = linkOf("/calendar");

        assertThat(record.roles()).containsExactly("SCHOOL_ADMIN", "TEACHER");
        assertThat(record.shortcutRoles()).containsExactly("SCHOOL_ADMIN", "TEACHER");
        assertThat(report.roles()).containsExactly("SCHOOL_ADMIN");
        assertThat(report.shortcutRoles()).containsExactly("SCHOOL_ADMIN");
        assertThat(calendar.roles()).containsExactly("SCHOOL_ADMIN");
        assertThat(calendar.shortcutRoles()).containsExactly("SCHOOL_ADMIN");
        assertThat(permissionsOf("/attendance/report")).containsExactly("ATTENDANCE_VIEW_ALL");
        assertThat(permissionsOf("/calendar")).containsExactly("CALENDAR_MANAGE");

        assertThat(groupOf("/attendance")).isEqualTo("education");
        assertThat(groupOf("/attendance/report")).isEqualTo("education");
        assertThat(groupOf("/calendar")).isEqualTo("school");
    }

    @Test
    void myAttendanceIsARoleOnlyEducationLinkWithAShortcutForStudentsAndParents() {
        Link mine = linkOf("/my-attendance");

        assertThat(mine.roles()).containsExactly("STUDENT", "PARENT");
        assertThat(mine.shortcutRoles()).containsExactly("STUDENT", "PARENT");
        assertThat(mine.permissions()).isEmpty();
        assertThat(mine.visibleWhen("role", "perms")).isEqualTo("['STUDENT', 'PARENT'].includes(role)");
        assertThat(groupOf("/my-attendance")).isEqualTo("education");
    }

    @Test
    void yearlyPlanIsARoleOnlyEducationLinkEveryMemberSeesWithAnAdminOnlyShortcut() {
        Link plan = linkOf("/yearly-plan");

        assertThat(plan.label()).isEqualTo("برنامه‌ی سالانه");
        assertThat(plan.roles()).containsExactly("SCHOOL_ADMIN", "TEACHER", "STUDENT", "PARENT");
        assertThat(plan.shortcutRoles()).containsExactly("SCHOOL_ADMIN");
        assertThat(plan.permissions()).isEmpty();
        assertThat(plan.visibleWhen("role", "perms")).isEqualTo("['SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT'].includes(role)");
        assertThat(groupOf("/yearly-plan")).isEqualTo("education");
        assertThat(NavCatalog.shortcuts()).contains(plan);
    }

    @Test
    void recordingAttendanceAcceptsEitherRecordPermissionForACustomRole() {
        Link record = linkOf("/attendance");

        // معلم ATTENDANCE_RECORD و مدیر ATTENDANCE_RECORD_ANY دارد: هر دو مجموعه‌ی «همه» جدا و با «یا»
        assertThat(record.permissionSets()).containsExactly(
                List.of("ATTENDANCE_RECORD"), List.of("ATTENDANCE_RECORD_ANY"));
        assertThat(record.visibleWhen("role", "perms")).isEqualTo(
                "['SCHOOL_ADMIN', 'TEACHER'].includes(role) || ['ATTENDANCE_RECORD'].every(p => perms.includes(p))"
                        + " || ['ATTENDANCE_RECORD_ANY'].every(p => perms.includes(p))");
        assertThat(record.shortcutVisibleWhen("role", "perms")).contains("['ATTENDANCE_RECORD'].every(", "['ATTENDANCE_RECORD_ANY'].every(");
        // لینک‌های بدونِ جایگزین دقیقاً مثل قبل‌اند
        assertThat(linkOf("/timetable").permissionSets()).containsExactly(List.of("TIMETABLE_MANAGE"));
    }

    @Test
    void financeAuditIsAFinanceLinkForTheSameRolesAsFinanceNeedingManageAndWithoutADashboardShortcut() {
        Link audit = linkOf("/finance-audit");

        assertThat(audit.label()).isEqualTo("سابقه‌ی تغییرات مالی");
        assertThat(groupOf("/finance-audit")).isEqualTo("finance");
        assertThat(audit.roles()).containsExactly("SCHOOL_ADMIN", "FINANCE_ADMIN").isEqualTo(linkOf("/finance").roles());
        assertThat(audit.shortcutRoles()).isEmpty();
        assertThat(audit.permissions()).containsExactly("FINANCE_MANAGE");
        assertThat(audit.visibleWhen("role", "perms")).isEqualTo(
                "['SCHOOL_ADMIN', 'FINANCE_ADMIN'].includes(role) || ['FINANCE_MANAGE'].every(p => perms.includes(p))");
        assertThat(NavCatalog.shortcuts()).doesNotContain(audit);
    }

    @Test
    void schoolSettingsReplacesTheAcademicYearLinkForAdminAndFinanceAdminAndAnyOneOfFourPermissions() {
        assertThat(allLinks()).noneMatch(l -> l.href().equals("/academic-year"));
        Link settings = linkOf("/settings");

        assertThat(settings.label()).isEqualTo("تنظیمات مدرسه");
        assertThat(settings.description()).isEqualTo("سال تحصیلی، اقساط، یادآوری‌ها و بات مدرسه");
        assertThat(groupOf("/settings")).isEqualTo("school");
        assertThat(settings.roles()).containsExactly("SCHOOL_ADMIN", "FINANCE_ADMIN");
        assertThat(settings.roles()).doesNotContain("STUDENT", "TEACHER", "PARENT");
        // کارتِ «دسترسی سریع» فقط برای مدیر (مدیر مالی همچنان کارتِ «امور مالی» دارد)
        assertThat(settings.shortcutRoles()).containsExactly("SCHOOL_ADMIN");
        // نقشِ سفارشی: هر یکی از چهار مجوز کافی است (هر کدام یک مجموعه‌ی جدا و «یا» بین آن‌ها)
        assertThat(settings.permissionSets()).containsExactly(List.of("ACADEMIC_YEAR_MANAGE"),
                List.of("FINANCE_MANAGE"), List.of("YEARLY_PLAN_MANAGE"), List.of("SCHOOL_SETTINGS_MANAGE"));
        assertThat(settings.visibleWhen("role", "perms")).isEqualTo(
                "['SCHOOL_ADMIN', 'FINANCE_ADMIN'].includes(role) || ['ACADEMIC_YEAR_MANAGE'].every(p => perms.includes(p))"
                        + " || ['FINANCE_MANAGE'].every(p => perms.includes(p))"
                        + " || ['YEARLY_PLAN_MANAGE'].every(p => perms.includes(p))"
                        + " || ['SCHOOL_SETTINGS_MANAGE'].every(p => perms.includes(p))");
        assertThat(NavCatalog.shortcuts()).contains(settings);
    }

    private static Link linkOf(String href) {
        return allLinks().stream().filter(l -> l.href().equals(href)).findFirst().orElseThrow();
    }

    private static String groupOf(String href) {
        return NavCatalog.groups().stream()
                .filter(g -> g.links().stream().anyMatch(l -> l.href().equals(href)))
                .findFirst().orElseThrow().id();
    }

    private static java.util.List<String> permissionsOf(String href) {
        return allLinks().stream().filter(l -> l.href().equals(href)).findFirst().orElseThrow().permissions();
    }

    @Test
    void visibleWhenWithPermissionsAddsAnOrBranchRequiringAllOfThemAndWithoutThemIsUnchanged() {
        NavCatalog.Link timetable = allLinks().stream().filter(l -> l.href().equals("/timetable")).findFirst().orElseThrow();
        NavCatalog.Link mine = allLinks().stream().filter(l -> l.href().equals("/my-timetable")).findFirst().orElseThrow();

        assertThat(timetable.visibleWhen("role", "perms"))
                .isEqualTo("['SCHOOL_ADMIN'].includes(role) || ['TIMETABLE_MANAGE'].every(p => perms.includes(p))");
        assertThat(timetable.visibleWhen("role")).isEqualTo("['SCHOOL_ADMIN'].includes(role)");
        NavCatalog.Link finance = allLinks().stream().filter(l -> l.href().equals("/finance")).findFirst().orElseThrow();
        assertThat(finance.visibleWhen("role", "perms")).isEqualTo(
                "['SCHOOL_ADMIN', 'FINANCE_ADMIN'].includes(role) "
                        + "|| ['FINANCE_MANAGE', 'FINANCE_VIEW'].every(p => perms.includes(p))"
                        + " || ['FINANCE_VIEW', 'FINANCE_VIEW_ALL'].every(p => perms.includes(p))");
        // لینکِ «من» حتی با عبارتِ Permission همان شرطِ نقش را می‌دهد
        assertThat(mine.visibleWhen("role", "perms")).isEqualTo(mine.visibleWhen("role"));
    }

    @Test
    void aGroupIsAlsoVisibleForTheUnionOfItsLinksPermissions() {
        Group school = NavCatalog.groups().stream().filter(g -> g.id().equals("school")).findFirst().orElseThrow();

        assertThat(school.visibleWhen("role", "perms"))
                .startsWith("['SCHOOL_ADMIN', 'TEACHER', 'FINANCE_ADMIN'].includes(role) || [")
                .contains("['MEMBERSHIP_MANAGE'].every(", "['STUDENT_ENROLL', 'STUDENT_VIEW'].every(",
                        "['ROLE_MANAGE'].every(", "['ACADEMIC_YEAR_MANAGE'].every(", "['SCHOOL_SETTINGS_MANAGE'].every(")
                .endsWith(".every(p => perms.includes(p))");
    }

    @Test
    void financeLinkAlsoShowsForAViewOnlyCustomRoleWithFinanceViewAll() {
        Link finance = linkOf("/finance");

        // finance-view-all-permission.md: نقشِ سفارشیِ فقط‌خواندنی (FINANCE_VIEW + FINANCE_VIEW_ALL) هم لینک را می‌بیند
        assertThat(finance.permissionSets()).containsExactly(
                List.of("FINANCE_MANAGE", "FINANCE_VIEW"), List.of("FINANCE_VIEW", "FINANCE_VIEW_ALL"));
        assertThat(finance.visibleWhen("role", "perms")).contains(
                "['FINANCE_VIEW', 'FINANCE_VIEW_ALL'].every(p => perms.includes(p))");
    }

}
