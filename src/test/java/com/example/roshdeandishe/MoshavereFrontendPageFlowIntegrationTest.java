package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * صفحه‌ی shell چهار برش فرانت‌اند پورت moshavere (CLAUDE.md §۳/§۴/§۷):
 * /curriculum، /study-plans، /exams، /my-exams. هر چهار مثل بقیه‌ی
 * صفحه‌های مدیریتی shell نازک‌اند - جریان REST زیرشان از قبل با
 * CurriculumFlowIntegrationTest/StudyPlanManagementFlowIntegrationTest/
 * ExamFlowIntegrationTest پوشش کامل دارد؛ این فایل فقط پوسته‌ی صفحه (رندر
 * پشت لاگین، ریدایرکت بدون لاگین، ناوبار مشترک) را تست می‌کند - همان الگوی
 * MyInvoicesPageFlowIntegrationTest.
 * <p>
 * /exam-take جدا و برعکس تست می‌شود: چون permitAll است (توکن جلسه‌ی آزمون
 * خودش اعتبار است، رجوع به ExamTakePageController)، باید حتی بدون سشن هم
 * ۲۰۰ برگرداند - نه ریدایرکت به /login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MoshavereFrontendPageFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    void unauthenticatedCurriculumPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/curriculum"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void curriculumPageRendersSharedNavbarForAuthenticatedUser() throws Exception {
        MockHttpSession session = registerAndLogin("curriculum.viewer@roshdeandishe.test", "بیننده سرفصل");

        mockMvc.perform(get("/curriculum").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("سرفصل‌ها و بانک سؤال")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")));
    }

    @Test
    void unauthenticatedStudyPlansPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/study-plans"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void studyPlansPageRendersSharedNavbarForAuthenticatedUser() throws Exception {
        MockHttpSession session = registerAndLogin("studyplans.viewer@roshdeandishe.test", "بیننده برنامه");

        mockMvc.perform(get("/study-plans").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("برنامه‌های مطالعه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")));
    }

    @Test
    void unauthenticatedExamsPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/exams"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void examsPageRendersSharedNavbarForAuthenticatedUser() throws Exception {
        MockHttpSession session = registerAndLogin("exams.viewer@roshdeandishe.test", "بیننده آزمون");

        mockMvc.perform(get("/exams").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("آزمون‌های تألیفی")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")));
    }

    @Test
    void unauthenticatedMyExamsPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/my-exams"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void myExamsPageRendersSharedNavbarForAuthenticatedUser() throws Exception {
        MockHttpSession session = registerAndLogin("myexams.viewer@roshdeandishe.test", "بیننده آزمون من");

        mockMvc.perform(get("/my-exams").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("آزمون‌های من")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")));
    }

    /**
     * برخلاف چهار صفحه‌ی بالا: permitAll است (توکن جلسه‌ی آزمون خودش
     * اعتبار است) - باید حتی بدون سشن هم ۲۰۰ بدهد، نه ریدایرکت.
     */
    @Test
    void examTakePageRendersWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/exam-take").param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("examTakePage")));
    }
}
