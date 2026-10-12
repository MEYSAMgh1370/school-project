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
 * قالب «گزارش‌های سؤال پلتفرم» (/platform/question-reports) بدون Spring و Docker: صفِ بدونِ اقدامِ اصلیِ هدر،
 * فیلترها، اقدام‌های ردیف، یک modalِ مشترک، نبودنِ school-scope و X-School-ID و نبودنِ ویژگیِ فیزیکیِ راست/چپ.
 * رفتارِ Alpine در مرورگر راستی‌آزمایی می‌شود.
 */
class PlatformQuestionReportsPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر پلتفرم", List.of(), Map.of(), true);

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("platform-question-reports.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = PlatformQuestionReportsPageRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void theQueueHasNoHeaderActionAndTheModalHoldsTheOnlyPrimaryButton() {
        String html = render();

        assertThat(html).contains("x-data=\"platformQuestionReportsPage()\"").contains("<title>گزارش‌های سؤال پلتفرم - ");
        assertThat(count(html, "button is-link")).isEqualTo(1);
        assertThat(html).contains("id=\"platformReportsEditSubmit\"").contains(">ثبت</button>");
        assertThat(html).doesNotContain(":disabled=");
        assertThat(html).contains("id=\"platformReportsSearch\"", "id=\"platformReportsStatus\"", "x-model=\"filterStatus\"");
        assertThat(html).contains("openEdit(item, 'REVIEWED')", "openEdit(item, 'FIXED')", "openEdit(item, 'CLOSED')");
        assertThat(html).contains("table-container").contains("x-math=\"item.questionText\"");
        assertThat(html).contains("maxlength=\"1000\"").contains("اصلاحِ خودِ سؤال جداگانه انجام می‌شود");
        assertThat(count(html, "window.ModalFocus.trap($event, $el)")).isEqualTo(1);
    }

    @Test
    void errorAndSuccessShareOneNoticeAreaAndEmptyStateTeaches() {
        String html = render();

        assertThat(count(html, "x-show=\"error\"")).isEqualTo(1);
        assertThat(html).contains("notification is-success\" role=\"status\" x-cloak x-show=\"notice\"");
        assertThat(html).contains("هنوز هیچ مدرسه‌ای مشکلی در سؤال‌های پلتفرم گزارش نکرده است");
        assertThat(html).contains("در حال بارگذاری...");
    }

    @Test
    void thePageIsPlatformScopedAndTheScriptKeepsCsrfAndPlatformUrls() throws IOException {
        String html = render();

        assertThat(html).doesNotContain("x-if=\"schoolId\"")
                .doesNotContain("مدرسه‌ای انتخاب نشده").doesNotContain("/js/school-api.js");
        assertThat(html.indexOf("/webjars/katex/0.16.22/dist/katex.min.js")).isLessThan(html.indexOf("/js/math.js"));
        assertThat(html.indexOf("/js/platform-question-reports.js")).isLessThan(html.indexOf("/webjars/alpinejs/"));
        assertThat(html).contains("href=\"/platform/question-reports\"");
        String js = resource("/static/js/platform-question-reports.js");
        assertThat(js).doesNotContain("X-School-ID").doesNotContain("SchoolApi").doesNotContain("selectedSchoolId");
        assertThat(js).contains("'X-XSRF-TOKEN'").contains("method !== 'GET'")
                .contains("/api/platform/question-reports").contains("/status`")
                .contains("endsWith('permission-denied')").contains("invalid-report-transition")
                .contains("گزارش به‌روزرسانی شد.");
    }

    @Test
    void noPhysicalLeftRightBrowserDialogsOrInlineStyle() throws IOException {
        String html = render();

        assertThat(html).doesNotContainPattern("(?i)(margin|padding|border)-(left|right)")
                .doesNotContainPattern("(?i)text-align:\\s*(left|right)")
                .doesNotContain("is-pulled-left").doesNotContain("is-pulled-right")
                .doesNotContain("<style");
        String page = html.substring(html.indexOf("x-data=\"platformQuestionReportsPage()\""));
        assertThat(page).doesNotContain("alert(").doesNotContainPattern("(?<![.\\w])confirm\\(");
        assertThat(resource("/static/js/platform-question-reports.js"))
                .doesNotContainPattern("(?i)\\b(margin|padding|border|text-align)-?(left|right)\\b")
                .doesNotContainPattern("(?<![.\\w])alert\\(")
                .doesNotContainPattern("(?<![.\\w])confirm\\(");
    }
}
