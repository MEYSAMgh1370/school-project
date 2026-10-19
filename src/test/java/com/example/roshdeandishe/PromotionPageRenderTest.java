package com.example.roshdeandishe;

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
 * صفحه‌ی «ارتقای پایه» (openspec/changes/academic-year.md، فاز ۳): ساختار قالب، نه رفتارِ Alpine (در مرورگر
 * راستی‌آزمایی شد؛ پروژه تست JS ندارد).
 */
class PromotionPageRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر نمونه",
            List.of(new NavbarModel.SchoolOption(1L, "مدرسه‌ی نمونه", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render() {
        StringOutput output = new StringOutput();
        engine.render("promotion.jte", Map.of("navbar", NAVBAR), output);
        return output.toString().replaceAll("\\s+", " ");
    }

    @Test
    void hasTheFourSectionsAndASinglePrimaryRunAction() {
        String html = render();

        assertThat(html).contains("id=\"promotionPending\"").contains("id=\"promotionGraduates\"")
                .contains("id=\"promotionRun\"").contains("id=\"promotionRuns\"");
        assertThat(html).contains("id=\"promotionRunButton\"").contains("اجرای ارتقا")
                .contains("id=\"promotionPlaceButton\"");
        assertThat(html).contains("/js/promotion.js").contains("/js/modal-focus.js").contains("/js/calendar.js");
    }

    @Test
    void anOutcomeCanBeChosenPerStudentAndOnlyTheOnesThatFitTheGrade() {
        String html = render();

        assertThat(html).contains("value=\"PROMOTE\"").contains("value=\"REPEAT\"").contains("value=\"GRADUATE\"")
                .contains("value=\"LEAVE\"");
        // پایه‌ی آخر فقط فارغ‌التحصیل می‌شود و بقیه ارتقا می‌گیرند؛ سرور هم همین را اجبار می‌کند
        assertThat(html).contains(":disabled=\"item.defaultOutcome === 'GRADUATE'\"")
                .contains(":disabled=\"item.defaultOutcome !== 'GRADUATE'\"");
    }

    @Test
    void runningAndUndoingAskForConfirmationInAModalNotAConfirmDialog() {
        String html = render();

        assertThat(html).contains("id=\"promotionConfirmTitle\"").contains("role=\"dialog\"")
                .contains("id=\"promotionConfirmButton\"").contains("openConfirm('undo', run, $event)")
                .contains("openConfirm('run', null, $event)");
        assertThat(html).doesNotContain("confirm(").doesNotContain("alert(");
    }

    @Test
    void explainsTheMissingYearAndUnattributedDataInsteadOfGuessing() {
        String html = render();

        assertThat(html).contains("id=\"promotionNoYear\"").contains("href=\"/settings\"")
                .contains("id=\"promotionUnattributed\"");
        assertThat(html).contains("دانش‌آموزی برای ارتقا از این سال نمانده است");
    }

    @Test
    void theNavigationLinksToThePageForTheSchoolAdminOnly() {
        NavCatalog.Link link = NavCatalog.groups().stream().flatMap(g -> g.links().stream())
                .filter(l -> l.href().equals("/promotion")).findFirst().orElseThrow();

        assertThat(link.roles()).containsExactly("SCHOOL_ADMIN");
        assertThat(link.permissions()).containsExactly("ACADEMIC_YEAR_MANAGE");
        assertThat(link.shortcutRoles()).isEmpty();
    }
}
