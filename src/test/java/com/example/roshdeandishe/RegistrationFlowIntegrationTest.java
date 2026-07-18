package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * جریان ثبت‌نام عمومی (self-service) - صفحه‌ی /register و
 * POST /api/auth/register - شامل قاعده‌ی امنیتی «ایمیل موجود، حتی حسابِ
 * دعوت‌شده‌ی فعال‌نشده، قابل ثبت‌نام مجدد نیست».
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistrationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

    @Test
    void registerPageIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ثبت‌نام")));
    }

    @Test
    void registerThenLoginAndSeeDashboardWithoutMemberships() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.test\",\"fullName\":\"کاربر تازه\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newuser@example.test"));

        // کاربر تازه هیچ Membershipای ندارد - داشبورد باید این حالت را
        // تحمل کند (تصمیم Q2: سوییچر بدون عضویت اصلاً رندر نمی‌شود) و در
        // حالت خالی، CTA ثبت مدرسه را نشان دهد (جریان بنیان‌گذار)
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/schools/new\"")));
    }

    @Test
    void registerThenCreateSchoolAndBecomeItsAdmin() throws Exception {
        // کل مسیر onboarding خودسرویسی B2B: ثبت‌نام → صفحه‌ی ثبت مدرسه
        // (/schools/new - تصمیم ۲۰۲۶-۰۷-۰۶: فرم دیگر روی داشبورد نیست) →
        // ساخت مدرسه → SCHOOL_ADMIN شدن خودکار → دیدن مدرسه در داشبورد
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"founder@example.test\",\"fullName\":\"بنیان‌گذار\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"founder@example.test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // صفحه‌ی اختصاصی ثبت مدرسه با کامپوننت استاتیک /js/school-new.js
        mockMvc.perform(get("/schools/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("createSchoolForm")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/school-new.js")));

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بنیان‌گذار\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("مدرسه‌ی بنیان‌گذار"))
                .andReturn();
        long schoolId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/me/memberships").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.schoolId == " + schoolId
                        + " && @.roleCode == 'SCHOOL_ADMIN' && @.active == true)]").exists());

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی بنیان‌گذار")));
    }

    @Test
    void duplicateEmailIsRejectedWithConflict() throws Exception {
        // ادمین نمونه seed شده و passwordHash دارد
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"fullName\":\"مزاحم\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ایمیل قبلاً ثبت شده"));
    }

    @Test
    void invitedButNotActivatedAccountCannotBeClaimedByRegistration() throws Exception {
        // کاربری که ادمین ساخته (passwordHash=null) - ثبت‌نام عمومی با همان
        // ایمیل باید رد شود؛ مسیر مجاز فقط توکن دعوتِ ایمیل‌شده است
        iamFacade.findOrCreateUser("invited@example.test", "دعوت‌شده");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invited@example.test\",\"fullName\":\"مهاجم\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"shortpw@example.test\",\"fullName\":\"کوتاه\",\"password\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("رمز عبور نامعتبر"));
    }

    @Test
    void blankFullNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noname@example.test\",\"fullName\":\" \",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("اطلاعات ثبت‌نام نامعتبر"));
    }
}
