package com.example.roshdeandishe;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * گزارش‌های حضور و غیاب (openspec/changes/attendance-reports.md) روی Postgres واقعی: درصد، گزارشِ کلاس، گزارشِ
 * معلم (معلمِ کلاس و معلمِ برنامه، کلاسِ چندمعلمی بدون یکی‌شدنِ جمع‌ها) و برگه‌ی دانش‌آموز.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AttendanceReportsFlowIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tehran");
    private static final String YEAR = "1405-1406";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /** امروز و دو روزِ درسیِ پیشین، به ترتیبِ قدیم به جدید. */
    private static List<LocalDate> threeSchoolDays(WeekStart weekStart) {
        List<LocalDate> days = new ArrayList<>();
        days.add(today());
        LocalDate d = today();
        while (days.size() < 3) {
            d = d.minusDays(1);
            if (weekStart.schoolDays().contains(d.getDayOfWeek())) {
                days.add(0, d);
            }
        }
        return days;
    }

    private record Fixture(long school, WeekStart weekStart, MockHttpSession admin, MockHttpSession teacher1,
                           long teacher1Id, long teacher2Id, long teacher3Id, long teacher4Id,
                           long classA, long classB, long classC, long classD,
                           long s1, long s2, long s3, long s4, long s5,
                           LocalDate older, LocalDate mid, LocalDate today) {
    }

    private long addTeacherWithLogin(MockHttpSession admin, long school, String prefix, String name) throws Exception {
        String email = unique(prefix);
        registerAndLogin(email, name);
        return addTeacher(admin, school, email, name);
    }

    private long insertActivity(long school) {
        return jdbc.queryForObject("insert into academic.activities (school_id, name) values (?, ?) returning id",
                Long.class, school, "فعالیت " + UUID.randomUUID());
    }

    private long insertPeriod(long school) {
        return jdbc.queryForObject("insert into academic.timetable_periods (school_id, academic_year, position, label, "
                + "start_time, end_time) values (?, ?, 1, 'زنگ اول', '08:00', '08:45') returning id", Long.class, school, YEAR);
    }

    private void insertEntry(long school, long classId, long periodId, String weekday, long activityId, long teacherId) {
        jdbc.update("insert into academic.timetable_entries (school_id, class_id, period_id, weekday, activity_id, "
                + "teacher_user_id) values (?, ?, ?, ?, ?, ?)", school, classId, periodId, weekday, activityId, teacherId);
    }

    /**
     * کلاس الف (معلم ۱، ۳ نفر)، ب (معلم ۲، ۲ نفر)، ج (بی‌معلم و بی‌دانش‌آموز، فقط معلمِ برنامه)، د (معلم ۴، بدون roll).
     * سه روز: الف: s1 غایب (میانی) و غایبِ موجه با یادداشت (امروز)، s2 دیرآمده (امروز)؛ ب: s4 غایب (امروز)؛ ج: roll امروز.
     * برنامه: الف ← معلم ۲، ب ← معلمِ ناشناخته، ج ← معلم ۳.
     */
    private Fixture fixture() throws Exception {
        WeekStart weekStart = weekStartCoveringToday();
        MockHttpSession admin = registerAndLogin(unique("rep.founder"), "مدیر گزارش");
        long school = createSchool(admin, weekStart);
        String t1Email = unique("rep.t1");
        MockHttpSession teacher1 = registerAndLogin(t1Email, "معلم یک");
        long t1 = addTeacher(admin, school, t1Email, "معلم یک");
        long t2 = addTeacherWithLogin(admin, school, "rep.t2", "معلم دو");
        long t3 = addTeacherWithLogin(admin, school, "rep.t3", "معلم سه");
        long t4 = addTeacherWithLogin(admin, school, "rep.t4", "معلم چهار");

        long a = createClass(admin, school, "پایه پنجم الف", t1);
        long b = createClass(admin, school, "پایه پنجم ب", t2);
        long c = createClass(admin, school, "پایه پنجم ج", null);
        long d = createClass(admin, school, "پایه پنجم د", t4);
        jdbc.update("update academic.classes set academic_year = ? where school_id = ?", YEAR, school);
        long s1 = enroll(admin, school, unique("rep.s1"), "علی رضایی", a);
        long s2 = enroll(admin, school, unique("rep.s2"), "محمد احمدی", a);
        long s3 = enroll(admin, school, unique("rep.s3"), "رضا علوی", a);
        long s4 = enroll(admin, school, unique("rep.s4"), "سارا کریمی", b);
        long s5 = enroll(admin, school, unique("rep.s5"), "نگار حسینی", b);

        long period = insertPeriod(school);
        long activity = insertActivity(school);
        insertEntry(school, a, period, "SAT", activity, t2);
        insertEntry(school, b, period, "SUN", activity, 987654321L);
        insertEntry(school, c, period, "MON", activity, t3);

        List<LocalDate> days = threeSchoolDays(weekStart);
        Fixture f = new Fixture(school, weekStart, admin, teacher1, t1, t2, t3, t4, a, b, c, d,
                s1, s2, s3, s4, s5, days.get(0), days.get(1), days.get(2));
        putRoll(f, a, f.older(), marks());
        putRoll(f, a, f.mid(), marks(mark(s1, "ABSENT", false, null)));
        putRoll(f, a, f.today(), marks(mark(s1, "ABSENT", true, "تب"), mark(s2, "LATE", true, null)));
        putRoll(f, b, f.older(), marks());
        putRoll(f, b, f.mid(), marks());
        putRoll(f, b, f.today(), marks(mark(s4, "ABSENT", false, null)));
        putRoll(f, c, f.today(), marks());
        return f;
    }

    private void putRoll(Fixture f, long classId, LocalDate day, String body) throws Exception {
        send(f.admin(), f.school(), put(attendance(f) + "/classes/" + classId + "/days/" + day), body)
                .andExpect(status().isOk());
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

    private JsonNode report(Fixture f, String path, MockHttpSession who) throws Exception {
        return json(send(who, f.school(), get(attendance(f) + path), null).andExpect(status().isOk()));
    }

    private String range(Fixture f) {
        return "from=" + f.older() + "&to=" + f.today();
    }

    private static JsonNode find(JsonNode list, String field, long id) {
        for (JsonNode node : list) {
            if (node.get(field).asLong() == id) {
                return node;
            }
        }
        throw new AssertionError(field + "=" + id + " در فهرست نیست");
    }

    // ---- گزارشِ دانش‌آموز: درصد ----

    @Test
    void theStudentReportCarriesAFlooredPercent() throws Exception {
        Fixture f = fixture();
        JsonNode rows = report(f, "/report?" + range(f), f.admin());

        // s1: ۳ روز، ۲ غایب → ۱ حاضر → ۳۳٪؛ s2: ۲ ... دیرآمده حاضر است → ۱۰۰٪؛ s4: ۳ روز، ۱ غایب → ۶۶٪
        assertThat(find(rows, "studentId", f.s1()).get("presentPercent").asInt()).isEqualTo(33);
        assertThat(find(rows, "studentId", f.s2()).get("presentPercent").asInt()).isEqualTo(100);
        assertThat(find(rows, "studentId", f.s4()).get("presentPercent").asInt()).isEqualTo(66);
        assertThat(find(rows, "studentId", f.s5()).get("presentPercent").asInt()).isEqualTo(100);
    }

    // ---- گزارشِ کلاس ----

    @Test
    void theClassReportTotalsAndSortsClassesAndOmitsClassesWithoutARoll() throws Exception {
        Fixture f = fixture();
        JsonNode rows = report(f, "/report/classes?" + range(f), f.admin());

        // الف، ب، ج به ترتیبِ نام؛ د (بدون roll) نیست
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get("classId").asLong()).isEqualTo(f.classA());
        assertThat(rows.get(1).get("classId").asLong()).isEqualTo(f.classB());
        assertThat(rows.get(2).get("classId").asLong()).isEqualTo(f.classC());
        JsonNode a = rows.get(0);
        assertThat(a.get("className").asText()).isEqualTo("پایه پنجم الف");
        assertThat(a.get("daysTaken").asInt()).isEqualTo(3);
        assertThat(a.get("absentDays").asInt()).isEqualTo(2);
        assertThat(a.get("lateDays").asInt()).isEqualTo(1);
        assertThat(a.get("presentDays").asInt()).isEqualTo(7);      // ۹ − ۲
        assertThat(a.get("presentPercent").asInt()).isEqualTo(77);  // ۷۰۰/۹
        JsonNode b = rows.get(1);
        assertThat(b.get("daysTaken").asInt()).isEqualTo(3);
        assertThat(b.get("absentDays").asInt()).isEqualTo(1);
        assertThat(b.get("presentDays").asInt()).isEqualTo(5);
        assertThat(b.get("presentPercent").asInt()).isEqualTo(83);
        JsonNode c = rows.get(2);
        assertThat(c.get("daysTaken").asInt()).isEqualTo(1);
        assertThat(c.get("presentDays").asInt()).isZero();
        assertThat(c.get("presentPercent").isNull()).isTrue();

        // فقط امروز: الف یک روز، ۱ غایب و ۱ دیرآمده از ۳
        JsonNode todayOnly = report(f, "/report/classes?from=" + f.today() + "&to=" + f.today(), f.admin());
        assertThat(find(todayOnly, "classId", f.classA()).get("presentPercent").asInt()).isEqualTo(66);

        // فیلترِ کلاس؛ کلاسِ ناشناخته یا کلاسِ مدرسه‌ی دیگر: خالی، نه خطا
        JsonNode one = report(f, "/report/classes?" + range(f) + "&classId=" + f.classB(), f.admin());
        assertThat(one).hasSize(1);
        assertThat(report(f, "/report/classes?" + range(f) + "&classId=987654321", f.admin())).isEmpty();
        Fixture other = fixture();
        assertThat(report(f, "/report/classes?" + range(f) + "&classId=" + other.classA(), f.admin())).isEmpty();

        // بازه‌ای بدون roll: خالی
        assertThat(report(f, "/report/classes?from=" + f.today().minusDays(400) + "&to=" + f.today().minusDays(300),
                f.admin())).isEmpty();
    }

    // ---- گزارشِ معلم ----

    @Test
    void theTeacherReportListsClassTeachersAndTimetableTeachersWithoutDeduplicatingTotals() throws Exception {
        Fixture f = fixture();
        JsonNode rows = report(f, "/report/teachers?" + range(f), f.admin());

        // معلم ۱ (کلاسِ الف)، معلم ۲ (ب + برنامه‌ی الف)، معلم ۳ (فقط برنامه‌ی ج)، معلمِ ناشناخته (برنامه‌ی ب)؛ معلم ۴ نه
        assertThat(rows).hasSize(4);
        for (JsonNode r : rows) {
            assertThat(r.get("teacherUserId").asLong()).isNotEqualTo(f.teacher4Id());
        }
        // ناشناخته آخر (نام تهی)
        assertThat(rows.get(3).get("teacherUserId").asLong()).isEqualTo(987654321L);
        assertThat(rows.get(3).get("teacherName").isNull()).isTrue();

        JsonNode t1 = find(rows, "teacherUserId", f.teacher1Id());
        assertThat(t1.get("teacherName").asText()).isEqualTo("معلم یک");
        assertThat(t1.get("classCount").asInt()).isEqualTo(1);
        assertThat(t1.get("daysTaken").asInt()).isEqualTo(3);
        assertThat(t1.get("absentDays").asInt()).isEqualTo(2);
        assertThat(t1.get("lateDays").asInt()).isEqualTo(1);
        assertThat(t1.get("presentDays").asInt()).isEqualTo(7);
        assertThat(t1.get("presentPercent").asInt()).isEqualTo(77);
        assertThat(t1.get("classes")).hasSize(1);

        // معلم ۲ هر دو کلاس را دارد؛ الف همزمان زیرِ معلم ۱ هم هست و جمع‌ها یکی نمی‌شوند
        JsonNode t2 = find(rows, "teacherUserId", f.teacher2Id());
        assertThat(t2.get("classCount").asInt()).isEqualTo(2);
        assertThat(t2.get("daysTaken").asInt()).isEqualTo(6);
        assertThat(t2.get("absentDays").asInt()).isEqualTo(3);
        assertThat(t2.get("lateDays").asInt()).isEqualTo(1);
        assertThat(t2.get("presentDays").asInt()).isEqualTo(12);
        assertThat(t2.get("presentPercent").asInt()).isEqualTo(80);
        assertThat(t2.get("classes").get(0).get("classId").asLong()).isEqualTo(f.classA());
        assertThat(t2.get("classes").get(1).get("classId").asLong()).isEqualTo(f.classB());

        JsonNode t3 = find(rows, "teacherUserId", f.teacher3Id());
        assertThat(t3.get("classCount").asInt()).isEqualTo(1);
        assertThat(t3.get("daysTaken").asInt()).isEqualTo(1);
        assertThat(t3.get("presentPercent").isNull()).isTrue();

        JsonNode unknown = find(rows, "teacherUserId", 987654321L);
        assertThat(unknown.get("classes")).hasSize(1);
        assertThat(unknown.get("classes").get(0).get("classId").asLong()).isEqualTo(f.classB());

        // بازه‌ای بدون roll: خالی
        assertThat(report(f, "/report/teachers?from=" + f.today().minusDays(400) + "&to=" + f.today().minusDays(300),
                f.admin())).isEmpty();
    }

    // ---- برگه‌ی دانش‌آموز ----

    @Test
    void theStudentSheetListsRowsAbsencesWithExcuseAndNotesAndOnlyRollsOfTheStudentsRoster() throws Exception {
        Fixture f = fixture();
        JsonNode s1 = report(f, "/report/students/" + f.s1() + "?" + range(f), f.admin());

        assertThat(s1.get("studentName").asText()).isEqualTo("علی رضایی");
        assertThat(s1.get("from").asText()).isEqualTo(f.older().toString());
        assertThat(s1.get("to").asText()).isEqualTo(f.today().toString());
        assertThat(s1.get("presentPercent").asInt()).isEqualTo(33);
        assertThat(s1.get("rows")).hasSize(1);
        assertThat(s1.get("rows").get(0).get("className").asText()).isEqualTo("پایه پنجم الف");
        assertThat(s1.get("rows").get(0).get("absentDays").asInt()).isEqualTo(2);
        JsonNode days = s1.get("days");
        assertThat(days).hasSize(2);
        assertThat(days.get(0).get("day").asText()).isEqualTo(f.mid().toString());
        assertThat(days.get(0).get("status").asText()).isEqualTo("ABSENT");
        assertThat(days.get(0).get("excused").asBoolean()).isFalse();
        assertThat(days.get(0).get("note").isNull()).isTrue();
        assertThat(days.get(1).get("day").asText()).isEqualTo(f.today().toString());
        assertThat(days.get(1).get("classId").asLong()).isEqualTo(f.classA());
        assertThat(days.get(1).get("excused").asBoolean()).isTrue();
        assertThat(days.get(1).get("note").asText()).isEqualTo("تب");

        JsonNode s2 = report(f, "/report/students/" + f.s2() + "?" + range(f), f.admin());
        assertThat(s2.get("presentPercent").asInt()).isEqualTo(100);
        assertThat(s2.get("days")).hasSize(1);
        assertThat(s2.get("days").get(0).get("status").asText()).isEqualTo("LATE");
        assertThat(s2.get("days").get(0).get("excused").asBoolean()).isFalse();

        // دانش‌آموزِ بدون غیبت: فهرستِ روزها خالی، ۱۰۰٪
        JsonNode s3 = report(f, "/report/students/" + f.s3() + "?" + range(f), f.admin());
        assertThat(s3.get("days")).isEmpty();
        assertThat(s3.get("presentPercent").asInt()).isEqualTo(100);

        // ثبت‌نامِ پس از roll‌ها: نه ردیف، نه درصد، نه روزی؛ و جمعِ کلاس تغییر نمی‌کند
        long late = enroll(f.admin(), f.school(), unique("rep.late"), "ثبت‌نامِ دیرهنگام", f.classA());
        JsonNode sheet = report(f, "/report/students/" + late + "?" + range(f), f.admin());
        assertThat(sheet.get("rows")).isEmpty();
        assertThat(sheet.get("days")).isEmpty();
        assertThat(sheet.get("presentPercent").isNull()).isTrue();
        assertThat(find(report(f, "/report/classes?" + range(f), f.admin()), "classId", f.classA())
                .get("presentDays").asInt()).isEqualTo(7);
    }

    @Test
    void aStudentOfAnotherSchoolOrUnknownIsNotFound() throws Exception {
        Fixture f = fixture();
        Fixture other = fixture();

        send(f.admin(), f.school(), get(attendance(f) + "/report/students/" + other.s1() + "?" + range(f)), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("student-not-found")));
        send(f.admin(), f.school(), get(attendance(f) + "/report/students/987654321?" + range(f)), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("student-not-found")));
    }

    // ---- بازه و مجوز ----

    @Test
    void theRangeIsRequiredOrderedAndCappedOnAllThreeReports() throws Exception {
        Fixture f = fixture();
        LocalDate today = f.today();
        for (String path : List.of("/report/classes", "/report/teachers", "/report/students/" + f.s1())) {
            String url = attendance(f) + path;
            send(f.admin(), f.school(), get(url).param("to", today.toString()), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
            send(f.admin(), f.school(), get(url).param("from", today.toString())
                    .param("to", today.minusDays(1).toString()), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
            send(f.admin(), f.school(), get(url).param("from", today.minusDays(366).toString())
                    .param("to", today.toString()), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
            send(f.admin(), f.school(), get(url).param("from", today.minusDays(365).toString())
                    .param("to", today.toString()), null)
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aTeacherWithOnlyRecordPermissionIsForbiddenOnAllThreeReports() throws Exception {
        Fixture f = fixture();
        for (String path : List.of("/report/classes", "/report/teachers", "/report/students/" + f.s1())) {
            send(f.teacher1(), f.school(), get(attendance(f) + path + "?" + range(f)), null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        }
    }

    @Test
    void aSchoolWithoutTheModuleIsDeniedLikeTheOtherReport() throws Exception {
        Fixture f = fixture();
        jdbc.update("delete from iam.school_entitlements where school_id = ? and module_id = "
                + "(select id from iam.modules where code = 'ATTENDANCE')", f.school());
        for (String path : List.of("/report", "/report/classes", "/report/teachers", "/report/students/" + f.s1())) {
            send(f.admin(), f.school(), get(attendance(f) + path + "?" + range(f)), null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("module-not-entitled")));
        }
    }
}
