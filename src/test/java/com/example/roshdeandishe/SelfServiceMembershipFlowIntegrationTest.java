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
 * endpointهای خودسرویس ({@code /api/me/**}) روی Postgres واقعی: با غیرفعال‌شدنِ همه‌ی
 * عضویت‌های کاربر در مدرسه، ثبت‌نام و لینکِ ولی می‌مانند ولی داده دیگر برنمی‌گردد؛ با فعال
 * شدنِ دوباره برمی‌گردد. شرطِ نقش نیست (قراردادِ اجتماعِ دانش‌آموز و ولی): کاربری که فقط عضویتِ
 * دانش‌آموزی‌اش غیرفعال شده ولی معلمِ فعال است همچنان داده‌ی خودش را می‌بیند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SelfServiceMembershipFlowIntegrationTest {

    private static final String YEAR = "1405-1406";

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

    private void recordGrade(MockHttpSession admin, long studentId, long classId) throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/grades").with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"classId\":" + classId + ",\"academicYear\":\"" + YEAR
                                + "\",\"term\":\"FIRST\",\"numericScore\":17}"))
                .andExpect(status().isOk());
    }

    private void setMembershipsActive(boolean active, String roleCode, String... emails) {
        for (String email : emails) {
            jdbc.update("update iam.school_memberships set active = ? where school_id = 1 "
                    + "and user_id = (select id from iam.users where email = ?) "
                    + "and role_id = (select id from iam.roles where code = ? and school_id is null)",
                    active, email, roleCode);
        }
    }

    private void expectGradeCount(MockHttpSession session, int count) throws Exception {
        mockMvc.perform(get("/api/me/schools/1/grades").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(count));
    }

    @Test
    void aStudentWhoseMembershipIsDeactivatedGetsNothingAndGetsItBackWhenReactivated() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        long classId = createClass(admin, "پایه پنجم - عضو سابق دانش‌آموز");
        String email = unique("me.student");
        MockHttpSession student = registerAndLogin(email, "دانش‌آموز سابق");
        long studentId = enroll(admin, email, "دانش‌آموز سابق", classId);
        recordGrade(admin, studentId, classId);

        expectGradeCount(student, 1);
        mockMvc.perform(get("/api/me/enrollment").param("schoolId", "1").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.classId").value(classId));

        setMembershipsActive(false, "STUDENT", email);
        expectGradeCount(student, 0);
        mockMvc.perform(get("/api/me/enrollment").param("schoolId", "1").session(student))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/schools/1/invoices").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/me/schools/1/exams").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        // شروعِ تلاش برای عضوِ سابق یک خطای دامنه‌ای است، نه دسترسی
        mockMvc.perform(post("/api/me/schools/1/exams/1/attempts").with(csrf()).session(student))
                .andExpect(status().is4xxClientError());

        // غیرفعال بودن عضویت، ثبت‌نام را پاک نکرده: با فعال‌شدن دوباره همه‌چیز برمی‌گردد
        setMembershipsActive(true, "STUDENT", email);
        expectGradeCount(student, 1);
    }

    @Test
    void aParentWhoseMembershipIsDeactivatedNoLongerSeesTheWardsGrades() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        long classId = createClass(admin, "پایه پنجم - عضو سابق والد");
        String studentEmail = unique("me.ward");
        enroll(admin, studentEmail, "فرزند", classId);
        long studentId = jdbc.queryForObject("select id from academic.students where user_id = "
                + "(select id from iam.users where email = ?)", Long.class, studentEmail);
        recordGrade(admin, studentId, classId);
        String parentEmail = unique("me.parent");
        MockHttpSession parent = registerAndLogin(parentEmail, "والد سابق");
        long parentId = membership(admin, parentEmail, "والد سابق", "PARENT");
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/guardians").with(csrf())
                        .header("X-School-ID", "1").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianUserId\":" + parentId + "}"))
                .andExpect(status().isOk());

        expectGradeCount(parent, 1);
        setMembershipsActive(false, "PARENT", parentEmail);
        expectGradeCount(parent, 0);
        setMembershipsActive(true, "PARENT", parentEmail);
        expectGradeCount(parent, 1);
    }

    @Test
    void theGateIsNotRoleBasedAnotherActiveMembershipKeepsTheUsersOwnData() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        long classId = createClass(admin, "پایه پنجم - چندنقشی");
        String email = unique("me.both");
        MockHttpSession user = registerAndLogin(email, "معلمِ دانش‌آموزِ سابق");
        long studentId = enroll(admin, email, "معلمِ دانش‌آموزِ سابق", classId);
        membership(admin, email, "معلمِ دانش‌آموزِ سابق", "TEACHER");
        recordGrade(admin, studentId, classId);

        // فقط عضویتِ دانش‌آموزی غیرفعال می‌شود؛ عضویتِ معلم فعال می‌ماند: همان قراردادِ بدونِ شرطِ نقش
        setMembershipsActive(false, "STUDENT", email);
        expectGradeCount(user, 1);
        // با غیرفعال‌شدنِ همه‌ی عضویت‌ها چیزی نمی‌ماند
        setMembershipsActive(false, "TEACHER", email);
        expectGradeCount(user, 0);
    }
}
