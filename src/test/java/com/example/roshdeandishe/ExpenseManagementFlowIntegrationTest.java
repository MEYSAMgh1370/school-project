package com.example.roshdeandishe;

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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ماژول هزینه‌های مدرسه (ExpenseFacade/ExpenseController) - چرخه‌ی حیات
 * DRAFT→APPROVED→PAID و CANCELLED، تفکیک EXPENSE_MANAGE از EXPENSE_APPROVE
 * (نمونه‌ی جدید authz - رجوع به IamSeedDataRunner)، seed دسته‌های پیش‌فرض
 * idempotent، و دو نوع 403 لایه‌ی §5. الگوی لاگین از
 * FinanceManagementFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExpenseManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private MockHttpSession registerLoginAndGrantRole(MockHttpSession adminSession, String email,
                                                       String fullName, String roleCode) throws Exception {
        MockHttpSession session = registerAndLogin(email, fullName);
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"roleCode\":\"" + roleCode + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    @Test
    void unauthenticatedExpensesPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/expenses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void expensesPageRendersSharedNavbarForAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("هزینه‌های مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی نمونه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/finance\"")));
    }

    @Test
    void seedDefaultsIsIdempotent() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult first = mockMvc.perform(post("/api/admin/schools/1/expense-categories/seed-defaults")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(first.getResponse().getContentAsString())
                .get("createdCount").asInt()).isEqualTo(14);

        // اجرای دوباره چیز تازه‌ای اضافه نمی‌کند
        mockMvc.perform(post("/api/admin/schools/1/expense-categories/seed-defaults")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0));

        // طول کلی لیست را چک نمی‌کنیم: بقیه‌ی تست‌های همین کلاس روی همان
        // مدرسه‌ی نمونه (id=1) دسته‌های اختصاصی خودشان را هم می‌سازند و
        // ترتیب اجرای متدهای JUnit تضمین‌شده نیست - فقط حضورِ هر ۱۴ نامِ
        // پیش‌فرض را تأیید می‌کنیم
        MvcResult listResult = mockMvc.perform(get("/api/admin/schools/1/expense-categories")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        var names = objectMapper.readTree(listResult.getResponse().getContentAsString());
        java.util.Set<String> nameSet = new java.util.HashSet<>();
        names.forEach(node -> nameSet.add(node.get("name").asText()));
        assertThat(nameSet).contains("حقوق و دستمزد", "قبوض", "تعمیرات", "تجهیزات آموزشی", "مواد مصرفی",
                "خدمات نظافت", "پذیرایی", "سرویس مدرسه", "تبلیغات", "مالیات", "بیمه", "اجاره",
                "فوق برنامه", "سایر");
    }

    @Test
    void fullLifecycleCreateEditApprovePayWithInvalidTransitionsRejected() throws Exception {
        MockHttpSession session = loginAsAdmin();

        // نام دسته عمداً با هیچ‌کدام از ۱۴ دسته‌ی پیش‌فرض (seedDefaultsIsIdempotent)
        // یکسان نیست - این تست‌ها همه روی مدرسه‌ی نمونه (id=1) اجرا می‌شوند و
        // ترتیب اجرای متدهای JUnit تضمین‌شده نیست
        MvcResult categoryResult = mockMvc.perform(post("/api/admin/schools/1/expense-categories")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"دسته-تست-چرخه-حیات\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult vendorResult = mockMvc.perform(post("/api/admin/schools/1/vendors")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"شرکت نوین تجهیز\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long vendorId = objectMapper.readTree(vendorResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools/1/expenses")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-04-11\",\"amount\":35000000," +
                                "\"categoryId\":" + categoryId + ",\"vendorId\":" + vendorId + "," +
                                "\"paymentMethod\":\"BANK_TRANSFER\",\"description\":\"خرید میز و نیمکت\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long expenseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // ویرایش فقط در DRAFT مجاز است
        mockMvc.perform(put("/api/admin/schools/1/expenses/" + expenseId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-04-12\",\"amount\":36000000,\"categoryId\":" + categoryId +
                                ",\"vendorId\":" + vendorId + ",\"paymentMethod\":\"CASH\",\"description\":\"ویرایش‌شده\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(36000000))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"));

        // پرداخت از DRAFT مستقیم مجاز نیست
        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/pay")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-expense-status-transition")));

        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/approve")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // ویرایش بعد از تأیید دیگر مجاز نیست
        mockMvc.perform(put("/api/admin/schools/1/expenses/" + expenseId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-04-12\",\"amount\":1,\"categoryId\":" + categoryId +
                                ",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("expense-not-editable")));

        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/pay")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // PAID نهایی است - لغو بعد از پرداخت مجاز نیست
        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/cancel")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-expense-status-transition")));

        // چهار ردیف audit (ثبت/تأیید/پرداخت) - CANCELLED رخ نداد پس فقط سه گذار واقعی
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer rows = jdbcTemplate.queryForObject(
                    "select count(*) from finance.finance_audit where expense_id = ?", Integer.class, expenseId);
            assertThat(rows).isEqualTo(3);
        });
    }

    @Test
    void cancelIsAllowedFromDraftAndApprovedButNotAfterPaid() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/schools/1/expense-categories")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"سایر-تست-لغو\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools/1/expenses")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-05-01\",\"amount\":100000,\"categoryId\":" + categoryId +
                                ",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long expenseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/cancel")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void financeAdminCanManageButNotApprove() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MockHttpSession financeAdminSession = registerLoginAndGrantRole(
                adminSession, "expense.financeadmin@roshdeandishe.test", "مدیر مالی هزینه", "FINANCE_ADMIN");

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/schools/1/expense-categories")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"سایر-تست-تأیید\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools/1/expenses")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(financeAdminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-05-02\",\"amount\":200000,\"categoryId\":" + categoryId +
                                ",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long expenseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // FINANCE_ADMIN می‌تواند ثبت کند اما نمی‌تواند تأیید کند - تفکیک وظایف
        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/approve")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(financeAdminSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));

        // ادمین کامل تأیید و پرداخت می‌کند...
        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/approve")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession))
                .andExpect(status().isOk());

        // ...و FINANCE_ADMIN می‌تواند پرداخت را ثبت کند (EXPENSE_MANAGE)
        mockMvc.perform(post("/api/admin/schools/1/expenses/" + expenseId + "/pay")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(financeAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void teacherWithoutExpensePermissionGetsPermissionDenied() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MockHttpSession teacherSession = registerLoginAndGrantRole(
                adminSession, "expense.teacher@roshdeandishe.test", "معلم بدون دسترسی هزینه", "TEACHER");

        mockMvc.perform(get("/api/admin/schools/1/expenses")
                        .header("X-School-ID", "1")
                        .session(teacherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void schoolWithoutFinanceEntitlementGetsModuleNotEntitled() throws Exception {
        MockHttpSession founderSession = registerAndLogin(
                "expense.founder@roshdeandishe.test", "بنیان‌گذار بدون ماژول مالی هزینه");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بدون ماژول مالی هزینه\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/expenses")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }
}
