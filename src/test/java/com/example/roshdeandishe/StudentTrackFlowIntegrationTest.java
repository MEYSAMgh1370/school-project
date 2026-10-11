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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * رشته‌ی دانش‌آموز («ریاضی»، «تجربی»، «انسانی») روی ردیفِ ثبت‌نامِ سالِ جاری، روی Postgres واقعی: از فرمِ ثبت‌نام و
 * از جا‌دادنِ دانش‌آموزِ ارتقایافته تعیین می‌شود؛ ارتقا آن را کپی نمی‌کند. هر تست مدرسه‌ی تازه‌ی خودش را می‌سازد
 * (دبستان، پایه‌های ۱ تا ۶)؛ سال‌ها از فهرستِ واقعی: «1404-1405» سالِ قبل، «1405-1406» جاری.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudentTrackFlowIntegrationTest {

    private static final String OLD = "1404-1405";
    private static final String CURRENT = "1405-1406";

    private static final String MATH = "ریاضی";
    private static final String SCIENCE = "تجربی";
    private static final String INVALID = "هنر";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record School(long id, MockHttpSession admin) {
    }

    private record Pupil(String email, long studentId) {
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

    private School newSchool() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("tr.founder"), "بنیان‌گذار رشته");
        long schoolId = json(mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"مدرسه‌ی رشته " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
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

    private String trackField(String track) {
        return ",\"track\":" + (track == null ? "null" : "\"" + track + "\"");
    }

    /** ثبت‌نامِ ایمیلی که می‌تواند از قبل دانش‌آموز باشد (ثبت‌نامِ دوباره = جابه‌جایی/به‌روزرسانی). */
    private ResultActions enrollEmail(School s, String email, long classId, String track) throws Exception {
        return adminPost(s, "/students", "{\"email\":\"" + email + "\",\"fullName\":\"دانش‌آموز رشته\",\"classId\":"
                + classId + trackField(track) + "}");
    }

    private Pupil enroll(School s, long classId, String track) throws Exception {
        String email = unique("tr.student");
        registerAndLogin(email, "دانش‌آموز رشته");
        JsonNode created = json(enrollEmail(s, email, classId, track).andExpect(status().isOk()));
        return new Pupil(email, created.get("studentId").asLong());
    }

    private String trackOf(long studentId, String year) {
        return jdbc.queryForObject("select track from academic.enrollments where student_id = ? and academic_year = ?",
                String.class, studentId, year);
    }

    private String statusOf(long studentId, String year) {
        return jdbc.queryForObject("select status from academic.enrollments where student_id = ? and academic_year = ?",
                String.class, studentId, year);
    }

    private int studentRows(School s) {
        return jdbc.queryForObject("select count(*) from academic.students where school_id = ?", Integer.class, s.id());
    }

    private int enrollmentRows(School s) {
        return jdbc.queryForObject("select count(*) from academic.enrollments where school_id = ?", Integer.class, s.id());
    }

    // ---- فرمِ ثبت‌نام ----

    @Test
    void enrollingWithATrackStoresItOnTheCurrentYearRow() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, 5);

        Pupil p = enroll(s, classId, MATH);

        assertThat(trackOf(p.studentId(), CURRENT)).isEqualTo(MATH);
        assertThat(statusOf(p.studentId(), CURRENT)).isEqualTo("ENROLLED");
    }

    @Test
    void enrollingWithoutATrackLeavesItEmpty() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, 5);

        Pupil p = enroll(s, classId, null);

        assertThat(trackOf(p.studentId(), CURRENT)).isNull();
    }

    @Test
    void enrollingAgainWithoutATrackKeepsItAndWithAnotherTrackChangesIt() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, 5);
        Pupil p = enroll(s, classId, MATH);

        // بدون رشته: رشته‌ی ذخیره‌شده دست نمی‌خورد
        enrollEmail(s, p.email(), classId, null).andExpect(status().isOk());
        assertThat(trackOf(p.studentId(), CURRENT)).isEqualTo(MATH);

        // با رشته‌ی دیگر: عوض می‌شود، روی همان ردیف
        enrollEmail(s, p.email(), classId, SCIENCE).andExpect(status().isOk());
        assertThat(trackOf(p.studentId(), CURRENT)).isEqualTo(SCIENCE);
        assertThat(enrollmentRows(s)).isEqualTo(1);
    }

    @Test
    void anInvalidTrackIsRejectedAndNothingIsCreated() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, 5);

        enrollEmail(s, unique("tr.invalid"), classId, INVALID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-track")));

        assertThat(studentRows(s)).isZero();
        assertThat(enrollmentRows(s)).isZero();
    }

    @Test
    void anInvalidTrackOnAnExistingStudentChangesNothing() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, 5);
        Pupil p = enroll(s, classId, MATH);

        enrollEmail(s, p.email(), classId, INVALID).andExpect(status().isBadRequest());

        assertThat(trackOf(p.studentId(), CURRENT)).isEqualTo(MATH);
    }

    @Test
    void aTrackInASchoolWithoutACurrentYearIsRejectedAndNothingIsCreated() throws Exception {
        School s = newSchool(); // سالِ جاری انتخاب نشده: ردیفِ ثبت‌نامی نیست که رشته در آن بنشیند
        long classId = createClass(s, 5);

        enrollEmail(s, unique("tr.noyear"), classId, MATH)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("track-needs-current-year")));

        assertThat(studentRows(s)).isZero();
        assertThat(enrollmentRows(s)).isZero();

        // بدون رشته همان رفتارِ قبلی: ثبت‌نام می‌شود و ردیفی نمی‌نویسد
        enrollEmail(s, unique("tr.noyear.ok"), classId, null).andExpect(status().isOk());
        assertThat(studentRows(s)).isEqualTo(1);
        assertThat(enrollmentRows(s)).isZero();
    }

    // ---- ارتقا ----

    @Test
    void promotionDoesNotCopyTheTrackAndPlacementSetsIt() throws Exception {
        School s = newSchool();
        setCurrentYear(s, OLD);
        long class5 = createClass(s, 5);
        Pupil withTrack = enroll(s, class5, MATH);
        Pupil withoutTrack = enroll(s, class5, null);
        Pupil placedWithoutTrack = enroll(s, class5, SCIENCE);
        setCurrentYear(s, CURRENT);

        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isOk());

        // سالِ جدید PENDING و بدون رشته؛ ردیفِ سالِ قبل دست نخورده
        for (Pupil p : new Pupil[]{withTrack, withoutTrack, placedWithoutTrack}) {
            assertThat(statusOf(p.studentId(), CURRENT)).isEqualTo("PENDING");
            assertThat(trackOf(p.studentId(), CURRENT)).isNull();
        }
        assertThat(trackOf(withTrack.studentId(), OLD)).isEqualTo(MATH);
        assertThat(trackOf(placedWithoutTrack.studentId(), OLD)).isEqualTo(SCIENCE);

        long new6 = createClass(s, 6); // سالِ جاری

        // رشته‌ی نامعتبر: همه یا هیچ، هیچ‌کس جا نمی‌شود
        adminPost(s, "/promotion/placements", "{\"placements\":[{\"studentId\":" + withTrack.studentId()
                + ",\"classId\":" + new6 + ",\"track\":\"" + INVALID + "\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-track")));
        assertThat(statusOf(withTrack.studentId(), CURRENT)).isEqualTo("PENDING");
        assertThat(trackOf(withTrack.studentId(), CURRENT)).isNull();

        adminPost(s, "/promotion/placements", "{\"placements\":["
                + "{\"studentId\":" + withTrack.studentId() + ",\"classId\":" + new6 + ",\"track\":\"" + SCIENCE + "\"},"
                + "{\"studentId\":" + withoutTrack.studentId() + ",\"classId\":" + new6 + ",\"track\":null},"
                + "{\"studentId\":" + placedWithoutTrack.studentId() + ",\"classId\":" + new6 + "}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.placed").value(3));

        assertThat(statusOf(withTrack.studentId(), CURRENT)).isEqualTo("ENROLLED");
        assertThat(trackOf(withTrack.studentId(), CURRENT)).isEqualTo(SCIENCE);
        // رشته‌ی تهی در جا‌دادن = بدون رشته (ردیف رشته‌ای نداشت)
        assertThat(trackOf(withoutTrack.studentId(), CURRENT)).isNull();
        assertThat(trackOf(placedWithoutTrack.studentId(), CURRENT)).isNull();
        // جا‌دادن ردیفِ سالِ قبل را عوض نمی‌کند
        assertThat(trackOf(withTrack.studentId(), OLD)).isEqualTo(MATH);
    }

    // ---- دیتابیس ----

    @Test
    void theDatabaseRejectsATrackOutsideTheThreeValues() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT);
        long classId = createClass(s, 5);
        Pupil p = enroll(s, classId, null);

        String insert = "insert into academic.enrollments "
                + "(school_id, student_id, academic_year, grade_level, status, created_at, track) "
                + "values (?, ?, ?, 5, 'PENDING', now(), ?)";

        // مقدارِ معتبر همین ردیف را می‌نویسد، پس شکستِ بعدی از خودِ CHECK است نه چیزِ دیگر
        jdbc.update(insert, s.id(), p.studentId(), "1300-1301", "انسانی");
        assertThatThrownBy(() -> jdbc.update(insert, s.id(), p.studentId(), "1301-1302", INVALID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
