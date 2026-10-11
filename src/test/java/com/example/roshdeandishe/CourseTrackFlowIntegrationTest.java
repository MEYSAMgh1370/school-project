package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * رشته‌ی درس: درسِ بدون رشته به همه‌ی دانش‌آموزانِ پایه نشان داده می‌شود، درسِ رشته‌ای فقط به دانش‌آموزِ همان رشته،
 * و دانش‌آموزِ بدون رشته فقط درس‌های مشترک را می‌بیند (تهی «همهٔ رشته‌ها» نیست). فهرستِ درس‌های کلاس
 * ({@code /grades/courses}) رشته را نمی‌شناسد و بدون فیلتر می‌ماند. درس‌ها با JDBC ساخته می‌شوند چون
 * endpoint ساختِ درس ندارد؛ فهرستِ درس‌ها سراسری و میان تست‌ها مشترک است، پس هر تست نامِ یکتای خودش را دارد و
 * فقط همان نام‌ها را می‌سنجد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CourseTrackFlowIntegrationTest {

    private static final String OLD = "1404-1405";
    private static final String CURRENT = "1405-1406";

    private static final String MATH = "ریاضی";
    private static final String SCIENCE = "تجربی";
    private static final String HUMANITIES = "انسانی";
    private static final String INVALID = "هنر";

    private static final int GRADE = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record School(long id, MockHttpSession admin) {
    }

    private record Pupil(String email, long studentId) {
    }

    /** درس‌های ساخته‌شده برای یک تست، با نامِ یکتا. */
    private record Courses(String common, String math, String science, String otherGrade) {
    }

    private String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(School s, MockHttpServletRequestBuilder builder, String body) throws Exception {
        MockHttpServletRequestBuilder request = builder.with(csrf()).header("X-School-ID", String.valueOf(s.id()))
                .session(s.admin());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions adminPost(School s, String path, String body) throws Exception {
        return send(s, post("/api/admin/schools/" + s.id() + path), body);
    }

    private ResultActions adminGet(School s, String path) throws Exception {
        return send(s, get("/api/admin/schools/" + s.id() + path), null);
    }

    private School newSchool() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("ct.founder"), "بنیان‌گذار رشته‌ی درس");
        long schoolId = json(mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"مدرسه‌ی رشته‌ی درس " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                        + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}")).andExpect(status().isOk())).get("id").asLong();
        return new School(schoolId, founder);
    }

    private long createClass(School s, int grade) throws Exception {
        return json(adminPost(s, "/classes", "{\"name\":\"پایه " + grade + "\",\"section\":null,\"teacherId\":null,"
                + "\"gradeLevel\":" + grade + ",\"gradingScheme\":\"NUMERIC\"}")
                .andExpect(status().isOk())).get("id").asLong();
    }

    private void setCurrentYear(School s, String code) throws Exception {
        long yearId = jdbc.queryForObject("select id from school.academic_years where code = ?", Long.class, code);
        Long expected = jdbc.queryForObject("select current_academic_year_id from school.schools where id = ?",
                Long.class, s.id());
        send(s, put("/api/admin/schools/" + s.id() + "/academic-year"),
                "{\"academicYearId\":" + yearId + ",\"expectedCurrentAcademicYearId\":" + expected + "}")
                .andExpect(status().isOk());
    }

    private Pupil enroll(School s, long classId, String track) throws Exception {
        String email = unique("ct.student");
        registerAndLogin(email, "دانش‌آموز رشته‌ی درس");
        String trackField = ",\"track\":" + (track == null ? "null" : "\"" + track + "\"");
        JsonNode created = json(adminPost(s, "/students", "{\"email\":\"" + email
                + "\",\"fullName\":\"دانش‌آموز رشته‌ی درس\",\"classId\":" + classId + trackField + "}")
                .andExpect(status().isOk()));
        return new Pupil(email, created.get("studentId").asLong());
    }

    private void insertCourse(int grade, String name, String track) {
        jdbc.update("insert into academic.courses (grade_level, name, graded, track) values (?, ?, true, ?)",
                grade, name, track);
    }

    private Courses newCourses() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Courses c = new Courses("مشترک " + suffix, "ریاضی-ویژه " + suffix, "تجربی-ویژه " + suffix,
                "پایه‌ی دیگر " + suffix);
        insertCourse(GRADE, c.common(), null);
        insertCourse(GRADE, c.math(), MATH);
        insertCourse(GRADE, c.science(), SCIENCE);
        insertCourse(GRADE + 1, c.otherGrade(), null);
        return c;
    }

    private List<String> namesOf(ResultActions actions) throws Exception {
        List<String> names = new ArrayList<>();
        json(actions).forEach(course -> names.add(course.get("name").asText()));
        return names;
    }

    private List<String> coursesOf(School s, Pupil p) throws Exception {
        return namesOf(adminGet(s, "/students/" + p.studentId() + "/courses").andExpect(status().isOk()));
    }

    // ---- فهرستِ دانش‌آموز ----

    @Test
    void aCourseWithoutATrackIsShownToStudentsOfAnyTrackAndToOneWithNoTrack() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, GRADE);
        Courses c = newCourses();
        Pupil math = enroll(s, classId, MATH);
        Pupil science = enroll(s, classId, SCIENCE);
        Pupil humanities = enroll(s, classId, HUMANITIES);
        Pupil none = enroll(s, classId, null);

        for (Pupil p : new Pupil[]{math, science, humanities, none}) {
            assertThat(coursesOf(s, p)).contains(c.common());
        }
    }

    @Test
    void aTrackCourseIsShownOnlyToStudentsOfThatTrack() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, GRADE);
        Courses c = newCourses();
        Pupil math = enroll(s, classId, MATH);
        Pupil science = enroll(s, classId, SCIENCE);
        Pupil humanities = enroll(s, classId, HUMANITIES);
        Pupil none = enroll(s, classId, null);

        assertThat(coursesOf(s, math)).contains(c.common(), c.math()).doesNotContain(c.science());
        assertThat(coursesOf(s, science)).contains(c.common(), c.science()).doesNotContain(c.math());
        assertThat(coursesOf(s, humanities)).contains(c.common()).doesNotContain(c.math(), c.science());
        assertThat(coursesOf(s, none)).contains(c.common()).doesNotContain(c.math(), c.science());
    }

    @Test
    void aStudentWithNoTrackSeesOnlyTheCommonCourses() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, GRADE);
        Courses c = newCourses();
        Pupil none = enroll(s, classId, null);

        // «تهی» همهٔ رشته‌ها نیست؛ هر درسِ رشته‌ای از فهرست بیرون می‌ماند
        List<String> names = coursesOf(s, none);
        assertThat(names).contains(c.common()).doesNotContain(c.math(), c.science());
        // فهرست فقط درس‌های هم‌پایه‌ی ثبت‌نامِ دانش‌آموز را می‌دهد
        assertThat(names).doesNotContain(c.otherGrade());
    }

    @Test
    void theListIsForTheEnrollmentGradeOfTheRequestedYear() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, GRADE);
        Courses c = newCourses();
        Pupil math = enroll(s, classId, MATH);

        // سالِ صریحِ جاری همان پیش‌فرض است
        assertThat(namesOf(adminGet(s, "/students/" + math.studentId() + "/courses?academicYear=" + CURRENT)
                .andExpect(status().isOk()))).contains(c.common(), c.math());

        // رشته و پایه از ردیفِ همان سال خوانده می‌شود، نه از چیزِ دیگر
        jdbc.update("insert into academic.enrollments "
                        + "(school_id, student_id, academic_year, grade_level, status, created_at, track) "
                        + "values (?, ?, ?, ?, 'PENDING', now(), ?)",
                s.id(), math.studentId(), OLD, GRADE + 1, SCIENCE);
        List<String> oldYear = namesOf(adminGet(s, "/students/" + math.studentId() + "/courses?academicYear=" + OLD)
                .andExpect(status().isOk()));
        assertThat(oldYear).contains(c.otherGrade()).doesNotContain(c.common(), c.math(), c.science());
    }

    @Test
    void aStudentWithNoEnrollmentRowForTheYearGetsConflict() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, GRADE);
        newCourses();
        Pupil p = enroll(s, classId, MATH);

        // فقط ردیفِ سالِ جاری هست؛ سالِ قبل ردیفی ندارد
        adminGet(s, "/students/" + p.studentId() + "/courses?academicYear=" + OLD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("student-not-enrolled-in-year")));

        // ردیفِ سالِ جاری را برمی‌داریم: همان خطا برای سالِ جاری
        jdbc.update("delete from academic.enrollments where student_id = ? and academic_year = ?",
                p.studentId(), CURRENT);
        adminGet(s, "/students/" + p.studentId() + "/courses")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("student-not-enrolled-in-year")));
    }

    // ---- فهرستِ کلاس بدون تغییر ----

    @Test
    void theClassCourseListIsNotFilteredByTrack() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, GRADE);
        Courses c = newCourses();

        // همان endpoint ی که انتخابگر درسِ دفتر نمرات می‌خواند: درسِ رشته‌ای هم هست
        List<String> names = namesOf(adminGet(s, "/grades/courses?classId=" + classId).andExpect(status().isOk()));
        assertThat(names).contains(c.common(), c.math(), c.science()).doesNotContain(c.otherGrade());
    }

    @Test
    void aSchoolWithoutACurrentYearAndNoRequestedYearIsRejectedWithItsOwnError() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        Pupil p = enroll(s, createClass(s, GRADE), MATH);
        jdbc.update("update school.schools set current_academic_year_id = null where id = ?", s.id());

        adminGet(s, "/students/" + p.studentId() + "/courses")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("no-current-academic-year")));
    }

    // ---- دیتابیس ----

    @Test
    void theDatabaseRejectsACourseTrackOutsideTheThreeValues() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // مقدارِ معتبر و تهی ردیف را می‌نویسند، پس شکستِ بعدی از خودِ CHECK است نه چیزِ دیگر
        insertCourse(GRADE, "رشته‌ی انسانی " + suffix, HUMANITIES);
        insertCourse(GRADE, "رشته‌ی تهی " + suffix, null);
        assertThatThrownBy(() -> insertCourse(GRADE, "رشته‌ی نامعتبر " + suffix, INVALID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
