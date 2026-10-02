package com.example.roshdeandishe;

import com.example.roshdeandishe.web.dto.DashboardModel;
import com.example.roshdeandishe.web.dto.MembershipRow;
import com.example.roshdeandishe.web.dto.SchoolEntryRow;
import com.example.roshdeandishe.web.model.NavCatalog;
import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * سایدبار و «دسترسی سریع» داشبورد را مستقیم (بدون Spring/Docker) رندر می‌کند
 * تا دو چیز قفل شود: هر دو از NavCatalog می‌آیند (لینکِ کارت‌ها همه در
 * سایدبار هم هست) و ترتیبِ محتوای داشبورد (دسترسی سریع ← عضویت‌ها ← نشانی
 * ورود مدرسه ← شماره تلفن).
 */
class NavSharedSourceRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("سارا احمدی",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN", "TEACHER"))));

    private static final Pattern HREF = Pattern.compile("href=\"(/[a-z-]+)\"");

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String template, Map<String, Object> params) {
        StringOutput output = new StringOutput();
        engine.render(template, params, output);
        return output.toString();
    }

    private static DashboardModel dashboardModel() {
        return new DashboardModel("سارا احمدی", "sara@example.com", null,
                List.of(new MembershipRow(1L, "مدرسه‌ی نمونه", "SCHOOL_ADMIN", true)),
                List.of(new SchoolEntryRow("مدرسه‌ی نمونه", "rosh")));
    }

    private static Set<String> hrefsIn(String html) {
        Set<String> hrefs = new LinkedHashSet<>();
        Matcher matcher = HREF.matcher(html);
        while (matcher.find()) {
            hrefs.add(matcher.group(1));
        }
        return hrefs;
    }

    @Test
    void sidebarRendersEveryCatalogLinkAndGroup() {
        String html = render("layout/navbar.jte", Map.of("navbar", NAVBAR));

        NavCatalog.groups().forEach(group -> {
            assertThat(html).contains("data-group=\"" + group.id() + "\"");
            group.links().forEach(link -> assertThat(html).contains("href=\"" + link.href() + "\""));
        });
    }

    @Test
    void sidebarLinkVisibilityComesFromTheCatalogRoles() {
        String html = render("layout/navbar.jte", Map.of("navbar", NAVBAR));

        // `'` و `&&` ممکن است escape شوند؛ مرورگر قبل از Alpine دوباره decode می‌کند
        String decoded = HtmlUtils.htmlUnescape(html);
        assertThat(decoded).contains(
                "href=\"/people\" x-show=\"['SCHOOL_ADMIN'].includes(selectedRole)\"",
                "href=\"/students\" x-show=\"['SCHOOL_ADMIN', 'TEACHER'].includes(selectedRole)\"",
                "href=\"/my-grades\" x-show=\"['STUDENT', 'PARENT'].includes(selectedRole)\"");
    }

    @Test
    void everyDashboardShortcutLinkAlsoExistsInTheSidebar() {
        Map<String, Object> params = Map.of("model", dashboardModel(), "navbar", NAVBAR);
        String dashboard = render("dashboard.jte", params);
        String sidebar = render("layout/navbar.jte", Map.of("navbar", NAVBAR));

        List<String> shortcutHrefs = new ArrayList<>();
        Matcher card = Pattern.compile("class=\"box app-shortcut\" href=\"(/[a-z-]+)\"").matcher(dashboard);
        while (card.find()) {
            shortcutHrefs.add(card.group(1));
        }

        assertThat(shortcutHrefs).isNotEmpty()
                .containsExactlyElementsOf(NavCatalog.shortcuts().stream().map(l -> l.href()).toList());
        assertThat(hrefsIn(sidebar)).containsAll(shortcutHrefs);
    }

    @Test
    void shortcutCardsAreGatedByTheSelectedRoleStore() {
        String html = HtmlUtils.htmlUnescape(render("dashboard.jte",
                Map.of("model", dashboardModel(), "navbar", NAVBAR)));

        // Alpine فقط زیر یک x-data ریشه x-show را اجرا می‌کند؛ بدونش کارت‌ها برای
        // همیشه با x-cloak پنهان می‌مانند (کامپایل و رندر JTE این را نشان نمی‌دهد)
        assertThat(html).containsPattern("<section[^>]*dashboard-shortcuts-title[^>]* x-data[ >\\n]");
        assertThat(html).contains("x-show=\"['SCHOOL_ADMIN', 'TEACHER'].includes($store.nav.selectedRole)\"");
        assertThat(html).contains("x-show=\"['STUDENT'].includes($store.nav.selectedRole)\"");
    }

    @Test
    void dashboardSectionsAreOrderedShortcutsMembershipsSchoolEntryPhone() {
        String html = render("dashboard.jte", Map.of("model", dashboardModel(), "navbar", NAVBAR));

        int shortcuts = html.indexOf("dashboard-shortcuts-title");
        int memberships = html.indexOf("<table");
        int schoolEntry = html.indexOf("schoolEntryLinks()");
        int phone = html.indexOf("phoneNumberField()");

        assertThat(shortcuts).isPositive();
        assertThat(shortcuts).isLessThan(memberships);
        assertThat(memberships).isLessThan(schoolEntry);
        assertThat(schoolEntry).isLessThan(phone);
    }

    @Test
    void shortcutColumnsStayTwoPerRowOnMobile() {
        String html = render("dashboard.jte", Map.of("model", dashboardModel(), "navbar", NAVBAR));

        assertThat(html).contains("columns is-multiline is-mobile");
        assertThat(html).contains("is-6-mobile");
    }
}
