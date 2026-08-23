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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void landingPageIsPubliclyReachableWithAllFounderEntryPaths() throws Exception {
        // صفحه‌ی اول عمومی (تصمیم ۲۰۲۶-۰۷-۰۶): درِ ورودی محصول با مسیر
        // ورود/ثبت‌نام/ثبت مدرسه - بدون نیاز به لاگین
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("کارهای مهم مدرسه، ساده‌تر و هماهنگ‌تر")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ثبت مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login?next=/schools/new\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/register?next=/schools/new\"")));
    }

    @Test
    void anonymousSchoolRegistrationCtaSurvivesLoginAndRegistration() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/login?next=/schools/new\"")));

        mockMvc.perform(get("/login").param("next", "/schools/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-post-authentication-destination=\"/schools/new\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/register?next=/schools/new\"")));

        mockMvc.perform(get("/register").param("next", "/schools/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-post-authentication-destination=\"/schools/new\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/login?next=/schools/new\"")));

        mockMvc.perform(get("/js/login.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.location.href = this.postAuthenticationDestination")));
        mockMvc.perform(get("/js/register.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "? this.postAuthenticationDestination")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "? '/login?next=/schools/new'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        ": this.loginPagePath")));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/schools/new").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void loginAndRegistrationPagesRejectExternalPostAuthenticationDestinations() throws Exception {
        mockMvc.perform(get("/login").param("next", "https://example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-post-authentication-destination=\"/dashboard\"")));

        mockMvc.perform(get("/register").param("next", "//example.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-post-authentication-destination=\"/dashboard\"")));
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
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .isEqualTo("/login?next=/schools/new"));
    }

    @Test
    void directSchoolRegistrationEntryPreservesDestinationThroughLogin() throws Exception {
        mockMvc.perform(get("/schools/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .isEqualTo("/login?next=/schools/new"));

        mockMvc.perform(get("/login").param("next", "/schools/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-post-authentication-destination=\"/schools/new\"")));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/schools/new").session(session))
                .andExpect(status().isOk());
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
    void dashboardShowsPhoneNumberFieldEmptyThenPrefilledAfterSaving() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // ادمین نمونه شماره تلفنی seed نشده - فیلد باید خالی رندر شود
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-phone-number=\"\"")));

        mockMvc.perform(put("/api/me/phone")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"09121234567\"}"))
                .andExpect(status().isOk());

        // بعد از ذخیره، رندر بعدیِ داشبورد باید مقدار را در data-attribute
        // پیش‌آماده کند (IamFacade.findUser اکنون phoneNumber را برمی‌گرداند)
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-phone-number=\"09121234567\"")));
    }

    /**
     * نشانی ورودِ مدرسه (/s/{slug}) تا پیش از این فقط هنگام ثبت مدرسه نوشته
     * می‌شد و هیچ‌جا نمایش داده نمی‌شد (UX_ROADMAP.md §6.4). مدرسه‌ی نمونه
     * با slug «nemooneh» seed شده و ادمین نمونه در آن SCHOOL_ADMIN است.
     */
    @Test
    void dashboardShowsEntryUrlForSchoolsWhereUserIsAdmin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("نشانی ورود مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/s/nemooneh\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-slug=\"nemooneh\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("کپی نشانی")));
    }

    /**
     * کاربر بدون هیچ عضویتِ مدیریتی نباید اصلاً این باکس را ببیند - و
     * مدرسه‌ی بدون slug به‌جای لینک، توضیح می‌گیرد (ویرایش slug بعد از ثبت
     * عمداً ساخته نشده، CLAUDE.md §11 Q9).
     */
    @Test
    void dashboardHidesEntryUrlBoxWithoutAdminMembershipAndExplainsMissingSlug() throws Exception {
        String email = "entry-url-" + System.nanoTime() + "@example.test";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"بنیان‌گذار آزمون\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // هنوز هیچ عضویتی ندارد - باکس نباید رندر شود
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("نشانی ورود مدرسه"))));

        // مدرسه‌ای بدون slug می‌سازد (slug اختیاری است) و SCHOOL_ADMIN آن می‌شود
        mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بی‌نشانی\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("نشانی ورود مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی بی‌نشانی")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "این مدرسه نشانی اختصاصی ندارد")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-slug="))));
    }

    @Test
    void loginPageOffersBothPasswordAndOtpTabs() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("رمز عبور")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("کد پیامکی")));
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
