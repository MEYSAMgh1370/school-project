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
 * قالب «بات بله‌ی مدرسه» (/bale-bot) بدون Spring و Docker: فیلد توکنِ password، اقدامِ اصلیِ «ثبت بات»، چهار
 * حالتِ صفحه (بدون بات / فعال / غیرفعال / خطا)، modal تأیید حذف با پیامد، نبودنِ سوییچ API کسب‌وکار در PR 1 و
 * اینکه توکن هرگز به صفحه برنمی‌گردد. رفتار Alpine در مرورگر راستی‌آزمایی می‌شود؛ منطق خالص در
 * src/test/js/bale-bot-logic.test.js است.
 */
class BaleBotPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر مدرسه", List.of(), Map.of(), false);

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("bale-bot.jte", Map.of("navbar", NAVBAR), output);
        return output.toString();
    }

    private static int count(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void pageHasTokenFieldAndPrimaryAction() {
        String html = render();

        assertThat(html).contains("x-data=\"baleBotPage()\"").contains("<title>بات بله‌ی مدرسه - ");
        assertThat(html).contains("type=\"password\"").contains("autocomplete=\"off\"");
        assertThat(html).contains("id=\"baleBotRegister\"").contains("ثبت بات</button>");
        // اقدامِ اصلیِ صفحه فقط یکی است (modal حذف از is-danger استفاده می‌کند)
        assertThat(count(html, "button is-link")).isEqualTo(1);
        // چهار حالت در Alpine
        assertThat(html).contains("id=\"baleBotNone\"").contains("id=\"baleBotActive\"")
                .contains("id=\"baleBotDisabled\"").contains("x-show=\"error\"");
        assertThat(html).contains("@botfather");
        assertThat(html).contains("توکن بات نامعتبر شده است؛ تا اصلاح آن، اولیا از بات کلی سرویس می‌گیرند");
        assertThat(html).contains("تعویض توکن").contains("حذف بات");
        assertThat(html).contains("id=\"baleBotDeleteTitle\"").contains("با بات کلی سرویس پیام می‌گیرد")
                .contains("باید خودشان بات کلی را در بله باز کنند")
                .doesNotContain("به اعضای متصل خبر می‌دهد");
        // «حذف بات» در حالت غیرفعال هم هست (همان تأیید)
        assertThat(html).contains("id=\"baleBotDeleteDisabled\"");
        assertThat(count(html, "@click=\"askRemove()\"")).isEqualTo(2);
        // کنترل بر اساس Permission پنهان یا غیرفعال نمی‌شود؛ سوییچ business در PR 1 نیست
        assertThat(html).doesNotContain(":disabled=\"!permissions").doesNotContain("business");
        assertThat(html).doesNotContain("margin-left").doesNotContain("margin-right");
    }

    @Test
    void registrationWarnsThatMembersMustRelinkAndActiveStateShowsTheLinkToShare() {
        String html = render();

        assertThat(html).contains("id=\"baleBotRelinkWarning\"")
                .contains("هیچ پیامی از این مدرسه (غیبت، فاکتور، یادآوری) دریافت نمی‌کنند")
                .contains("شماره‌ی خود را به اشتراک بگذارند");
        assertThat(html).contains("id=\"baleBotReplaceWarning\"").contains("اگر بات دیگری ثبت کنید");
        // هشدار داخل فرمِ توکن و پیش از دکمه‌ی submit (که در هدر است) دیده می‌شود
        assertThat(html.indexOf("id=\"baleBotRelinkWarning\"")).isGreaterThan(html.indexOf("id=\"baleBotForm\""));
        assertThat(html).contains("این لینک را برای اولیا بفرستید");
        assertThat(html).doesNotContain("همه‌چیز مثل قبل");
    }

    @Test
    void tokenIsNeverRenderedBack() throws IOException {
        String html = render();

        // حالت فعال فقط username و link را می‌بندد
        assertThat(html).contains("x-text=\"'@' + bot.username\"").contains(":href=\"bot.link\"");
        assertThat(html).doesNotContain("bot.token").doesNotContain("x-model=\"bot");
        // تنها ورودیِ توکن به token (حالت محلی فرم) bind است و هرگز مقدارِ اولیه ندارد
        assertThat(html).contains("x-model=\"token\"").doesNotContain("value=\"");
        try (InputStream in = BaleBotPageRenderTest.class.getResourceAsStream("/static/js/bale-bot.js")) {
            assertThat(in).isNotNull();
            String js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(js).contains("this.token = ''").doesNotContain("localStorage.setItem");
        }
    }
}
