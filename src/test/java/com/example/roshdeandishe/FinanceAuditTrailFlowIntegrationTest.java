package com.example.roshdeandishe;

import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.internal.DiscountSweepHook;
import com.example.roshdeandishe.finance.FinanceFacade.DiscountInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InstallmentEdit;
import com.example.roshdeandishe.finance.FinanceFacade.InstallmentInfo;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.FinanceFacade.PaymentInfo;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.school.SchoolFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ردیابیِ کاملِ audit مالی: هر تغییرِ وضعیتِ مالی یک ردیفِ تغییرناپذیرِ {@code finance.finance_audit} می‌گذارد که
 * می‌گوید چه کسی (actor، تهی = سیستم)، کی، کدام مدرسه/دانش‌آموز/فاکتور و چه چیزی (قبل → بعد) عوض شد.
 * ردیف‌ها async و از راه ثبتِ رویدادِ پایدار نوشته می‌شوند، پس همه‌جا await.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceAuditTrailFlowIntegrationTest {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    private static final Long STUDENT = 77L;
    private static final String ADMIN_EMAIL = "admin@roshdeandishe.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinanceFacade financeFacade;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** facade را «به‌عنوان ادمین» صدا می‌زند (IamFacade.currentUserId از SecurityContext می‌خواند). */
    private Long actAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(ADMIN_EMAIL, "x", "ROLE_X"));
        return jdbcTemplate.queryForObject("select id from iam.users where email = ?", Long.class, ADMIN_EMAIL);
    }

    private void actAsSystem() {
        SecurityContextHolder.clearContext();
    }

    private Long newSchool(boolean installmentsEnabled) {
        SchoolFacade.SchoolId id = schoolFacade.createSchool(
                "مدرسه‌ی audit " + UUID.randomUUID(), "Asia/Tehran", "fa-IR",
                SchoolFacade.SchoolLevel.ELEMENTARY, null);
        if (installmentsEnabled) {
            schoolFacade.setInstallmentsEnabled(id, true);
        }
        return id.value();
    }

    private InvoiceInfo newInvoice(Long schoolId, String amount) {
        financeFacade.setTuitionFee(schoolId, new BigDecimal(amount));
        financeFacade.issueTuitionInvoice(schoolId, STUDENT);
        return financeFacade.listInvoices(schoolId).get(0);
    }

    private static LocalDate today() {
        return LocalDate.now(TEHRAN);
    }

    private List<Map<String, Object>> rows(Long schoolId, String action) {
        return jdbcTemplate.queryForList(
                "select * from finance.finance_audit where school_id = ? and action = ? order by id", schoolId, action);
    }

    private Map<String, Object> awaitRow(Long schoolId, String action, int index) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rows(schoolId, action)).hasSizeGreaterThan(index));
        return rows(schoolId, action).get(index);
    }

    private JsonNode details(Map<String, Object> row) throws Exception {
        Object text = row.get("details");
        assertThat(text).isNotNull();
        return objectMapper.readTree(text.toString());
    }

    private void expire(Long discountId) {
        jdbcTemplate.update("update finance.invoice_discounts set deadline = ? where id = ?",
                today().minusDays(1), discountId);
    }

    private void assertCommon(Map<String, Object> row, Long schoolId, Long invoiceId, Long actorOrNull) {
        assertThat(((Number) row.get("school_id")).longValue()).isEqualTo(schoolId);
        assertThat(row.get("invoice_id")).isEqualTo(invoiceId);
        assertThat(row.get("student_id")).isEqualTo(STUDENT);
        assertThat(row.get("actor_user_id")).isEqualTo(actorOrNull);
        assertThat(row.get("occurred_at")).isNotNull();
    }

    @Test
    void paymentRecordedWritesInvoiceActorAndMethodNoteDetails() throws Exception {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000.00");
        Long admin = actAsAdmin();
        String note = "n".repeat(512); // سقفِ ستونِ یادداشتِ پرداخت؛ رویدادِ پایدار هم باید این طول را نگه دارد

        PaymentInfo payment = financeFacade.recordPayment(
                schoolId, invoice.id(), new BigDecimal("250.00"), PaymentMethod.CASH, note);

        Map<String, Object> row = awaitRow(schoolId, "PAYMENT_RECORDED", 0);
        assertCommon(row, schoolId, invoice.id(), admin);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("250.00");
        JsonNode details = details(row);
        assertThat(details.get("paymentId").asLong()).isEqualTo(payment.id());
        assertThat(details.get("method").asText()).isEqualTo("CASH");
        assertThat(details.get("note").asText()).hasSize(512).isEqualTo(note);
    }

    @Test
    void plainAndConditionalDiscountsWriteDiscountDetails() throws Exception {
        Long schoolId = newSchool(false);
        InvoiceInfo invoice = newInvoice(schoolId, "1000.00");
        Long admin = actAsAdmin();
        LocalDate deadline = today().plusDays(5);

        DiscountInfo plain = financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("100.00"), "ساده");
        DiscountInfo conditional = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("200.00"), "شرطی", deadline);

        Map<String, Object> plainRow = awaitRow(schoolId, "INVOICE_DISCOUNTED", 0);
        assertCommon(plainRow, schoolId, invoice.id(), admin);
        assertThat((BigDecimal) plainRow.get("amount")).isEqualByComparingTo("100.00");
        JsonNode plainDetails = details(plainRow);
        assertThat(plainDetails.get("discountId").asLong()).isEqualTo(plain.id());
        assertThat(plainDetails.get("reason").asText()).isEqualTo("ساده");
        assertThat(plainDetails.get("deadline").isNull()).isTrue();
        assertThat(plainDetails.get("status").asText()).isEqualTo("APPLIED");

        Map<String, Object> conditionalRow = awaitRow(schoolId, "INVOICE_DISCOUNTED", 1);
        assertCommon(conditionalRow, schoolId, invoice.id(), admin);
        JsonNode conditionalDetails = details(conditionalRow);
        assertThat(conditionalDetails.get("discountId").asLong()).isEqualTo(conditional.id());
        assertThat(conditionalDetails.get("deadline").asText()).isEqualTo(deadline.toString());
        assertThat(conditionalDetails.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void discountEarnedAndLapsedAreSystemRowsWithDiscountAndDeadline() throws Exception {
        Long schoolId = newSchool(false);
        InvoiceInfo earnedInvoice = newInvoice(schoolId, "1000.00");
        LocalDate deadline = today().plusDays(5);
        DiscountInfo earnable = financeFacade.applyDiscount(
                schoolId, earnedInvoice.id(), new BigDecimal("200.00"), null, deadline);
        Long admin = actAsAdmin();
        financeFacade.recordPayment(schoolId, earnedInvoice.id(), new BigDecimal("800.00"), PaymentMethod.CASH, null);

        // فرد دومی با دانش‌آموز دیگر، چون فاکتورِ هر (مدرسه، دانش‌آموز، سال) یکتاست
        financeFacade.issueTuitionInvoice(schoolId, 78L);
        InvoiceInfo lapseInvoice = financeFacade.listInvoices(schoolId).stream()
                .filter(i -> i.studentId().equals(78L)).findFirst().orElseThrow();
        DiscountInfo lapsing = financeFacade.applyDiscount(
                schoolId, lapseInvoice.id(), new BigDecimal("100.00"), null, deadline);
        actAsSystem();
        expire(lapsing.id());
        DiscountSweepHook.sweep(financeFacade);

        Map<String, Object> earned = awaitRow(schoolId, "DISCOUNT_EARNED", 0);
        assertThat(earned.get("actor_user_id")).isNull(); // سیستم، نه کاربرِ پرداخت‌کننده
        assertThat(admin).isNotNull();
        assertThat(earned.get("invoice_id")).isEqualTo(earnedInvoice.id());
        assertThat(earned.get("student_id")).isEqualTo(STUDENT);
        assertThat((BigDecimal) earned.get("amount")).isEqualByComparingTo("200.00");
        JsonNode earnedDetails = details(earned);
        assertThat(earnedDetails.get("discountId").asLong()).isEqualTo(earnable.id());
        assertThat(earnedDetails.get("deadline").asText()).isEqualTo(deadline.toString());
        assertThat(earnedDetails.get("trigger").asText()).isEqualTo("payment");

        Map<String, Object> lapsed = awaitRow(schoolId, "DISCOUNT_LAPSED", 0);
        assertThat(lapsed.get("actor_user_id")).isNull();
        assertThat(lapsed.get("invoice_id")).isEqualTo(lapseInvoice.id());
        assertThat(lapsed.get("student_id")).isEqualTo(78L);
        JsonNode lapsedDetails = details(lapsed);
        assertThat(lapsedDetails.get("discountId").asLong()).isEqualTo(lapsing.id());
        // رویداد مهلتِ «اکنون»ِ ردیف را می‌آورد (expire آن را به دیروز برده)
        assertThat(lapsedDetails.get("deadline").asText()).isEqualTo(today().minusDays(1).toString());
        assertThat(lapsedDetails.get("trigger").asText()).isEqualTo("sweep");
    }

    @Test
    void installmentsChangedRecordsBeforeAfterForGenerateEditResplitAndLapseResplit() throws Exception {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "900.00");
        Long admin = actAsAdmin();
        LocalDate first = today().plusDays(10);

        // GENERATED: قبل خالی، بعد سه قسط
        List<InstallmentInfo> plan = financeFacade.generateInstallments(schoolId, invoice.id(), 3, first, 1);
        Map<String, Object> generated = awaitRow(schoolId, "INSTALLMENTS_CHANGED", 0);
        assertCommon(generated, schoolId, invoice.id(), admin);
        assertThat((BigDecimal) generated.get("amount")).isEqualByComparingTo("900.00");
        JsonNode generatedDetails = details(generated);
        assertThat(generatedDetails.get("cause").asText()).isEqualTo("GENERATED");
        assertThat(generatedDetails.get("before")).isEmpty();
        assertThat(generatedDetails.get("after")).hasSize(3);
        assertThat(generatedDetails.get("after").get(0).get("amount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(generatedDetails.get("after").get(0).get("dueDate").asText()).isEqualTo(first.toString());

        // بازسازیِ همان طرح (همان تعداد/تاریخ) چیزی عوض نمی‌کند → ردیفِ تازه‌ای نیست
        plan = financeFacade.generateInstallments(schoolId, invoice.id(), 3, first, 1); // شناسه‌ها تازه‌اند

        // EDITED
        financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(plan.get(0).id(), new BigDecimal("400.00"), plan.get(0).dueDate()),
                new InstallmentEdit(plan.get(1).id(), new BigDecimal("300.00"), plan.get(1).dueDate()),
                new InstallmentEdit(plan.get(2).id(), new BigDecimal("200.00"), plan.get(2).dueDate())));
        Map<String, Object> edited = awaitRow(schoolId, "INSTALLMENTS_CHANGED", 1);
        assertCommon(edited, schoolId, invoice.id(), admin);
        JsonNode editedDetails = details(edited);
        assertThat(editedDetails.get("cause").asText()).isEqualTo("EDITED");
        assertThat(editedDetails.get("before").get(0).get("amount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(editedDetails.get("after").get(0).get("amount").decimalValue()).isEqualByComparingTo("400.00");

        // ذخیره‌ی دوباره‌ی همان مقدارها: بدون ردیف
        financeFacade.updateInstallments(schoolId, invoice.id(), List.of(
                new InstallmentEdit(plan.get(0).id(), new BigDecimal("400.00"), plan.get(0).dueDate()),
                new InstallmentEdit(plan.get(1).id(), new BigDecimal("300.00"), plan.get(1).dueDate()),
                new InstallmentEdit(plan.get(2).id(), new BigDecimal("200.00"), plan.get(2).dueDate())));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rows(schoolId, "INSTALLMENTS_CHANGED")).hasSize(2));

        // DISCOUNT_RESPLIT: تخفیفِ مشروط ۳۰۰ → سه قسطِ ۲۰۰
        DiscountInfo discount = financeFacade.applyDiscount(
                schoolId, invoice.id(), new BigDecimal("300.00"), "x", today().plusDays(3));
        Map<String, Object> resplit = awaitRow(schoolId, "INSTALLMENTS_CHANGED", 2);
        assertCommon(resplit, schoolId, invoice.id(), admin);
        JsonNode resplitDetails = details(resplit);
        assertThat(resplitDetails.get("cause").asText()).isEqualTo("DISCOUNT_RESPLIT");
        assertThat(resplitDetails.get("before").get(0).get("amount").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(resplitDetails.get("after").get(0).get("amount").decimalValue()).isEqualByComparingTo("200.00");
        assertThat((BigDecimal) resplit.get("amount")).isEqualByComparingTo("600.00");

        // DISCOUNT_LAPSE_RESPLIT (خودکار؛ سیستم): تخفیف برمی‌خیزد و اقساط به ۳۰۰ برمی‌گردند
        actAsSystem();
        expire(discount.id());
        DiscountSweepHook.sweep(financeFacade);
        Map<String, Object> lapse = awaitRow(schoolId, "INSTALLMENTS_CHANGED", 3);
        assertCommon(lapse, schoolId, invoice.id(), null);
        JsonNode lapseDetails = details(lapse);
        assertThat(lapseDetails.get("cause").asText()).isEqualTo("DISCOUNT_LAPSE_RESPLIT");
        assertThat(lapseDetails.get("before").get(0).get("amount").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(lapseDetails.get("after").get(0).get("amount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat((BigDecimal) lapse.get("amount")).isEqualByComparingTo("900.00");
    }

    @Test
    void installmentsSettingChangeIsAuditedOnlyWhenTheValueChanges() throws Exception {
        Long schoolId = newSchool(false);
        Long admin = actAsAdmin();

        financeFacade.setInstallmentsEnabled(schoolId, true);
        Map<String, Object> on = awaitRow(schoolId, "INSTALLMENTS_SETTING_CHANGED", 0);
        assertThat(on.get("actor_user_id")).isEqualTo(admin);
        assertThat(on.get("student_id")).isNull();
        assertThat(on.get("invoice_id")).isNull();
        assertThat(details(on).get("from").asBoolean()).isFalse();
        assertThat(details(on).get("to").asBoolean()).isTrue();
        assertThat(schoolFacade.isInstallmentsEnabled(new SchoolFacade.SchoolId(schoolId))).isTrue();

        // همان مقدار: ردیفِ تازه‌ای نیست
        financeFacade.setInstallmentsEnabled(schoolId, true);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rows(schoolId, "INSTALLMENTS_SETTING_CHANGED")).hasSize(1));

        financeFacade.setInstallmentsEnabled(schoolId, false);
        Map<String, Object> off = awaitRow(schoolId, "INSTALLMENTS_SETTING_CHANGED", 1);
        assertThat(details(off).get("from").asBoolean()).isTrue();
        assertThat(details(off).get("to").asBoolean()).isFalse();
    }

    @Test
    void settingEndpointGoesThroughTheFinanceFacadeAndIsAudited() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        Long admin = jdbcTemplate.queryForObject("select id from iam.users where email = ?", Long.class, ADMIN_EMAIL);
        boolean current = schoolFacade.isInstallmentsEnabled(new SchoolFacade.SchoolId(1L));
        int before = rows(1L, "INSTALLMENTS_SETTING_CHANGED").size();

        put1(session, !current);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rows(1L, "INSTALLMENTS_SETTING_CHANGED")).hasSize(before + 1));
        Map<String, Object> row = rows(1L, "INSTALLMENTS_SETTING_CHANGED").get(before);
        assertThat(row.get("actor_user_id")).isEqualTo(admin);
        assertThat(details(row).get("to").asBoolean()).isEqualTo(!current);

        put1(session, !current); // بدون تغییر
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rows(1L, "INSTALLMENTS_SETTING_CHANGED")).hasSize(before + 1));
        put1(session, current); // برگرداندنِ وضعیتِ مدرسه‌ی ۱ برای تست‌های دیگر
        mockMvc.perform(get("/api/admin/schools/1/finance/settings").header("X-School-ID", "1").session(session))
                .andExpect(status().isOk());
    }

    private void put1(MockHttpSession session, boolean enabled) throws Exception {
        mockMvc.perform(put("/api/admin/schools/1/finance/settings/installments")
                        .with(csrf()).header("X-School-ID", "1").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void auditRowsAreNeverModifiedByLaterOperationsAndTheRepositoryOnlyAppends() throws Exception {
        Long schoolId = newSchool(true);
        InvoiceInfo invoice = newInvoice(schoolId, "1000.00");
        actAsAdmin();
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("100.00"), PaymentMethod.CASH, "اول");
        Map<String, Object> first = awaitRow(schoolId, "PAYMENT_RECORDED", 0);

        // عملیات‌های بعدی: پرداخت، تخفیف، طرح اقساط، تنظیم، جارو
        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("50.00"), PaymentMethod.CASH, "دوم");
        financeFacade.applyDiscount(schoolId, invoice.id(), new BigDecimal("10.00"), "t", today().plusDays(2));
        financeFacade.generateInstallments(schoolId, invoice.id(), 2, today().plusDays(5), 1);
        financeFacade.setInstallmentsEnabled(schoolId, false);
        DiscountSweepHook.sweep(financeFacade);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rows(schoolId, "PAYMENT_RECORDED")).hasSize(2);
            assertThat(rows(schoolId, "INSTALLMENTS_CHANGED")).hasSize(1);
            assertThat(rows(schoolId, "INSTALLMENTS_SETTING_CHANGED")).hasSize(1);
        });

        assertThat(rows(schoolId, "PAYMENT_RECORDED").get(0)).isEqualTo(first);

        // سطحِ repository فقط save و خواندنِ search دارد: مسیری برای update/delete نیست
        Class<?> repository = Class.forName("com.example.roshdeandishe.finance.internal.FinanceAuditRepository");
        Set<String> methods = java.util.Arrays.stream(repository.getMethods()).map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(methods).containsExactlyInAnyOrder("save", "search");
    }
}
