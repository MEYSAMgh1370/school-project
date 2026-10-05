package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * حضور و غیاب (openspec/changes/attendance.md، فاز ۱ و ۲) روی Postgres واقعی: ثبت، بازکردنِ دوباره، جایگزینیِ
 * idempotent، خطاهای ۴۰۰/۴۰۳/۴۰۹، معلم در برابر مدیر، جداییِ مدرسه‌ها، غایبین، رقمِ مدیر، گزارش و رایگان‌بودنِ
 * ماژول با backfill. هر تست مدرسه‌ی خودش را می‌سازد (دیتابیس بین تست‌ها پاک نمی‌شود)، با روزِ شروعِ هفته‌ای که
 * «امروز» در آن روزِ درسی است تا تست در هر روزِ هفته بدونِ دست‌کاریِ ساعت کار کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AttendanceFlowIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tehran");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IamFacade iamFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- کمکی‌های عمومی ----

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

    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpSession session, long schoolId, MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        var builder = request.with(csrf()).header("X-School-ID", String.valueOf(schoolId)).session(session);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    // ---- تاریخ‌ها: همه نسبت به «امروز» به وقتِ تهران ----

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** مدرسه‌ی شنبه‌محور اگر امروز شنبه تا چهارشنبه است، وگرنه دوشنبه‌محور؛ یکی از این دو همیشه امروز را درسی می‌کند. */
    private static WeekStart weekStartCoveringToday() {
        return WeekStart.SATURDAY.schoolDays().contains(today().getDayOfWeek()) ? WeekStart.SATURDAY : WeekStart.MONDAY;
    }

    private static LocalDate pastSchoolDay(WeekStart weekStart) {
        for (int back = 1; back <= 7; back++) {
            LocalDate day = today().minusDays(back);
            if (weekStart.schoolDays().contains(day.getDayOfWeek())) {
                return day;
            }
        }
        throw new AssertionError("هر الگوی هفته در ۷ روز یک روزِ درسی دارد");
    }

    private static LocalDate nonSchoolDay(WeekStart weekStart) {
        for (int back = 1; back <= 7; back++) {
            LocalDate day = today().minusDays(back);
            if (!weekStart.schoolDays().contains(day.getDayOfWeek())) {
                return day;
            }
        }
        throw new AssertionError("هر الگوی هفته در ۷ روز یک روزِ غیردرسی دارد");
    }

    // ---- ساختنِ مدرسه و داده ----

    /** مدرسه‌ی تازه با مدیر (بنیان‌گذار)، دو معلم و سه کلاس: الف (معلم ۱، سه دانش‌آموز)، ب (معلم ۲، دو نفر)، ج (بی‌معلم، خالی). */
    private record Fixture(long school, WeekStart weekStart, MockHttpSession admin, MockHttpSession teacher1,
                           MockHttpSession teacher2, long classA, long classB, long classC,
                           long s1, long s2, long s3, long s4, long s5) {
    }

    private long createSchool(MockHttpSession founder, WeekStart weekStart) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی حضور و غیاب " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\",\"weekStart\":\"" + weekStart.name() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long addTeacher(MockHttpSession admin, long school, String email, String name) throws Exception {
        return json(send(admin, school, post("/api/admin/schools/" + school + "/memberships"),
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\",\"roleCode\":\"TEACHER\"}")
                .andExpect(status().isOk())).get("userId").asLong();
    }

    private long createClass(MockHttpSession admin, long school, String name, Long teacherId) throws Exception {
        return json(send(admin, school, post("/api/admin/schools/" + school + "/classes"),
                "{\"name\":\"" + name + "\",\"section\":null,\"teacherId\":" + teacherId
                        + ",\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}").andExpect(status().isOk())).get("id").asLong();
    }

    private long enroll(MockHttpSession admin, long school, String email, String name, long classId) throws Exception {
        return json(send(admin, school, post("/api/admin/schools/" + school + "/students"),
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\",\"classId\":" + classId + "}")
                .andExpect(status().isOk())).get("studentId").asLong();
    }

    private Fixture fixture() throws Exception {
        WeekStart weekStart = weekStartCoveringToday();
        MockHttpSession admin = registerAndLogin(unique("att.founder"), "مدیر حضور و غیاب");
        long school = createSchool(admin, weekStart);

        String teacher1Email = unique("att.teacher1");
        String teacher2Email = unique("att.teacher2");
        MockHttpSession teacher1 = registerAndLogin(teacher1Email, "معلم یک");
        MockHttpSession teacher2 = registerAndLogin(teacher2Email, "معلم دو");
        long teacher1Id = addTeacher(admin, school, teacher1Email, "معلم یک");
        long teacher2Id = addTeacher(admin, school, teacher2Email, "معلم دو");

        long classA = createClass(admin, school, "پایه پنجم الف", teacher1Id);
        long classB = createClass(admin, school, "پایه پنجم ب", teacher2Id);
        long classC = createClass(admin, school, "پایه پنجم ج", null);
        long s1 = enroll(admin, school, unique("att.s1"), "علی رضایی", classA);
        long s2 = enroll(admin, school, unique("att.s2"), "محمد احمدی", classA);
        long s3 = enroll(admin, school, unique("att.s3"), "رضا علوی", classA);
        long s4 = enroll(admin, school, unique("att.s4"), "سارا کریمی", classB);
        long s5 = enroll(admin, school, unique("att.s5"), "نگار حسینی", classB);
        return new Fixture(school, weekStart, admin, teacher1, teacher2, classA, classB, classC, s1, s2, s3, s4, s5);
    }

    private static String attendance(Fixture f) {
        return "/api/admin/schools/" + f.school() + "/attendance";
    }

    private static String mark(long studentId, String status, boolean excused, String note) {
        return "{\"studentId\":" + studentId + ",\"status\":\"" + status + "\",\"excused\":" + excused
                + ",\"note\":" + (note == null ? "null" : "\"" + note + "\"") + "}";
    }

    private static String marks(String... items) {
        return "{\"marks\":[" + String.join(",", items) + "]}";
    }

    private ResultActions putRoll(Fixture f, MockHttpSession who, long classId, LocalDate day, String body)
            throws Exception {
        return send(who, f.school(), put(attendance(f) + "/classes/" + classId + "/days/" + day), body);
    }

    private ResultActions getClassDay(Fixture f, MockHttpSession who, long classId, LocalDate day) throws Exception {
        return send(who, f.school(), get(attendance(f) + "/classes/" + classId + "/days/" + day), null);
    }

    private static JsonNode student(JsonNode classDay, long studentId) {
        for (JsonNode node : classDay.get("students")) {
            if (node.get("studentId").asLong() == studentId) {
                return node;
            }
        }
        throw new AssertionError("دانش‌آموز " + studentId + " در فهرستِ کلاس نیست");
    }

    private long count(String table, long classId) {
        return jdbc.queryForObject("select count(*) from attendance." + table + " where class_id = ?", Long.class, classId);
    }

    // ---- ثبت، بازکردن، جایگزینی ----

    @Test
    void aTeacherRecordsTheRollReopensItAndSavingAgainReplacesAndIsIdempotent() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();

        // پیش از ثبت: همه‌ی دانش‌آموزان در فهرست‌اند، ولی «ثبت نشده»
        JsonNode before = json(getClassDay(f, f.teacher1(), f.classA(), today).andExpect(status().isOk()));
        assertThat(before.get("taken").asBoolean()).isFalse();
        assertThat(before.get("recordable").asBoolean()).isTrue();
        assertThat(before.get("students")).hasSize(3);

        // ثبت: یکی غایبِ موجه با یادداشت، یکی دیرآمده (موجه برای دیرآمده بی‌اثر است)
        putRoll(f, f.teacher1(), f.classA(), today,
                marks(mark(f.s1(), "ABSENT", true, "تب"), mark(f.s2(), "LATE", true, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(true));

        JsonNode reopened = json(getClassDay(f, f.teacher1(), f.classA(), today).andExpect(status().isOk()));
        assertThat(reopened.get("taken").asBoolean()).isTrue();
        assertThat(student(reopened, f.s1()).get("status").asText()).isEqualTo("ABSENT");
        assertThat(student(reopened, f.s1()).get("excused").asBoolean()).isTrue();
        assertThat(student(reopened, f.s1()).get("note").asText()).isEqualTo("تب");
        assertThat(student(reopened, f.s1()).get("studentName").asText()).isEqualTo("علی رضایی");
        assertThat(student(reopened, f.s2()).get("status").asText()).isEqualTo("LATE");
        assertThat(student(reopened, f.s2()).get("excused").asBoolean()).isFalse();
        assertThat(student(reopened, f.s3()).get("status").isNull()).isTrue();

        // ذخیره‌ی دوباره با مُهرِ کمتر: مُهرِ حذف‌شده برمی‌گردد به «حاضر» و یک مُهرِ تازه می‌آید
        String second = marks(mark(f.s3(), "ABSENT", false, null));
        putRoll(f, f.teacher1(), f.classA(), today, second).andExpect(status().isOk());
        assertThat(count("marks", f.classA())).isEqualTo(1);
        assertThat(count("rolls", f.classA())).isEqualTo(1);
        JsonNode replaced = json(getClassDay(f, f.teacher1(), f.classA(), today));
        assertThat(student(replaced, f.s1()).get("status").isNull()).isTrue();
        assertThat(student(replaced, f.s3()).get("status").asText()).isEqualTo("ABSENT");

        // همان بدنه دوباره: هیچ‌چیز زیاد نمی‌شود
        putRoll(f, f.teacher1(), f.classA(), today, second).andExpect(status().isOk());
        assertThat(count("marks", f.classA())).isEqualTo(1);
        assertThat(count("rolls", f.classA())).isEqualTo(1);

        // فهرستِ خالی = همه حاضر، و حضور و غیاب همچنان «گرفته‌شده» است
        putRoll(f, f.teacher1(), f.classA(), today, marks())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(true));
        assertThat(count("marks", f.classA())).isZero();
        assertThat(count("rolls", f.classA())).isEqualTo(1);
    }

    // ---- ۴۰۰ / ۴۰۹ ----

    @Test
    void invalidDaysMarksAndStudentsAreRejected() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();
        String ok = marks(mark(f.s1(), "ABSENT", false, null));

        // روزِ غیردرسی و روزِ آینده (مدیر، تا مجوزِ «روزِ گذشته» مانعِ دیگری نباشد): ۴۰۰
        putRoll(f, f.admin(), f.classA(), nonSchoolDay(f.weekStart()), ok)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-day")));
        putRoll(f, f.admin(), f.classA(), today.plusDays(1), ok)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-day")));

        // روزِ تعطیلِ تقویم
        LocalDate closed = pastSchoolDay(f.weekStart());
        send(f.admin(), f.school(), post("/api/admin/schools/" + f.school() + "/calendar"),
                "{\"day\":\"" + closed + "\",\"kind\":\"CLOSED\",\"title\":\"تعطیلی آزمایشی\"}")
                .andExpect(status().isOk());
        putRoll(f, f.admin(), f.classA(), closed, ok)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-day")));

        // دانش‌آموزِ کلاسِ دیگر: ۴۰۹
        putRoll(f, f.teacher1(), f.classA(), today, marks(mark(f.s4(), "ABSENT", false, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("student-not-in-class")));
        assertThat(count("rolls", f.classA())).isZero();

        // وضعیتِ ناشناخته، تکراری، یادداشتِ بلند، بدنه‌ی بدونِ فهرست: ۴۰۰
        putRoll(f, f.teacher1(), f.classA(), today, marks(mark(f.s1(), "PRESENT", false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-mark")));
        putRoll(f, f.teacher1(), f.classA(), today,
                marks(mark(f.s1(), "ABSENT", false, null), mark(f.s1(), "LATE", false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-mark")));
        putRoll(f, f.teacher1(), f.classA(), today, marks(mark(f.s1(), "ABSENT", false, "x".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-mark")));
        putRoll(f, f.teacher1(), f.classA(), today, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-mark")));
        // هیچ‌کدام چیزی ننوشت
        assertThat(count("rolls", f.classA())).isZero();
        assertThat(count("marks", f.classA())).isZero();
    }

    // ---- ۴۰۳: معلم در برابر مدیر ----

    @Test
    void aTeacherRecordsOnlyForOwnClassAndOnlyForTodayWhileTheAdminMayDoBoth() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();
        LocalDate past = pastSchoolDay(f.weekStart());
        String body = marks(mark(f.s1(), "ABSENT", false, null));

        // کلاسِ معلمِ دیگر: ۴۰۳ (نوشتن و خواندن)
        putRoll(f, f.teacher2(), f.classA(), today, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("attendance-not-your-class")));
        getClassDay(f, f.teacher2(), f.classA(), today)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("attendance-not-your-class")));
        assertThat(count("rolls", f.classA())).isZero();

        // روزِ گذشته بدونِ ATTENDANCE_EDIT_PAST: ۴۰۳
        putRoll(f, f.teacher1(), f.classA(), past, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("attendance-past-edit-not-allowed")));
        assertThat(count("rolls", f.classA())).isZero();

        // مدیر (ATTENDANCE_RECORD_ANY + ATTENDANCE_EDIT_PAST): هر کلاس، روزِ گذشته
        putRoll(f, f.admin(), f.classA(), past, body).andExpect(status().isOk());
        putRoll(f, f.admin(), f.classB(), today, marks()).andExpect(status().isOk());
        assertThat(count("rolls", f.classA())).isEqualTo(1);
        assertThat(count("rolls", f.classB())).isEqualTo(1);
    }

    @Test
    void peopleWithoutAnAttendancePermissionAreDeniedAndTeachersCannotSeeTheSchoolWideViews() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();

        // دانش‌آموز: نه ATTENDANCE_RECORD و نه ATTENDANCE_RECORD_ANY
        String studentEmail = unique("att.pupil");
        MockHttpSession pupil = registerAndLogin(studentEmail, "دانش‌آموز بی‌مجوز");
        enroll(f.admin(), f.school(), studentEmail, "دانش‌آموز بی‌مجوز", f.classA());
        getClassDay(f, pupil, f.classA(), today)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        putRoll(f, pupil, f.classA(), today, marks()).andExpect(status().isForbidden());
        send(pupil, f.school(), get(attendance(f) + "/classes"), null).andExpect(status().isForbidden());

        // معلم ATTENDANCE_VIEW_ALL ندارد: غایبین، رقم و گزارش ۴۰۳
        send(f.teacher1(), f.school(), get(attendance(f) + "/days/" + today + "/absentees"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        send(f.teacher1(), f.school(), get(attendance(f) + "/days/" + today + "/summary"), null)
                .andExpect(status().isForbidden());
        send(f.teacher1(), f.school(), get(attendance(f) + "/report").param("from", today.toString())
                .param("to", today.toString()), null)
                .andExpect(status().isForbidden());
    }

    // ---- جداییِ مدرسه‌ها ----

    @Test
    void anotherSchoolsAdminNeverReachesThisSchoolsClasses() throws Exception {
        Fixture f = fixture();
        Fixture other = fixture();
        LocalDate today = today();

        // مدیرِ مدرسه‌ی دیگر با مسیر و هدرِ مدرسه‌ی خودش و شناسه‌ی کلاسِ ما: «کلاس یافت نشد»
        send(other.admin(), other.school(),
                put(attendance(other) + "/classes/" + f.classA() + "/days/" + today),
                marks(mark(f.s1(), "ABSENT", false, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("class-not-found")));
        send(other.admin(), other.school(),
                get(attendance(other) + "/classes/" + f.classA() + "/days/" + today), null)
                .andExpect(status().isNotFound());
        assertThat(count("rolls", f.classA())).isZero();

        // هدرِ مدرسه‌ی خودش با مسیرِ مدرسه‌ی ما: رد می‌شود
        send(other.admin(), other.school(),
                get(attendance(f) + "/classes/" + f.classA() + "/days/" + today), null)
                .andExpect(status().is4xxClientError());

        // و غایبینِ هر مدرسه فقط کلاس‌های خودش را دارد
        putRoll(f, f.admin(), f.classA(), today, marks(mark(f.s1(), "ABSENT", false, null))).andExpect(status().isOk());
        JsonNode otherAbsentees = json(send(other.admin(), other.school(),
                get(attendance(other) + "/days/" + today + "/absentees"), null).andExpect(status().isOk()));
        for (JsonNode cls : otherAbsentees) {
            assertThat(cls.get("classId").asLong()).isNotEqualTo(f.classA());
            assertThat(cls.get("taken").asBoolean()).isFalse();
        }
    }

    // ---- انتخابگرِ کلاس ----

    @Test
    void theClassPickerShowsATeachersOwnClassesAndAnAdminsAllWithTheTakenFlag() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();

        JsonNode mine = json(send(f.teacher1(), f.school(), get(attendance(f) + "/classes").param("day", today.toString()), null)
                .andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("classId").asLong()).isEqualTo(f.classA());
        assertThat(mine.get(0).get("taken").asBoolean()).isFalse();

        putRoll(f, f.teacher1(), f.classA(), today, marks()).andExpect(status().isOk());
        JsonNode afterwards = json(send(f.teacher1(), f.school(), get(attendance(f) + "/classes"), null)
                .andExpect(status().isOk()));
        assertThat(afterwards.get(0).get("taken").asBoolean()).isTrue();

        JsonNode all = json(send(f.admin(), f.school(), get(attendance(f) + "/classes"), null).andExpect(status().isOk()));
        assertThat(all).hasSize(3);
    }

    // ---- غایبین، رقمِ مدیر و گزارش ----

    @Test
    void absenteesSummaryAndReportFollowTheRecordedRolls() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();

        // کلاس الف: ۱ غایبِ موجه + ۱ دیرآمده از ۳ نفر؛ کلاس ب: ۱ غایب از ۲ نفر؛ کلاس ج: ثبت نشده
        putRoll(f, f.teacher1(), f.classA(), today,
                marks(mark(f.s1(), "ABSENT", true, null), mark(f.s2(), "LATE", false, null)))
                .andExpect(status().isOk());
        putRoll(f, f.teacher2(), f.classB(), today, marks(mark(f.s4(), "ABSENT", false, "بیمار")))
                .andExpect(status().isOk());

        JsonNode absentees = json(send(f.admin(), f.school(), get(attendance(f) + "/days/" + today + "/absentees"), null)
                .andExpect(status().isOk()));
        assertThat(absentees).hasSize(3);
        JsonNode a = byClass(absentees, f.classA());
        assertThat(a.get("taken").asBoolean()).isTrue();
        assertThat(a.get("expected").asInt()).isEqualTo(3);
        assertThat(a.get("absent").asInt()).isEqualTo(1);
        assertThat(a.get("late").asInt()).isEqualTo(1);
        assertThat(a.get("excusedAbsent").asInt()).isEqualTo(1);
        assertThat(a.get("students")).hasSize(2);
        assertThat(byClass(absentees, f.classB()).get("students").get(0).get("studentName").asText()).isEqualTo("سارا کریمی");
        JsonNode c = byClass(absentees, f.classC());
        assertThat(c.get("taken").asBoolean()).isFalse();
        assertThat(c.get("students")).isEmpty();

        // رقم: فقط الف و ب (۵ نفر)، ۲ غایب (دیرآمده حاضر است) → ۶۰٪؛ ج در درصد نیست
        send(f.admin(), f.school(), get(attendance(f) + "/days/" + today + "/summary"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolDay").value(true))
                .andExpect(jsonPath("$.absent").value(2))
                .andExpect(jsonPath("$.excusedAbsent").value(1))
                .andExpect(jsonPath("$.expected").value(5))
                .andExpect(jsonPath("$.presentPercent").value(60))
                .andExpect(jsonPath("$.classesTaken").value(2))
                .andExpect(jsonPath("$.classesTotal").value(3));

        // روزِ غیردرسی: رقمی نیست و فهرستِ غایبین خالی
        send(f.admin(), f.school(), get(attendance(f) + "/days/" + nonSchoolDay(f.weekStart()) + "/summary"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolDay").value(false))
                .andExpect(jsonPath("$.presentPercent").doesNotExist());
        send(f.admin(), f.school(), get(attendance(f) + "/days/" + nonSchoolDay(f.weekStart()) + "/absentees"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // گزارش: برای هر دانش‌آموزِ کلاس‌های ثبت‌شده؛ مبنا یک روز
        JsonNode report = json(send(f.admin(), f.school(), get(attendance(f) + "/report")
                .param("from", today.toString()).param("to", today.toString()), null).andExpect(status().isOk()));
        assertThat(report).hasSize(5);
        assertThat(row(report, f.s1()).get("absentDays").asInt()).isEqualTo(1);
        assertThat(row(report, f.s1()).get("presentDays").asInt()).isZero();
        assertThat(row(report, f.s2()).get("lateDays").asInt()).isEqualTo(1);
        assertThat(row(report, f.s2()).get("presentDays").asInt()).isEqualTo(1);
        assertThat(row(report, f.s3()).get("presentDays").asInt()).isEqualTo(1);
        assertThat(row(report, f.s4()).get("className").asText()).isEqualTo("پایه پنجم ب");

        // فیلترِ نام (شاملِ حروف) و کلاس
        JsonNode byName = json(send(f.admin(), f.school(), get(attendance(f) + "/report")
                .param("from", today.toString()).param("to", today.toString()).param("name", "رضا"), null)
                .andExpect(status().isOk()));
        assertThat(byName).hasSize(2); // «علی رضایی» و «رضا علوی»
        JsonNode byClass = json(send(f.admin(), f.school(), get(attendance(f) + "/report")
                .param("from", today.toString()).param("to", today.toString())
                .param("classId", String.valueOf(f.classB())), null).andExpect(status().isOk()));
        assertThat(byClass).hasSize(2);

        // بازه‌ای که هیچ حضور و غیابی در آن نیست: خالی
        send(f.admin(), f.school(), get(attendance(f) + "/report")
                .param("from", today.minusDays(400).toString()).param("to", today.minusDays(300).toString()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theReportRangeIsRequiredOrderedAndCapped() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();

        send(f.admin(), f.school(), get(attendance(f) + "/report").param("to", today.toString()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
        send(f.admin(), f.school(), get(attendance(f) + "/report")
                .param("from", today.toString()).param("to", today.minusDays(1).toString()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
        send(f.admin(), f.school(), get(attendance(f) + "/report")
                .param("from", today.minusDays(366).toString()).param("to", today.toString()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
        send(f.admin(), f.school(), get(attendance(f) + "/report")
                .param("from", today.minusDays(365).toString()).param("to", today.toString()), null)
                .andExpect(status().isOk());
    }

    private static JsonNode byClass(JsonNode list, long classId) {
        for (JsonNode node : list) {
            if (node.get("classId").asLong() == classId) {
                return node;
            }
        }
        throw new AssertionError("کلاس " + classId + " در فهرست نیست");
    }

    private static JsonNode row(JsonNode report, long studentId) {
        for (JsonNode node : report) {
            if (node.get("studentId").asLong() == studentId) {
                return node;
            }
        }
        throw new AssertionError("دانش‌آموز " + studentId + " در گزارش نیست");
    }

    // ---- ماژول رایگان: مدرسه‌ی تازه، backfill و ردِ ماژول ----

    private long attendanceRows(long schoolId) {
        return jdbc.queryForObject("select count(*) from iam.school_entitlements e join iam.modules m on m.id = e.module_id "
                + "where e.school_id = ? and m.code = 'ATTENDANCE'", Long.class, schoolId);
    }

    @Test
    void aFreshSchoolGetsAttendanceForFreeAndBackfillIsIdempotentAndRespectsASwitchOff() throws Exception {
        Fixture f = fixture();
        assertThat(attendanceRows(f.school())).isEqualTo(1);
        getClassDay(f, f.teacher1(), f.classA(), today()).andExpect(status().isOk());

        // مدرسه‌ای که قبل از رایگان‌شدن ساخته شده ردیف ندارد: backfill یک‌بار می‌سازد و بعد دست نمی‌زند
        jdbc.update("delete from iam.school_entitlements where school_id = ? and module_id = "
                + "(select id from iam.modules where code = 'ATTENDANCE')", f.school());
        assertThat(attendanceRows(f.school())).isZero();
        getClassDay(f, f.teacher1(), f.classA(), today()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("module-not-entitled")));

        IamFacade.ModuleCode attendance = new IamFacade.ModuleCode("ATTENDANCE");
        assertThat(iamFacade.ensureSchoolEntitlement(f.school(), attendance)).isTrue();
        assertThat(iamFacade.ensureSchoolEntitlement(f.school(), attendance)).isFalse();
        assertThat(attendanceRows(f.school())).isEqualTo(1);
        getClassDay(f, f.teacher1(), f.classA(), today()).andExpect(status().isOk());

        // مدیری که ماژول را خاموش کرده با backfill دوباره روشن نمی‌شود
        iamFacade.setSchoolEntitlement(f.school(), attendance, false);
        assertThat(iamFacade.ensureSchoolEntitlement(f.school(), attendance)).isFalse();
        getClassDay(f, f.teacher1(), f.classA(), today()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("module-not-entitled")));
    }
}
