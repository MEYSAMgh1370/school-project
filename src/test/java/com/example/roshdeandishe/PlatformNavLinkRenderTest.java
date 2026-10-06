package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * لینکِ سایدبارِ «تعطیلی‌های سراسری» فقط وقتی دیده می‌شود که پرچمِ مدیر پلتفرم درست باشد (فقط نمایش) و ناوبار
 * برای کاربرِ بدونِ هیچ عضویتی هم رندر می‌شود.
 */
class PlatformNavLinkRenderTest {

    private static final NavbarModel.SchoolOption SCHOOL =
            new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(NavbarModel navbar) {
        StringOutput output = new StringOutput();
        engine.render("layout/navbar.jte", Map.of("navbar", navbar), output);
        return output.toString();
    }

    @Test
    void theLinkAppearsOnlyForAPlatformAdmin() {
        String admin = render(new NavbarModel("مدیر", List.of(), Map.of(), true));
        String other = render(new NavbarModel("مدیر", List.of(SCHOOL), Map.of(), false));

        assertThat(admin).contains("href=\"/platform/holidays\"").contains("تعطیلی‌های سراسری</a>");
        assertThat(other).doesNotContain("/platform/holidays").doesNotContain("تعطیلی‌های سراسری");
        assertThat(render(new NavbarModel("مدیر", List.of(SCHOOL)))).doesNotContain("/platform/holidays");
    }

    @Test
    void aPlatformAdminWithoutSchoolsGetsTheLinkAndNoSchoolSwitcherOrCatalogGroups() {
        String html = render(new NavbarModel("مدیر پلتفرم", List.of(), Map.of(), true));

        assertThat(html).contains("schoolNavbar({}, {})").contains("href=\"/dashboard\"");
        assertThat(html).doesNotContain("data-group=").doesNotContain("selectSchool(");
        assertThat(html).contains("مدیر پلتفرم").contains("@click=\"logout\"");
    }

    @Test
    void aPlatformAdminWhoAlsoHasSchoolsKeepsTheCatalogGroupsAndGetsTheLink() {
        String html = render(new NavbarModel("مدیر", List.of(SCHOOL), Map.of(), true));

        assertThat(html).contains("href=\"/platform/holidays\"").contains("data-group=");
        // لینکِ پلتفرم زیرِ «پیشخوان» و پیش از گروه‌های کاتالوگ است
        assertThat(html.indexOf("href=\"/dashboard\"")).isLessThan(html.indexOf("href=\"/platform/holidays\""));
        assertThat(html.indexOf("href=\"/platform/holidays\"")).isLessThan(html.indexOf("data-group="));
    }
}
