package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.DashboardModel;
import com.example.roshdeandishe.web.model.MembershipRow;
import com.example.roshdeandishe.web.model.NavbarModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * بخش «رویدادهای نزدیک» پیشخوان (openspec/changes/event-reminders.md) را مستقیم (بدون Spring/Docker) رندر می‌کند:
 * بخش client-side است و تا وقتی رویدادی نباشد پنهان می‌ماند؛ داده از endpoint خودسرویس می‌آید، پس برای هر نقش رندر
 * می‌شود و سمت سرور لیستِ خالی برای غیرگیرنده می‌دهد.
 */
class DashboardUpcomingRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("دانش‌آموز",
            List.of(new NavbarModel.SchoolOption(7L, "دبستان مهر", List.of("STUDENT"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String role) {
        DashboardModel model = new DashboardModel("دانش‌آموز", "student@example.com", null,
                List.of(new MembershipRow(7L, "دبستان مهر", role, true)), List.of(), false, List.of(),
                List.of(), List.of());
        StringOutput output = new StringOutput();
        engine.render("dashboard.jte", Map.of("model", model, "navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void theBoxIsAHiddenUntilFilledSectionWithTheEventListAndALinkToThePlan() {
        String html = render("STUDENT");

        assertThat(html).contains("id=\"dashboardUpcoming\"", "x-data=\"dashboardUpcoming()\"",
                "x-show=\"rows.length > 0\"", "رویدادهای نزدیک", "id=\"dashboardUpcomingList\"");
        assertThat(html).contains("x-text=\"row.title\"", "x-text=\"row.date + '، ' + row.relative\"");
        assertThat(html).contains("href=\"/yearly-plan\">برنامه‌ی سالانه</a>");
        // x-cloak: پیش از آماده شدن Alpine چیزی نمایش داده نمی‌شود
        assertThat(html.substring(html.indexOf("id=\"dashboardUpcoming\"") - 200,
                html.indexOf("id=\"dashboardUpcoming\"") + 40)).contains("x-cloak");
    }

    @Test
    void theScriptsNeededByTheBoxAreLoadedInDependencyOrder() {
        String html = render("STUDENT");

        int jalali = html.indexOf("/js/jalali.js");
        int calendar = html.indexOf("/js/calendar.js");
        int dateInput = html.indexOf("/js/date-input.js");
        int api = html.indexOf("/js/school-api.js");
        int logic = html.indexOf("/js/upcoming-events-logic.js");
        int box = html.indexOf("/js/dashboard-upcoming.js");
        assertThat(jalali).isPositive();
        assertThat(jalali).isLessThan(calendar);
        assertThat(calendar).isLessThan(dateInput);
        assertThat(api).isPositive();
        assertThat(logic).isGreaterThan(api).isLessThan(box);
    }

    @Test
    void itIsRenderedForEveryRoleBecauseTheServerDecidesWhoIsARecipient() {
        for (String role : List.of("STUDENT", "PARENT", "TEACHER", "SCHOOL_ADMIN")) {
            assertThat(render(role)).as(role).contains("id=\"dashboardUpcoming\"");
        }
    }

    @Test
    void thePageHasNoBrowserDialogsNoInlineStyleAndNoPhysicalDirections() {
        String html = render("STUDENT");
        String section = html.substring(html.indexOf("id=\"dashboardUpcoming\""),
                html.indexOf("</section>", html.indexOf("id=\"dashboardUpcoming\"")));

        assertThat(section).doesNotContain("alert(", "confirm(", "<style", "style=\"", "margin-left", "margin-right",
                "padding-left", "padding-right", "text-align: left", "text-align: right");
    }
}
