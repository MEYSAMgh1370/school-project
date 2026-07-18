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
 * GET /api/me/enrollment - پشتیبان بخش خواندنی اپ موبایل دانش‌آموز
 * (academic.MyEnrollmentController). عمداً هیچ Permission ای گیت این
 * endpoint را کنترل نمی‌کند؛ این تست‌ها همان چیز را با آزمایش موفقیت بدون
 * هیچ Permission خاصی (فقط عضویت STUDENT) ثابت می‌کنند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyEnrollmentFlowIntegrationTest {

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
    void studentSeesOwnEnrollment() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        // یک کاربر واقعی با رمز عبور واقعی می‌سازیم (register)، بعد همان
        // ایمیل را به‌عنوان دانش‌آموز ثبت‌نام می‌کنیم - enrollStudent داخلاً
        // findOrCreateUser صدا می‌زند که idempotent است و رمز موجود را
        // دست‌نخورده می‌گذارد (رجوع به IamFacade.findOrCreateUser).
        String studentEmail = "enrollment.student@roshdeandishe.test";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"fullName\":\"دانش‌آموز آزمایشی\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس آزمایشی ثبت‌نام\",\"section\":\"الف\",\"teacherId\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        Long classId = objectMapper.readTree(classResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"fullName\":\"دانش‌آموز آزمایشی\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk());

        MvcResult studentLogin = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession studentSession = (MockHttpSession) studentLogin.getRequest().getSession(false);

        mockMvc.perform(get("/api/me/enrollment").param("schoolId", "1").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolId").value(1))
                .andExpect(jsonPath("$.classId").value(classId))
                .andExpect(jsonPath("$.className").value("کلاس آزمایشی ثبت‌نام"))
                .andExpect(jsonPath("$.section").value("الف"));
    }

    @Test
    void nonStudentGetsNoContentForEnrollment() throws Exception {
        // ادمین نمونه‌ی seed شده هیچ StudentEntity ای ندارد - ۲۰۴ حالت درست
        // و مورد انتظار است، نه یک خطا.
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(get("/api/me/enrollment").param("schoolId", "1").session(adminSession))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthenticatedEnrollmentCallIsForbidden() throws Exception {
        mockMvc.perform(get("/api/me/enrollment").param("schoolId", "1"))
                .andExpect(status().isForbidden());
    }
}
