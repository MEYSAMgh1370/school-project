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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * عذرهای حضور و غیاب (openspec/changes/attendance.md، «Excuses») روی Postgres واقعی: ثبتِ ولی، جایگزینیِ
 * یادداشتِ PENDING، ۴۰۹ پس از تصمیم، تأیید/ردّ توسط مدیر و «موجه»شدنِ مُهر، خواندنِ «من»، ولیِ بیگانه، و
 * ماندنِ عذر پس از ذخیره‌ی دوباره‌ی حضور و غیاب. هر تست مدرسه‌ی خودش را می‌سازد؛ روزِ آزمون «امروز» است و
 * الگوی هفته طوری انتخاب می‌شود که امروز روزِ درسی باشد (مثل AttendanceFlowIntegrationTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AttendanceExcusesFlowIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tehran");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

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

    /** درخواستِ مدیریتی با هدر مدرسه. */
    private ResultActions send(MockHttpSession session, long schoolId, MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        var builder = request.with(csrf()).header("X-School-ID", String.valueOf(schoolId)).session(session);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    /** درخواستِ «من» (/api/me): بدون هدر مدرسه. */
    private ResultActions sendMe(MockHttpSession session, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        var builder = request.with(csrf()).session(session);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static WeekStart weekStartCoveringToday() {
        return WeekStart.SATURDAY.schoolDays().contains(today().getDayOfWeek()) ? WeekStart.SATURDAY : WeekStart.MONDAY;
    }

    // ---- ساختنِ مدرسه و داده ----

    /**
     * مدرسه‌ی تازه: مدیر (بنیان‌گذار)، یک معلم با کلاس الف (سه دانش‌آموز)، ولیِ s1 و ولیِ بی‌ربط (فرزندش در کلاس ب).
     */
    private record Fixture(long school, MockHttpSession admin, MockHttpSession teacher, MockHttpSession parent,
                           MockHttpSession otherParent, long classA, long s1, long s2, long s3, long otherStudent) {
    }

    private long addMember(MockHttpSession admin, long school, String email, String name, String role) throws Exception {
        return json(send(admin, school, post("/api/admin/schools/" + school + "/memberships"),
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\",\"roleCode\":\"" + role + "\"}")
                .andExpect(status().isOk())).get("userId").asLong();
    }

    private long createClass(MockHttpSession admin, long school, String name, Long teacherId) throws Exception {
        return json(send(admin, school, post("/api/admin/schools/" + school + "/classes"),
                "{\"name\":\"" + name + "\",\"section\":null,\"teacherId\":" + teacherId
                        + ",\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}").andExpect(status().isOk())).get("id").asLong();
    }

    private long enroll(MockHttpSession admin, long school, String name, long classId) throws Exception {
        return json(send(admin, school, post("/api/admin/schools/" + school + "/students"),
                "{\"email\":\"" + unique("exc.s") + "\",\"fullName\":\"" + name + "\",\"classId\":" + classId + "}")
                .andExpect(status().isOk())).get("studentId").asLong();
    }

    private void link(MockHttpSession admin, long school, long studentId, long parentUserId) throws Exception {
        send(admin, school, post("/api/admin/schools/" + school + "/students/" + studentId + "/guardians"),
                "{\"guardianUserId\":" + parentUserId + "}").andExpect(status().isOk());
    }

    private Fixture fixture() throws Exception {
        WeekStart weekStart = weekStartCoveringToday();
        MockHttpSession admin = registerAndLogin(unique("exc.founder"), "مدیر عذرها");
        MvcResult created = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی عذرها " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\",\"weekStart\":\"" + weekStart.name() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long school = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String teacherEmail = unique("exc.teacher");
        MockHttpSession teacher = registerAndLogin(teacherEmail, "معلم عذرها");
        long teacherId = addMember(admin, school, teacherEmail, "معلم عذرها", "TEACHER");
        long classA = createClass(admin, school, "پایه پنجم الف", teacherId);
        long classB = createClass(admin, school, "پایه پنجم ب", null);
        long s1 = enroll(admin, school, "علی رضایی", classA);
        long s2 = enroll(admin, school, "محمد احمدی", classA);
        long s3 = enroll(admin, school, "رضا علوی", classA);
        long other = enroll(admin, school, "سارا کریمی", classB);

        String parentEmail = unique("exc.parent");
        MockHttpSession parent = registerAndLogin(parentEmail, "ولیِ علی");
        link(admin, school, s1, addMember(admin, school, parentEmail, "ولیِ علی", "PARENT"));
        String otherParentEmail = unique("exc.parent2");
        MockHttpSession otherParent = registerAndLogin(otherParentEmail, "ولیِ سارا");
        link(admin, school, other, addMember(admin, school, otherParentEmail, "ولیِ سارا", "PARENT"));
        return new Fixture(school, admin, teacher, parent, otherParent, classA, s1, s2, s3, other);
    }

    private static String attendance(Fixture f) {
        return "/api/admin/schools/" + f.school() + "/attendance";
    }

    private static String me(Fixture f) {
        return "/api/me/schools/" + f.school() + "/attendance";
    }

    private static String mark(long studentId, String status, boolean excused) {
        return "{\"studentId\":" + studentId + ",\"status\":\"" + status + "\",\"excused\":" + excused + ",\"note\":null}";
    }

    private static String marks(String... items) {
        return "{\"marks\":[" + String.join(",", items) + "]}";
    }

    private ResultActions putRoll(Fixture f, MockHttpSession who, LocalDate day, String body) throws Exception {
        return send(who, f.school(), put(attendance(f) + "/classes/" + f.classA() + "/days/" + day), body);
    }

    private static String excuseBody(long studentId, LocalDate day, String note) {
        return "{\"studentId\":" + studentId + ",\"day\":\"" + day + "\",\"note\":\"" + note + "\"}";
    }

    private ResultActions submit(Fixture f, MockHttpSession who, long studentId, LocalDate day, String note)
            throws Exception {
        return sendMe(who, post(me(f) + "/excuses"), excuseBody(studentId, day, note));
    }

    private long excuseRows(long studentId) {
        return jdbc.queryForObject("select count(*) from attendance.excuses where student_id = ?", Long.class, studentId);
    }

    private boolean markExcused(long studentId, LocalDate day) {
        return jdbc.queryForObject("select excused from attendance.marks where student_id = ? and day = ?",
                Boolean.class, studentId, day);
    }

    private String range(LocalDate day) {
        return "?from=" + day.minusDays(2) + "&to=" + day.plusDays(2);
    }

    // ---- ثبت، جایگزینی، تصمیم ----

    @Test
    void aParentSubmitsReplacesAndTheAdminApprovesWhichMakesTheAbsenceExcused() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();
        putRoll(f, f.teacher(), today, marks(mark(f.s1(), "ABSENT", false), mark(f.s2(), "LATE", false)))
                .andExpect(status().isOk());

        // ثبتِ عذر: PENDING
        long excuseId = json(submit(f, f.parent(), f.s1(), today, "سرماخوردگی").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.studentName").value("علی رضایی"))
                .andExpect(jsonPath("$.className").value("پایه پنجم الف"))).get("id").asLong();

        // ثبتِ دوباره تا وقتی PENDING است یادداشت را عوض می‌کند، نه ردیفِ دوم می‌سازد
        submit(f, f.parent(), f.s1(), today, "تب و سرماخوردگی").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(excuseId))
                .andExpect(jsonPath("$.note").value("تب و سرماخوردگی"));
        assertThat(excuseRows(f.s1())).isEqualTo(1);

        // خواندنِ «من»: غیبتِ فرزند با عذرِ PENDING، هنوز موجه نیست
        JsonNode mine = json(sendMe(f.parent(), get(me(f) + range(today)), null).andExpect(status().isOk()));
        assertThat(mine.get("students")).hasSize(1);
        JsonNode child = mine.get("students").get(0);
        assertThat(child.get("studentId").asLong()).isEqualTo(f.s1());
        assertThat(child.get("own").asBoolean()).isFalse();
        assertThat(child.get("days")).hasSize(1);
        assertThat(child.get("days").get(0).get("status").asText()).isEqualTo("ABSENT");
        assertThat(child.get("days").get(0).get("excused").asBoolean()).isFalse();
        assertThat(child.get("days").get(0).get("excuse").get("status").asText()).isEqualTo("PENDING");
        assertThat(child.get("days").get(0).get("excuse").get("note").asText()).isEqualTo("تب و سرماخوردگی");

        // مدیر فهرست را می‌بیند؛ معلم (بدون ATTENDANCE_VIEW_ALL/RECORD_ANY) نه فهرست و نه تصمیم
        JsonNode pending = json(send(f.admin(), f.school(), get(attendance(f) + "/excuses?status=PENDING"), null)
                .andExpect(status().isOk()));
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("studentName").asText()).isEqualTo("علی رضایی");
        assertThat(pending.get(0).get("className").asText()).isEqualTo("پایه پنجم الف");
        assertThat(pending.get(0).get("day").asText()).isEqualTo(today.toString());
        send(f.teacher(), f.school(), get(attendance(f) + "/excuses"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        send(f.teacher(), f.school(), post(attendance(f) + "/excuses/" + excuseId + "/decision"),
                "{\"decision\":\"APPROVED\"}").andExpect(status().isForbidden());
        send(f.admin(), f.school(), get(attendance(f) + "/excuses?status=BOGUS"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-excuse")));

        // تأیید: وضعیت و مُهرِ «موجه» در همان تراکنش
        assertThat(markExcused(f.s1(), today)).isFalse();
        send(f.admin(), f.school(), post(attendance(f) + "/excuses/" + excuseId + "/decision"),
                "{\"decision\":\"APPROVED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(markExcused(f.s1(), today)).isTrue();
        JsonNode after = json(sendMe(f.parent(), get(me(f) + range(today)), null).andExpect(status().isOk()));
        assertThat(after.get("students").get(0).get("days").get(0).get("excused").asBoolean()).isTrue();
        assertThat(after.get("students").get(0).get("days").get(0).get("excuse").get("status").asText())
                .isEqualTo("APPROVED");
        assertThat(json(send(f.admin(), f.school(), get(attendance(f) + "/excuses?status=PENDING"), null))).isEmpty();

        // پس از تصمیم: تصمیمِ دوباره و ثبتِ دوباره ۴۰۹؛ شناسه‌ی ناشناخته ۴۰۴
        send(f.admin(), f.school(), post(attendance(f) + "/excuses/" + excuseId + "/decision"),
                "{\"decision\":\"REJECTED\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("excuse-already-decided")));
        submit(f, f.parent(), f.s1(), today, "یادداشت تازه").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("excuse-already-decided")));
        send(f.admin(), f.school(), post(attendance(f) + "/excuses/999999999/decision"),
                "{\"decision\":\"APPROVED\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("excuse-not-found")));
    }

    @Test
    void rejectingLeavesTheAbsenceUnexcused() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();
        putRoll(f, f.admin(), today, marks(mark(f.s1(), "ABSENT", false))).andExpect(status().isOk());
        long excuseId = json(submit(f, f.parent(), f.s1(), today, "دلیل").andExpect(status().isOk())).get("id").asLong();

        send(f.admin(), f.school(), post(attendance(f) + "/excuses/" + excuseId + "/decision"),
                "{\"decision\":\"REJECTED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(markExcused(f.s1(), today)).isFalse();
        submit(f, f.parent(), f.s1(), today, "دوباره").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("excuse-already-decided")));
        send(f.admin(), f.school(), post(attendance(f) + "/excuses/" + excuseId + "/decision"),
                "{\"decision\":\"PENDING\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-excuse")));
    }

    // ---- قواعدِ ثبت ----

    @Test
    void onlyTheLinkedParentMaySubmitAndOnlyForAnAbsenceWithAValidNote() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();

        // هنوز مُهری نیست: ۴۰۹
        submit(f, f.parent(), f.s1(), today, "دلیل").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("no-absence-to-excuse")));

        putRoll(f, f.admin(), today, marks(mark(f.s1(), "ABSENT", false), mark(f.s2(), "LATE", false),
                mark(f.s3(), "ABSENT", false))).andExpect(status().isOk());

        // ولیِ دیگر، فرزندِ دیگر، دانش‌آموزِ بی‌ربط، و معلم: ۴۰۳
        submit(f, f.otherParent(), f.s1(), today, "دلیل").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("attendance-not-your-child")));
        submit(f, f.parent(), f.s3(), today, "دلیل").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("attendance-not-your-child")));
        submit(f, f.teacher(), f.s1(), today, "دلیل").andExpect(status().isForbidden());
        assertThat(excuseRows(f.s1())).isZero();

        // یادداشتِ خالی یا بلند: ۴۰۰
        submit(f, f.parent(), f.s1(), today, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-excuse")));
        submit(f, f.parent(), f.s1(), today, "x".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-excuse")));
        submit(f, f.parent(), f.s1(), today, "x".repeat(500)).andExpect(status().isOk());
    }

    @Test
    void aLateMarkCannotBeExcused() throws Exception {
        Fixture f = fixture();
        putRoll(f, f.admin(), today(), marks(mark(f.s1(), "LATE", false))).andExpect(status().isOk());

        submit(f, f.parent(), f.s1(), today(), "دلیل").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("no-absence-to-excuse")));
    }

    // ---- خواندنِ «من» ----

    @Test
    void theSelfServiceViewShowsOnlyOwnLinkedChildrenAndCapsTheRange() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();
        putRoll(f, f.admin(), today, marks(mark(f.s1(), "ABSENT", false), mark(f.s3(), "ABSENT", false)))
                .andExpect(status().isOk());

        // ولیِ بی‌ربط چیزی از دانش‌آموزانِ کلاس الف نمی‌بیند (فقط فرزندِ خودش، بدونِ روز)
        JsonNode stranger = json(sendMe(f.otherParent(), get(me(f) + range(today)), null).andExpect(status().isOk()));
        assertThat(stranger.get("students")).hasSize(1);
        assertThat(stranger.get("students").get(0).get("studentId").asLong()).isEqualTo(f.otherStudent());
        assertThat(stranger.get("students").get(0).get("days")).isEmpty();

        // کسی که نه دانش‌آموز است نه ولی (معلم): فهرستِ خالی
        assertThat(json(sendMe(f.teacher(), get(me(f) + range(today)), null).andExpect(status().isOk()))
                .get("students")).isEmpty();

        // بازه: لازم و حداکثر ۳۶۶ روز
        sendMe(f.parent(), get(me(f) + "?to=" + today), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
        sendMe(f.parent(), get(me(f) + "?from=" + today.minusDays(366) + "&to=" + today), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-attendance-range")));
        sendMe(f.parent(), get(me(f) + "?from=" + today.minusDays(365) + "&to=" + today), null)
                .andExpect(status().isOk());
    }

    @Test
    void theModuleMustBeEntitledForTheSelfServiceEndpoints() throws Exception {
        Fixture f = fixture();
        jdbc.update("delete from iam.school_entitlements where school_id = ? and module_id = "
                + "(select id from iam.modules where code = 'ATTENDANCE')", f.school());

        sendMe(f.parent(), get(me(f) + range(today())), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("module-not-entitled")));
        submit(f, f.parent(), f.s1(), today(), "دلیل").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("module-not-entitled")));
        send(f.admin(), f.school(), get(attendance(f) + "/excuses"), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("module-not-entitled")));
    }

    // ---- ذخیره‌ی دوباره‌ی حضور و غیاب ----

    @Test
    void resavingTheRollRemovesAPendingExcuseOfADroppedAbsenceAndKeepsAnApprovedExcuseExcused() throws Exception {
        Fixture f = fixture();
        LocalDate today = today();
        putRoll(f, f.admin(), today, marks(mark(f.s1(), "ABSENT", false))).andExpect(status().isOk());
        submit(f, f.parent(), f.s1(), today, "دلیل").andExpect(status().isOk());
        assertThat(excuseRows(f.s1())).isEqualTo(1);

        // غیبت برداشته شد: عذرِ PENDING هم می‌رود و ردیفِ یتیم نمی‌ماند
        putRoll(f, f.admin(), today, marks()).andExpect(status().isOk());
        assertThat(excuseRows(f.s1())).isZero();
        assertThat(json(send(f.admin(), f.school(), get(attendance(f) + "/excuses"), null))).isEmpty();

        // دوباره غایب + عذر + تأیید؛ ذخیره‌ی دوباره با excused=false: عذرِ تأییدشده می‌چربد
        putRoll(f, f.admin(), today, marks(mark(f.s1(), "ABSENT", false))).andExpect(status().isOk());
        long id = json(submit(f, f.parent(), f.s1(), today, "دلیل").andExpect(status().isOk())).get("id").asLong();
        send(f.admin(), f.school(), post(attendance(f) + "/excuses/" + id + "/decision"),
                "{\"decision\":\"APPROVED\"}").andExpect(status().isOk());
        putRoll(f, f.teacher(), today, marks(mark(f.s1(), "ABSENT", false), mark(f.s2(), "ABSENT", false)))
                .andExpect(status().isOk());
        assertThat(markExcused(f.s1(), today)).isTrue();
        assertThat(markExcused(f.s2(), today)).isFalse();

        // غیبت برداشته شد: عذرِ تأییدشده به‌عنوان تاریخچه می‌ماند (و در نمای «من» نادیده است)
        putRoll(f, f.teacher(), today, marks()).andExpect(status().isOk());
        assertThat(excuseRows(f.s1())).isEqualTo(1);
        JsonNode mine = json(sendMe(f.parent(), get(me(f) + range(today)), null).andExpect(status().isOk()));
        assertThat(mine.get("students").get(0).get("days")).isEmpty();
    }
}
