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
 * صفحه‌ی /students (چهارمین برش UI مدیریتی، بعد از Classes - UX_ROADMAP.md).
 * الگوی لاگین از ClassManagementFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudentManagementFlowIntegrationTest {

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
    void unauthenticatedStudentsPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/students"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void studentsPageRendersSharedNavbarForAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/students").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("دانش‌آموزان مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی نمونه")))
                // لینک‌های ناوبری navbar روی همه‌ی صفحه‌های مدیریتی هستند
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/classes\"")));
    }

    @Test
    void listingStudentsWithoutTenantHeaderIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/students").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanEnrollAStudentIntoAClassAndListIt() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"پایه سوم - ب\",\"section\":\"ب\",\"teacherId\":null,\"gradeLevel\":3}"))
                .andExpect(status().isOk())
                .andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.pupil@roshdeandishe.test\",\"fullName\":\"دانش‌آموز تازه\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classId").value(classId));

        mockMvc.perform(get("/api/admin/schools/1/students")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.classId == " + classId + ")]").exists());
    }

    @Test
    void enrolledStudentsMembershipIsFindableAndInvitableRightAfterEnrollment() throws Exception {
        // اثبات مسیرِ ترکیبی‌ای که میان‌بر دعوت روی /students استفاده می‌کند
        // (UX_ROADMAP.md §6.3): بدون REST جدید - فقط ثبت‌نام، فهرست عضویت‌ها
        // (که students.js هم برای resolve کردن نام دانش‌آموزان می‌گیرد)، پیدا
        // کردن عضویتِ متناظر بر اساس userId، و صدور توکن دعوت روی همان
        // membershipId - دقیقاً همان زنجیره‌ای که students.js انجام می‌دهد.
        MockHttpSession session = loginAsAdmin();

        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"پایه چهارم - دعوت\",\"section\":null,\"teacherId\":null,\"gradeLevel\":4}"))
                .andExpect(status().isOk())
                .andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult enrollResult = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invite.pupil@roshdeandishe.test\",\"fullName\":\"دانش‌آموز میان‌بر دعوت\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Long enrolledUserId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("userId").asLong();

        MvcResult membershipsResult = mockMvc.perform(get("/api/admin/schools/1/memberships")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.userId == " + enrolledUserId + " && @.roleCode == 'STUDENT'"
                                + " && @.hasPassword == false)]"
                ).exists())
                .andReturn();
        Long membershipId = null;
        for (var node : objectMapper.readTree(membershipsResult.getResponse().getContentAsString())) {
            if (node.get("userId").asLong() == enrolledUserId) {
                membershipId = node.get("membershipId").asLong();
                break;
            }
        }
        assertThat(membershipId).isNotNull();

        mockMvc.perform(post("/api/admin/schools/1/memberships/" + membershipId + "/invite-token")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(true));
    }

    @Test
    void userWithNoMembershipCannotEnrollAStudent() throws Exception {
        // بدون Membership در مدرسه‌ی نمونه، هیچ نقشی نمی‌تواند دانش‌آموز
        // ثبت‌نام کند - STUDENT_ENROLL فقط SCHOOL_ADMIN و TEACHER دارد
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider.students@roshdeandishe.test\",\"fullName\":\"Outsider\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider.students@roshdeandishe.test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked.pupil@roshdeandishe.test\",\"fullName\":\"Blocked\",\"classId\":null}"))
                .andExpect(status().isForbidden());
    }
}
