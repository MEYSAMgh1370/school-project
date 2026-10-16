package com.example.roshdeandishe;

import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.AuditEntry;
import com.example.roshdeandishe.finance.FinanceFacade.AuditPage;
import com.example.roshdeandishe.finance.FinanceFacade.AuditQuery;
import com.example.roshdeandishe.finance.FinanceFacade.DiscountInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.finance.internal.DiscountSweepHook;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * خواندنِ audit trail مالی ({@code GET /api/admin/schools/{id}/finance/audit}): ردیف‌ها با کارهای واقعی
 * (پرداخت/تخفیف/اقساط/جاروی تخفیف) ساخته می‌شوند؛ فیلترها، صفحه‌بندی، منطقه‌ی زمانیِ مدرسه، نام‌ها، دسترسی و
 * ایزولاسیونِ مدارس. خواندن هیچ ردیفی را عوض نمی‌کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinanceAuditReadFlowIntegrationTest {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    private static final String FOUNDER_NAME = "بنیان‌گذارِ خواندنِ audit";
    private static final String STUDENT_NAME = "دانش‌آموزِ خواندنِ audit";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinanceFacade financeFacade;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession founderSession;
    private String founderEmail;
    private Long founderId;
    private Long schoolId;
    private Long otherSchoolId;
    private MockHttpSession otherFounderSession;
    private MockHttpSession parentSession;

    private Long realStudentId;
    private Long invoiceA;   // دانش‌آموز 77
    private Long invoiceB;   // دانش‌آموز 78
    private Long otherInvoice;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- seeding

    @BeforeAll
    void seed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        founderEmail = "audit.read.founder." + suffix + "@roshdeandishe.test";
        founderSession = registerAndLogin(founderEmail, FOUNDER_NAME);
        schoolId = createSchool(founderSession, "مدرسه‌ی خواندنِ audit " + suffix);
        founderId = jdbcTemplate.queryForObject("select id from iam.users where email = ?", Long.class, founderEmail);

        String otherEmail = "audit.read.other." + suffix + "@roshdeandishe.test";
        otherFounderSession = registerAndLogin(otherEmail, "بنیان‌گذارِ مدرسه‌ی دیگر");
        otherSchoolId = createSchool(otherFounderSession, "مدرسه‌ی دیگرِ audit " + suffix);

        String parentEmail = "audit.read.parent." + suffix + "@roshdeandishe.test";
        parentSession = registerAndLogin(parentEmail, "ولیِ audit");
        mockMvc.perform(post("/api/admin/schools/" + schoolId + "/memberships")
                        .with(csrf()).header("X-School-ID", schoolId).session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولیِ audit\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk());

        schoolFacade.setInstallmentsEnabled(new SchoolFacade.SchoolId(schoolId), true);
        financeFacade.setTuitionFee(schoolId, new BigDecimal("1000.00"));

        // مدرسه‌ی دیگر با یک فاکتورِ خودش (باید هیچ‌وقت در خروجیِ مدرسه‌ی ما نیاید)
        financeFacade.setTuitionFee(otherSchoolId, new BigDecimal("500.00"));
        financeFacade.issueTuitionInvoice(otherSchoolId, 77L);
        otherInvoice = financeFacade.listInvoices(otherSchoolId).get(0).id();

        // دانش‌آموزِ واقعی (برای نامِ دانش‌آموز): فاکتورش را listener می‌سازد
        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/" + schoolId + "/classes")
                        .with(csrf()).header("X-School-ID", schoolId).session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"پایه چهارم - audit\",\"section\":\"ج\",\"teacherId\":null,\"gradeLevel\":4}"))
                .andExpect(status().isOk()).andReturn();
        long classId = objectMapper.readTree(classResult.getResponse().getContentAsString()).get("id").asLong();
        MvcResult studentResult = mockMvc.perform(post("/api/admin/schools/" + schoolId + "/students")
                        .with(csrf()).header("X-School-ID", schoolId).session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"audit.read.student." + suffix + "@roshdeandishe.test\",\"fullName\":\""
                                + STUDENT_NAME + "\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk()).andReturn();
        realStudentId = objectMapper.readTree(studentResult.getResponse().getContentAsString()).get("studentId").asLong();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(count("INVOICE_ISSUED")).isEqualTo(1));

        // کارهای واقعی به‌عنوان بنیان‌گذار
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(founderEmail, "x", "ROLE_X"));
        financeFacade.issueTuitionInvoice(schoolId, 77L);
        financeFacade.issueTuitionInvoice(schoolId, 78L);
        for (InvoiceInfo invoice : financeFacade.listInvoices(schoolId)) {
            if (invoice.studentId().equals(77L)) {
                invoiceA = invoice.id();
            } else if (invoice.studentId().equals(78L)) {
                invoiceB = invoice.id();
            }
        }
        financeFacade.recordPayment(schoolId, invoiceA, new BigDecimal("250.00"), PaymentMethod.CASH, "اول");
        financeFacade.recordPayment(schoolId, invoiceA, new BigDecimal("100.00"), PaymentMethod.BANK_TRANSFER, "دوم");
        financeFacade.applyDiscount(schoolId, invoiceA, new BigDecimal("50.00"), "تخفیف برادر");
        financeFacade.generateInstallments(schoolId, invoiceA, 2, LocalDate.now(TEHRAN).plusDays(10), 1);
        DiscountInfo conditional = financeFacade.applyDiscount(
                schoolId, invoiceB, new BigDecimal("100.00"), null, LocalDate.now(TEHRAN).plusDays(5));
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("update finance.invoice_discounts set deadline = ? where id = ?",
                LocalDate.now(TEHRAN).minusDays(1), conditional.id());
        DiscountSweepHook.sweep(financeFacade);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(count("INVOICE_ISSUED")).isEqualTo(3);
            assertThat(count("PAYMENT_RECORDED")).isEqualTo(2);
            assertThat(count("INVOICE_DISCOUNTED")).isEqualTo(2);
            assertThat(count("INSTALLMENTS_CHANGED")).isEqualTo(1);
            assertThat(count("DISCOUNT_LAPSED")).isEqualTo(1);
        });
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(totalRows()).isEqualTo(totalRows()));
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private long createSchool(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private int count(String action) {
        return jdbcTemplate.queryForObject(
                "select count(*) from finance.finance_audit where school_id = ? and action = ?",
                Integer.class, schoolId, action);
    }

    private int totalRows() {
        return jdbcTemplate.queryForObject(
                "select count(*) from finance.finance_audit where school_id = ?", Integer.class, schoolId);
    }

    // ---------------------------------------------------------------- HTTP helpers

    private MockHttpServletRequestBuilder audit(MockHttpSession session, String query) {
        return get("/api/admin/schools/" + schoolId + "/finance/audit" + query)
                .header("X-School-ID", schoolId).session(session);
    }

    private JsonNode fetch(String query) throws Exception {
        MvcResult result = mockMvc.perform(audit(founderSession, query)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> actions(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("items").forEach(item -> out.add(item.get("action").asText()));
        return out;
    }

    private void assertInvalidFilter(String query) throws Exception {
        mockMvc.perform(audit(founderSession, query))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("type").asText()).endsWith("invalid-audit-filter"));
    }

    // ---------------------------------------------------------------- tests

    @Test
    void unfilteredListIsNewestFirstWithNamesAndParsedDetails() throws Exception {
        JsonNode page = fetch("");

        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(50);
        assertThat(page.get("totalItems").asInt()).isEqualTo(totalRows());
        assertThat(page.get("items")).hasSize(totalRows());

        List<Instant> times = new ArrayList<>();
        page.get("items").forEach(item -> times.add(Instant.parse(item.get("occurredAt").asText())));
        assertThat(times).isSortedAccordingTo(java.util.Comparator.reverseOrder());

        JsonNode payment = null;
        for (JsonNode item : page.get("items")) {
            if (item.get("action").asText().equals("PAYMENT_RECORDED")
                    && item.get("details").get("note").asText().equals("اول")) {
                payment = item;
            }
        }
        assertThat(payment).isNotNull();
        assertThat(payment.get("invoiceId").asLong()).isEqualTo(invoiceA);
        assertThat(payment.get("studentId").asLong()).isEqualTo(77L);
        assertThat(payment.get("studentName").isNull()).isTrue(); // 77 دانش‌آموزِ واقعی نیست
        assertThat(payment.get("actorUserId").asLong()).isEqualTo(founderId);
        assertThat(payment.get("actorName").asText()).isEqualTo(FOUNDER_NAME);
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(payment.get("details").isObject()).isTrue(); // آبجکتِ JSON، نه رشته
        assertThat(payment.get("details").get("method").asText()).isEqualTo("CASH");
        assertThat(payment.get("expenseId").isNull()).isTrue();

        // نامِ دانش‌آموزِ واقعی
        JsonNode issued = fetch("?studentId=" + realStudentId).get("items").get(0);
        assertThat(issued.get("action").asText()).isEqualTo("INVOICE_ISSUED");
        assertThat(issued.get("studentName").asText()).isEqualTo(STUDENT_NAME);
        assertThat(issued.get("details").isNull()).isTrue(); // INVOICE_ISSUED جزئیات ندارد
    }

    @Test
    void filtersByInvoiceStudentActionAndCombinations() throws Exception {
        JsonNode byInvoice = fetch("?invoiceId=" + invoiceA);
        assertThat(byInvoice.get("totalItems").asInt()).isEqualTo(4); // 2 پرداخت + تخفیف + اقساط (INVOICE_ISSUED شناسه‌ی فاکتور ندارد)
        byInvoice.get("items").forEach(item -> assertThat(item.get("invoiceId").asLong()).isEqualTo(invoiceA));

        JsonNode byStudent = fetch("?studentId=78");
        assertThat(actions(byStudent)).containsExactlyInAnyOrder("INVOICE_ISSUED", "INVOICE_DISCOUNTED", "DISCOUNT_LAPSED");

        assertThat(fetch("?action=PAYMENT_RECORDED").get("totalItems").asInt()).isEqualTo(2);

        JsonNode combined = fetch("?studentId=77&action=PAYMENT_RECORDED");
        assertThat(combined.get("totalItems").asInt()).isEqualTo(2);
        assertThat(fetch("?studentId=78&action=PAYMENT_RECORDED").get("totalItems").asInt()).isZero();
        assertThat(fetch("?invoiceId=" + invoiceB + "&action=DISCOUNT_LAPSED").get("totalItems").asInt()).isEqualTo(1);

        // اقدامِ سیستمی: actor تهی ⇒ actorName تهی
        JsonNode lapsed = fetch("?action=DISCOUNT_LAPSED").get("items").get(0);
        assertThat(lapsed.get("actorUserId").isNull()).isTrue();
        assertThat(lapsed.get("actorName").isNull()).isTrue();
        assertThat(lapsed.get("details").get("trigger").asText()).isEqualTo("sweep");
    }

    @Test
    void unknownActionAndForeignSchoolIdsYieldEmptyNotErrors() throws Exception {
        assertThat(fetch("?action=NO_SUCH_ACTION").get("totalItems").asInt()).isZero();
        assertThat(fetch("?action=NO_SUCH_ACTION").get("items")).isEmpty();

        // فاکتورِ مدرسه‌ی دیگر: هیچ ردیفی (و لو نمی‌رود)
        JsonNode foreign = fetch("?invoiceId=" + otherInvoice);
        assertThat(foreign.get("totalItems").asInt()).isZero();
        assertThat(foreign.get("items")).isEmpty();
        // و فهرستِ بدون فیلتر هیچ‌وقت ردیفِ مدرسه‌ی دیگر ندارد
        fetch("").get("items").forEach(item ->
                assertThat(item.get("invoiceId").isNull() || item.get("invoiceId").asLong() != otherInvoice).isTrue());
    }

    @Test
    void pagingReportsTotalAndSlices() throws Exception {
        int total = totalRows();
        JsonNode all = fetch("?size=100");
        JsonNode second = fetch("?size=2&page=1");

        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(second.get("size").asInt()).isEqualTo(2);
        assertThat(second.get("totalItems").asInt()).isEqualTo(total);
        assertThat(second.get("items")).hasSize(2);
        assertThat(second.get("items").get(0).get("id").asLong()).isEqualTo(all.get("items").get(2).get("id").asLong());
        assertThat(second.get("items").get(1).get("id").asLong()).isEqualTo(all.get("items").get(3).get("id").asLong());

        assertThat(fetch("?size=2&page=1000").get("items")).isEmpty();
    }

    @Test
    void dateRangeUsesWholeDaysInSchoolTimezoneInclusive() throws Exception {
        // Asia/Tehran = UTC+03:30 (بدون DST): 2026-03-10T20:29:59Z هنوز 10 مارس، 20:30:00Z برابر 00:00 ِ 11 مارس است
        Long boundaryInvoice = 987_654_321L;
        insertAudit(boundaryInvoice, "BOUNDARY_PROBE", "2026-03-10T20:29:59Z");
        insertAudit(boundaryInvoice, "BOUNDARY_PROBE", "2026-03-10T20:30:00Z");
        insertAudit(boundaryInvoice, "BOUNDARY_PROBE", "2026-03-11T20:29:59Z");

        assertThat(fetch("?action=BOUNDARY_PROBE&from=2026-03-10&to=2026-03-10").get("totalItems").asInt()).isEqualTo(1);
        assertThat(fetch("?action=BOUNDARY_PROBE&from=2026-03-11&to=2026-03-11").get("totalItems").asInt()).isEqualTo(2);
        assertThat(fetch("?action=BOUNDARY_PROBE&from=2026-03-10&to=2026-03-11").get("totalItems").asInt()).isEqualTo(3);
        assertThat(fetch("?action=BOUNDARY_PROBE&from=2026-03-11").get("totalItems").asInt()).isEqualTo(2);
        assertThat(fetch("?action=BOUNDARY_PROBE&to=2026-03-10").get("totalItems").asInt()).isEqualTo(1);
        assertThat(fetch("?action=BOUNDARY_PROBE&from=2026-03-12").get("totalItems").asInt()).isZero();

        // ترکیب با بقیه‌ی فیلترها: همه‌ی رخدادهای امروز، ولی فقط پرداخت‌های فاکتور A
        String today = LocalDate.now(TEHRAN).toString();
        assertThat(fetch("?invoiceId=" + invoiceA + "&action=PAYMENT_RECORDED&from=" + today + "&to=" + today)
                .get("totalItems").asInt()).isEqualTo(2);
        String yesterday = LocalDate.now(TEHRAN).minusDays(1).toString();
        assertThat(fetch("?invoiceId=" + invoiceA + "&from=" + yesterday + "&to=" + yesterday)
                .get("totalItems").asInt()).isZero();
    }

    private void insertAudit(Long invoiceId, String action, String occurredAt) {
        jdbcTemplate.update("""
                insert into finance.finance_audit (school_id, action, invoice_id, occurred_at)
                values (?, ?, ?, ?::timestamptz)
                """, schoolId, action, invoiceId, occurredAt);
    }

    @Test
    void invalidFiltersAreRejectedWithInvalidAuditFilter() throws Exception {
        assertInvalidFilter("?from=2026-03-11&to=2026-03-10");
        assertInvalidFilter("?size=0");
        assertInvalidFilter("?size=101");
        assertInvalidFilter("?page=-1");
        assertInvalidFilter("?from=not-a-date");
        // مرزهای مجاز
        fetch("?size=1");
        fetch("?size=100");
        fetch("?from=2026-03-10&to=2026-03-10");
    }

    @Test
    void facadeValidatesAndReturnsTypedPage() {
        assertThatThrownBy(() -> financeFacade.listAuditTrail(schoolId,
                new AuditQuery(null, null, null, LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 10), 0, 50)))
                .isInstanceOf(FinanceException.class)
                .extracting(e -> ((FinanceException) e).error()).isInstanceOf(FinanceError.InvalidAuditFilter.class);

        AuditPage page = financeFacade.listAuditTrail(schoolId,
                new AuditQuery(invoiceA, null, "PAYMENT_RECORDED", null, null, 0, 10));
        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.items()).extracting(AuditEntry::actorName).containsOnly(FOUNDER_NAME);
        assertThat(page.items().get(0).occurredAt()).isAfterOrEqualTo(page.items().get(1).occurredAt());
        assertThat(page.items().get(0).details()).containsEntry("method", "BANK_TRANSFER");
    }

    @Test
    void corruptStoredDetailsComeBackAsNull() throws Exception {
        jdbcTemplate.update("""
                insert into finance.finance_audit (school_id, action, details, occurred_at)
                values (?, 'CORRUPT_PROBE', 'not json {', now()), (?, 'CORRUPT_PROBE', '[1,2]', now())
                """, schoolId, schoolId);

        JsonNode page = fetch("?action=CORRUPT_PROBE");
        assertThat(page.get("totalItems").asInt()).isEqualTo(2);
        page.get("items").forEach(item -> assertThat(item.get("details").isNull()).isTrue());
    }

    @Test
    void parentWithOnlyFinanceViewIsForbidden() throws Exception {
        mockMvc.perform(audit(parentSession, ""))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("type").asText()).endsWith("permission-denied"));
    }

    @Test
    void userOfAnotherSchoolCannotRead() throws Exception {
        // با هدر مدرسه‌ی خودش و مسیر مدرسه‌ی ما: عدم تطابق
        MvcResult mismatch = mockMvc.perform(get("/api/admin/schools/" + schoolId + "/finance/audit")
                        .header("X-School-ID", otherSchoolId).session(otherFounderSession))
                .andReturn();
        assertThat(mismatch.getResponse().getStatus()).isIn(400, 403, 404);

        // با هدر مدرسه‌ی ما ولی بدون عضویت
        MvcResult nonMember = mockMvc.perform(audit(otherFounderSession, "")).andReturn();
        assertThat(nonMember.getResponse().getStatus()).isIn(403, 404);
        assertThat(nonMember.getResponse().getContentAsString()).doesNotContain("PAYMENT_RECORDED");
    }

    @Test
    void readingNeverChangesRowsAndRepositoryHasNoWritePathBesideSave() throws Exception {
        List<Map<String, Object>> before = jdbcTemplate.queryForList(
                "select * from finance.finance_audit where school_id = ? order by id", schoolId);
        fetch("");
        fetch("?action=PAYMENT_RECORDED&size=1");
        fetch("?invoiceId=" + invoiceA + "&from=2020-01-01&to=2099-12-31");
        assertThat(jdbcTemplate.queryForList(
                "select * from finance.finance_audit where school_id = ? order by id", schoolId)).isEqualTo(before);

        Class<?> repository = Class.forName("com.example.roshdeandishe.finance.internal.FinanceAuditRepository");
        List<String> methods = Arrays.stream(repository.getMethods()).map(Method::getName).sorted().toList();
        assertThat(methods).containsExactly("save", "search");
        assertThat(repository.getInterfaces()).extracting(Class::getSimpleName).containsExactly("Repository");
    }
}
