package com.example.roshdeandishe;

import com.example.roshdeandishe.web.model.DashboardModel;
import com.example.roshdeandishe.web.model.MembershipRow;
import com.example.roshdeandishe.web.model.NavbarModel;
import com.example.roshdeandishe.web.model.TuitionRow;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * بخش «وضعیت شهریه» پیشخوانِ مدیر را مستقیم (بدون Spring/Docker) رندر می‌کند و محاسبه‌ی
 * درصدِ پرداخت را می‌سنجد.
 */
class DashboardTuitionRenderTest {

    private static final NavbarModel NAVBAR = new NavbarModel("مدیر",
            List.of(new NavbarModel.SchoolOption(7L, "دبستان مهر", List.of("SCHOOL_ADMIN"))));

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

    private String render(List<TuitionRow> tuition) {
        DashboardModel model = new DashboardModel("مدیر", "admin@example.com", null,
                List.of(new MembershipRow(7L, "دبستان مهر", "SCHOOL_ADMIN", true)), List.of(), false, List.of(),
                tuition);
        StringOutput output = new StringOutput();
        engine.render("dashboard.jte", Map.of("model", model, "navbar", NAVBAR), output);
        return output.toString();
    }

    @Test
    void showsThePaidPercentageForTheSelectedAdminSchoolOnly() {
        String html = render(List.of(new TuitionRow(7L, "دبستان مهر", 3, 72)));

        assertThat(html).contains("وضعیت شهریه", "Calendar.digits('72')", "value=\"72\"");
        assertThat(html).contains("$store.nav.selectedRole === 'SCHOOL_ADMIN'")
                .contains("$store.nav.selectedSchoolId === 7");
    }

    @Test
    void aSchoolWithoutInvoicesSaysSoInsteadOfZeroPercent() {
        String html = render(List.of(new TuitionRow(7L, "دبستان مهر", 0, 0)));

        assertThat(html).contains("هنوز فاکتور شهریه‌ای صادر نشده است").doesNotContain("<progress");
    }

    @Test
    void nonAdminsGetNoTuitionSection() {
        assertThat(render(List.of())).doesNotContain("وضعیت شهریه");
    }

    @Test
    void percentageIsAFlooredRatioClampedToZeroToHundred() {
        assertThat(TuitionRow.of(1L, "م", 2, new BigDecimal("1000"), new BigDecimal("720")).percentPaid()).isEqualTo(72);
        // ۹۹٫۹٪ نباید «۱۰۰٪» نشان داده شود
        assertThat(TuitionRow.of(1L, "م", 1, new BigDecimal("1000"), new BigDecimal("999")).percentPaid()).isEqualTo(99);
        assertThat(TuitionRow.of(1L, "م", 1, new BigDecimal("1000"), new BigDecimal("1000")).percentPaid()).isEqualTo(100);
        assertThat(TuitionRow.of(1L, "م", 1, BigDecimal.ZERO, BigDecimal.ZERO).percentPaid()).isZero();
    }
}
