package com.example.roshdeandishe;

import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * تخفیف روی فاکتور شهریه و تنظیم «اقساط» مدرسه (openspec/changes/tuition-installments.md، بخش اول):
 * مبلغ قابل پرداخت = amount - discountTotal، وضعیتِ مشتق‌شده و سقفِ پرداخت از آن می‌آیند، و تخفیف
 * هرگز قابل پرداخت را از مبلغ پرداخت‌شده کمتر نمی‌کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceDiscountFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinanceFacade financeFacade;

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

    /** فاکتورِ تازه به مبلغ amount برای مدرسه‌ی ساختگیِ schoolId (الگوی FinancePaymentFlowIntegrationTest). */
    private InvoiceInfo newInvoice(Long schoolId, Long studentId, String amount) {
        financeFacade.setTuitionFee(schoolId, new BigDecimal(amount));
        financeFacade.issueTuitionInvoice(schoolId, studentId);
        return financeFacade.listInvoices(schoolId).get(0);
    }

    private InvoiceInfo reload(Long schoolId, Long invoiceId) {
        return financeFacade.listInvoices(schoolId).stream()
                .filter(i -> i.id().equals(invoiceId)).findFirst().orElseThrow();
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
    void discountReducesPayableAndPaymentsCoveringPayableMakeInvoicePaid() {
        Long schoolId = 9301L;
        InvoiceInfo invoice = newInvoice(schoolId, 51L, "1000000.00");
        assertThat(invoice.discountTotal()).isEqualByComparingTo("0");

        FinanceFacade.DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("250000.00"), "  بورسیه‌ی بخشی  ");
        assertThat(discount.reason()).isEqualTo("بورسیه‌ی بخشی");
        assertThat(discount.amount()).isEqualByComparingTo("250000.00");

        InvoiceInfo discounted = reload(schoolId, invoice.id());
        assertThat(discounted.discountTotal()).isEqualByComparingTo("250000.00");
        assertThat(discounted.amount()).isEqualByComparingTo("1000000.00");
        assertThat(discounted.status()).isEqualTo(InvoiceStatus.UNPAID);

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("750000.00"), PaymentMethod.CASH, null);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PAID);

        assertThat(financeFacade.listDiscounts(schoolId, invoice.id())).hasSize(1);
    }

    @Test
    void discountAfterPartialPaymentIsAllowedAndCanSettleTheInvoice() {
        Long schoolId = 9302L;
        InvoiceInfo invoice = newInvoice(schoolId, 52L, "1000000.00");
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("600000.00"), PaymentMethod.CASH, null);
        assertThat(reload(schoolId, invoice.id()).status()).isEqualTo(InvoiceStatus.PARTIAL);

        // بخشودنِ کلِ مانده: قابل پرداخت = پرداخت‌شده → PAID
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("400000.00"), null);
        InvoiceInfo settled = reload(schoolId, invoice.id());
        assertThat(settled.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(settled.discountTotal()).isEqualByComparingTo("400000.00");
    }

    @Test
    void discountBelowAmountPaidIsRejected() {
        Long schoolId = 9303L;
        InvoiceInfo invoice = newInvoice(schoolId, 53L, "1000000.00");
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("600000.00"), PaymentMethod.CASH, null);

        Object error = errorOf(() -> financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("400000.01"), null));
        assertThat(error).isInstanceOf(FinanceError.DiscountExceedsBalance.class);
        assertThat(reload(schoolId, invoice.id()).discountTotal()).isEqualByComparingTo("0");
        assertThat(financeFacade.listDiscounts(schoolId, invoice.id())).isEmpty();
    }

    @Test
    void zeroNegativeNullAmountAndTooLongReasonAreRejected() {
        Long schoolId = 9304L;
        InvoiceInfo invoice = newInvoice(schoolId, 54L, "500000.00");

        for (BigDecimal bad : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-1.00"), null}) {
            assertThat(errorOf(() -> financeFacade.applyDiscount(schoolId, invoice.id(), bad, null)))
                    .isInstanceOf(FinanceError.InvalidDiscount.class);
        }
        assertThat(errorOf(() -> financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("1.00"), "x".repeat(256))))
                .isInstanceOf(FinanceError.InvalidDiscount.class);
        assertThat(financeFacade.listDiscounts(schoolId, invoice.id())).isEmpty();
    }

    @Test
    void paymentExceedingDiscountedBalanceIsRejected() {
        Long schoolId = 9305L;
        InvoiceInfo invoice = newInvoice(schoolId, 55L, "1000000.00");
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("300000.00"), null);

        assertThatThrownBy(() -> financeFacade.recordPayment(schoolId, invoice.id(),
                new BigDecimal("700000.01"), PaymentMethod.CASH, null))
                .isInstanceOf(FinanceException.class)
                .extracting(e -> ((FinanceException) e).error())
                .isInstanceOfSatisfying(FinanceError.PaymentExceedsBalance.class,
                        e -> assertThat(e.remaining()).isEqualByComparingTo("700000.00"));
    }

    @Test
    void fullyDiscountedInvoiceIsPaidWithoutAnyPayment() {
        Long schoolId = 9306L;
        InvoiceInfo invoice = newInvoice(schoolId, 56L, "800000.00");
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("800000.00"), "معافیت کامل");

        InvoiceInfo settled = reload(schoolId, invoice.id());
        assertThat(settled.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(settled.amountPaid()).isEqualByComparingTo("0");
    }

    @Test
    void discountOnForeignSchoolInvoiceIsNotFound() {
        Long schoolId = 9307L;
        InvoiceInfo invoice = newInvoice(schoolId, 57L, "500000.00");

        assertThat(errorOf(() -> financeFacade.applyDiscount(
                9308L, invoice.id(), new BigDecimal("1.00"), null)))
                .isInstanceOf(FinanceError.InvoiceNotFound.class);
        assertThat(errorOf(() -> financeFacade.listDiscounts(9308L, invoice.id())))
                .isInstanceOf(FinanceError.InvoiceNotFound.class);
    }

    @Test
    void discountHttpFlowAndParentVisibility() throws Exception {
        MockHttpSession adminSession = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(put("/api/admin/schools/1/tuition-fee")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1200000.00}"))
                .andExpect(status().isOk());

        String studentEmail = unique("disc.student");
        registerAndLogin(studentEmail, "دانش‌آموز تخفیف");
        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس تخفیف\",\"section\":\"ب\",\"teacherId\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk()).andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString()).get("id").asLong();
        MvcResult enrollResult = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"fullName\":\"دانش‌آموز تخفیف\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk()).andReturn();
        Long studentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("studentId").asLong();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(financeFacade.listInvoicesForStudents(1L, List.of(studentId))).hasSize(1));
        Long wardInvoiceId = financeFacade.listInvoicesForStudents(1L, List.of(studentId)).get(0).id();

        String parentEmail = unique("disc.parent");
        MockHttpSession parentSession = registerAndLogin(parentEmail, "ولی تخفیف");
        MvcResult membershipResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولی تخفیف\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk()).andReturn();
        Long parentUserId = objectMapper.readTree(membershipResult.getResponse().getContentAsString())
                .get("userId").asLong();
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/guardians")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianUserId\":" + parentUserId + "}"))
                .andExpect(status().isOk());

        Long otherStudentId = 7_306_000L + System.nanoTime() % 1_000_000L;
        financeFacade.issueTuitionInvoice(1L, otherStudentId);
        Long otherInvoiceId = financeFacade.listInvoicesForStudents(1L, List.of(otherStudentId)).get(0).id();

        // ادمین: تخفیف روی فاکتور فرزند و فاکتور دیگری
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + wardInvoiceId + "/discounts")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":200000.00,\"reason\":\"تخفیف خواهر و برادر\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200000.00))
                .andExpect(jsonPath("$.reason").value("تخفیف خواهر و برادر"));
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + otherInvoiceId + "/discounts")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000.00}"))
                .andExpect(status().isOk());

        // مبلغ نامعتبر: 400 و بیش از مانده: 409
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + wardInvoiceId + "/discounts")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invalid-discount")));
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + wardInvoiceId + "/discounts")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999999999.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("discount-exceeds-balance")));

        // ولی FINANCE_MANAGE ندارد: 403
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + wardInvoiceId + "/discounts")
                        .with(csrf()).header("X-School-ID", "1").session(parentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00}"))
                .andExpect(status().isForbidden());

        // ولی فقط تخفیف‌های فاکتور فرزند خودش را می‌بیند
        mockMvc.perform(get("/api/admin/schools/1/invoices/" + wardInvoiceId + "/discounts")
                        .header("X-School-ID", "1").session(parentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(200000.00));
        mockMvc.perform(get("/api/admin/schools/1/invoices/" + otherInvoiceId + "/discounts")
                        .header("X-School-ID", "1").session(parentSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invoice-not-found")));

        // discountTotal در پاسخ فهرست فاکتورها و endpoint خودسرویس
        mockMvc.perform(get("/api/me/schools/1/invoices").session(parentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")].discountTotal")
                        .value(Matchers.contains(200000.00)));

        // فاکتور مدرسه‌ی دیگر: NotFound
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + newInvoice(9309L, 59L, "100000.00").id()
                        + "/discounts")
                        .with(csrf()).header("X-School-ID", "1").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invoice-not-found")));

        // تنظیمات: پیش‌فرض خاموش، PUT ماندگار، body خالی 400، ولی بدون FINANCE_MANAGE 403
        mockMvc.perform(get("/api/admin/schools/1/finance/settings")
                        .header("X-School-ID", "1").session(parentSession))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/schools/1/finance/settings/installments")
                        .with(csrf()).header("X-School-ID", "1").session(parentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void installmentsSettingDefaultsOffAndPersists() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("set.founder"), "بنیان‌گذار تنظیمات");
        MvcResult created = mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی تنظیمات " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode school = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = school.get("id").asText();
        String base = "/api/admin/schools/" + id + "/finance/settings";

        mockMvc.perform(get(base).header("X-School-ID", id).session(founder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentsEnabled").value(false));

        mockMvc.perform(put(base + "/installments").with(csrf()).header("X-School-ID", id).session(founder)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("invalid-setting")));

        mockMvc.perform(put(base + "/installments").with(csrf()).header("X-School-ID", id).session(founder)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentsEnabled").value(true));

        mockMvc.perform(get(base).header("X-School-ID", id).session(founder))
                .andExpect(jsonPath("$.installmentsEnabled").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "select installments_enabled from school.schools where id = ?", Boolean.class, Long.valueOf(id)))
                .isTrue();
    }
}
