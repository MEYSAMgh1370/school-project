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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * قالب‌های باکس سؤالات (openspec/changes/question-box.md) بدون Spring و Docker: ساختار صفحه، اقدام اصلی در هدر،
 * حالت‌های خالیِ راهنما، modalها، نبودنِ ناوبار در صفحه‌ی چاپ و نبودنِ ویژگیِ فیزیکیِ راست/چپ در CSS. رفتار Alpine
 * در مرورگر راستی‌آزمایی می‌شود؛ پروژه تست JS ندارد.
 */
class QuestionBoxPagesRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("معلم نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("TEACHER"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(String template, Map<String, Object> params) {
        StringOutput output = new StringOutput();
        engine.render(template, params, output);
        return output.toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = QuestionBoxPagesRenderTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theBoxPageHasThePrimaryActionInTheHeaderAndTheQuestionModals() {
        String html = render("question-box.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("x-data=\"questionBoxPage()\"", "/js/question-box-common.js", "/js/question-box.js",
                "/css/question-box.css", "/webjars/katex/0.16.22/dist/katex.min.js");
        assertThat(html.indexOf("id=\"questionAdd\"")).isLessThan(html.indexOf("id=\"qbSearch\""));
        assertThat(html).contains("افزودن سؤال", "id=\"qbType\"", "id=\"qbFormType\"", "id=\"qbFormMode\"",
                "id=\"qbCorrect\"", "x-qbimg=\"{ kind: 'question', id: image.id }\"");
        // حالت خالی راه را نشان می‌دهد
        assertThat(html).contains("باکس هنوز سؤالی ندارد", "با «افزودن سؤال»");
        // حذف با modal تأیید، نه confirm()
        assertThat(html).contains("id=\"qbDeleteTitle\"").doesNotContain("confirm(");
    }

    @Test
    void theSheetsPageHasTabsABuilderWithSectionsAPickerAndHeaderForm() {
        String html = render("question-sheets.jte", Map.of("navbar", NAVBAR));

        assertThat(html).contains("x-data=\"questionSheetsPage()\"", "/js/question-sheets.js");
        assertThat(html).contains("id=\"sheetNew\"", "id=\"headerNew\"", "id=\"sheetSave\"", "ذخیره‌ی برگه");
        assertThat(html).contains("افزودن بخش", "عنوان برگه", "id=\"sheetHeader\"", "چاپ پاسخنامه در صفحه‌ی جدا");
        assertThat(html).contains("باکس سؤالات</a></li>", "بانک سؤال</a></li>", "id=\"pickerTopic\"");
        assertThat(html).contains("id=\"headerMode\"", "id=\"headerLayout\"", "x-model=\"headerForm.fields\"");
        assertThat(html).contains("هنوز برگه‌ای نساخته‌اید", "هنوز سربرگی ندارید");
        // x-model روی شیء null نباید در modalهای بسته ارزیابی شود
        assertThat(html).contains("<template x-if=\"picker\">", "<template x-if=\"headerForm\">");
    }

    @Test
    void thePrintPageHasNoNavbarAndCarriesTheSchoolNamesAndTheSheetId() {
        String html = render("question-sheet-print.jte", Map.of("navbar", NAVBAR, "sheetId", 7L));

        assertThat(html).contains("x-data=\"questionSheetPrintPage()\"", "data-sheet-id=\"7\"",
                "data-school-id=\"1\"", "data-school-name=\"مدرسه‌ی نمونه\"", "/js/question-sheet-print.js");
        assertThat(html).contains("id=\"sheetPrint\"", "no-print", "Save as PDF");
        assertThat(html).doesNotContain("app-sidebar").doesNotContain("schoolNavbar");
        // سه شکل سربرگ و پاسخنامه‌ی صفحه‌ی جدا
        assertThat(html).contains("qb-sheet-header--", "qb-answer-key", "qb-answer-space");
    }

    @Test
    void aColleaguesQuestionOrSheetShowsAWarningAndTheSheetCanBeSavedAsMyOwnCopy() {
        String box = render("question-box.jte", Map.of("navbar", NAVBAR));
        assertThat(box).contains("سؤال همکار شماست", "فقط سازنده یا مدیر");

        String sheets = render("question-sheets.jte", Map.of("navbar", NAVBAR));
        assertThat(sheets).contains("این برگه از همکار شماست", "id=\"sheetCopy\"", "ذخیره به‌عنوان نسخه‌ی جدید");
    }

    @Test
    void theStylesheetPrintsOnA4HidesControlsAndUsesNoPhysicalDirections() throws IOException {
        String css = resource("/static/css/question-box.css");

        assertThat(css).contains("@media print", "size: A4", ".no-print");
        // خطِ جدیدِ متنِ سؤال (الف/ب در چند خط) باید در چاپ و فهرست بماند
        assertThat(css).contains("white-space: pre-line");
        assertThat(css).doesNotContainPattern(Pattern.compile(
                "(margin|padding|border)-(left|right)|(^|[\\s{;])(left|right)\\s*:|text-align:\\s*(left|right)"));
    }
}
