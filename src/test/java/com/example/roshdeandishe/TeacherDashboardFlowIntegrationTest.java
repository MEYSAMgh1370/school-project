package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * پیشخوانِ معلم: عنوانِ شخصی، «کلاس‌های من» با تعدادِ دانش‌آموز و وضعیتِ حضور و غیابِ امروز، و پیامِ روشن وقتی
 * هنوز کلاسی به او واگذار نشده؛ فرمِ ساختِ کلاس فقط برای نقشِ دارای CLASS_MANAGE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeacherDashboardFlowIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aTeacherSeesAGreetingAnEmptyStateAndThenTheirClassWithTodaysAttendanceToDo() throws Exception {
        String email = "teacher-dash-" + System.nanoTime() + "@example.test";
        MockHttpSession teacher = registerAndLogin(email, "معلم داشبورد");
        long teacherUserId = objectMapper.readTree(mockMvc.perform(get("/api/me").session(teacher))
                .andReturn().getResponse().getContentAsString()).get("userId").asLong();
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        postAsAdmin(admin, "/api/admin/schools/1/memberships",
                "{\"email\":\"" + email + "\",\"fullName\":\"معلم داشبورد\",\"roleCode\":\"TEACHER\"}");

        mockMvc.perform(get("/dashboard").session(teacher))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("سلام، معلم داشبورد")))
                .andExpect(content().string(containsString("معلم در ")))
                .andExpect(content().string(containsString("کلاس‌های من")))
                .andExpect(content().string(containsString("هنوز کلاسی به شما واگذار نشده است")));

        String className = "کلاس معلم " + System.nanoTime();
        String classBody = postAsAdmin(admin, "/api/admin/schools/1/classes",
                "{\"name\":\"" + className + "\",\"teacherId\":" + teacherUserId + ",\"gradeLevel\":1}");
        long classId = objectMapper.readTree(classBody).get("id").asLong();
        postAsAdmin(admin, "/api/admin/schools/1/students",
                "{\"email\":\"dash-student-" + System.nanoTime() + "@example.test\",\"fullName\":\"دانش‌آموز داشبورد\",\"classId\":" + classId + "}");

        mockMvc.perform(get("/dashboard").session(teacher))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(className)))
                .andExpect(content().string(containsString("1 دانش‌آموز")))
                .andExpect(content().string(containsString("ثبت حضور و غیاب امروز")))
                .andExpect(content().string(not(containsString("هنوز کلاسی به شما واگذار نشده است"))));
    }

    @Test
    void anAdminDoesNotGetTheTeachersMyClassesSection() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(get("/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("سلام، ")))
                .andExpect(content().string(not(containsString("dashboard-my-classes-"))));
    }

    @Test
    void theClassesPageHidesTheCreateFormUnlessTheRoleCanManageClasses() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(get("/classes").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$store.nav.permissions.includes('CLASS_MANAGE')")));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private String postAsAdmin(MockHttpSession admin, String url, String json) throws Exception {
        return mockMvc.perform(post(url)
                        .with(csrf())
                        .session(admin)
                        .header("X-School-ID", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
