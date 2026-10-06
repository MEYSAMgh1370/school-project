package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * کلاس و ثبت‌نام به‌تفکیک سال و ارتقا (openspec/changes/academic-year.md، فاز ۳) روی Postgres واقعی. هر تست
 * مدرسه‌ی تازه‌ی خودش را می‌سازد (دبستان، پایه‌های ۱ تا ۶؛ بنیان‌گذار = SCHOOL_ADMIN با ACADEMIC_YEAR_MANAGE)،
 * پس با دیتای بقیه‌ی تست‌ها هم‌پوشانی ندارد. سال‌ها از فهرستِ واقعی: «1404-1405» سالِ قبل، «1405-1406» جاری.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AcademicRolloverFlowIntegrationTest {

    private static final String OLD = "1404-1405";
    private static final String CURRENT = "1405-1406";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record School(long id, MockHttpSession admin) {
    }

    private record Pupil(String email, long studentId, MockHttpSession session) {
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

    private ResultActions adminGet(School s, String path) throws Exception {
        return send(s, get("/api/admin/schools/" + s.id() + path), null);
    }

    private ResultActions adminPost(School s, String path, String body) throws Exception {
        return send(s, post("/api/admin/schools/" + s.id() + path), body);
    }

    private School newSchool() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("ro.founder"), "بنیان‌گذار ارتقا");
        long schoolId = json(mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"مدرسه‌ی ارتقا " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                        + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}")).andExpect(status().isOk())).get("id").asLong();
        return new School(schoolId, founder);
    }

    private long createClass(School s, int grade, String year) throws Exception {
        String yearField = year == null ? "" : ",\"academicYear\":\"" + year + "\"";
        return json(adminPost(s, "/classes", "{\"name\":\"پایه " + grade + "\",\"section\":null,\"teacherId\":null,"
                + "\"gradeLevel\":" + grade + ",\"gradingScheme\":\"NUMERIC\"" + yearField + "}")
                .andExpect(status().isOk())).get("id").asLong();
    }

    private Pupil enroll(School s, long classId) throws Exception {
        String email = unique("ro.student");
        MockHttpSession session = registerAndLogin(email, "دانش‌آموز آزمایشی");
        JsonNode created = json(adminPost(s, "/students",
                "{\"email\":\"" + email + "\",\"fullName\":\"دانش‌آموز آزمایشی\",\"classId\":" + classId + "}")
                .andExpect(status().isOk()));
        return new Pupil(email, created.get("studentId").asLong(), session);
    }

    private void setCurrentYear(School s, String code) throws Exception {
        long yearId = jdbc.queryForObject("select id from school.academic_years where code = ?", Long.class, code);
        Long expected = jdbc.queryForObject("select current_academic_year_id from school.schools where id = ?",
                Long.class, s.id());
        send(s, put("/api/admin/schools/" + s.id() + "/academic-year"),
                "{\"academicYearId\":" + yearId + ",\"expectedCurrentAcademicYearId\":" + expected + "}")
                .andExpect(status().isOk());
    }

    private JsonNode studentList(School s) throws Exception {
        return json(adminGet(s, "/students").andExpect(status().isOk()));
    }

    private JsonNode student(School s, long studentId) throws Exception {
        return json(adminGet(s, "/students/" + studentId).andExpect(status().isOk()));
    }

    private int enrollmentRows(School s, String year) {
        return jdbc.queryForObject("select count(*) from academic.enrollments where school_id = ? and academic_year = ?",
                Integer.class, s.id(), year);
    }

    /** مدرسه با دو کلاس (پایه‌ی ۵ و ۶) و سه دانش‌آموز، همه‌ی داده‌ها به سالِ قبل نسبت داده شده و سالِ جاری عوض شده. */
    private record Setup(School school, long class5, long class6, Pupil a, Pupil b, Pupil c) {
    }

    private Setup schoolReadyToPromote() throws Exception {
        School s = newSchool();
        long class5 = createClass(s, 5, null);
        long class6 = createClass(s, 6, null);
        Pupil a = enroll(s, class5);
        Pupil b = enroll(s, class5);
        Pupil c = enroll(s, class6);
        setCurrentYear(s, OLD);
        adminPost(s, "/academic-year/attribution", "{\"academicYear\":\"" + OLD + "\"}").andExpect(status().isOk());
        setCurrentYear(s, CURRENT);
        return new Setup(s, class5, class6, a, b, c);
    }

    // ---- انتساب ----

    @Test
    void existingClassesAndStudentsAreAttributedOnlyByAnExplicitActionAndOnlyToAYearNotAfterTheCurrentOne() throws Exception {
        School s = newSchool();
        long class5 = createClass(s, 5, null);
        enroll(s, class5);
        enroll(s, class5);

        // بدونِ سالِ جاری هیچ‌چیز حدس زده نمی‌شود
        adminGet(s, "/academic-year/unattributed").andExpect(status().isOk())
                .andExpect(jsonPath("$.currentYear").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.classes").value(1)).andExpect(jsonPath("$.students").value(2));
        adminPost(s, "/academic-year/attribution", "{\"academicYear\":\"" + OLD + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("year-not-chosen")));

        setCurrentYear(s, OLD);
        // سالِ ناشناخته و سالِ بعد از جاری هر دو رد می‌شوند و چیزی نوشته نمی‌شود
        adminPost(s, "/academic-year/attribution", "{\"academicYear\":\"1300-1301\"}").andExpect(status().isBadRequest());
        adminPost(s, "/academic-year/attribution", "{\"academicYear\":\"" + CURRENT + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-rollover")));
        assertThat(enrollmentRows(s, CURRENT)).isZero();

        adminPost(s, "/academic-year/attribution", "{\"academicYear\":\"" + OLD + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.classes").value(1)).andExpect(jsonPath("$.students").value(2));
        assertThat(enrollmentRows(s, OLD)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select academic_year from academic.classes where id = ?", String.class, class5))
                .isEqualTo(OLD);
        adminGet(s, "/academic-year/unattributed").andExpect(jsonPath("$.classes").value(0))
                .andExpect(jsonPath("$.students").value(0));
        // دوباره: چیزی برای انتساب نیست
        adminPost(s, "/academic-year/attribution", "{\"academicYear\":\"" + OLD + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.classes").value(0)).andExpect(jsonPath("$.students").value(0));
    }

    @Test
    void aTrackedSchoolWritesEnrollmentRowsForNewStudentsAndPutsNewClassesInTheCurrentYear() throws Exception {
        School s = newSchool();
        setCurrentYear(s, CURRENT); // مدرسه‌ی تازه و بدون دانش‌آموز: از اول ردیاب‌شده است
        long classId = createClass(s, 5, null);
        assertThat(jdbc.queryForObject("select academic_year from academic.classes where id = ?", String.class, classId))
                .isEqualTo(CURRENT);

        Pupil p = enroll(s, classId);

        assertThat(enrollmentRows(s, CURRENT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from academic.enrollments where student_id = ?", String.class,
                p.studentId())).isEqualTo("ENROLLED");
        // کلاسِ سالِ دیگر برای ثبت‌نامِ امسال رد می‌شود
        long oldClass = createClass(s, 5, OLD);
        adminPost(s, "/students", "{\"email\":\"" + unique("ro.x") + "\",\"fullName\":\"x\",\"classId\":" + oldClass + "}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("class-not-in-year")));
    }

    @Test
    void aSchoolWithoutACurrentYearBehavesAsBefore() throws Exception {
        School s = newSchool();
        long classId = createClass(s, 5, null);
        enroll(s, classId);

        assertThat(jdbc.queryForObject("select academic_year from academic.classes where id = ?", String.class, classId))
                .isNull();
        assertThat(enrollmentRows(s, CURRENT) + enrollmentRows(s, OLD)).isZero();
        adminGet(s, "/classes").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    // ---- فهرست کلاس‌ها ----

    @Test
    void classesAreListedByYearWithTheCurrentYearAsTheDefault() throws Exception {
        Setup x = schoolReadyToPromote();
        long newClass = createClass(x.school(), 5, null); // سالِ جاری

        List<Long> current = ids(adminGet(x.school(), "/classes").andExpect(status().isOk()));
        assertThat(current).containsExactly(newClass);
        assertThat(ids(adminGet(x.school(), "/classes?academicYear=all").andExpect(status().isOk())))
                .containsExactlyInAnyOrder(x.class5(), x.class6(), newClass);
        assertThat(ids(adminGet(x.school(), "/classes?academicYear=" + OLD).andExpect(status().isOk())))
                .containsExactlyInAnyOrder(x.class5(), x.class6());
        adminGet(x.school(), "/classes?academicYear=1300-1301").andExpect(status().isBadRequest());
    }

    private List<Long> ids(ResultActions actions) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(actions).forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    // ---- ارتقا ----

    @Test
    void theDefaultRulePromotesEveryoneAndGraduatesTheLastGradeAndAnOutcomeCanBeOverridden() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();

        JsonNode plan = json(adminGet(s, "/promotion/preview?fromYear=" + OLD).andExpect(status().isOk()));
        assertThat(plan).hasSize(3);
        for (JsonNode item : plan) {
            assertThat(item.get("defaultOutcome").asText())
                    .isEqualTo(item.get("studentId").asLong() == x.c().studentId() ? "GRADUATE" : "PROMOTE");
        }

        JsonNode run = json(adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\",\"decisions\":[{\"studentId\":"
                + x.b().studentId() + ",\"outcome\":\"REPEAT\"}]}").andExpect(status().isOk()));
        assertThat(run.get("toYear").asText()).isEqualTo(CURRENT);
        assertThat(run.get("mode").asText()).isEqualTo("MANUAL");
        assertThat(run.get("counts").get("PROMOTE").asInt()).isEqualTo(1);
        assertThat(run.get("counts").get("REPEAT").asInt()).isEqualTo(1);
        assertThat(run.get("counts").get("GRADUATE").asInt()).isEqualTo(1);
        assertThat(run.get("undoable").asBoolean()).isTrue();

        // فهرست دانش‌آموزان: فارغ‌التحصیل نیست؛ دو نفرِ ارتقایافته PENDING و بدون کلاس‌اند
        JsonNode list = studentList(s);
        assertThat(list).hasSize(2);
        for (JsonNode st : list) {
            assertThat(st.get("status").asText()).isEqualTo("PENDING");
            assertThat(st.get("classId").isNull()).isTrue();
        }
        // ولی خودِ فارغ‌التحصیل با شناسه همچنان قابل خواندن است (تاریخچه)
        assertThat(student(s, x.c().studentId()).get("status").asText()).isEqualTo("GRADUATED");
        // ثبت‌نامِ سالِ قبل دست‌نخورده، و برای هر ارتقایافته یک ردیفِ PENDING با پایه‌ی هدف
        assertThat(enrollmentRows(s, OLD)).isEqualTo(3);
        assertThat(enrollmentRows(s, CURRENT)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select grade_level from academic.enrollments where student_id = ? and academic_year = ?",
                Integer.class, x.a().studentId(), CURRENT)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select grade_level from academic.enrollments where student_id = ? and academic_year = ?",
                Integer.class, x.b().studentId(), CURRENT)).isEqualTo(5);

        JsonNode pending = json(adminGet(s, "/promotion/pending").andExpect(status().isOk()));
        assertThat(pending).hasSize(2);
        for (JsonNode item : pending) {
            // ارتقا: پایه‌ی هدف یکی بیشتر از سالِ قبل؛ تکرار: همان پایه
            boolean repeated = item.get("studentId").asLong() == x.b().studentId();
            assertThat(item.get("gradeLevel").asInt())
                    .isEqualTo(item.get("previousGradeLevel").asInt() + (repeated ? 0 : 1));
        }
        JsonNode graduates = json(adminGet(s, "/promotion/graduates").andExpect(status().isOk()));
        assertThat(graduates).hasSize(1);
        assertThat(graduates.get(0).get("studentId").asLong()).isEqualTo(x.c().studentId());
        assertThat(graduates.get(0).get("gradeLevel").asInt()).isEqualTo(6);

        // دوباره اجرا: کسی برای ارتقا نمانده
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void outcomesThatDoNotFitTheGradeAreRejectedAndNothingIsWritten() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();

        // پایه‌ی ۶ آخرین پایه‌ی دبستان است: ارتقا ندارد
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\",\"decisions\":[{\"studentId\":" + x.c().studentId()
                + ",\"outcome\":\"PROMOTE\"}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-rollover")));
        // فارغ‌التحصیلی فقط در آخرین پایه
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\",\"decisions\":[{\"studentId\":" + x.a().studentId()
                + ",\"outcome\":\"GRADUATE\"}]}").andExpect(status().isBadRequest());
        // دانش‌آموزی که ارتقاپذیر نیست
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\",\"decisions\":[{\"studentId\":999999,\"outcome\":\"REPEAT\"}]}")
                .andExpect(status().isBadRequest());
        // سالِ مبدأ باید قبل از جاری باشد
        adminPost(s, "/promotion", "{\"fromYear\":\"" + CURRENT + "\"}").andExpect(status().isBadRequest());

        assertThat(enrollmentRows(s, CURRENT)).isZero();
        assertThat(studentList(s)).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from academic.promotion_runs where school_id = ?", Integer.class,
                s.id())).isZero();
    }

    @Test
    void leavingDeactivatesTheMembershipAndAnUndoRestoresEverything() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();

        JsonNode run = json(adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\",\"decisions\":[{\"studentId\":"
                + x.a().studentId() + ",\"outcome\":\"LEAVE\"}]}").andExpect(status().isOk()));
        assertThat(student(s, x.a().studentId()).get("status").asText()).isEqualTo("LEFT");
        // رفتن از فهرست، نه حذف
        assertThat(studentList(s)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from academic.students where school_id = ?", Integer.class,
                s.id())).isEqualTo(3);
        assertThat(membershipActive(x.a())).isFalse();
        assertThat(membershipActive(x.b())).isTrue();

        adminPost(s, "/promotion/runs/" + run.get("id").asLong() + "/undo", null).andExpect(status().isNoContent());

        assertThat(studentList(s)).hasSize(3);
        for (Pupil p : List.of(x.a(), x.b(), x.c())) {
            JsonNode st = student(s, p.studentId());
            assertThat(st.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(st.get("classId").isNull()).isFalse();
        }
        assertThat(student(s, x.a().studentId()).get("classId").asLong()).isEqualTo(x.class5());
        assertThat(student(s, x.c().studentId()).get("classId").asLong()).isEqualTo(x.class6());
        assertThat(membershipActive(x.a())).isTrue();
        assertThat(enrollmentRows(s, CURRENT)).isZero();
        JsonNode runs = json(adminGet(s, "/promotion/runs").andExpect(status().isOk()));
        assertThat(runs.get(0).get("undoneAt").isNull()).isFalse();
        assertThat(runs.get(0).get("undoable").asBoolean()).isFalse();
        // برگرداندنِ دوباره
        adminPost(s, "/promotion/runs/" + run.get("id").asLong() + "/undo", null).andExpect(status().isConflict());
        // و می‌شود دوباره ارتقا داد
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isOk());
    }

    private boolean membershipActive(Pupil p) {
        return jdbc.queryForObject("select m.active from iam.school_memberships m join iam.users u on u.id = m.user_id "
                + "where u.email = ? and m.school_id = (select school_id from academic.students where id = ?)",
                Boolean.class, p.email(), p.studentId());
    }

    @Test
    void aGraduateCanBeMarkedToRepeatAndAPendingStudentCanBeChangedBetweenRepeatAndPromote() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isOk());

        // فارغ‌التحصیل → تکرار پایه: دوباره PENDING در همان پایه‌ی ۶
        adminPost(s, "/promotion/outcome", "{\"studentId\":" + x.c().studentId() + ",\"outcome\":\"REPEAT\"}")
                .andExpect(status().isNoContent());
        assertThat(student(s, x.c().studentId()).get("status").asText()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select grade_level from academic.enrollments where student_id = ? and academic_year = ?",
                Integer.class, x.c().studentId(), CURRENT)).isEqualTo(6);
        // ولی پایه‌ی بعد از ۶ نیست
        adminPost(s, "/promotion/outcome", "{\"studentId\":" + x.c().studentId() + ",\"outcome\":\"PROMOTE\"}")
                .andExpect(status().isBadRequest());
        // ارتقایافته → تکرار → دوباره ارتقا
        adminPost(s, "/promotion/outcome", "{\"studentId\":" + x.a().studentId() + ",\"outcome\":\"REPEAT\"}")
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select grade_level from academic.enrollments where student_id = ? and academic_year = ?",
                Integer.class, x.a().studentId(), CURRENT)).isEqualTo(5);
        adminPost(s, "/promotion/outcome", "{\"studentId\":" + x.a().studentId() + ",\"outcome\":\"PROMOTE\"}")
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select grade_level from academic.enrollments where student_id = ? and academic_year = ?",
                Integer.class, x.a().studentId(), CURRENT)).isEqualTo(6);
        // نتیجه‌ی دیگری دستی عوض نمی‌شود
        adminPost(s, "/promotion/outcome", "{\"studentId\":" + x.a().studentId() + ",\"outcome\":\"LEAVE\"}")
                .andExpect(status().isBadRequest());

        // تغییر دستی هم با undo برمی‌گردد
        long runId = json(adminGet(s, "/promotion/runs")).get(0).get("id").asLong();
        adminPost(s, "/promotion/runs/" + runId + "/undo", null).andExpect(status().isNoContent());
        assertThat(student(s, x.c().studentId()).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(enrollmentRows(s, CURRENT)).isZero();
    }

    // ---- جا‌دادن ----

    @Test
    void pendingStudentsArePlacedOnlyInAClassOfTheirTargetGradeInTheCurrentYearAndPlacementBlocksUndo() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();
        long runId = json(adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\",\"decisions\":[{\"studentId\":"
                + x.b().studentId() + ",\"outcome\":\"REPEAT\"}]}").andExpect(status().isOk())).get("id").asLong();

        long new5 = createClass(s, 5, null);
        long new6 = createClass(s, 6, null);
        long oldYearClass = createClass(s, 6, OLD);

        // پایه نمی‌خواند (a به ۶ می‌رود)
        adminPost(s, "/promotion/placements", "{\"placements\":[{\"studentId\":" + x.a().studentId() + ",\"classId\":" + new5 + "}]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("class-grade-mismatch")));
        // کلاسِ سالِ دیگر
        adminPost(s, "/promotion/placements", "{\"placements\":[{\"studentId\":" + x.a().studentId() + ",\"classId\":" + oldYearClass + "}]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("class-not-in-year")));
        // همه یا هیچ: دومی خراب است، اولی هم جا نمی‌شود
        adminPost(s, "/promotion/placements", "{\"placements\":[{\"studentId\":" + x.a().studentId() + ",\"classId\":" + new6
                + "},{\"studentId\":" + x.b().studentId() + ",\"classId\":" + new6 + "}]}").andExpect(status().isConflict());
        assertThat(student(s, x.a().studentId()).get("status").asText()).isEqualTo("PENDING");

        adminPost(s, "/promotion/placements", "{\"placements\":[{\"studentId\":" + x.a().studentId() + ",\"classId\":" + new6
                + "},{\"studentId\":" + x.b().studentId() + ",\"classId\":" + new5 + "}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.placed").value(2));

        JsonNode a = student(s, x.a().studentId());
        assertThat(a.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(a.get("classId").asLong()).isEqualTo(new6);
        assertThat(student(s, x.b().studentId()).get("classId").asLong()).isEqualTo(new5);
        assertThat(jdbc.queryForObject("select status from academic.enrollments where student_id = ? and academic_year = ?",
                String.class, x.a().studentId(), CURRENT)).isEqualTo("ENROLLED");
        assertThat(json(adminGet(s, "/promotion/pending"))).isEmpty();
        // ثبت‌نامِ سالِ قبل هنوز به کلاسِ قبلی اشاره می‌کند (تاریخچه)
        assertThat(jdbc.queryForObject("select class_id from academic.enrollments where student_id = ? and academic_year = ?",
                Long.class, x.a().studentId(), OLD)).isEqualTo(x.class5());

        // جا‌شده‌ها دیگر قابل برگرداندن نیستند
        adminPost(s, "/promotion/runs/" + runId + "/undo", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("promotion-run-not-undoable")));
        assertThat(json(adminGet(s, "/promotion/runs")).get(0).get("undoable").asBoolean()).isFalse();
        // و دانش‌آموزِ جا‌شده با نتیجه‌ی دستی عوض نمی‌شود
        adminPost(s, "/promotion/outcome", "{\"studentId\":" + x.a().studentId() + ",\"outcome\":\"REPEAT\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOrdinaryEnrollmentPlacesAPendingStudentToo() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isOk());
        long new6 = createClass(s, 6, null);
        long new5 = createClass(s, 5, null);

        adminPost(s, "/students", "{\"email\":\"" + x.a().email() + "\",\"fullName\":\"دانش‌آموز آزمایشی\",\"classId\":" + new5 + "}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("class-grade-mismatch")));
        adminPost(s, "/students", "{\"email\":\"" + x.a().email() + "\",\"fullName\":\"دانش‌آموز آزمایشی\",\"classId\":" + new6 + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.classId").value(new6));
        assertThat(jdbc.queryForObject("select status from academic.enrollments where student_id = ? and academic_year = ?",
                String.class, x.a().studentId(), CURRENT)).isEqualTo("ENROLLED");
    }

    // ---- نمره ----

    @Test
    void gradesNeedAnEnrollmentInTheClassButNotAMatchingYearAndAnAdminCanStillCorrectAnEarlierYearInTheOldClass() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isOk());
        long new6 = createClass(s, 6, null);
        adminPost(s, "/promotion/placements", "{\"placements\":[{\"studentId\":" + x.a().studentId() + ",\"classId\":" + new6 + "}]}")
                .andExpect(status().isOk());

        // نمره‌ها جدی گرفته نمی‌شوند (سامانه‌ی رسمی نمره‌ی آموزش و پرورش است): سالِ نمره با سالِ کلاس هم‌خوانی اجباری ندارد
        adminPost(s, "/grades", grade(x.a().studentId(), new6, OLD)).andExpect(status().isOk());
        adminPost(s, "/grades", grade(x.a().studentId(), new6, CURRENT)).andExpect(status().isOk());
        // اصلاحِ نمره‌ی سالِ قبل در کلاسِ سالِ قبل (دانش‌آموز دیگر در آن کلاس نیست ولی ثبت‌نامش را داشته)
        adminPost(s, "/grades", grade(x.a().studentId(), x.class5(), OLD)).andExpect(status().isOk());
        // برای فارغ‌التحصیل هم
        adminPost(s, "/grades", grade(x.c().studentId(), x.class6(), OLD)).andExpect(status().isOk());
        // و در کلاسی که هرگز در آن نبوده نه
        adminPost(s, "/grades", grade(x.b().studentId(), x.class6(), OLD)).andExpect(status().isConflict());
        // کلاسی که دانش‌آموز ترک کرده برای سالِ جاری نمره نمی‌گیرد؛ ثبت‌نامِ سالِ قبل فقط برای همان سال کافی است
        adminPost(s, "/grades", grade(x.a().studentId(), x.class5(), CURRENT)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("student-not-in-class")));
        // نمره‌ی سالِ دیگر در کلاسِ جدید ذخیره و از همان کلاس با همان سال خوانده می‌شود
        for (String year : List.of(OLD, CURRENT)) {
            JsonNode stored = json(adminGet(s, "/grades?classId=" + new6 + "&academicYear=" + year + "&term=FIRST")
                    .andExpect(status().isOk()));
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).get("studentId").asLong()).isEqualTo(x.a().studentId());
            assertThat(stored.get(0).get("classId").asLong()).isEqualTo(new6);
            assertThat(stored.get(0).get("academicYear").asText()).isEqualTo(year);
        }
    }

    private String grade(long studentId, long classId, String year) {
        return "{\"studentId\":" + studentId + ",\"classId\":" + classId + ",\"academicYear\":\"" + year
                + "\",\"term\":\"FIRST\",\"numericScore\":17,\"descriptiveLevel\":null,\"comment\":null,\"courseId\":null}";
    }

    // ---- خودکار ----

    @Test
    void automaticPromotionRunsOnlyWhenTheSettingIsOnAndTheYearMovesForward() throws Exception {
        School s = newSchool();
        long class5 = createClass(s, 5, null);
        enroll(s, class5);
        setCurrentYear(s, OLD);
        adminPost(s, "/academic-year/attribution", "{\"academicYear\":\"" + OLD + "\"}").andExpect(status().isOk());

        adminGet(s, "/academic-year").andExpect(status().isOk()).andExpect(jsonPath("$.autoPromotion").value(false));
        send(s, put("/api/admin/schools/" + s.id() + "/academic-year/auto-promotion"), "{\"enabled\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.autoPromotion").value(true));
        send(s, put("/api/admin/schools/" + s.id() + "/academic-year/auto-promotion"), "{}")
                .andExpect(status().isBadRequest());

        setCurrentYear(s, CURRENT);
        JsonNode runs = awaitRuns(s, 1);
        assertThat(runs.get(0).get("mode").asText()).isEqualTo("AUTO");
        assertThat(runs.get(0).get("fromYear").asText()).isEqualTo(OLD);
        assertThat(runs.get(0).get("toYear").asText()).isEqualTo(CURRENT);
        assertThat(json(adminGet(s, "/promotion/pending"))).hasSize(1);

        // بازگشت به سالِ قبل هرگز ارتقا نمی‌دهد
        setCurrentYear(s, OLD);
        Thread.sleep(700);
        assertThat(json(adminGet(s, "/promotion/runs"))).hasSize(1);
    }

    @Test
    void nothingIsPromotedAutomaticallyWhileTheSettingIsOff() throws Exception {
        Setup x = schoolReadyToPromote(); // سالِ جاری از قبل به ۱۴۰۵-۱۴۰۶ رفته و تنظیم خاموش بود
        Thread.sleep(700);

        assertThat(json(adminGet(x.school(), "/promotion/runs"))).isEmpty();
        assertThat(studentList(x.school())).hasSize(3);
        for (JsonNode st : studentList(x.school())) {
            assertThat(st.get("status").asText()).isEqualTo("ACTIVE");
        }
    }

    private JsonNode awaitRuns(School s, int expected) throws Exception {
        JsonNode runs = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            runs = json(adminGet(s, "/promotion/runs").andExpect(status().isOk()));
            if (runs.size() >= expected) {
                return runs;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no automatic promotion run appeared: " + runs);
    }

    // ---- فاکتور ----

    @Test
    void invoicesOfAYearGoOnlyToStudentsEnrolledInThatYear() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();
        // ارتقا: a و b ارتقا، c فارغ‌التحصیل
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isOk());
        Thread.sleep(300); // رویدادهای ناهمگامِ ثبت‌نام تمام شده باشند
        send(s, put("/api/admin/schools/" + s.id() + "/tuition-fee?academicYear=" + CURRENT), "{\"amount\":5000}")
                .andExpect(status().isOk());

        adminPost(s, "/invoices/backfill?academicYear=" + CURRENT, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedCount").value(2));
        JsonNode invoices = json(adminGet(s, "/invoices?academicYear=" + CURRENT).andExpect(status().isOk()));
        List<Long> invoiced = new ArrayList<>();
        invoices.forEach(i -> {
            if (CURRENT.equals(i.get("academicYear").asText())) {
                invoiced.add(i.get("studentId").asLong());
            }
        });
        assertThat(invoiced).containsExactlyInAnyOrder(x.a().studentId(), x.b().studentId());
        assertThat(invoiced).doesNotContain(x.c().studentId());
        // دوباره: چیزی نمی‌ماند
        adminPost(s, "/invoices/backfill?academicYear=" + CURRENT, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedCount").value(0));
    }

    // ---- خودخدمتی ----

    @Test
    void selfServiceKeepsWorkingForAPendingStudentWithoutAClass() throws Exception {
        Setup x = schoolReadyToPromote();
        School s = x.school();
        MockHttpSession pupil = x.a().session();
        adminPost(s, "/promotion", "{\"fromYear\":\"" + OLD + "\"}").andExpect(status().isOk());

        // «کلاس من»: بدون کلاس 204 (اپ موبایل classId را غیرتهی می‌خواند)
        mockMvc.perform(get("/api/me/enrollment").param("schoolId", String.valueOf(s.id())).session(pupil))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/schools/" + s.id() + "/timetable").session(pupil))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/schools/" + s.id() + "/grades").session(pupil))
                .andExpect(status().isOk());
    }

    // ---- دسترسی ----

    @Test
    void onlyTheYearManagerMayRunTheRollover() throws Exception {
        Setup x = schoolReadyToPromote();
        MockHttpSession stranger = registerAndLogin(unique("ro.stranger"), "بیگانه");
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/admin/schools/" + x.school().id() + "/promotion/runs"),
                get("/api/admin/schools/" + x.school().id() + "/promotion/pending"),
                get("/api/admin/schools/" + x.school().id() + "/academic-year/unattributed"))) {
            mockMvc.perform(request.header("X-School-ID", String.valueOf(x.school().id())).session(stranger))
                    .andExpect(status().is(org.hamcrest.Matchers.anyOf(is(401), is(403))));
        }
        mockMvc.perform(post("/api/admin/schools/" + x.school().id() + "/promotion").with(csrf())
                        .header("X-School-ID", String.valueOf(x.school().id())).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fromYear\":\"" + OLD + "\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(is(401), is(403))));
        assertThat(enrollmentRows(x.school(), CURRENT)).isZero();
    }
}
