package com.example.roshdeandishe;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * پوسته‌ی layout/empty-state.jte را مستقیم رندر می‌کند (بدون Spring و بدون
 * Testcontainers) تا یک نکته‌ی مشخص را قفل کند: پارامتر `show` داخل
 * attributeِ x-show می‌نشیند و JTE در آن context، `&` را escape می‌کند.
 *
 * چرا تست دارد: عبارت‌های Alpine ما `&&` دارند. اگر روزی escapeِ JTE یا
 * contentType عوض شود و `&&` به چیزی برود که مرورگر به `&&` برنمی‌گرداند،
 * همه‌ی پانزده حالتِ خالی بی‌صدا همیشه-نمایان یا همیشه-پنهان می‌شوند — یعنی
 * خرابی‌ای که کامپایل شدن JTE اصلاً نشانش نمی‌دهد.
 */
class EmptyStatePartialRenderTest {

    private String render(Map<String, Object> params) {
        TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);
        TemplateOutput output = new StringOutput();
        engine.render("layout/empty-state.jte", params, output);
        return output.toString();
    }

    @Test
    void alpineConditionSurvivesAttributeEscaping() {
        String html = render(Map.of(
                "show", "!loading && classes.length === 0",
                "message", (gg.jte.Content) o -> o.writeContent("هنوز کلاسی ثبت نشده است."),
                "art", "list"));

        // `&&` may be emitted literally or as `&amp;&amp;`; both decode to `&&`
        // when the browser hands the attribute to Alpine. What must NOT happen
        // is the condition being dropped, truncated, or turned into `&amp;` only.
        String xShow = html.replaceAll("(?s).*x-show=\"([^\"]*)\".*", "$1");
        String decoded = xShow.replace("&amp;", "&").replace("&#34;", "\"");
        assertThat(decoded).isEqualTo("!loading && classes.length === 0");
    }

    @Test
    void artIsDecorativeAndTextIsRendered() {
        String html = render(Map.of(
                "show", "!loading && members.length === 0",
                "message", (gg.jte.Content) o -> o.writeContent("هنوز عضوی ثبت نشده است."),
                "art", "people"));

        assertThat(html).contains("aria-hidden=\"true\"");
        assertThat(html).contains("<svg");
        assertThat(html).contains("هنوز عضوی ثبت نشده است.");
        assertThat(html).contains("app-empty__text");
    }

    @Test
    void artNoneOmitsTheIllustrationEntirely() {
        String html = render(Map.of(
                "show", "items.length === 0",
                "message", (gg.jte.Content) o -> o.writeContent("خالی است."),
                "art", "none"));

        assertThat(html).doesNotContain("<svg");
        assertThat(html).doesNotContain("app-empty__art");
        assertThat(html).contains("خالی است.");
    }

    @Test
    void messageContentMayCarryMarkupSuchAsLinks() {
        String html = render(Map.of(
                "show", "rosterStudents().length === 0",
                "message", (gg.jte.Content) o -> o.writeContent(
                        "این کلاس دانش‌آموزی ندارد. از صفحه‌ی <a href=\"/students\">دانش‌آموزان</a> اضافه کنید."),
                "art", "people"));

        // Content params are written through, not escaped — links in the
        // existing empty-state copy (grades.jte, dashboard.jte) depend on this.
        assertThat(html).contains("<a href=\"/students\">دانش‌آموزان</a>");
    }
}
