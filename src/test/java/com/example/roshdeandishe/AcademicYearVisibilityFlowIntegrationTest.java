package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * «چه کسی کدام سال را می‌بیند» روی Postgres واقعی (openspec/changes/academic-year.md فاز ۱): مدیر
 * (ACADEMIC_YEAR_MANAGE) هر سال را می‌نویسد و می‌خواند؛ معلم فقط سالِ جاری (در غیر این صورت 403)؛
 * دانش‌آموز در «نمره‌های من» فقط سالِ جاری را می‌بیند؛ مدرسه‌ی بدونِ سالِ جاری محدود نیست.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AcademicYearVisibilityFlowIntegrationTest {

    private static final String OLD = "1404-1405";
    private static final String CURRENT = "1405-1406";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
    }

    private long membership(MockHttpSession admin, String email, String fullName, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/memberships").with(csrf())
                        .header("X-School-ID", "1").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"roleCode\":\"" + role + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("userId").asLong();
    }

    private long createClass(MockHttpSession admin, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/classes").with(csrf())
                        .header("X-School-ID", "1").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long enroll(MockHttpSession admin, String email, String fullName, long classId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students").with(csrf())
                        .header("X-School-ID", "1").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("studentId").asLong();
    }

    private void recordGrade(MockHttpSession session, long studentId, long classId, String year, int expectedStatus)
            throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/grades").with(csrf()).header("X-School-ID", "1").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId + ",\"academicYear\":\"" + year
                                + "\",\"term\":\"FIRST\",\"numericScore\":17}"))
                .andExpect(status().is(expectedStatus));
    }

    private void setCurrentYear(String code) {
        jdbc.update("update school.schools set current_academic_year_id = "
                + "(select id from school.academic_years where code = ?) where id = 1", code);
    }

    private void clearCurrentYear() {
        jdbc.update("update school.schools set current_academic_year_id = null where id = 1");
    }

    @Test
    void onlyTheYearPermissionHolderSeesOtherYearsAndTheSchoolWithoutACurrentYearIsUnrestricted() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        long classId = createClass(admin, "پایه پنجم - دید سال");
        String studentEmail = unique("yr.student");
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز سال");
        long studentId = enroll(admin, studentEmail, "دانش‌آموز سال", classId);
        String teacherEmail = unique("yr.teacher");
        MockHttpSession teacher = registerAndLogin(teacherEmail, "معلم سال");
        membership(admin, teacherEmail, "معلم سال", "TEACHER");
        clearCurrentYear();
        try {
            // بدونِ سالِ جاری هیچ‌کس محدود نیست
            recordGrade(teacher, studentId, classId, OLD, 200);
            recordGrade(admin, studentId, classId, CURRENT, 200);
            mockMvc.perform(get("/api/me/schools/1/grades").session(student))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

            setCurrentYear(CURRENT);

            // معلم: فقط سالِ جاری
            recordGrade(teacher, studentId, classId, OLD, 403);
            recordGrade(teacher, studentId, classId, CURRENT, 200);
            mockMvc.perform(get("/api/admin/schools/1/grades").header("X-School-ID", "1").session(teacher)
                            .param("classId", String.valueOf(classId)).param("academicYear", OLD).param("term", "FIRST"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("academic-year-not-allowed")));
            mockMvc.perform(get("/api/admin/schools/1/grades").header("X-School-ID", "1").session(teacher)
                            .param("classId", String.valueOf(classId)).param("academicYear", CURRENT).param("term", "FIRST"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/admin/schools/1/grades/courses").header("X-School-ID", "1").session(teacher)
                            .param("classId", String.valueOf(classId)).param("academicYear", OLD))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/schools/1/grades/courses").header("X-School-ID", "1").session(teacher)
                            .param("classId", String.valueOf(classId)))
                    .andExpect(status().isOk());

            // مدیر: هر سال
            recordGrade(admin, studentId, classId, OLD, 200);
            mockMvc.perform(get("/api/admin/schools/1/grades").header("X-School-ID", "1").session(admin)
                            .param("classId", String.valueOf(classId)).param("academicYear", OLD).param("term", "FIRST"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

            // دانش‌آموز: فقط سالِ جاری (کارنامه‌ی سال‌های قبل را مدیر می‌دهد)
            mockMvc.perform(get("/api/me/schools/1/grades").session(student))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].academicYear").value(CURRENT));
        } finally {
            clearCurrentYear();
        }
        // با برداشتنِ سالِ جاری دوباره همه‌چیز دیده می‌شود
        mockMvc.perform(get("/api/me/schools/1/grades").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void theDashboardShowsTheCurrentYearOnlyWhenTheSchoolHasChosenOne() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        clearCurrentYear();
        try {
            String without = mockMvc.perform(get("/dashboard").session(admin)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(without).doesNotContain("app-year-tag").doesNotContain("سال تحصیلی جاری:");

            setCurrentYear(CURRENT);
            String with = mockMvc.perform(get("/dashboard").session(admin)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(with).contains("app-year-tag").contains("سال تحصیلی جاری:").contains("۱۴۰۵-۱۴۰۶");
        } finally {
            clearCurrentYear();
        }
    }
}
