package com.example.roshdeandishe;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.finance.PaymentMethod;
import com.example.roshdeandishe.finance.shared.FinanceError;
import com.example.roshdeandishe.finance.shared.FinanceException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * فاز پرداخت‌های ماژول مالی: ثبت پرداخت دستی (جزئی/کامل)، وضعیت مشتق‌شده‌ی
 * فاکتور (InvoiceStatus - هرگز persist نمی‌شود)، backfill idempotent، و
 * endpoint خودسرویس /api/me/schools/{id}/invoices (دانش‌آموز خودش + ولیِ
 * لینک‌شده - StudentGuardianEntity). الگوی لاگین از
 * FinanceManagementFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancePaymentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinanceFacade financeFacade;

    @Autowired
    private AcademicFacade academicFacade;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

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

    @Test
    void partialThenFullPaymentDrivesDerivedStatus() {
        Long schoolId = 9201L;
        financeFacade.setTuitionFee(schoolId, new BigDecimal("1000000.00"));
        financeFacade.issueTuitionInvoice(schoolId, 42L);
        InvoiceInfo invoice = financeFacade.listInvoices(schoolId).get(0);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoice.dueDate()).isNotNull();

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("400000.00"),
                PaymentMethod.CASH, "قسط اول");
        assertThat(financeFacade.listInvoices(schoolId).get(0).status()).isEqualTo(InvoiceStatus.PARTIAL);

        financeFacade.recordPayment(schoolId, invoice.id(), new BigDecimal("600000.00"),
                PaymentMethod.BANK_TRANSFER, "تسویه");
        InvoiceInfo paid = financeFacade.listInvoices(schoolId).get(0);
        assertThat(paid.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.amountPaid()).isEqualByComparingTo("1000000.00");

        assertThat(financeFacade.listPayments(schoolId, invoice.id())).hasSize(2);
    }

    @Test
    void overpaymentAndInvalidAmountsAreRejected() {
        Long schoolId = 9202L;
        financeFacade.setTuitionFee(schoolId, new BigDecimal("500000.00"));
        financeFacade.issueTuitionInvoice(schoolId, 43L);
        Long invoiceId = financeFacade.listInvoices(schoolId).get(0).id();

        assertThatThrownBy(() -> financeFacade.recordPayment(schoolId, invoiceId,
                new BigDecimal("500000.01"), PaymentMethod.CASH, null))
                .isInstanceOf(FinanceException.class)
                .extracting(e -> ((FinanceException) e).error())
                .isInstanceOf(FinanceError.PaymentExceedsBalance.class);

        assertThatThrownBy(() -> financeFacade.recordPayment(schoolId, invoiceId,
                BigDecimal.ZERO, PaymentMethod.CASH, null))
                .isInstanceOf(FinanceException.class);

        // فاکتور متعلق به مدرسه‌ی دیگر باید NotFound باشد، نه قابل پرداخت
        assertThatThrownBy(() -> financeFacade.recordPayment(9999L, invoiceId,
                new BigDecimal("1"), PaymentMethod.CASH, null))
                .isInstanceOf(FinanceException.class)
                .extracting(e -> ((FinanceException) e).error())
                .isInstanceOf(FinanceError.InvoiceNotFound.class);
    }

    @Test
    void unpaidInvoicePastDueDateDerivesOverdue() {
        Long schoolId = 9203L;
        financeFacade.setTuitionFee(schoolId, new BigDecimal("800000.00"));
        financeFacade.issueTuitionInvoice(schoolId, 44L);
        Long invoiceId = financeFacade.listInvoices(schoolId).get(0).id();

        // وضعیت persist نمی‌شود؛ برای شبیه‌سازی گذر زمان، سررسید را مستقیم در
        // DB به گذشته می‌بریم و فقط خواندن بعدی باید OVERDUE مشتق کند
        jdbcTemplate.update(
                "update finance.invoices set due_date = now() - interval '1 day' where id = ?", invoiceId);

        assertThat(financeFacade.listInvoices(schoolId).get(0).status()).isEqualTo(InvoiceStatus.OVERDUE);

        // پرداخت جزئیِ دیرشده هم OVERDUE می‌ماند (نه PARTIAL)؛ تسویه‌ی کامل PAID
        financeFacade.recordPayment(schoolId, invoiceId, new BigDecimal("100000.00"), PaymentMethod.CASH, null);
        assertThat(financeFacade.listInvoices(schoolId).get(0).status()).isEqualTo(InvoiceStatus.OVERDUE);
        financeFacade.recordPayment(schoolId, invoiceId, new BigDecimal("700000.00"), PaymentMethod.CASH, null);
        assertThat(financeFacade.listInvoices(schoolId).get(0).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void backfillIssuesInvoicesOnlyForUninvoicedStudentsAndIsIdempotent() {
        // academicFacade.createClass اکنون schoolId را در برابر یک SchoolEntity
        // واقعی اعتبارسنجی می‌کند (school.SchoolFacade.SchoolLevel، CLAUDE.md §4)
        // - برخلاف بقیه‌ی این فایل، این یک متد ساختگی نمی‌تواند باشد.
        Long schoolId = schoolFacade.createSchool(
                "مدرسه‌ی تست backfill", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null).value();
        Long classId = academicFacade.createClass(schoolId, "پایه ششم - الف", null, "الف", 6, GradingScheme.NUMERIC).value();

        // ثبت‌نام قبل از تنظیم شهریه: listener مالی چیزی صادر نمی‌کند (تصمیم
        // صریح CLAUDE.md §4). صبر می‌کنیم همه‌ی publication های async تمام
        // شوند تا listener دیررس با backfill مسابقه ندهد.
        academicFacade.enrollStudent("backfill.one@example.com", "دانش‌آموز یک", schoolId, classId);
        academicFacade.enrollStudent("backfill.two@example.com", "دانش‌آموز دو", schoolId, classId);
        // registry رویدادها (event_publication، ثبت‌شده توسط starter-jpa در
        // اسکیمای پیش‌فرض iam) تا وقتی listener تمام نشده completion_date
        // ندارد - صبر تا کامل شدن publication های ثبت‌نامِ همین مدرسه یعنی
        // listener دیررس با backfill مسابقه نمی‌دهد. عمداً فقط رویدادهای همین
        // مدرسه: تست‌های دیگر (همین کلاس و FinanceFlowIntegrationTest) با
        // studentId ساختگی فاکتور صادر می‌کنند که listener ماژول bale را
        // می‌شکند (StudentNotFound) و آن publication ها هرگز کامل نمی‌شوند -
        // یک وضعیت از قبل موجود، نه محصول این تست.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer incomplete = jdbcTemplate.queryForObject(
                    """
                    select count(*) from iam.event_publication
                    where completion_date is null
                      and event_type = 'com.example.roshdeandishe.academic.StudentEnrolledEvent'
                      and serialized_event like '%' || ? || '%'
                    """,
                    Integer.class, String.valueOf(schoolId));
            assertThat(incomplete).isZero();
        });
        assertThat(financeFacade.listInvoices(schoolId)).isEmpty();

        // بدون شهریه backfill خطاست (برخلاف مسیر ثبت‌نام که ساکت رد می‌شود)
        assertThatThrownBy(() -> financeFacade.backfillInvoices(schoolId))
                .isInstanceOf(FinanceException.class)
                .extracting(e -> ((FinanceException) e).error())
                .isInstanceOf(FinanceError.TuitionFeeNotSet.class);

        financeFacade.setTuitionFee(schoolId, new BigDecimal("2000000.00"));

        assertThat(financeFacade.backfillInvoices(schoolId)).isEqualTo(2);
        List<InvoiceInfo> invoices = financeFacade.listInvoices(schoolId);
        assertThat(invoices).hasSize(2);
        assertThat(invoices).allSatisfy(i -> assertThat(i.amount()).isEqualByComparingTo("2000000.00"));

        // idempotent: اجرای دوباره هیچ فاکتور تکراری‌ای صادر نمی‌کند
        assertThat(financeFacade.backfillInvoices(schoolId)).isZero();
        assertThat(financeFacade.listInvoices(schoolId)).hasSize(2);
    }

    @Test
    void recordingPaymentOverRestWritesAuditRowAndRejectsOverpaymentWith409() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/schools/1/tuition-fee")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":3000000.00}"))
                .andExpect(status().isOk());

        // فاکتور مستقیم از facade صادر می‌شود تا تست به async ثبت‌نام گره نخورد
        financeFacade.issueTuitionInvoice(1L, 4242L);
        Long invoiceId = financeFacade.listInvoices(1L).stream()
                .filter(i -> i.studentId().equals(4242L))
                .findFirst().orElseThrow().id();

        mockMvc.perform(post("/api/admin/schools/1/invoices/" + invoiceId + "/payments")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000000.00,\"method\":\"CASH\",\"note\":\"قسط اول\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000000.00));

        mockMvc.perform(get("/api/admin/schools/1/invoices/" + invoiceId + "/payments")
                        .header("X-School-ID", "1")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].method").value("CASH"));

        // اضافه‌پرداخت: مانده 2,000,000 است - 409 با type مخصوص خودش
        mockMvc.perform(post("/api/admin/schools/1/invoices/" + invoiceId + "/payments")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":2000000.01,\"method\":\"CASH\",\"note\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("payment-exceeds-balance")));

        // ردیف audit پرداخت (PaymentRecordedEvent → FinanceAuditEventListener) async نوشته می‌شود
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer auditRows = jdbcTemplate.queryForObject(
                    "select count(*) from finance.finance_audit where action = 'PAYMENT_RECORDED' and student_id = 4242",
                    Integer.class);
            assertThat(auditRows).isEqualTo(1);
        });
    }

    @Test
    void myInvoicesReturnsOwnAndWardsInvoicesOnly() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/schools/1/tuition-fee")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1500000.00}"))
                .andExpect(status().isOk());

        // دانش‌آموز: اول register (با رمز) بعد enroll همان ایمیل -
        // findOrCreateUser رمز موجود را دست نمی‌زند (الگوی MyEnrollmentFlowIntegrationTest)
        String studentEmail = "payment.student@roshdeandishe.test";
        MockHttpSession studentSession = registerAndLogin(studentEmail, "دانش‌آموز پرداخت");

        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس پرداخت\",\"section\":\"الف\",\"teacherId\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult enrollResult = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"fullName\":\"دانش‌آموز پرداخت\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Long studentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("studentId").asLong();

        // فاکتور ثبت‌نام async صادر می‌شود - تا پیدا شدنش صبر می‌کنیم
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(financeFacade.listInvoicesForStudents(1L, List.of(studentId))).hasSize(1));

        // ولی: register + عضویت PARENT + لینک guardian توسط ادمین
        String parentEmail = "payment.parent@roshdeandishe.test";
        MockHttpSession parentSession = registerAndLogin(parentEmail, "ولی پرداخت");
        MvcResult membershipResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولی پرداخت\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long parentUserId = objectMapper.readTree(membershipResult.getResponse().getContentAsString())
                .get("userId").asLong();

        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/guardians")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianUserId\":" + parentUserId + "}"))
                .andExpect(status().isOk());

        // دانش‌آموز فاکتور خودش را می‌بیند - بدون هیچ Permission خاصی
        mockMvc.perform(get("/api/me/schools/1/invoices").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")]").exists());

        // ولی هم فاکتور فرزندِ واقعاً لینک‌شده‌اش را می‌بیند
        mockMvc.perform(get("/api/me/schools/1/invoices").session(parentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")]").exists());

        // کاربر بی‌ربط (نه دانش‌آموز، نه ولیِ لینک‌شده): لیست خالی، نه 403 -
        // endpoint فقط داده‌ی خودِ کاربر را برمی‌گرداند
        MockHttpSession strangerSession = registerAndLogin("payment.stranger@roshdeandishe.test", "کاربر بی‌ربط");
        mockMvc.perform(get("/api/me/schools/1/invoices").session(strangerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // بدون احراز هویت: 403 (قرارداد /api/** در SecurityConfig)
        mockMvc.perform(get("/api/me/schools/1/invoices"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminInvoicesEndpointScopesParentToOwnWardsButAdminKeepsFullVisibility() throws Exception {
        // تصمیم صریح CLAUDE.md §10 (حل‌شده): GET .../invoices دیگر برای هر
        // دارنده‌ی FINANCE_VIEW بی‌قید همه‌ی فاکتورهای مدرسه را برنمی‌گرداند -
        // PARENT فقط فاکتورهای فرزندِ واقعاً لینک‌شده‌اش را می‌بیند، ولی
        // SCHOOL_ADMIN/FINANCE_ADMIN همچنان دید کامل مدرسه را دارند.
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/schools/1/tuition-fee")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1200000.00}"))
                .andExpect(status().isOk());

        String studentEmail = "scoped.student@roshdeandishe.test";
        MockHttpSession studentSession = registerAndLogin(studentEmail, "دانش‌آموز محدود");

        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس محدود\",\"section\":\"ب\",\"teacherId\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult enrollResult = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"fullName\":\"دانش‌آموز محدود\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Long studentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("studentId").asLong();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(financeFacade.listInvoicesForStudents(1L, List.of(studentId))).hasSize(1));

        String parentEmail = "scoped.parent@roshdeandishe.test";
        MockHttpSession parentSession = registerAndLogin(parentEmail, "ولی محدود");
        MvcResult membershipResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + parentEmail + "\",\"fullName\":\"ولی محدود\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long parentUserId = objectMapper.readTree(membershipResult.getResponse().getContentAsString())
                .get("userId").asLong();

        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/guardians")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianUserId\":" + parentUserId + "}"))
                .andExpect(status().isOk());

        // ادمین: بین همه‌ی فاکتورهای مدرسه، فاکتور این دانش‌آموز هم هست
        mockMvc.perform(get("/api/admin/schools/1/invoices").header("X-School-ID", "1").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")]").exists());

        // ولی: فقط دقیقاً همان یک فاکتورِ فرزندش را می‌بیند، نه بقیه‌ی مدرسه
        mockMvc.perform(get("/api/admin/schools/1/invoices").header("X-School-ID", "1").session(parentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].studentId").value(studentId));

        // دانش‌آموز خودش FINANCE_VIEW ندارد - همچنان 403 روی endpoint ادمینی
        mockMvc.perform(get("/api/admin/schools/1/invoices").header("X-School-ID", "1").session(studentSession))
                .andExpect(status().isForbidden());
    }
}
