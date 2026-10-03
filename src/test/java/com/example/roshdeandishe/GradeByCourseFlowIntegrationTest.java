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
 * نمره به تفکیک درس (openspec/changes/grades-by-course.md) روی Postgres واقعی:
 * نمرهٔ کل و نمره‌ی هر درس ردیف‌های جدا هستند (ایندکس‌های جزئیِ V5)، لیست بدون
 * {@code courseId} فقط نمرهٔ کل را می‌دهد (سازگاری با رفتار پیشین)، و درسِ
 * ناموجود یا ناهم‌پایه رد می‌شود. فهرست درس‌ها در این فاز فقط‌خواندنی است و
 * از راه برنامه نوشته نمی‌شود، پس تست درس‌ها را مستقیم با JDBC درج می‌کند (مثل
 * مهاجرتِ داده‌ی اپراتور). کلاس‌های این تست پایه‌ی ۵ دارند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GradeByCourseFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long insertCourse(int gradeLevel, String name) {
        // نام یکتا: جدول سراسری است و تست‌ها در یک دیتابیس مشترک اجرا می‌شوند
        return jdbc.queryForObject(
                "insert into academic.courses (grade_level, name) values (?, ?) returning id",
                Long.class, gradeLevel, name + " " + UUID.randomUUID());
    }

    private String recordBody(long studentId, long classId, String term, String score, Long courseId) {
        return "{\"studentId\":" + studentId + ",\"classId\":" + classId
                + ",\"academicYear\":\"1404-1405\",\"term\":\"" + term + "\",\"numericScore\":" + score
                + (courseId == null ? "" : ",\"courseId\":" + courseId) + "}";
    }

    private void record(MockHttpSession admin, long studentId, long classId, String score, Long courseId) throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(studentId, classId, "FIRST", score, courseId)))
                .andExpect(status().isOk());
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private long createClass(MockHttpSession adminSession, String name, String gradingScheme) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"" + gradingScheme + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long enrollStudent(MockHttpSession adminSession, String email, String fullName, long classId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("studentId").asLong();
    }

    @Test
    void overallAndPerCourseGradesAreSeparateRowsAndTheListIsFilteredByCourse() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - نمره به تفکیک درس", "NUMERIC");
        long studentId = enrollStudent(admin, "bycourse.student@roshdeandishe.test", "دانش‌آموز تفکیک درس", classId);
        long math = insertCourse(5, "ریاضی");
        long science = insertCourse(5, "علوم");

        record(admin, studentId, classId, "14", null);
        record(admin, studentId, classId, "18", math);
        record(admin, studentId, classId, "16", science);
        // ثبت دوباره‌ی همان درس، همان ردیف را به‌روز می‌کند
        record(admin, studentId, classId, "19", math);

        // بدون courseId: فقط «نمرهٔ کل» (همان رفتار پیش از این قابلیت)
        mockMvc.perform(get("/api/admin/schools/1/grades")
                        .header("X-School-ID", "1")
                        .session(admin)
                        .param("classId", String.valueOf(classId))
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numericScore").value(14))
                .andExpect(jsonPath("$[0].courseId").doesNotExist());

        mockMvc.perform(get("/api/admin/schools/1/grades")
                        .header("X-School-ID", "1")
                        .session(admin)
                        .param("classId", String.valueOf(classId))
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST")
                        .param("courseId", String.valueOf(math)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numericScore").value(19))
                .andExpect(jsonPath("$[0].courseId").value(math))
                .andExpect(jsonPath("$[0].courseName").value(org.hamcrest.Matchers.startsWith("ریاضی")));
    }

    @Test
    void anUnknownCourseIsNotFoundAndAnotherGradeLevelsCourseIsRejected() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - درس نامعتبر", "NUMERIC");
        long studentId = enrollStudent(admin, "badcourse.student@roshdeandishe.test", "دانش‌آموز درس نامعتبر", classId);
        long sixthGradeCourse = insertCourse(6, "علوم ششم");

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(studentId, classId, "FIRST", "12", 999999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("course-not-found")));

        mockMvc.perform(post("/api/admin/schools/1/grades")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(studentId, classId, "FIRST", "12", sixthGradeCourse)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("course-not-for-class")));

        // هیچ ردیفی ذخیره نشده: لیست نمرهٔ کل هم خالی است
        mockMvc.perform(get("/api/admin/schools/1/grades")
                        .header("X-School-ID", "1")
                        .session(admin)
                        .param("classId", String.valueOf(classId))
                        .param("academicYear", "1404-1405")
                        .param("term", "FIRST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void courseListIsTheClassGradeLevelsCoursesAndNeedsGradesEdit() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - فهرست درس", "NUMERIC");
        long fifth = insertCourse(5, "فارسی");
        long sixth = insertCourse(6, "فارسی ششم");

        mockMvc.perform(get("/api/admin/schools/1/grades/courses")
                        .header("X-School-ID", "1")
                        .session(admin)
                        .param("classId", String.valueOf(classId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + fifth + " && @.gradeLevel == 5)]").exists())
                .andExpect(jsonPath("$[?(@.id == " + sixth + ")]").doesNotExist());

        // STUDENT فقط GRADES_VIEW دارد؛ مثل بقیه‌ی این کنترلر با GRADES_EDIT گیت می‌شود
        String studentEmail = "courselist.student@roshdeandishe.test";
        MockHttpSession studentSession = registerAndLogin(studentEmail, "دانش‌آموز فهرست درس");
        enrollStudent(admin, studentEmail, "دانش‌آموز فهرست درس", classId);
        mockMvc.perform(get("/api/admin/schools/1/grades/courses")
                        .header("X-School-ID", "1")
                        .session(studentSession)
                        .param("classId", String.valueOf(classId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void myGradesShowsTheCourseOfEachRowAndNullForTheOverallGrade() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - نمره‌های من به تفکیک درس", "NUMERIC");
        String studentEmail = "mycourse.student@roshdeandishe.test";
        MockHttpSession studentSession = registerAndLogin(studentEmail, "دانش‌آموز نمره‌های من به تفکیک درس");
        long studentId = enrollStudent(admin, studentEmail, "دانش‌آموز نمره‌های من به تفکیک درس", classId);
        long math = insertCourse(5, "ریاضی من");

        record(admin, studentId, classId, "13", null);
        record(admin, studentId, classId, "20", math);

        mockMvc.perform(get("/api/me/schools/1/grades").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.numericScore == 13 && @.courseId == null)]").exists())
                .andExpect(jsonPath("$[?(@.numericScore == 20 && @.courseId == " + math + ")]").exists())
                .andExpect(jsonPath("$[?(@.courseId == " + math + ")].courseName")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.startsWith("ریاضی من"))));
    }
}
