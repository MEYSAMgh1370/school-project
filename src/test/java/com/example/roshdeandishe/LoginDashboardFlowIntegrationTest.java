package com.example.roshdeandishe;

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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginDashboardFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ورود")));
    }

    @Test
    void landingPageIsPubliclyReachableWithSchoolRegistrationPath() throws Exception {
        // صفحه‌ی اول عمومی (تصمیم ۲۰۲۶-۰۷-۰۶): درِ ورودی محصول با مسیر
        // ورود/ثبت‌نام/ثبت مدرسه - بدون نیاز به لاگین
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ثبت مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/schools/new\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login\"")));
    }

    @Test
    void landingPageRedirectsAuthenticatedUsersToDashboard() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/dashboard"));
    }

    @Test
    void unauthenticatedSchoolRegistrationPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/schools/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void unauthenticatedDashboardRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void unauthenticatedApiStaysForbiddenInsteadOfRedirecting() throws Exception {
        mockMvc.perform(get("/api/me/memberships"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginThenSeeOwnMembershipsAndDashboard() throws Exception {
        // لاگین با ادمین نمونه (seed شده) و نگه‌داشتن session
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@roshdeandishe.test"));

        // عضویت SCHOOL_ADMIN ادمین نمونه در مدرسه‌ی نمونه (id=1) نیز seed شده
        mockMvc.perform(get("/api/me/memberships").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.schoolId == 1 && @.roleCode == 'SCHOOL_ADMIN' && @.active == true)]").exists());

        // داشبورد حالا نام مدرسه را (از ماژول school، از طریق ماژول web)
        // به‌جای schoolId خام نشان می‌دهد + سوییچر مدرسه/نقش را رندر می‌کند.
        // نقش با برچسب فارسی (RoleLabels) نمایش داده می‌شود نه کد خام، و
        // navbar باید لینک صفحه‌ها را داشته باشد (UX_ROADMAP.md §2: هیچ
        // URL یتیمی وجود ندارد). کد SCHOOL_ADMIN همچنان در سند هست (در
        // selectRole سوییچر) - سیم‌کشی سوییچر را تأیید می‌کند.
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SCHOOL_ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدیر مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/people\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("عضویت‌های من")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی نمونه")));
    }

    @Test
    void mutatingRequestWithoutCsrfTokenIsForbidden() throws Exception {
        // CSRF فعال است: POST بدون توکن باید رد شود، حتی با اعتبارنامه‌ی درست
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isForbidden());
    }
}
