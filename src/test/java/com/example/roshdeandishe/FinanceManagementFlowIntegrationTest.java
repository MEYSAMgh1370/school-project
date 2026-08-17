package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * صفحه‌ی /finance (ششمین و آخرین برش UI مدیریتیِ نقش SCHOOL_ADMIN، بعد از
 * Enrollment - UX_ROADMAP.md). الگوی لاگین از StudentManagementFlowIntegrationTest
 * کپی شده است. اولین صفحه‌ای که باید دو نوع 403 لایه‌ی کنترل دسترسی §5
 * (ModuleNotEntitled در برابر PermissionDenied) را جدا از هم تست کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.example.roshdeandishe.iam.IamFacade iamFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
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

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    @Test
    void unauthenticatedFinancePageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/finance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void financePageRendersSharedNavbarForAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/finance").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("امور مالی مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی نمونه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("src=\"/js/money.js\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Money.formatAmount(invoice.amount)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Money.normalizeAmountInput($event)")))
                // لینک‌های ناوبری navbar روی همه‌ی صفحه‌های مدیریتی هستند
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/students\"")));

        mockMvc.perform(get("/js/finance.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "amount: Money.normalizeAmount(this.paymentForm.amount)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "amount: Money.normalizeAmount(this.form.amount)")));
    }

    @Test
    void gettingTuitionFeeWithoutTenantHeaderIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/tuition-fee").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanSetTuitionFeeAndSeeInvoiceAfterEnrollment() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(put("/api/admin/schools/1/tuition-fee")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1800000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1800000.00));

        mockMvc.perform(get("/api/admin/schools/1/tuition-fee")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1800000.00));

        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"پایه چهارم - ج\",\"section\":\"ج\",\"teacherId\":null,\"gradeLevel\":4}"))
                .andExpect(status().isOk())
                .andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"finance.pupil@roshdeandishe.test\",\"fullName\":\"دانش‌آموز مالی\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk());

        // StudentEnrolledEvent → StudentEnrolledEventListener → فاکتور، همه async بعد از commit
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mockMvc.perform(get("/api/admin/schools/1/invoices")
                                .header("X-School-ID", "1")
                                .session(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[?(@.amount == 1800000.00)]").exists()));
    }

    @Test
    void teacherWithoutFinancePermissionGetsPermissionDenied() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MockHttpSession teacherSession = registerAndLogin("finance.teacher@roshdeandishe.test", "معلم بدون دسترسی مالی");

        // ادمین همین کاربرِ از قبل ثبت‌نام‌شده را به‌عنوان TEACHER در مدرسه‌ی
        // نمونه عضو می‌کند - findOrCreateUser رمز موجودش را دست نمی‌زند
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"finance.teacher@roshdeandishe.test\",\"fullName\":\"معلم بدون دسترسی مالی\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());

        // مدرسه‌ی نمونه ماژول FINANCE را دارد (seed data)، اما TEACHER هیچ‌کدام
        // از FINANCE_VIEW/FINANCE_MANAGE را ندارد - این باید permission-denied
        // باشد، نه module-not-entitled (CLAUDE.md §5، دو لایه‌ی جدا)
        mockMvc.perform(get("/api/admin/schools/1/tuition-fee")
                        .header("X-School-ID", "1")
                        .session(teacherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void freshSchoolGetsFreeFinanceTierAndRevocationYieldsModuleNotEntitled() throws Exception {
        MockHttpSession founderSession = registerAndLogin("finance.founder@roshdeandishe.test", "بنیان‌گذار بسته‌ی رایگان مالی");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بسته‌ی رایگان مالی\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // مدرسه‌ی تازه‌ساخته‌ی خودسرویس بسته‌ی رایگان (ACADEMIC + FINANCE) را
        // در لحظه‌ی ساخت می‌گیرد (CLAUDE.md §11 Q10) - پس tuition-fee باید
        // 404 «تنظیم نشده» بدهد، نه 403 module-not-entitled
        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/tuition-fee")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isNotFound());

        // با پس‌گرفتن FINANCE از طریق facade (تنها مسیر مجاز - §10)، حتی
        // SCHOOL_ADMIN خودش هم باید module-not-entitled بگیرد، نه permission-denied
        iamFacade.setSchoolEntitlement(schoolId, new com.example.roshdeandishe.iam.IamFacade.ModuleCode("FINANCE"), false);

        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/tuition-fee")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }
}
