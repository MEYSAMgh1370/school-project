package com.example.roshdeandishe;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.shared.FinanceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FinanceFlowIntegrationTest {

    @Autowired
    private FinanceFacade financeFacade;

    @Autowired
    private AcademicFacade academicFacade;

    @Test
    void tuitionFeeUpsertIsIdempotent() {
        Long schoolId = 9101L;

        financeFacade.setTuitionFee(schoolId, new BigDecimal("500000.00"));
        assertThat(financeFacade.getTuitionFee(schoolId).orElseThrow().amount())
                .isEqualByComparingTo("500000.00");

        // دومین فراخوانی باید همان ردیف را به‌روزرسانی کند، نه ردیف تکراری بسازد
        financeFacade.setTuitionFee(schoolId, new BigDecimal("750000.00"));
        assertThat(financeFacade.getTuitionFee(schoolId).orElseThrow().amount())
                .isEqualByComparingTo("750000.00");
    }

    @Test
    void settingNonPositiveTuitionFeeThrows() {
        assertThatThrownBy(() -> financeFacade.setTuitionFee(9102L, new BigDecimal("-1")))
                .isInstanceOf(FinanceException.class);
        assertThatThrownBy(() -> financeFacade.setTuitionFee(9102L, BigDecimal.ZERO))
                .isInstanceOf(FinanceException.class);
        assertThatThrownBy(() -> financeFacade.setTuitionFee(9102L, null))
                .isInstanceOf(FinanceException.class);
    }

    @Test
    void issuingInvoiceUsesConfiguredTuitionFee() {
        Long schoolId = 9103L;
        financeFacade.setTuitionFee(schoolId, new BigDecimal("1200000.00"));

        financeFacade.issueTuitionInvoice(schoolId, 42L);

        List<InvoiceInfo> invoices = financeFacade.listInvoices(schoolId);
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).studentId()).isEqualTo(42L);
        assertThat(invoices.get(0).amount()).isEqualByComparingTo("1200000.00");
    }

    @Test
    void issuingInvoiceWithoutTuitionFeeIsSkipped() {
        Long schoolId = 9104L;

        // تصمیم صریح: بدون شهریه‌ی مصوب، فاکتوری صادر نمی‌شود و خطایی هم رخ نمی‌دهد
        financeFacade.issueTuitionInvoice(schoolId, 42L);

        assertThat(financeFacade.listInvoices(schoolId)).isEmpty();
    }

    @Test
    void enrollingStudentEventuallyIssuesInvoiceWithRealPrice() {
        Long schoolId = 9105L;
        financeFacade.setTuitionFee(schoolId, new BigDecimal("2500000.00"));
        Long classId = academicFacade.createClass(schoolId, "پایه پنجم - الف", null, "الف").value();

        academicFacade.enrollStudent(
                "finance-flow-student@example.com", "Finance Flow Student", schoolId, classId
        );

        // StudentEnrolledEvent → StudentEnrolledEventListener → فاکتور، همه async بعد از commit
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<InvoiceInfo> invoices = financeFacade.listInvoices(schoolId);
            assertThat(invoices).hasSize(1);
            assertThat(invoices.get(0).amount()).isEqualByComparingTo("2500000.00");
        });
    }
}
