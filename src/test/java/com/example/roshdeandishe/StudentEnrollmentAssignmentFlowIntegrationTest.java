package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * نسبت دادنِ برنامه‌ی مطالعه یا آزمون به دانش‌آموز، روی Postgres واقعی: در مدرسه‌ای که سالِ جاری دارد، دانش‌آموز باید
 * در سالِ تحصیلیِ حاوی تاریخ ردیفِ ثبت‌نام داشته باشد (برنامه: «امروز»ِ مدرسه؛ آزمون: تاریخِ شروعِ خودِ آزمون)،
 * وگرنه 409 {@code student-not-enrolled-in-year}؛ تاریخی که در هیچ سالی نیست 400 {@code no-academic-year-for-date}.
 * مدرسه‌ی بدونِ سالِ جاری مثل قبل بدون بررسی می‌گذرد. هر تست مدرسه‌ی تازه‌ی خودش را می‌سازد (دبستان، پایه‌ی ۵).
 * <p>
 * دانش‌آموزِ «بدونِ ردیفِ ثبت‌نام» با پاک‌کردنِ ردیفِ ساخته‌شده ساخته می‌شود، چون مدرسه‌ی دارای سالِ جاری ردیف می‌نویسد
 * و مسیرِ عادی چنین دانش‌آموزی نمی‌سازد (او فقط از پیش از انتخابِ سال یا از داده‌ی قدیمی می‌آید).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudentEnrollmentAssignmentFlowIntegrationTest {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record School(long id, MockHttpSession admin) {
    }

    private record Year(long id, String code, LocalDate start, LocalDate end) {
        boolean contains(LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }
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

    private void grantModules(long schoolId, String... codes) {
        for (String code : codes) {
            jdbc.update("insert into iam.school_entitlements (active, module_id, school_id) "
                    + "select true, id, ? from iam.modules where code = ?", schoolId, code);
        }
    }

    private School newSchool() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("en.founder"), "بنیان‌گذار ثبت‌نام");
        long schoolId = json(mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"مدرسه‌ی ثبت‌نام " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                        + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}")).andExpect(status().isOk())).get("id").asLong();
        grantModules(schoolId, "CURRICULUM", "STUDY_PLANNER", "EXAM");
        return new School(schoolId, founder);
    }

    /** مدرسه‌ای که سالِ جاری دارد: سالِ حاوی «امروزِ تهران» اگر باشد، وگرنه آخرین سالِ فهرست. */
    private School newSchoolWithCurrentYear() throws Exception {
        School s = newSchool();
        List<Year> years = years(s);
        LocalDate today = LocalDate.now(TEHRAN);
        Year chosen = years.stream().filter(y -> y.contains(today)).findFirst()
                .orElse(years.get(years.size() - 1));
        Long expected = jdbc.queryForObject("select current_academic_year_id from school.schools where id = ?",
                Long.class, s.id());
        send(s, put("/api/admin/schools/" + s.id() + "/academic-year"),
                "{\"academicYearId\":" + chosen.id() + ",\"expectedCurrentAcademicYearId\":" + expected + "}")
                .andExpect(status().isOk());
        return s;
    }

    private List<Year> years(School s) throws Exception {
        JsonNode body = json(send(s, get("/api/admin/schools/" + s.id() + "/academic-year"), null)
                .andExpect(status().isOk()));
        List<Year> result = new ArrayList<>();
        for (JsonNode y : body.get("years")) {
            result.add(new Year(y.get("id").asLong(), y.get("code").asText(),
                    LocalDate.parse(y.get("startDate").asText()), LocalDate.parse(y.get("endDate").asText())));
        }
        result.sort(Comparator.comparing(Year::start));
        return result;
    }

    private Year currentYear(School s) throws Exception {
        long currentId = jdbc.queryForObject("select current_academic_year_id from school.schools where id = ?",
                Long.class, s.id());
        return years(s).stream().filter(y -> y.id() == currentId).findFirst().orElseThrow();
    }

    private long createClass(School s) throws Exception {
        return json(adminPost(s, "/classes", "{\"name\":\"پایه ۵\",\"section\":null,\"teacherId\":null,"
                + "\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}").andExpect(status().isOk())).get("id").asLong();
    }

    private long enroll(School s, long classId) throws Exception {
        return json(adminPost(s, "/students", "{\"email\":\"" + unique("en.student")
                + "\",\"fullName\":\"دانش‌آموز ثبت‌نام\",\"classId\":" + classId + "}")
                .andExpect(status().isOk())).get("studentId").asLong();
    }

    /** دانش‌آموزی در مدرسه‌ی دارای سالِ جاری که ردیفِ ثبت‌نامش پاک شده. */
    private long enrollWithoutRow(School s, long classId) throws Exception {
        long studentId = enroll(s, classId);
        assertThat(jdbc.update("delete from academic.enrollments where student_id = ?", studentId)).isEqualTo(1);
        return studentId;
    }

    private long createPlan(School s) throws Exception {
        long topicId = json(adminPost(s, "/topics", "{\"name\":\"ریاضی ثبت‌نام\",\"parentId\":null,\"order\":0}")
                .andExpect(status().isOk())).get("id").asLong();
        return json(adminPost(s, "/study-plans", "{\"title\":\"برنامه‌ی ثبت‌نام\",\"weights\":[{\"topicId\":"
                + topicId + ",\"weight\":1.0,\"priority\":1}]}").andExpect(status().isOk())).get("id").asLong();
    }

    private ResultActions assignPlan(School s, long planId, long studentId) throws Exception {
        return adminPost(s, "/study-plans/" + planId + "/assignments", "{\"studentId\":" + studentId + "}");
    }

    private long createExam(School s, LocalDate date) throws Exception {
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(10, 0));
        return json(adminPost(s, "/exams", "{\"title\":\"آزمون ثبت‌نام\",\"start\":\"" + start + "\",\"end\":\""
                + start.plusHours(2) + "\",\"durationMinutes\":60}").andExpect(status().isOk())).get("id").asLong();
    }

    private ResultActions assignExam(School s, long examId, long studentId) throws Exception {
        return adminPost(s, "/exams/" + examId + "/assign", "{\"studentIds\":[" + studentId + "]}");
    }

    private int planAssignments(long studentId) {
        return jdbc.queryForObject("select count(*) from studyplan.student_plan_assignments where student_id = ?",
                Integer.class, studentId);
    }

    private int examAssignments(long examId) {
        return jdbc.queryForObject("select count(*) from exam.exam_assignments where exam_id = ?",
                Integer.class, examId);
    }

    /** برنامه «امروز» را می‌سنجد؛ در فاصله‌ی تابستانی (بعد از ۳۱ خرداد تا ۱ مهر) هیچ سالی امروز را در بر ندارد. */
    private void assumeTodayIsInTheCurrentYear(School s) throws Exception {
        Assumptions.assumeTrue(currentYear(s).contains(LocalDate.now(TEHRAN)),
                "امروز در هیچ سال تحصیلی نیست (فاصله‌ی تابستانی)");
    }

    // ---- برنامه‌ی مطالعه ----

    @Test
    void assigningAPlanToAnEnrolledStudentSucceeds() throws Exception {
        School s = newSchoolWithCurrentYear();
        assumeTodayIsInTheCurrentYear(s);
        long classId = createClass(s);
        long studentId = enroll(s, classId);
        long planId = createPlan(s);

        assignPlan(s, planId, studentId).andExpect(status().isOk());

        assertThat(planAssignments(studentId)).isEqualTo(1);
    }

    @Test
    void assigningAPlanToAStudentWithoutAnEnrollmentRowIsRejected() throws Exception {
        School s = newSchoolWithCurrentYear();
        assumeTodayIsInTheCurrentYear(s);
        long classId = createClass(s);
        long studentId = enrollWithoutRow(s, classId);
        long planId = createPlan(s);

        assignPlan(s, planId, studentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("student-not-enrolled-in-year")));

        assertThat(planAssignments(studentId)).isZero();
    }

    // ---- آزمون ----

    @Test
    void assigningAnExamToAnEnrolledStudentSucceeds() throws Exception {
        School s = newSchoolWithCurrentYear();
        long classId = createClass(s);
        long studentId = enroll(s, classId);
        long examId = createExam(s, currentYear(s).start().plusDays(10));

        assignExam(s, examId, studentId).andExpect(status().isOk());

        assertThat(examAssignments(examId)).isEqualTo(1);
    }

    @Test
    void assigningAnExamToAStudentWithoutAnEnrollmentRowIsRejected() throws Exception {
        School s = newSchoolWithCurrentYear();
        long classId = createClass(s);
        long studentId = enrollWithoutRow(s, classId);
        long examId = createExam(s, currentYear(s).start().plusDays(10));

        assignExam(s, examId, studentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("student-not-enrolled-in-year")));

        assertThat(examAssignments(examId)).isZero();
    }

    @Test
    void anExamInAnotherYearChecksEnrollmentInThatYear() throws Exception {
        School s = newSchoolWithCurrentYear();
        long classId = createClass(s);
        long studentId = enroll(s, classId); // فقط در سالِ جاری ثبت‌نام است
        List<Year> years = years(s);
        Year current = currentYear(s);
        int index = years.indexOf(current);
        Assumptions.assumeTrue(index > 0, "سالِ جاری قدیمی‌ترین سالِ فهرست است");
        Year previous = years.get(index - 1);
        long examId = createExam(s, previous.start().plusDays(10));

        assignExam(s, examId, studentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("student-not-enrolled-in-year")));

        assertThat(examAssignments(examId)).isZero();
    }

    @Test
    void anExamWhoseDateIsInNoAcademicYearIsRejected() throws Exception {
        School s = newSchoolWithCurrentYear();
        long classId = createClass(s);
        long studentId = enroll(s, classId); // ثبت‌نام‌شده؛ مشکل خودِ تاریخ است
        long examId = createExam(s, LocalDate.of(2000, 1, 1)); // پیش از اولین سالِ فهرست

        assignExam(s, examId, studentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("no-academic-year-for-date")));

        assertThat(examAssignments(examId)).isZero();
    }

    // ---- مدرسه‌ی بدونِ سالِ جاری ----

    @Test
    void aSchoolWithoutACurrentYearStillAssignsAStudentWithoutAnEnrollmentRow() throws Exception {
        School s = newSchool(); // سالِ جاری انتخاب نشده: ردیفِ ثبت‌نامی نوشته نمی‌شود و بررسی هم نمی‌شود
        long classId = createClass(s);
        long studentId = enroll(s, classId);
        assertThat(jdbc.queryForObject("select count(*) from academic.enrollments where student_id = ?",
                Integer.class, studentId)).isZero();
        long planId = createPlan(s);
        // تاریخی که در هیچ سالی نیست: بدون سالِ جاری حتی این هم خطا نیست
        long examId = createExam(s, LocalDate.of(2000, 1, 1));

        assignPlan(s, planId, studentId).andExpect(status().isOk());
        assignExam(s, examId, studentId).andExpect(status().isOk());

        assertThat(planAssignments(studentId)).isEqualTo(1);
        assertThat(examAssignments(examId)).isEqualTo(1);
    }
}
