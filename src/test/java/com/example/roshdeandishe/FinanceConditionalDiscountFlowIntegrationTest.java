package com.example.roshdeandishe;

import com.example.roshdeandishe.finance.DiscountStatus;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.internal.DiscountSweepHook;
import com.example.roshdeandishe.finance.FinanceFacade.DiscountInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InstallmentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
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
 * تخفیفِ مشروط (openspec/changes/tuition-installments.md، ضمیمه‌ی «تخفیف مشروط»): تخفیف از لحظه‌ی ثبت قابل پرداخت را
 * کم می‌کند، با تسویه‌ی کامل تا پایانِ روزِ مهلت EARNED (دائمی) می‌شود و در غیر این صورت خودکار LAPSED می‌شود -
 * هم تنبل زیر قفل در اولین نوشتن و هم با جاروی ساعتی. «مهلتِ گذشته» با تغییرِ ستون deadline به دیروز شبیه‌سازی می‌شود.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceConditionalDiscountFlowIntegrationTest {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinanceFacade financeFacade;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    /** مدرسه‌ی واقعی و تازه با منطقه‌ی تهران (روزِ مهلت به وقتِ همین مدرسه سنجیده می‌شود). */
    private Long newSchool(boolean installmentsEnabled) {
        SchoolFacade.SchoolId id = schoolFacade.createSchool(
                "مدرسه‌ی تخفیف مشروط " + UUID.randomUUID(), "Asia/Tehran", "fa-IR",
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

    private static LocalDate today() {
        return LocalDate.now(TEHRAN);
    }

    private DiscountInfo discount(Long schoolId, Long invoiceId, Long discountId) {
        return financeFacade.listDiscounts(schoolId, invoiceId).stream()
                .filter(d -> d.id().equals(discountId)).findFirst().orElseThrow();
    }

    /** مهلت را به دیروز می‌برد: شبیه‌سازیِ گذشتنِ زمان بدون انتظار. */
    private void expire(Long discountId) {
        jdbcTemplate.update("update finance.invoice_discounts set deadline = ? where id = ?",
                today().minusDays(1), discountId);
    }

    private BigDecimal remaining(InvoiceInfo invoice) {
        return invoice.amount().subtract(invoice.discountTotal()).subtract(invoice.amountPaid());
    }

    private int auditCount(Long schoolId, String action) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from finance.finance_audit where school_id = ? and action = ?",
                Integer.class, schoolId, action);
        return count == null ? 0 : count;
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
    void conditionalDiscountIsPendingLowersPayableAndPartialPaymentDoesNotEarnIt() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        LocalDate deadline = today().plusDays(5);

        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("200000.00"), "شرطی", deadline);
        assertThat(discount.status()).isEqualTo(DiscountStatus.PENDING);
        assertThat(discount.deadline()).isEqualTo(deadline);
        assertThat(discount.resolvedAt()).isNull();

        // از همان لحظه قابل پرداخت کمتر است
        InvoiceInfo discounted = reload(schoolId, invoice.id());
        assertThat(discounted.discountTotal()).isEqualByComparingTo("200000.00");
        assertThat(discounted.status()).isEqualTo(InvoiceStatus.UNPAID);

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("500000.00"), PaymentMethod.CASH, null);
        assertThat(discount(schoolId, invoice.id(), discount.id()).status()).isEqualTo(DiscountStatus.PENDING);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PARTIAL);
    }

    @Test
    void fullPaymentBeforeDeadlineEarnsTheDiscountPermanently() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("200000.00"), null, today().plusDays(5));

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("500000.00"), PaymentMethod.CASH, null);
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("300000.00"), PaymentMethod.CASH, null);

        DiscountInfo earned = discount(schoolId, invoice.id(), discount.id());
        assertThat(earned.status()).isEqualTo(DiscountStatus.EARNED);
        assertThat(earned.resolvedAt()).isNotNull();
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PAID);

        // حتی اگر مهلت بعداً بگذرد، جارو آن را برنمی‌دارد
        expire(discount.id());
        DiscountSweepHook.sweep(financeFacade);
        assertThat(discount(schoolId, invoice.id(), discount.id()).status()).isEqualTo(DiscountStatus.EARNED);
        InvoiceInfo after = reload(schoolId, invoice.id());
        assertThat(after.discountTotal()).isEqualByComparingTo("200000.00");
        assertThat(after.status()).isEqualTo(InvoiceStatus.PAID);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditCount(schoolId, "DISCOUNT_EARNED")).isEqualTo(1));
    }

    @Test
    void deadlineIsEndOfDaySoPayingOnTheDeadlineDayStillEarns() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("100000.00"), null, today());
        assertThat(discount.status()).isEqualTo(DiscountStatus.PENDING);

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("900000.00"), PaymentMethod.CASH, null);

        assertThat(discount(schoolId, invoice.id(), discount.id()).status()).isEqualTo(DiscountStatus.EARNED);
    }

    @Test
    void deadlineBeforeTodayIsRejected() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");

        Object error = errorOf(() -> financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("100000.00"), null, today().minusDays(1)));

        assertThat(error).isInstanceOf(FinanceError.InvalidDiscount.class);
        assertThat(financeFacade.listDiscounts(schoolId, invoice.id())).isEmpty();
        assertThat(reload(schoolId, invoice.id()).discountTotal()).isEqualByComparingTo("0");
    }

    @Test
    void sweepLapsesAnExpiredDiscountAndRaisesTheRemainingAndIsIdempotent() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("200000.00"), null, today().plusDays(1));
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("500000.00"), PaymentMethod.CASH, null);
        assertThat(remaining(reload(schoolId, invoice.id()))).isEqualByComparingTo("300000.00");

        expire(discount.id());
        assertThat(DiscountSweepHook.sweep(financeFacade)).isGreaterThanOrEqualTo(1);

        DiscountInfo lapsed = discount(schoolId, invoice.id(), discount.id());
        assertThat(lapsed.status()).isEqualTo(DiscountStatus.LAPSED);
        assertThat(lapsed.resolvedAt()).isNotNull();
        InvoiceInfo after = reload(schoolId, invoice.id());
        assertThat(after.discountTotal()).isEqualByComparingTo("0");
        assertThat(remaining(after)).isEqualByComparingTo("500000.00");
        assertThat(after.status()).isEqualTo(InvoiceStatus.PARTIAL);

        // دوباره: چیزی برای برداشتن نمانده
        assertThat(DiscountSweepHook.sweep(financeFacade)).isZero();
        assertThat(reload(schoolId, invoice.id()).discountTotal()).isEqualByComparingTo("0");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditCount(schoolId, "DISCOUNT_LAPSED")).isEqualTo(1));
    }

    @Test
    void readsAlreadyIgnoreAnExpiredDiscountBeforeAnySweepRuns() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("200000.00"), null, today().plusDays(1));
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("500000.00"), PaymentMethod.CASH, null);
        assertThat(reload(schoolId, invoice.id()).discountTotal()).isEqualByComparingTo("200000.00");
        assertThat(financeFacade.pendingDiscountsForInvoices(schoolId, List.of(invoice.id()))).containsKey(invoice.id());

        expire(discount.id()); // مهلت گذشته، ولی نه جاروب شده نه نوشتنی روی فاکتور بوده

        InvoiceInfo read = reload(schoolId, invoice.id());
        assertThat(read.discountTotal()).isEqualByComparingTo("0");
        assertThat(remaining(read)).isEqualByComparingTo("500000.00");
        // پیامِ «تخفیف مشروط» همان‌جا پنهان است، پس مبلغ و پیام با هم ناسازگار نمی‌شوند
        assertThat(financeFacade.pendingDiscountsForInvoices(schoolId, List.of(invoice.id()))).isEmpty();
        // خواندن چیزی نمی‌نویسد: ردیف هنوز PENDING است تا اولین نوشتن یا جاروب آن را LAPSED کند
        assertThat(discount(schoolId, invoice.id(), discount.id()).status()).isEqualTo(DiscountStatus.PENDING);
        FinanceFacade.TuitionSummary summary = financeFacade.tuitionSummary(schoolId, null);
        assertThat(summary.billed()).isEqualByComparingTo("1000000.00");
        assertThat(summary.paid()).isEqualByComparingTo("500000.00");
    }

    @Test
    void tuitionSummaryMatchesTheInvoiceListTotals() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("100000.00"), null);
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("300000.00"), PaymentMethod.CASH, null);

        FinanceFacade.TuitionSummary summary = financeFacade.tuitionSummary(schoolId, null);

        assertThat(summary.invoiceCount()).isEqualTo(1);
        assertThat(summary.billed()).isEqualByComparingTo("900000.00");
        assertThat(summary.paid()).isEqualByComparingTo("300000.00");
        assertThat(financeFacade.tuitionSummary(newSchool(false), null).invoiceCount()).isZero();
    }

    @Test
    void payingAfterTheDeadlineLapsesLazilyFirstAndDoesNotEarn() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("200000.00"), null, today().plusDays(1));

        // مهلت گذشته و جارو هنوز نیامده: ۸۰۰٬۰۰۰ با قابل پرداختِ کهنه کل را می‌بست، ولی اکنون قابل پرداخت ۱٬۰۰۰٬۰۰۰ است
        expire(discount.id());
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("800000.00"), PaymentMethod.CASH, null);

        assertThat(discount(schoolId, invoice.id(), discount.id()).status()).isEqualTo(DiscountStatus.LAPSED);
        InvoiceInfo after = reload(schoolId, invoice.id());
        assertThat(after.discountTotal()).isEqualByComparingTo("0");
        assertThat(after.status()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(remaining(after)).isEqualByComparingTo("200000.00");
    }

    @Test
    void lapseResplitsUnpaidInstallmentsUpwardAndKeepsPaidSumEqualToAmountPaid() {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "9000000.00");
        List<InstallmentInfo> original = financeFacade.generateInstallments(
                schoolId, invoice.id(), 3, today().plusDays(10), 1);
        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("3000000.00"), null, today().plusDays(5));
        // قابل پرداخت ۶٬۰۰۰٬۰۰۰ → سه قسطِ ۲٬۰۰۰٬۰۰۰
        assertThat(financeFacade.listInstallments(schoolId, invoice.id()))
                .extracting(InstallmentInfo::amount)
                .allSatisfy(a -> assertThat(a).isEqualByComparingTo("2000000.00"));
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("2000000.00"), PaymentMethod.CASH, null);

        expire(discount.id());
        DiscountSweepHook.sweep(financeFacade);

        // قابل پرداخت ۹٬۰۰۰٬۰۰۰؛ قسطِ اول (پرداخت‌شده) می‌ماند و ۷٬۰۰۰٬۰۰۰ روی دو قسطِ باقی‌مانده تقسیم می‌شود
        List<InstallmentInfo> plan = financeFacade.listInstallments(schoolId, invoice.id());
        assertThat(plan).hasSize(3);
        assertThat(plan.get(0).id()).isEqualTo(original.get(0).id());
        assertThat(plan.get(1).amount()).isEqualByComparingTo("3500000.00");
        assertThat(plan.get(2).amount()).isEqualByComparingTo("3500000.00");
        assertThat(plan).extracting(InstallmentInfo::dueDate)
                .containsExactlyElementsOf(original.stream().map(InstallmentInfo::dueDate).toList());
        assertThat(plan.stream().map(InstallmentInfo::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("9000000.00");
        assertThat(plan.stream().map(InstallmentInfo::amountPaid).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(reload(schoolId, invoice.id()).amountPaid());
    }

    @Test
    void twoDiscountsLapseIndependently() {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000000.00");
        DiscountInfo unconditional = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("100000.00"), null, null);
        DiscountInfo soon = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("200000.00"), null, today().plusDays(1));
        DiscountInfo later = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("300000.00"), null, today().plusDays(10));
        assertThat(unconditional.status()).isEqualTo(DiscountStatus.APPLIED);
        assertThat(unconditional.deadline()).isNull();
        assertThat(reload(schoolId, invoice.id()).discountTotal()).isEqualByComparingTo("600000.00");

        expire(soon.id());
        DiscountSweepHook.sweep(financeFacade);

        assertThat(discount(schoolId, invoice.id(), soon.id()).status()).isEqualTo(DiscountStatus.LAPSED);
        assertThat(discount(schoolId, invoice.id(), later.id()).status()).isEqualTo(DiscountStatus.PENDING);
        assertThat(discount(schoolId, invoice.id(), unconditional.id()).status()).isEqualTo(DiscountStatus.APPLIED);
        assertThat(reload(schoolId, invoice.id()).discountTotal()).isEqualByComparingTo("400000.00");

        // خلاصه‌ی خودسرویس فقط تخفیفِ باز را می‌شمارد
        FinanceFacade.PendingDiscountSummary summary = financeFacade
                .pendingDiscountsForInvoices(schoolId, List.of(invoice.id())).get(invoice.id());
        assertThat(summary.amount()).isEqualByComparingTo("300000.00");
        assertThat(summary.earliestDeadline()).isEqualTo(today().plusDays(10));
    }

    @Test
    void httpDeadlineValidationAndParentSelfServiceSummary() throws Exception {
        MockHttpSession adminSession = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(put("/api/admin/schools/1/tuition-fee")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1200000.00}"))
                .andExpect(status().isOk());

        String studentEmail = unique("cond.student");
        registerAndLogin(studentEmail, "دانش‌آموز شرطی");
        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس شرطی\",\"section\":\"ج\",\"teacherId\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk()).andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString()).get("id").asLong();
        MvcResult enrollResult = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"fullName\":\"دانش‌آموز شرطی\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk()).andReturn();
        Long studentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("studentId").asLong();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(financeFacade.listInvoicesForStudents(1L, List.of(studentId))).hasSize(1));
        Long invoiceId = financeFacade.listInvoicesForStudents(1L, List.of(studentId)).get(0).id();

        String parentEmail = unique("cond.parent");
        MockHttpSession parentSession = registerAndLogin(parentEmail, "ولی شرطی");
        MvcResult membershipResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولی شرطی\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk()).andReturn();
        Long parentUserId = objectMapper.readTree(membershipResult.getResponse().getContentAsString())
                .get("userId").asLong();
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/guardians")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianUserId\":" + parentUserId + "}"))
                .andExpect(status().isOk());

        String url = "/api/admin/schools/1/invoices/" + invoiceId + "/discounts";

        // مهلتِ خراب: 400 (نه 500)؛ مهلتِ گذشته: 400 invalid-discount
        mockMvc.perform(post(url).with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000.00,\"deadline\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000.00,\"deadline\":\"" + today().minusDays(2) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invalid-discount")));

        // مهلتِ معتبر
        String deadline = today().plusDays(3).toString();
        MvcResult created = mockMvc.perform(post(url).with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":200000.00,\"deadline\":\"" + deadline + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.deadline").value(deadline))
                .andReturn();
        Long discountId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        JsonNode pending = myInvoice(parentSession, studentId);
        assertThat(pending.get("pendingDiscount").decimalValue()).isEqualByComparingTo("200000.00");
        assertThat(pending.get("pendingDiscountDeadline").asText()).isEqualTo(deadline);
        assertThat(pending.get("discountTotal").decimalValue()).isEqualByComparingTo("200000.00");

        expire(discountId);
        DiscountSweepHook.sweep(financeFacade);

        JsonNode lapsed = myInvoice(parentSession, studentId);
        assertThat(lapsed.get("pendingDiscount").decimalValue()).isEqualByComparingTo("0");
        assertThat(lapsed.get("pendingDiscountDeadline").isNull()).isTrue();
        assertThat(lapsed.get("discountTotal").decimalValue()).isEqualByComparingTo("0");
        mockMvc.perform(get(url).header("X-School-ID", "1").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("LAPSED"));
    }

    private JsonNode myInvoice(MockHttpSession session, Long studentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me/schools/1/invoices").session(session))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (node.get("studentId").asLong() == studentId) {
                return node;
            }
        }
        throw new AssertionError("فاکتورِ دانش‌آموز " + studentId + " در پاسخ نیست");
    }
}
