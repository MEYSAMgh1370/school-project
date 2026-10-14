package com.example.roshdeandishe;

import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.internal.DiscountSweepHook;
import com.example.roshdeandishe.finance.FinanceFacade.InstallmentEdit;
import com.example.roshdeandishe.finance.FinanceFacade.InstallmentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InstallmentStatus;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * طرحِ اقساطِ فاکتور شهریه (openspec/changes/tuition-installments.md، بخش دوم): تقسیمِ مساوی با سررسیدِ
 * ماهِ شمسی، ویرایشِ دستی، تخصیصِ پرداخت به قدیمی‌ترین سررسید، بازشمارش، تخفیف و وضعیتِ مشتق‌شده.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceInstallmentsFlowIntegrationTest {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinanceFacade financeFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SchoolFacade schoolFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@roshdeandishe.test";
    }

    /** مدرسه‌ی واقعی و تازه (تنظیمِ اقساط فقط روی مدرسه‌ی واقعی ممکن است). */
    private Long newSchool(boolean installmentsEnabled) {
        SchoolFacade.SchoolId id = schoolFacade.createSchool(
                "مدرسه‌ی اقساط " + UUID.randomUUID(), "Asia/Tehran", "fa-IR",
                SchoolFacade.SchoolLevel.ELEMENTARY, null);
        if (installmentsEnabled) {
            schoolFacade.setInstallmentsEnabled(id, true);
        }
        return id.value();
    }

    private InvoiceInfo newInvoice(Long schoolId, String amount) {
        financeFacade.setTuitionFee(schoolId, new BigDecimal(amount));
        financeFacade.issueTuitionInvoice(schoolId, 77L);
        return financeFacade.listInvoices(schoolId).get(0);
    }

    private InvoiceInfo reload(Long schoolId, Long invoiceId) {
        return financeFacade.listInvoices(schoolId).stream()
                .filter(i -> i.id().equals(invoiceId)).findFirst().orElseThrow();
    }

    private BigDecimal paidSum(Long schoolId, Long invoiceId) {
        return financeFacade.listInstallments(schoolId, invoiceId).stream()
                .map(InstallmentInfo::amountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate soon() {
        return LocalDate.now(TEHRAN).plusDays(10);
    }

    private static Object errorOf(Runnable action) {
        try {
            action.run();
        } catch (FinanceException e) {
            return e.error();
        }
        throw new AssertionError("FinanceException انتظار می‌رفت");
    }

    @Test
    void generateSplitsEvenlyLastAbsorbsRemainderWithJalaliMonthDates() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "10000000.00");

        // ۱۴ آذر ۱۴۰۵؛ آذر/دی/بهمن هر کدام ۳۰ روز
        List<InstallmentInfo> plan = financeFacade.generateInstallments(
                schoolId, invoice.id(), 3, LocalDate.of(2026, 12, 5), 1);

        assertThat(plan).hasSize(3);
        assertThat(plan.get(0).amount()).isEqualByComparingTo("3333333.33");
        assertThat(plan.get(1).amount()).isEqualByComparingTo("3333333.33");
        assertThat(plan.get(2).amount()).isEqualByComparingTo("3333333.34");
        assertThat(plan).extracting(InstallmentInfo::seq).containsExactly(1, 2, 3);
        assertThat(plan).extracting(InstallmentInfo::dueDate).containsExactly(
                LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 4), LocalDate.of(2027, 2, 3));
        assertThat(plan.stream().map(InstallmentInfo::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10000000.00");

        // سررسیدِ فاکتور = آغازِ روزِ پس از آخرین قسط به وقتِ مدرسه
        assertThat(reload(schoolId, invoice.id()).dueDate())
                .isEqualTo(LocalDate.of(2027, 2, 4).atStartOfDay(TEHRAN).toInstant());

        // فاصله‌ی دوماهه
        List<InstallmentInfo> everyTwo = financeFacade.generateInstallments(
                schoolId, invoice.id(), 2, LocalDate.of(2026, 12, 5), 2);
        assertThat(everyTwo).extracting(InstallmentInfo::dueDate).containsExactly(
                LocalDate.of(2026, 12, 5), LocalDate.of(2027, 2, 3));
    }

    @Test
    void generateRejectsBadInput() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "0.02");

        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 0, soon(), 1)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 61, soon(), 1)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 1, soon(), 13)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 1, soon(), 0)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 1, null, 1)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        // ۰٫۰۲ بین سه قسط: هر سهم ۰٫۰۰ می‌شود
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 3, soon(), 1)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(financeFacade.listInstallments(schoolId, invoice.id())).isEmpty();

        // ولی دو قسطِ ۰٫۰۱ جا می‌شود
        assertThat(financeFacade.generateInstallments(schoolId, invoice.id(), 2, soon(), 1))
                .extracting(InstallmentInfo::amount).allSatisfy(a -> assertThat(a).isEqualByComparingTo("0.01"));
    }

    @Test
    void settingOffBlocksGenerateAndEditButUnknownSchoolIsTreatedAsOff() {
        Long off = newSchool(false);
        InvoiceInfo invoice = newInvoice(off, "900.00");
        assertThat(errorOf(() -> financeFacade.generateInstallments(off, invoice.id(), 3, soon(), 1)))
                .isInstanceOf(FinanceError.InstallmentsNotEnabled.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(off, invoice.id(), List.of())))
                .isInstanceOf(FinanceError.InstallmentsNotEnabled.class);

        Long fake = 9_410L;
        InvoiceInfo fakeInvoice = newInvoice(fake, "900.00");
        assertThat(errorOf(() -> financeFacade.generateInstallments(fake, fakeInvoice.id(), 3, soon(), 1)))
                .isInstanceOf(FinanceError.InstallmentsNotEnabled.class);
    }

    @Test
    void editAmountsAndDatesRequiresExactUnpaidSetAndSum() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "9000000.00");
        LocalDate base = soon();
        List<InstallmentInfo> plan = financeFacade.generateInstallments(schoolId, invoice.id(), 3, base, 1);
        assertThat(plan).extracting(InstallmentInfo::amount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("3000000.00"));
        Long a =plan.get(0).id();
        Long b = plan.get(1).id();
        Long c = plan.get(2).id();

        // جمعِ غلط: expected همراه خطاست
        Object mismatch = errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, new BigDecimal("2000000"), base),
                new InstallmentEdit(b, new BigDecimal("3000000"), base.plusDays(30)),
                new InstallmentEdit(c, new BigDecimal("3000000"), base.plusDays(60)))));
        assertThat(mismatch).isInstanceOfSatisfying(FinanceError.InstallmentsSumMismatch.class,
                e -> assertThat(e.expected()).isEqualByComparingTo("9000000.00"));

        // قسطِ جاافتاده، ناشناخته، تکراری، مبلغِ صفر/اعشارِ زیاد/تهی، تاریخِ تهی
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, new BigDecimal("4500000"), base),
                new InstallmentEdit(b, new BigDecimal("4500000"), base.plusDays(30))))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, new BigDecimal("3000000"), base),
                new InstallmentEdit(b, new BigDecimal("3000000"), base.plusDays(30)),
                new InstallmentEdit(999_999_999L, new BigDecimal("3000000"), base.plusDays(60))))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, new BigDecimal("3000000"), base),
                new InstallmentEdit(a, new BigDecimal("3000000"), base.plusDays(30)),
                new InstallmentEdit(c, new BigDecimal("3000000"), base.plusDays(60))))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, BigDecimal.ZERO, base),
                new InstallmentEdit(b, new BigDecimal("4500000"), base.plusDays(30)),
                new InstallmentEdit(c, new BigDecimal("4500000"), base.plusDays(60))))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, new BigDecimal("3000000.001"), base),
                new InstallmentEdit(b, new BigDecimal("3000000"), base.plusDays(30)),
                new InstallmentEdit(c, new BigDecimal("2999999.999"), base.plusDays(60))))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, null, base),
                new InstallmentEdit(b, new BigDecimal("3000000"), base.plusDays(30)),
                new InstallmentEdit(c, new BigDecimal("6000000"), null)))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);

        // ویرایشِ درست: مبلغ‌ها و یک سررسید؛ سررسیدِ a به پس از c می‌رود پس seq بازشماری می‌شود
        LocalDate late = base.plusDays(200);
        List<InstallmentInfo> edited = financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(a, new BigDecimal("2000000"), late),
                new InstallmentEdit(b, new BigDecimal("3000000"), base.plusDays(30)),
                new InstallmentEdit(c, new BigDecimal("4000000"), base.plusDays(60))));
        assertThat(edited).extracting(InstallmentInfo::id).containsExactly(b, c, a);
        assertThat(edited).extracting(InstallmentInfo::seq).containsExactly(1, 2, 3);
        assertThat(edited).extracting(InstallmentInfo::amount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3000000"), new BigDecimal("4000000"), new BigDecimal("2000000"));
        assertThat(reload(schoolId, invoice.id()).dueDate())
                .isEqualTo(late.plusDays(1).atStartOfDay(TEHRAN).toInstant());

        // قسطِ کاملاً پرداخت‌شده ثابت است: پرداخت ۳٬۰۰۰٬۰۰۰ قسطِ b را (نخستین سررسید) می‌بندد
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("3000000"), PaymentMethod.CASH, null);
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(b, new BigDecimal("3000000"), base),
                new InstallmentEdit(c, new BigDecimal("3000000"), base.plusDays(60)),
                new InstallmentEdit(a, new BigDecimal("3000000"), late)))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
        // جمعِ لازم حالا مانده‌ی بدونِ قسطِ ثابت است: ۶٬۰۰۰٬۰۰۰
        List<InstallmentInfo> afterPaid = financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(c, new BigDecimal("5000000"), base.plusDays(60)),
                new InstallmentEdit(a, new BigDecimal("1000000"), late)));
        assertThat(afterPaid).extracting(InstallmentInfo::status)
                .containsExactly(InstallmentStatus.PAID, InstallmentStatus.UNPAID, InstallmentStatus.UNPAID);
        assertThat(paidSum(schoolId, invoice.id())).isEqualByComparingTo(reload(schoolId, invoice.id()).amountPaid());
    }

    @Test
    void paymentFillsFirstInstallmentThenSpillsToTheNext() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "10000000.00");
        financeFacade.generateInstallments(schoolId, invoice.id(), 3, soon(), 1);

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("4000000"), PaymentMethod.CASH, null);

        List<InstallmentInfo> plan = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(plan.get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(plan.get(0).amountPaid()).isEqualByComparingTo("3333333.33");
        assertThat(plan.get(1).status()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(plan.get(1).amountPaid()).isEqualByComparingTo("666666.67");
        assertThat(plan.get(2).status()).isEqualTo(InstallmentStatus.UNPAID);
        assertThat(plan.get(2).amountPaid()).isEqualByComparingTo("0");
        InvoiceInfo after = reload(schoolId, invoice.id());
        assertThat(after.status()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(paidSum(schoolId, invoice.id())).isEqualByComparingTo(after.amountPaid());

        // تسویه‌ی کامل: همه PAID و فاکتور PAID
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("6000000"), PaymentMethod.CASH, null);
        assertThat(financeFacade.listInstallments(schoolId, invoice.id()))
                .allSatisfy(i -> assertThat(i.status()).isEqualTo(InstallmentStatus.PAID));
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void regenerateKeepsPaidInstallmentAndResplitsTheRest() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "9000000.00");
        LocalDate base = soon();
        List<InstallmentInfo> original = financeFacade.generateInstallments(schoolId, invoice.id(), 3, base, 1);
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("3000000"), PaymentMethod.CASH, null);

        List<InstallmentInfo> two = financeFacade.generateInstallments(schoolId, invoice.id(), 2, base.plusDays(60), 1);
        assertThat(two).hasSize(3);
        assertThat(two.get(0).id()).isEqualTo(original.get(0).id());
        assertThat(two.get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(two.get(1).amount()).isEqualByComparingTo("3000000.00");
        assertThat(two.get(2).amount()).isEqualByComparingTo("3000000.00");
        assertThat(two).extracting(InstallmentInfo::seq).containsExactly(1, 2, 3);

        // پرداختِ جزئی روی قسطِ بعدی، سپس بازشمارش به چهار قسط: پرداختی حفظ و از نو تخصیص می‌یابد
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("1000000"), PaymentMethod.CASH, null);
        List<InstallmentInfo> four = financeFacade.generateInstallments(schoolId, invoice.id(), 4, base.plusDays(60), 1);
        assertThat(four).hasSize(5);
        assertThat(four.get(0).id()).isEqualTo(original.get(0).id());
        assertThat(four.subList(1, 5)).extracting(InstallmentInfo::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1500000"), new BigDecimal("1500000"),
                        new BigDecimal("1500000"), new BigDecimal("1500000"));
        assertThat(four.get(1).amountPaid()).isEqualByComparingTo("1000000");
        assertThat(four.get(1).status()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(paidSum(schoolId, invoice.id())).isEqualByComparingTo("4000000");
        assertThat(reload(schoolId, invoice.id()).amountPaid()).isEqualByComparingTo("4000000");

        // مانده دارد: صفر قسط مجاز نیست
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 0, base, 1)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
    }

    @Test
    void discountResplitsUnpaidInstallmentsAndFullRemainderDiscountDeletesThem() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "9000000.00");
        LocalDate base = soon();
        List<InstallmentInfo> original = financeFacade.generateInstallments(schoolId, invoice.id(), 3, base, 1);
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("3000000"), PaymentMethod.CASH, null);

        // قابل پرداخت ۶٬۰۰۰٬۰۰۰، قسطِ پرداخت‌شده ۳٬۰۰۰٬۰۰۰ → دو قسطِ باقی‌مانده ۱٬۵۰۰٬۰۰۰ روی همان سررسیدها
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("3000000"), null);
        List<InstallmentInfo> afterDiscount = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(afterDiscount).hasSize(3);
        assertThat(afterDiscount.get(0).id()).isEqualTo(original.get(0).id());
        assertThat(afterDiscount.get(1).amount()).isEqualByComparingTo("1500000.00");
        assertThat(afterDiscount.get(2).amount()).isEqualByComparingTo("1500000.00");
        assertThat(afterDiscount).extracting(InstallmentInfo::dueDate)
                .containsExactlyElementsOf(original.stream().map(InstallmentInfo::dueDate).toList());
        assertThat(paidSum(schoolId, invoice.id())).isEqualByComparingTo("3000000");

        // بخشودنِ کلِ مانده: اقساطِ پرداخت‌نشده حذف می‌شوند و فقط قسطِ پرداخت‌شده می‌ماند
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("3000000"), null);
        List<InstallmentInfo> settled = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(settled).hasSize(1);
        assertThat(settled.get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void discountSmallerThanRemainderKeepsCountWhenShareFitsAndShrinksWhenItDoesNot() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "1.00");
        financeFacade.generateInstallments(schoolId, invoice.id(), 4, soon(), 1);

        // قابل پرداخت ۰٫۰۲ برای ۴ قسط جا ندارد → دو قسطِ ۰٫۰۱
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("0.98"), null);
        List<InstallmentInfo> plan = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(plan).hasSize(2);
        assertThat(plan).extracting(InstallmentInfo::amount)
                .allSatisfy(a -> assertThat(a).isEqualByComparingTo("0.01"));
    }

    @Test
    void overdueInstallmentMakesTheInvoiceOverdueUntilItIsPaid() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        LocalDate threeDaysAgo = LocalDate.now(TEHRAN).minusDays(3);
        financeFacade.generateInstallments(schoolId, invoice.id(), 2, threeDaysAgo, 1);

        List<InstallmentInfo> plan = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(plan.get(0).status()).isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(plan.get(1).status()).isEqualTo(InstallmentStatus.UNPAID);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.OVERDUE);

        // پرداختِ جزئیِ قسطِ دیرشده هنوز OVERDUE است
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("100000"), PaymentMethod.CASH, null);
        assertThat(financeFacade.listInstallments(schoolId, invoice.id()).get(0).status())
                .isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.OVERDUE);

        // قسطِ اول کامل شد؛ قسطِ دوم هنوز سررسید نشده → فاکتور PARTIAL
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("400000"), PaymentMethod.CASH, null);
        assertThat(financeFacade.listInstallments(schoolId, invoice.id()).get(0).status())
                .isEqualTo(InstallmentStatus.PAID);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PARTIAL);
    }

    @Test
    void turningTheSettingOffLaterKeepsAnExistingPlanWorking() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        financeFacade.generateInstallments(schoolId, invoice.id(), 2, soon(), 1);
        schoolFacade.setInstallmentsEnabled(new SchoolFacade.SchoolId(schoolId), false);

        // ساخت/ویرایش مسدود، ولی پرداخت، تخفیف و خواندن روی طرحِ موجود کار می‌کند
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 3, soon(), 1)))
                .isInstanceOf(FinanceError.InstallmentsNotEnabled.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of())))
                .isInstanceOf(FinanceError.InstallmentsNotEnabled.class);

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("200000"), PaymentMethod.CASH, null);
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("100000"), null);
        List<InstallmentInfo> plan = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).amountPaid()).isEqualByComparingTo("200000");
        assertThat(plan.stream().map(InstallmentInfo::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("900000");
        assertThat(paidSum(schoolId, invoice.id())).isEqualByComparingTo("200000");
    }

    @Test
    void foreignSchoolInvoiceIsNotFound() {
        Long schoolId = newSchool(true);
        Long otherSchool = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "1000.00");

        assertThat(errorOf(() -> financeFacade.generateInstallments(otherSchool, invoice.id(), 2, soon(), 1)))
                .isInstanceOf(FinanceError.InvoiceNotFound.class);
        assertThat(errorOf(() -> financeFacade.updateInstallments(otherSchool, invoice.id(), List.of())))
                .isInstanceOf(FinanceError.InvoiceNotFound.class);
        assertThat(errorOf(() -> financeFacade.listInstallments(otherSchool, invoice.id())))
                .isInstanceOf(FinanceError.InvoiceNotFound.class);
        // نسخه‌ی دسته‌ای هم شناسه‌ی مدرسه‌ی دیگر را نمی‌بیند
        financeFacade.generateInstallments(schoolId, invoice.id(), 2, soon(), 1);
        assertThat(financeFacade.listInstallmentsForInvoices(otherSchool, List.of(invoice.id()))).isEmpty();
        assertThat(financeFacade.listInstallmentsForInvoices(schoolId, List.of(invoice.id())))
                .containsOnlyKeys(invoice.id());
    }

    @Test
    void installmentsHttpFlowParentVisibilityAndSelfService() throws Exception {
        schoolFacade.setInstallmentsEnabled(new SchoolFacade.SchoolId(1L), true);
        MockHttpSession adminSession = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(put("/api/admin/schools/1/tuition-fee")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1200000.00}"))
                .andExpect(status().isOk());

        String studentEmail = unique("inst.student");
        registerAndLogin(studentEmail, "دانش‌آموز اقساط");
        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس اقساط\",\"section\":\"ب\",\"teacherId\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk()).andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString()).get("id").asLong();
        MvcResult enrollResult = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"fullName\":\"دانش‌آموز اقساط\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk()).andReturn();
        Long studentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("studentId").asLong();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(financeFacade.listInvoicesForStudents(1L, List.of(studentId))).hasSize(1));
        InvoiceInfo wardInvoice = financeFacade.listInvoicesForStudents(1L, List.of(studentId)).get(0);
        Long wardInvoiceId = wardInvoice.id();

        String parentEmail = unique("inst.parent");
        MockHttpSession parentSession = registerAndLogin(parentEmail, "ولی اقساط");
        MvcResult membershipResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولی اقساط\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk()).andReturn();
        Long parentUserId = objectMapper.readTree(membershipResult.getResponse().getContentAsString())
                .get("userId").asLong();
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/guardians")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianUserId\":" + parentUserId + "}"))
                .andExpect(status().isOk());

        Long otherStudentId = 7_410_000L + System.nanoTime() % 1_000_000L;
        financeFacade.issueTuitionInvoice(1L, otherStudentId);
        Long otherInvoiceId = financeFacade.listInvoicesForStudents(1L, List.of(otherStudentId)).get(0).id();

        String base = "/api/admin/schools/1/invoices/" + wardInvoiceId + "/installments";
        LocalDate first = soon();

        // پیش از ساخت: فهرستِ خالی
        mockMvc.perform(get(base).header("X-School-ID", "1").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(0)));

        // بدنه‌ی خراب: تاریخ نامعتبر، تعدادِ خالی، تاریخِ خالی → 400 (نه 500)
        mockMvc.perform(post(base + "/generate").with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":3,\"firstDueDate\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(base + "/generate").with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstDueDate\":\"" + first + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invalid-installments")));
        mockMvc.perform(post(base + "/generate").with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invalid-installments")));

        // ساخت: سه قسط، فاصله‌ی پیش‌فرض یک ماه
        MvcResult generated = mockMvc.perform(post(base + "/generate")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":3,\"firstDueDate\":\"" + first + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(3)))
                .andExpect(jsonPath("$[0].seq").value(1))
                .andExpect(jsonPath("$[0].status").value("UNPAID"))
                .andExpect(jsonPath("$[0].dueDate").value(first.toString()))
                .andReturn();
        JsonNode plan = objectMapper.readTree(generated.getResponse().getContentAsString());
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode item : plan) {
            total = total.add(item.get("amount").decimalValue());
        }
        assertThat(total).isEqualByComparingTo(wardInvoice.amount());

        // ویرایش با بدنه‌ی ناقص → 400؛ جمعِ غلط → 400 installments-sum-mismatch
        mockMvc.perform(put(base).with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + plan.get(0).get("id").asLong()
                                + ",\"amount\":null,\"dueDate\":\"" + first + "\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invalid-installments")));
        StringBuilder wrongSum = new StringBuilder("{\"items\":[");
        for (int i = 0; i < plan.size(); i++) {
            wrongSum.append(i == 0 ? "" : ",").append("{\"id\":").append(plan.get(i).get("id").asLong())
                    .append(",\"amount\":1,\"dueDate\":\"").append(first.plusDays(i)).append("\"}");
        }
        wrongSum.append("]}");
        mockMvc.perform(put(base).with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(wrongSum.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("installments-sum-mismatch")));

        // ولی: فقط فرزندِ خودش را می‌بیند، ساخت/ویرایش 403
        mockMvc.perform(get(base).header("X-School-ID", "1").session(parentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(3)));
        mockMvc.perform(get("/api/admin/schools/1/invoices/" + otherInvoiceId + "/installments")
                        .header("X-School-ID", "1").session(parentSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invoice-not-found")));
        mockMvc.perform(post(base + "/generate").with(csrf()).header("X-School-ID", "1").session(parentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":2,\"firstDueDate\":\"" + first + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base).with(csrf()).header("X-School-ID", "1").session(parentSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isForbidden());

        // فاکتورِ مدرسه‌ی دیگر: 404
        Long foreignInvoiceId = newInvoice(newSchool(true), "1000.00").id();
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + foreignInvoiceId + "/installments/generate")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":2,\"firstDueDate\":\"" + first + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invoice-not-found")));

        // endpoint خودسرویس: اقساط همراه فاکتور می‌آید؛ فاکتورِ بی‌طرح لیستِ خالی دارد
        financeFacade.recordPayment(1L, wardInvoiceId, new BigDecimal("1.00"), PaymentMethod.CASH, null);
        MvcResult mine = mockMvc.perform(get("/api/me/schools/1/invoices").session(parentSession))
                .andExpect(status().isOk()).andReturn();
        JsonNode mineJson = objectMapper.readTree(mine.getResponse().getContentAsString());
        JsonNode wardNode = null;
        for (JsonNode node : mineJson) {
            if (node.get("studentId").asLong() == studentId) {
                wardNode = node;
            }
        }
        assertThat(wardNode).isNotNull();
        assertThat(wardNode.get("installments")).hasSize(3);
        assertThat(wardNode.get("installments").get(0).get("amountPaid").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(wardNode.get("installments").get(0).get("status").asText()).isEqualTo("PARTIAL");
    }

    // ------------------------------------------------------------------
    // قسطِ بدون سررسید (V31): برداشته شدنِ تخفیف با مانده ولی بدونِ قسطِ پرداخت‌نشده
    // ------------------------------------------------------------------

    /**
     * وضعیتی که فقط با دادهٔ قدیمی/ناسازگار پیش می‌آید (تخفیفِ مشروطِ مهلت‌گذشته روی فاکتورِ تسویه‌شده): فاکتور ۹ میلیون،
     * تخفیفِ ۳ میلیون، دو قسطِ ۳ میلیونیِ کاملاً پرداخت‌شده؛ تخفیف را با SQL «مشروطِ مهلت‌گذشته» می‌کنیم و جارو
     * آن را برمی‌دارد: مانده ۳ میلیون و هیچ قسطِ پرداخت‌نشده‌ای نیست.
     */
    private InvoiceInfo lapsedWithOnlyPaidInstallments(Long schoolId) {
        InvoiceInfo invoice = newInvoice(schoolId, "9000000.00");
        FinanceFacade.DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("3000000.00"), "x");
        financeFacade.generateInstallments(schoolId, invoice.id(), 2, soon(), 1);
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("6000000.00"), PaymentMethod.CASH, null);
        jdbcTemplate.update("update finance.invoice_discounts set status = 'PENDING', deadline = ? where id = ?",
                LocalDate.now(TEHRAN).minusDays(1), discount.id());
        DiscountSweepHook.sweep(financeFacade);
        return invoice;
    }

    @Test
    void lapseWithAllInstallmentsPaidCreatesOneUndatedInstallmentForTheRemainder() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = lapsedWithOnlyPaidInstallments(schoolId);

        List<InstallmentInfo> plan = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(plan).hasSize(3);
        InstallmentInfo undated = plan.get(2);
        assertThat(undated.dueDate()).isNull();
        assertThat(undated.amount()).isEqualByComparingTo("3000000.00");
        assertThat(undated.amountPaid()).isEqualByComparingTo("0");
        assertThat(undated.status()).isEqualTo(InstallmentStatus.UNPAID);
        assertThat(plan.get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(plan.get(1).status()).isEqualTo(InstallmentStatus.PAID);

        // بدون سررسید هرگز دیرکرد نیست: فاکتور OVERDUE نمی‌شود
        InvoiceInfo after = reload(schoolId, invoice.id());
        assertThat(after.status()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(financeFacade.listInstallmentsForInvoices(schoolId, List.of(invoice.id())).get(invoice.id()).get(2)
                .dueDate()).isNull();

        // مانده‌ی قابل پرداخت کاملاً روی همین قسط تخصیص می‌یابد
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("3000000.00"), PaymentMethod.CASH, null);
        List<InstallmentInfo> settled = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(settled.get(2).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(settled.get(2).amountPaid()).isEqualByComparingTo("3000000.00");
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void undatedInstallmentCanBeDatedByTheAdmin() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = lapsedWithOnlyPaidInstallments(schoolId);
        InstallmentInfo undated = financeFacade.listInstallments(schoolId, invoice.id()).get(2);

        // خالی ماندنِ تاریخِ قسطِ بدون سررسید مجاز است (مبلغ را می‌شود ویرایش کرد)
        List<InstallmentInfo> same = financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(undated.id(), new BigDecimal("3000000.00"), null)));
        assertThat(same.get(2).dueDate()).isNull();

        LocalDate date = soon().plusDays(30);
        List<InstallmentInfo> dated = financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(undated.id(), new BigDecimal("3000000.00"), date)));
        assertThat(dated.get(2).dueDate()).isEqualTo(date);
        assertThat(dated.get(2).status()).isEqualTo(InstallmentStatus.UNPAID);
        // سررسیدِ فاکتور از آخرین قسطِ دارای تاریخ می‌آید
        assertThat(reload(schoolId, invoice.id()).dueDate())
                .isEqualTo(date.plusDays(1).atStartOfDay(TEHRAN).toInstant());

        // پاک کردنِ تاریخِ موجود مجاز نیست
        assertThat(errorOf(() -> financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(undated.id(), new BigDecimal("3000000.00"), null)))))
                .isInstanceOf(FinanceError.InvalidInstallments.class);
    }

    @Test
    void undatedSortsLastIsPaidLastAndOtherInstallmentsCanBeEditedWithoutDatingIt() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "900.00");
        List<InstallmentInfo> generated = financeFacade.generateInstallments(schoolId, invoice.id(), 3, soon(), 1);
        // یکی از اقساط را بدون سررسید می‌کنیم (همان وضعیتِ پس از برداشته شدنِ تخفیف، کنارِ اقساطِ دارای تاریخ)
        jdbcTemplate.update("update finance.installments set due_date = null where id = ?", generated.get(0).id());

        List<InstallmentInfo> plan = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(plan).hasSize(3);
        assertThat(plan.get(2).id()).isEqualTo(generated.get(0).id());
        assertThat(plan.get(2).dueDate()).isNull();
        assertThat(plan.get(0).dueDate()).isNotNull();
        assertThat(plan.get(1).dueDate()).isNotNull();
        InvoiceInfo listed = financeFacade.listInvoices(schoolId).get(0);
        assertThat(listed.status()).isNotEqualTo(InvoiceStatus.OVERDUE);

        // ویرایشِ مبلغِ بقیه بدون تاریخ دادن به قسطِ بدون سررسید
        List<InstallmentInfo> edited = financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(plan.get(0).id(), new BigDecimal("400.00"), plan.get(0).dueDate()),
                new InstallmentEdit(plan.get(1).id(), new BigDecimal("300.00"), plan.get(1).dueDate()),
                new InstallmentEdit(plan.get(2).id(), new BigDecimal("200.00"), null)));
        assertThat(edited).extracting(InstallmentInfo::dueDate).containsExactly(
                plan.get(0).dueDate(), plan.get(1).dueDate(), null);

        // تخصیص: اول اقساطِ دارای تاریخ، سپس بدون سررسید
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("700.00"), PaymentMethod.CASH, null);
        List<InstallmentInfo> paid = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(paid.get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(paid.get(1).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(paid.get(2).amountPaid()).isEqualByComparingTo("0");
        assertThat(paid.get(2).status()).isEqualTo(InstallmentStatus.UNPAID);
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("200.00"), PaymentMethod.CASH, null);
        assertThat(financeFacade.listInstallments(schoolId, invoice.id()).get(2).status())
                .isEqualTo(InstallmentStatus.PAID);
    }

    @Test
    void generateStillRequiresFirstDueDateAndUndatedInstallmentIsNeverOverdue() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "900.00");
        assertThat(errorOf(() -> financeFacade.generateInstallments(schoolId, invoice.id(), 2, null, 1)))
                .isInstanceOf(FinanceError.InvalidInstallments.class);

        List<InstallmentInfo> generated = financeFacade.generateInstallments(schoolId, invoice.id(), 1, soon(), 1);
        jdbcTemplate.update("update finance.installments set due_date = null where id = ?", generated.get(0).id());
        // حتی با سررسیدِ فاکتور در گذشته: قسطِ بدون سررسید دیرکرد نمی‌شود
        jdbcTemplate.update("update finance.invoices set due_date = now() - interval '30 days' where id = ?",
                invoice.id());
        assertThat(financeFacade.listInstallments(schoolId, invoice.id()).get(0).status())
                .isEqualTo(InstallmentStatus.UNPAID);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.UNPAID);
    }

    @Test
    void httpShowsNullDueDateAndRejectsClearingAnExistingDate() throws Exception {
        MockHttpSession adminSession = login("admin@roshdeandishe.test", "password");
        schoolFacade.setInstallmentsEnabled(new SchoolFacade.SchoolId(1L), true);
        financeFacade.setTuitionFee(1L, new BigDecimal("900.00"));
        Long studentId = 7_420_000L + System.nanoTime() % 1_000_000L;
        financeFacade.issueTuitionInvoice(1L, studentId);
        Long invoiceId = financeFacade.listInvoicesForStudents(1L, List.of(studentId)).get(0).id();
        List<InstallmentInfo> generated = financeFacade.generateInstallments(1L, invoiceId, 2, soon(), 1);
        jdbcTemplate.update("update finance.installments set due_date = null where id = ?", generated.get(0).id());

        String url = "/api/admin/schools/1/invoices/" + invoiceId + "/installments";
        mockMvc.perform(get(url).header("X-School-ID", "1").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].dueDate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[1].id").value(generated.get(0).id()))
                .andExpect(jsonPath("$[0].dueDate").value(Matchers.notNullValue()));

        List<InstallmentInfo> plan = financeFacade.listInstallments(1L, invoiceId);
        // تاریخِ موجود پاک نمی‌شود → 400
        mockMvc.perform(put(url).with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + plan.get(0).id() + ",\"amount\":450.00,\"dueDate\":null},"
                                + "{\"id\":" + plan.get(1).id() + ",\"amount\":450.00,\"dueDate\":null}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invalid-installments")));
        // بدون تاریخ دادن به قسطِ بدون سررسید → 200
        mockMvc.perform(put(url).with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + plan.get(0).id() + ",\"amount\":500.00,\"dueDate\":\""
                                + plan.get(0).dueDate() + "\"},{\"id\":" + plan.get(1).id()
                                + ",\"amount\":400.00,\"dueDate\":null}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].dueDate").value(Matchers.nullValue()));
    }
}
