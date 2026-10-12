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
 * پوسته‌ی صفحه‌ی /question-reports (رندر پشت لاگین، ریدایرکت بدون لاگین، ناوبار مشترک) - همان
 * الگوی MoshavereFrontendPageFlowIntegrationTest؛ جریان REST گزارش‌ها در
 * QuestionReportFlowIntegrationTest پوشش داده شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QuestionReportsPageFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedQuestionReportsPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/question-reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void questionReportsPageRendersSharedNavbarForAuthenticatedUser() throws Exception {
        MockHttpSession session = registerAndLogin("question.reports.viewer@roshdeandishe.test", "بیننده گزارش");

        mockMvc.perform(get("/question-reports").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("گزارش‌های سؤال")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("questionReportsPage")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")));
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
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }
}
