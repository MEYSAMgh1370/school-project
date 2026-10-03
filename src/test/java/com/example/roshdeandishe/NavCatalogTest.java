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
 * منبع واحدِ لینک‌ها (سایدبار + «دسترسی سریع» داشبورد) را بدون Spring و
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
        // وگرنه داشبوردِ آن نقش بخش «دسترسی سریع» خالی نشان می‌دهد
        Set<String> roles = new HashSet<>();
        allLinks().forEach(link -> roles.addAll(link.roles()));
        roles.forEach(role -> assertThat(NavCatalog.shortcuts().stream()
                .anyMatch(link -> link.shortcutRoles().contains(role))).as(role).isTrue());
    }

    @Test
    void groupIsVisibleForTheUnionOfItsLinkRoles() {
        Group school = NavCatalog.groups().stream().filter(g -> g.id().equals("school")).findFirst().orElseThrow();
        assertThat(school.visibleWhen("selectedRole"))
                .isEqualTo("['SCHOOL_ADMIN', 'TEACHER'].includes(selectedRole)");
    }

    @Test
    void anyShortcutExpressionCoversEveryShortcutRole() {
        String expression = NavCatalog.anyShortcutVisibleWhen("$store.nav.selectedRole");
        assertThat(expression).endsWith("].includes($store.nav.selectedRole)")
                .contains("'SCHOOL_ADMIN'", "'TEACHER'", "'STUDENT'", "'PARENT'", "'FINANCE_ADMIN'");
    }

    @Test
    void selfServiceLinksAreRoleOnlyAndManagementLinksNameTheirManagePermission() {
        allLinks().forEach(link -> {
            if (link.href().startsWith("/my-")) {
                // «من»: بدونِ Permission - GRADES_VIEW/FINANCE_VIEW را نقش‌های دیگر هم دارند
                assertThat(link.permissions()).as(link.href()).isEmpty();
            } else {
                assertThat(link.permissions()).as(link.href()).isNotEmpty();
            }
        });
        assertThat(allLinks().stream().filter(l -> l.href().equals("/timetable")).findFirst().orElseThrow()
                .permissions()).containsExactly("TIMETABLE_MANAGE");
    }

    @Test
    void visibleWhenWithPermissionsAddsAnOrBranchAndWithoutThemIsUnchanged() {
        NavCatalog.Link timetable = allLinks().stream().filter(l -> l.href().equals("/timetable")).findFirst().orElseThrow();
        NavCatalog.Link mine = allLinks().stream().filter(l -> l.href().equals("/my-timetable")).findFirst().orElseThrow();

        assertThat(timetable.visibleWhen("role", "perms"))
                .isEqualTo("['SCHOOL_ADMIN'].includes(role) || ['TIMETABLE_MANAGE'].some(p => perms.includes(p))");
        assertThat(timetable.visibleWhen("role")).isEqualTo("['SCHOOL_ADMIN'].includes(role)");
        // لینکِ «من» حتی با عبارتِ Permission همان شرطِ نقش را می‌دهد
        assertThat(mine.visibleWhen("role", "perms")).isEqualTo(mine.visibleWhen("role"));
    }

    @Test
    void aGroupIsAlsoVisibleForTheUnionOfItsLinksPermissions() {
        Group school = NavCatalog.groups().stream().filter(g -> g.id().equals("school")).findFirst().orElseThrow();

        assertThat(school.visibleWhen("role", "perms"))
                .startsWith("['SCHOOL_ADMIN', 'TEACHER'].includes(role) || [")
                .contains("'MEMBERSHIP_MANAGE'", "'ACADEMIC_YEAR_MANAGE'", "'ROLE_MANAGE'")
                .endsWith(".some(p => perms.includes(p))");
    }
}
