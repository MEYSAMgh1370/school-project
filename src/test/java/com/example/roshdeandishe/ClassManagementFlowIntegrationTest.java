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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * صفحه‌ی /classes (دومین برش UI مدیریتی، بعد از People - UX_ROADMAP.md).
 * الگوی لاگین از MembershipManagementFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClassManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void unauthenticatedClassesPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/classes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void classesPageRendersSharedNavbarForAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/classes").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("کلاس‌های مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی نمونه")))
                // لینک‌های ناوبری navbar روی همه‌ی صفحه‌های مدیریتی هستند
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/people\"")));
    }

    @Test
    void listingClassesWithoutTenantHeaderIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/classes").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanCreateAClassWithATeacherAndListIt() throws Exception {
        MockHttpSession session = loginAsAdmin();

        // یک معلم بسازیم تا تخصیصش به کلاس آزمایش شود
        MvcResult teacherResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"class.teacher@roshdeandishe.test\",\"fullName\":\"معلم کلاس\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long teacherUserId = objectMapper.readTree(teacherResult.getResponse().getContentAsString())
                .get("userId").asLong();

        mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"پایه پنجم - الف\",\"section\":\"الف\",\"teacherId\":" + teacherUserId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("پایه پنجم - الف"))
                .andExpect(jsonPath("$.teacherId").value(teacherUserId));

        mockMvc.perform(get("/api/admin/schools/1/classes")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.name == 'پایه پنجم - الف' && @.teacherId == " + teacherUserId + ")]"
                ).exists());
    }

    @Test
    void userWithNoMembershipCannotCreateAClass() throws Exception {
        // بدون Membership در مدرسه‌ی نمونه، هیچ نقشی (نه فقط SCHOOL_ADMIN)
        // نمی‌تواند کلاس بسازد - CLASS_MANAGE فقط SCHOOL_ADMIN دارد
        // (CLAUDE.md §4: ساخت کلاس اداری/چیدمانی است، بر خلاف ثبت‌نام دانش‌آموز)
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider.classes@roshdeandishe.test\",\"fullName\":\"Outsider\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider.classes@roshdeandishe.test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس ممنوع\",\"section\":null,\"teacherId\":null}"))
                .andExpect(status().isForbidden());
    }
}
