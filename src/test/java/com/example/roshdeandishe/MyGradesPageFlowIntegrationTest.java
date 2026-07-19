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
 * صفحه‌ی «نمره‌های من» (/my-grades) - برای STUDENT/PARENT. یک shell نازک
 * مثل /my-invoices؛ داده کاملاً client-side از academic.MyGradesController
 * (الگوی «من»، از قبل با GradeManagementFlowIntegrationTest.myGradesReturns...
 * پوشش کامل REST دارد) خوانده می‌شود - این فایل فقط پوسته‌ی صفحه را تست
 * می‌کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyGradesPageFlowIntegrationTest {

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
    void unauthenticatedMyGradesPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/my-grades"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void myGradesPageRendersSharedNavbarForAuthenticatedUser() throws Exception {
        MockHttpSession session = registerAndLogin("mygrades.viewer@roshdeandishe.test", "بیننده نمره‌های من");

        mockMvc.perform(get("/my-grades").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("نمره‌های من")))
                // لینک‌های ناوبری روی همه‌ی صفحه‌های مدیریتی هستند
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")));
    }
}
