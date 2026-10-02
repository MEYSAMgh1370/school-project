package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * برنامهٔ کلاسی (openspec/changes/class-timetable.md) روی Postgres واقعی: CRUD زنگ
 * و فعالیت، خانه‌ها و قوانینشان (درس هم‌پایه، دقیقاً یکی از درس/فعالیت، معلمِ فعال،
 * رزرو دوباره‌ی معلم)، درسِ غیرنمره‌پذیر، مجوز/مدرسه، خواندنِ خودسرویس برای دانش‌آموز،
 * ولی، معلم و بیگانه، و سدِّ آخرِ ایندکس‌های یکتای V7. جدول‌های برنامه پیش از هر
 * تست خالی می‌شوند چون همه‌ی تست‌ها یک دیتابیس و یک مدرسه را مشترک دارند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimetableFlowIntegrationTest {

    private static final String YEAR = "1405-1406";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanTimetable() {
        jdbc.update("delete from academic.timetable_entries");
        jdbc.update("delete from academic.timetable_periods");
        jdbc.update("delete from academic.activities where school_id is not null");
    }

    // ---- کمکی‌ها ----

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

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    private long membership(MockHttpSession admin, String email, String fullName, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"roleCode\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("userId").asLong();
    }

    private long createClass(MockHttpSession admin, String name, int gradeLevel) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"section\":null,\"teacherId\":null,\"gradeLevel\":" + gradeLevel
                                + ",\"gradingScheme\":\"NUMERIC\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long enrollStudent(MockHttpSession admin, String email, String fullName, long classId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("studentId").asLong();
    }

    private long insertCourse(int gradeLevel, String name, boolean graded) {
        return jdbc.queryForObject(
                "insert into academic.courses (grade_level, name, graded) values (?, ?, ?) returning id",
                Long.class, gradeLevel, name + " " + UUID.randomUUID(), graded);
    }

    private long insertPlatformActivity(String name) {
        return jdbc.queryForObject(
                "insert into academic.activities (school_id, name) values (null, ?) returning id",
                Long.class, name + " " + UUID.randomUUID());
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpSession session, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        var builder = request.with(csrf()).header("X-School-ID", "1").session(session);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    private long createPeriod(MockHttpSession admin, String label, String start, String end) throws Exception {
        return json(send(admin, post("/api/admin/schools/1/timetable/periods"),
                "{\"academicYear\":\"" + YEAR + "\",\"label\":\"" + label + "\",\"startTime\":\"" + start
                        + "\",\"endTime\":\"" + end + "\"}").andExpect(status().isOk())).get("id").asLong();
    }

    private ResultActions setEntry(MockHttpSession admin, long classId, long periodId, String day,
                                   Long courseId, Long activityId, Long teacherUserId) throws Exception {
        return send(admin, put("/api/admin/schools/1/timetable/classes/" + classId + "/entries"),
                "{\"periodId\":" + periodId + ",\"weekday\":\"" + day + "\",\"courseId\":" + courseId
                        + ",\"activityId\":" + activityId + ",\"teacherUserId\":" + teacherUserId + "}");
    }

    private void makeCurrentYear(MockHttpSession admin) throws Exception {
        long yearId = jdbc.queryForObject("select id from school.academic_years where code = ?", Long.class, YEAR);
        send(admin, put("/api/admin/schools/1/academic-year"),
                "{\"academicYearId\":" + yearId + ",\"expectedCurrentAcademicYearId\":"
                        + jdbc.queryForObject("select current_academic_year_id from school.schools where id = 1", Long.class)
                        + "}").andExpect(status().isOk());
    }

    // ---- زنگ‌ها ----

    @Test
    void adminDefinesEditsAndDeletesPeriodsPerYearAndTheyMayNotOverlap() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        long first = createPeriod(admin, "زنگ اول", "08:00", "08:45");
        long second = createPeriod(admin, "زنگ دوم", "08:45", "09:30");

        // ترتیب خودکار است و فهرست بر اساس ساعت شروع
        mockMvc.perform(get("/api/admin/schools/1/timetable/periods").header("X-School-ID", "1").session(admin)
                        .param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].startTime").value("08:00"))
                .andExpect(jsonPath("$[1].position").value(2));

        send(admin, post("/api/admin/schools/1/timetable/periods"),
                "{\"academicYear\":\"" + YEAR + "\",\"label\":\"روی هم\",\"startTime\":\"08:30\",\"endTime\":\"09:00\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("period-overlaps")));
        send(admin, post("/api/admin/schools/1/timetable/periods"),
                "{\"academicYear\":\"" + YEAR + "\",\"label\":\"وارونه\",\"startTime\":\"10:00\",\"endTime\":\"09:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-period")));
        send(admin, post("/api/admin/schools/1/timetable/periods"),
                "{\"academicYear\":\"" + YEAR + "\",\"label\":\"خراب\",\"startTime\":\"abc\",\"endTime\":\"09:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-period")));
        send(admin, post("/api/admin/schools/1/timetable/periods"),
                "{\"academicYear\":\"1300-1301\",\"label\":\"سال ناموجود\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-academic-year")));

        send(admin, put("/api/admin/schools/1/timetable/periods/" + first),
                "{\"label\":\"اول (بلندتر)\",\"startTime\":\"08:00\",\"endTime\":\"08:40\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("اول (بلندتر)"))
                .andExpect(jsonPath("$.endTime").value("08:40"));

        send(admin, delete("/api/admin/schools/1/timetable/periods/" + second), null)
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/schools/1/timetable/periods").header("X-School-ID", "1").session(admin)
                        .param("academicYear", YEAR))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---- فعالیت‌ها ----

    @Test
    void schoolActivitiesAreManagedByTheSchoolAndPlatformDefaultsAreReadOnly() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long platform = insertPlatformActivity("ورزش پیش‌فرض");

        long own = json(send(admin, post("/api/admin/schools/1/timetable/activities"), "{\"name\":\"شطرنج\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformDefault").value(false))).get("id").asLong();

        // نام تکراری در همان مدرسه، و هم‌نام با یک پیش‌فرضِ پلتفرم
        send(admin, post("/api/admin/schools/1/timetable/activities"), "{\"name\":\"شطرنج\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("activity-name-taken")));
        String platformName = jdbc.queryForObject("select name from academic.activities where id = ?", String.class, platform);
        send(admin, post("/api/admin/schools/1/timetable/activities"), "{\"name\":\"" + platformName + "\"}")
                .andExpect(status().isConflict());

        // مدرسه پیش‌فرض را می‌بیند ولی نمی‌تواند عوضش کند
        mockMvc.perform(get("/api/admin/schools/1/timetable/activities").header("X-School-ID", "1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + platform + ")].platformDefault").value(true))
                .andExpect(jsonPath("$[?(@.id == " + own + ")].platformDefault").value(false));
        send(admin, put("/api/admin/schools/1/timetable/activities/" + platform), "{\"name\":\"عوض شد\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("activity-not-editable")));
        send(admin, delete("/api/admin/schools/1/timetable/activities/" + platform), null)
                .andExpect(status().isConflict());

        send(admin, put("/api/admin/schools/1/timetable/activities/" + own), "{\"name\":\"شطرنج پیشرفته\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("شطرنج پیشرفته"));
        send(admin, delete("/api/admin/schools/1/timetable/activities/" + own), null)
                .andExpect(status().isNoContent());
    }

    // ---- خانه‌ها ----

    @Test
    void aCellHoldsACourseOrAnActivityAndItsRulesAreEnforced() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - برنامه", 5);
        long period = createPeriod(admin, "زنگ اول", "08:00", "08:45");
        long math = insertCourse(5, "ریاضی", true);
        long sixth = insertCourse(6, "علوم ششم", true);
        long club = insertCourse(5, "کارگاه (غیرنمره‌پذیر)", false);
        long ownActivity = json(send(admin, post("/api/admin/schools/1/timetable/activities"),
                "{\"name\":\"نقاشی " + UUID.randomUUID() + "\"}").andExpect(status().isOk())).get("id").asLong();

        setEntry(admin, classId, period, "SAT", math, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(math))
                .andExpect(jsonPath("$.activityId").doesNotExist());
        // همان خانه با فعالیت جایگزین می‌شود (نه ردیف دوم)
        setEntry(admin, classId, period, "SAT", null, ownActivity, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(ownActivity))
                .andExpect(jsonPath("$.courseId").doesNotExist());
        mockMvc.perform(get("/api/admin/schools/1/timetable/classes/" + classId)
                        .header("X-School-ID", "1").session(admin).param("academicYear", YEAR))
                .andExpect(jsonPath("$.length()").value(1));

        setEntry(admin, classId, period, "SUN", math, ownActivity, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-timetable-entry")));
        setEntry(admin, classId, period, "SUN", null, null, null)
                .andExpect(status().isBadRequest());
        setEntry(admin, classId, period, "SUN", sixth, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("course-not-for-class")));
        setEntry(admin, classId, period, "SUN", 999999999L, null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("course-not-found")));
        setEntry(admin, classId, period, "SUN", null, 999999999L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("activity-not-found")));
        // پنجشنبه جزو روزهای مدرسه نیست
        setEntry(admin, classId, period, "THU", math, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-timetable-entry")));
        send(admin, delete("/api/admin/schools/1/timetable/classes/" + classId + "/entries?periodId=" + period
                + "&weekday=THU"), null).andExpect(status().isBadRequest());

        // درس غیرنمره‌پذیر را می‌شود در برنامه گذاشت
        setEntry(admin, classId, period, "MON", club, null, null).andExpect(status().isOk());

        // انتخابگر درسِ برنامه همه‌ی درس‌های پایه را دارد، شاملِ غیرنمره‌پذیر
        mockMvc.perform(get("/api/admin/schools/1/timetable/classes/" + classId + "/courses")
                        .header("X-School-ID", "1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + club + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + math + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + sixth + ")]").doesNotExist());

        // زنگ و فعالیتی که خانه دارند حذف نمی‌شوند؛ با خالی کردن خانه‌ها می‌شوند
        send(admin, delete("/api/admin/schools/1/timetable/periods/" + period), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("period-has-entries")));
        send(admin, delete("/api/admin/schools/1/timetable/activities/" + ownActivity), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("activity-in-use")));
        for (String day : new String[]{"SAT", "MON"}) {
            send(admin, delete("/api/admin/schools/1/timetable/classes/" + classId + "/entries?periodId=" + period
                    + "&weekday=" + day), null).andExpect(status().isNoContent());
        }
        // خالی کردنِ خانه‌ی خالی هم خطا نیست
        send(admin, delete("/api/admin/schools/1/timetable/classes/" + classId + "/entries?periodId=" + period
                + "&weekday=TUE"), null).andExpect(status().isNoContent());
        send(admin, delete("/api/admin/schools/1/timetable/activities/" + ownActivity), null)
                .andExpect(status().isNoContent());
        send(admin, delete("/api/admin/schools/1/timetable/periods/" + period), null)
                .andExpect(status().isNoContent());
    }

    @Test
    void aTeacherMustBeActiveAndCannotBeInTwoClassesInTheSameSlot() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classA = createClass(admin, "پایه پنجم - الف برنامه", 5);
        long classB = createClass(admin, "پایه پنجم - ب برنامه", 5);
        long period = createPeriod(admin, "زنگ اول", "08:00", "08:45");
        long math = insertCourse(5, "ریاضی", true);
        long teacher = membership(admin, unique("tt.teacher") + "@roshdeandishe.test", "معلم ریاضی", "TEACHER");
        long parent = membership(admin, unique("tt.parent") + "@roshdeandishe.test", "ولی", "PARENT");

        // ولی معلم نیست؛ کاربرِ ناشناس هم نیست
        setEntry(admin, classA, period, "SAT", math, null, parent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("teacher-not-eligible")));
        setEntry(admin, classA, period, "SAT", math, null, 999999999L)
                .andExpect(status().isConflict());

        setEntry(admin, classA, period, "SAT", math, null, teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherUserId").value(teacher))
                .andExpect(jsonPath("$.teacherName").value("معلم ریاضی"));
        // همان معلم، همان روز و زنگ، کلاس دیگر
        setEntry(admin, classB, period, "SAT", math, null, teacher)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("teacher-double-booked")));
        // روز دیگر مشکلی ندارد، و ثبتِ دوباره‌ی همان خانه با همان معلم هم نه
        setEntry(admin, classB, period, "SUN", math, null, teacher).andExpect(status().isOk());
        setEntry(admin, classA, period, "SAT", math, null, teacher).andExpect(status().isOk());

        // معلمی که غیرفعال می‌شود دیگر قابل انتخاب نیست
        long benched = membership(admin, unique("tt.benched") + "@roshdeandishe.test", "معلم کنار", "TEACHER");
        jdbc.update("update iam.school_memberships set active = false where user_id = ?", benched);
        setEntry(admin, classB, period, "MON", math, null, benched)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("teacher-not-eligible")));
        mockMvc.perform(get("/api/admin/schools/1/timetable/teachers").header("X-School-ID", "1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == " + teacher + ")]").exists())
                .andExpect(jsonPath("$[?(@.userId == " + benched + ")]").doesNotExist())
                .andExpect(jsonPath("$[?(@.userId == " + parent + ")]").doesNotExist());
    }

    @Test
    void aCourseThatIsNotGradedIsHiddenFromGradingAndCannotReceiveAGrade() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - غیرنمره‌پذیر", 5);
        String email = unique("tt.graded") + "@roshdeandishe.test";
        long studentId = enrollStudent(admin, email, "دانش‌آموز", classId);
        long graded = insertCourse(5, "فارسی", true);
        long ungraded = insertCourse(5, "پرورشی", false);

        mockMvc.perform(get("/api/admin/schools/1/grades/courses").header("X-School-ID", "1").session(admin)
                        .param("classId", String.valueOf(classId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + graded + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + ungraded + ")]").doesNotExist());

        send(admin, post("/api/admin/schools/1/grades"),
                "{\"studentId\":" + studentId + ",\"classId\":" + classId + ",\"academicYear\":\"" + YEAR
                        + "\",\"term\":\"FIRST\",\"numericScore\":15,\"courseId\":" + ungraded + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("course-not-gradable")));
        send(admin, post("/api/admin/schools/1/grades"),
                "{\"studentId\":" + studentId + ",\"classId\":" + classId + ",\"academicYear\":\"" + YEAR
                        + "\",\"term\":\"FIRST\",\"numericScore\":15,\"courseId\":" + graded + "}")
                .andExpect(status().isOk());
    }

    // ---- مجوز و مدرسه ----

    @Test
    void everyAdminEndpointNeedsTheTimetablePermissionAndTheCallersOwnSchool() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - مجوز برنامه", 5);
        String teacherEmail = unique("tt.nopermission") + "@roshdeandishe.test";
        MockHttpSession teacher = registerAndLogin(teacherEmail, "معلم بی‌مجوز برنامه");
        membership(admin, teacherEmail, "معلم بی‌مجوز برنامه", "TEACHER");

        // معلم TIMETABLE_MANAGE ندارد: نه خواندن، نه نوشتن
        for (String path : new String[]{"/years", "/periods?academicYear=" + YEAR, "/activities", "/teachers", "/classes",
                "/classes/" + classId + "?academicYear=" + YEAR, "/classes/" + classId + "/courses"}) {
            mockMvc.perform(get("/api/admin/schools/1/timetable" + path).header("X-School-ID", "1").session(teacher))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        }
        send(teacher, post("/api/admin/schools/1/timetable/periods"),
                "{\"academicYear\":\"" + YEAR + "\",\"label\":\"زنگ\",\"startTime\":\"08:00\",\"endTime\":\"09:00\"}")
                .andExpect(status().isForbidden());
        send(teacher, post("/api/admin/schools/1/timetable/activities"), "{\"name\":\"x\"}")
                .andExpect(status().isForbidden());
        setEntry(teacher, classId, 1L, "SAT", 1L, null, null).andExpect(status().isForbidden());

        // مسیرِ مدرسه‌ی دیگر با هدرِ مدرسه‌ی خودش: tenant نمی‌خواند
        mockMvc.perform(get("/api/admin/schools/2/timetable/periods").header("X-School-ID", "1").session(admin)
                        .param("academicYear", YEAR))
                .andExpect(status().is4xxClientError());

        // مدیرِ مدرسه‌ی دیگر (ماژول ACADEMIC را دارد، ولی عضو مدرسه‌ی ۱ نیست) به برنامه‌ی مدرسه‌ی ۱ دسترسی ندارد
        MockHttpSession founder = registerAndLogin(unique("tt.founder") + "@roshdeandishe.test", "بنیان‌گذار");
        mockMvc.perform(post("/api/admin/schools").with(csrf()).session(founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی دیگر\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/schools/1/timetable/activities").header("X-School-ID", "1").session(founder))
                .andExpect(status().isForbidden());
        setEntry(founder, classId, 1L, "SAT", 1L, null, null).andExpect(status().isForbidden());
    }

    // ---- خودسرویس ----

    @Test
    void studentParentTeacherAndStrangerEachSeeOnlyTheirOwnCurrentYearTimetable() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        makeCurrentYear(admin);
        long classId = createClass(admin, "پایه پنجم - خودسرویس", 5);
        long otherClass = createClass(admin, "پایه پنجم - دیگران", 5);
        long period = createPeriod(admin, "زنگ اول", "08:00", "08:45");
        long math = insertCourse(5, "ریاضی", true);
        long sport = insertPlatformActivity("ورزش");
        String teacherEmail = unique("tt.my.teacher") + "@roshdeandishe.test";
        MockHttpSession teacherSession = registerAndLogin(teacherEmail, "معلم من");
        long teacher = membership(admin, teacherEmail, "معلم من", "TEACHER");

        setEntry(admin, classId, period, "SAT", math, null, teacher).andExpect(status().isOk());
        setEntry(admin, classId, period, "SUN", null, sport, null).andExpect(status().isOk());
        setEntry(admin, otherClass, period, "MON", math, null, null).andExpect(status().isOk());

        String studentEmail = unique("tt.my.student") + "@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز برنامه");
        long studentId = enrollStudent(admin, studentEmail, "دانش‌آموز برنامه", classId);

        // دانش‌آموز: فقط کلاسِ خودش، با نامِ معلم
        mockMvc.perform(get("/api/me/schools/1/timetable").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value(YEAR))
                .andExpect(jsonPath("$.periods.length()").value(1))
                .andExpect(jsonPath("$.views.length()").value(1))
                .andExpect(jsonPath("$.views[0].kind").value("OWN"))
                .andExpect(jsonPath("$.views[0].classId").value(classId))
                .andExpect(jsonPath("$.views[0].entries.length()").value(2))
                .andExpect(jsonPath("$.views[0].entries[?(@.weekday == 'SAT')].teacherName").value("معلم من"))
                .andExpect(jsonPath("$.views[0].entries[?(@.weekday == 'SUN')].activityName").exists());

        // ولی: فرزندِ لینک‌شده‌اش، نه هیچ کلاس دیگری
        String parentEmail = unique("tt.my.parent") + "@roshdeandishe.test";
        MockHttpSession parent = registerAndLogin(parentEmail, "ولی برنامه");
        long parentUserId = membership(admin, parentEmail, "ولی برنامه", "PARENT");
        send(admin, post("/api/admin/schools/1/students/" + studentId + "/guardians"),
                "{\"guardianUserId\":" + parentUserId + "}").andExpect(status().isOk());
        mockMvc.perform(get("/api/me/schools/1/timetable").session(parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views.length()").value(1))
                .andExpect(jsonPath("$.views[0].kind").value("WARD"))
                .andExpect(jsonPath("$.views[0].studentId").value(studentId))
                .andExpect(jsonPath("$.views[0].studentName").value("دانش‌آموز برنامه"))
                .andExpect(jsonPath("$.views[0].className").value("پایه پنجم - خودسرویس"))
                .andExpect(jsonPath("$.views[0].entries.length()").value(2));

        // معلم: فقط ساعت‌های خودش (یک خانه)، با نامِ کلاس
        mockMvc.perform(get("/api/me/schools/1/timetable").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views.length()").value(1))
                .andExpect(jsonPath("$.views[0].kind").value("TEACHING"))
                .andExpect(jsonPath("$.views[0].entries.length()").value(1))
                .andExpect(jsonPath("$.views[0].entries[0].className").value("پایه پنجم - خودسرویس"));

        // بیگانه: نتیجه‌ی خالی، نه خطا و نه سالِ جاریِ مدرسه
        MockHttpSession stranger = registerAndLogin(unique("tt.my.stranger") + "@roshdeandishe.test", "بیگانه");
        mockMvc.perform(get("/api/me/schools/1/timetable").session(stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").doesNotExist())
                .andExpect(jsonPath("$.views").isEmpty())
                .andExpect(jsonPath("$.periods").isEmpty());
        // و مدرسه‌ی دیگر برای همین دانش‌آموز چیزی ندارد
        mockMvc.perform(get("/api/me/schools/2/timetable").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views").isEmpty());
    }

    @Test
    void selfServiceShowsOnlyTheCurrentYearNotAnotherYearsTimetable() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        makeCurrentYear(admin);
        long classId = createClass(admin, "پایه پنجم - سال دیگر", 5);
        long math = insertCourse(5, "ریاضی", true);
        // زنگ و خانه‌ی سالِ دیگر (مستقیم: ساختنِ زنگ برای هر سال ممکن است، نمایش فقط برای سالِ جاری)
        String otherYear = jdbc.queryForObject(
                "select code from school.academic_years where code <> ? order by start_year limit 1", String.class, YEAR);
        long otherPeriod = jdbc.queryForObject(
                "insert into academic.timetable_periods (school_id, academic_year, position, label, start_time, end_time) "
                        + "values (1, ?, 1, 'زنگ سال قبل', '08:00', '09:00') returning id", Long.class, otherYear);
        jdbc.update("insert into academic.timetable_entries (school_id, class_id, period_id, weekday, course_id) "
                + "values (1, ?, ?, 'SAT', ?)", classId, otherPeriod, math);
        String email = unique("tt.otheryear") + "@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(email, "دانش‌آموز سال دیگر");
        enrollStudent(admin, email, "دانش‌آموز سال دیگر", classId);

        mockMvc.perform(get("/api/me/schools/1/timetable").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value(YEAR))
                .andExpect(jsonPath("$.views[0].entries").isEmpty());
        // مدیر همان سال را می‌تواند باز کند
        mockMvc.perform(get("/api/admin/schools/1/timetable/classes/" + classId)
                        .header("X-School-ID", "1").session(admin).param("academicYear", otherYear))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---- ایندکس‌ها و قیدهای V7 ----

    @Test
    void theDatabaseItselfRefusesWhatTheFacadeValidates() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classA = createClass(admin, "پایه پنجم - قید الف", 5);
        long classB = createClass(admin, "پایه پنجم - قید ب", 5);
        long period = createPeriod(admin, "زنگ اول", "08:00", "08:45");
        long math = insertCourse(5, "ریاضی", true);
        long activity = insertPlatformActivity("ورزش");
        String insert = "insert into academic.timetable_entries "
                + "(school_id, class_id, period_id, weekday, course_id, activity_id, teacher_user_id) values (1, ?, ?, ?, ?, ?, ?)";

        jdbc.update(insert, classA, period, "SAT", math, null, 777L);
        // دو موضوع در یک خانه، معلمِ دو کلاس در یک خانه، هر دو/هیچ‌کدام از درس و فعالیت، و روزِ بیرون از هفته
        assertThatThrownBy(() -> jdbc.update(insert, classA, period, "SAT", math, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, classB, period, "SAT", math, null, 777L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, classB, period, "SUN", math, activity, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, classB, period, "SUN", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, classB, period, "THU", math, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // معلمِ خالی در چند کلاسِ همان خانه مجاز است (ایندکس جزئی)
        jdbc.update(insert, classA, period, "MON", math, null, null);
        jdbc.update(insert, classB, period, "MON", math, null, null);

        // زنگ با پایان نه‌بعد از شروع و شماره‌ی تکراری
        assertThatThrownBy(() -> jdbc.update("insert into academic.timetable_periods "
                + "(school_id, academic_year, position, label, start_time, end_time) values (1, ?, 9, 'x', '10:00', '10:00')", YEAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into academic.timetable_periods "
                + "(school_id, academic_year, position, label, start_time, end_time) values (1, ?, 1, 'x', '11:00', '12:00')", YEAR))
                .isInstanceOf(DataIntegrityViolationException.class);
        // فعالیتِ پیش‌فرض یا مدرسه با نام تکراری در همان دامنه
        String name = jdbc.queryForObject("select name from academic.activities where id = ?", String.class, activity);
        assertThatThrownBy(() -> jdbc.update("insert into academic.activities (school_id, name) values (null, ?)", name))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- صفحه‌ها ----

    @Test
    void thePagesAreReachableWithoutATenantHeaderAndNeedLogin() throws Exception {
        mockMvc.perform(get("/timetable"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/my-timetable"))
                .andExpect(status().is3xxRedirection());

        MockHttpSession admin = loginAsAdmin();
        // بدون X-School-ID: مسیرِ صفحه از TenantResolverFilter مستثناست (داده را fetch می‌آورد)
        mockMvc.perform(get("/timetable").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("x-data=\"timetablePage()\"")));
        mockMvc.perform(get("/my-timetable").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("x-data=\"myTimetablePage()\"")));
        // لینک‌های سایدبار/داشبورد
        mockMvc.perform(get("/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/timetable\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"dashboardTimetable\"")));
    }

    // ---- بازبینی Codex: هم‌زمانی زنگ‌ها، عضویتِ غیرفعال، درسِ غیرنمره‌پذیر ----

    @Test
    void concurrentOverlappingPeriodsLetExactlyOneThrough() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        int threads = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            results.add(pool.submit(() -> {
                start.await();
                // همه‌ی بازه‌ها با هم هم‌پوشانی دارند (۸:۰۰ تا ۹:۰۰ با شروع‌های کمی متفاوت)
                return send(admin, post("/api/admin/schools/1/timetable/periods"),
                        "{\"academicYear\":\"" + YEAR + "\",\"label\":\"هم‌زمان " + n + "\",\"startTime\":\"08:0" + n
                                + "\",\"endTime\":\"09:00\"}").andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        for (var f : results) {
            if (f.get() == 200) {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from academic.timetable_periods where academic_year = ?",
                Integer.class, YEAR)).isEqualTo(1);
    }

    @Test
    void concurrentUpdatesMovingTwoPeriodsIntoTheSameRangeLetExactlyOneThrough() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                jdbc.update("delete from academic.timetable_periods");
                long a = createPeriod(admin, "الف", "08:00", "08:30");
                long b = createPeriod(admin, "ب", "09:00", "09:30");
                java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
                // هر دو به بازه‌ی هنوز خالیِ ۱۰ تا ۱۱:۳۰ می‌روند و با هم هم‌پوشانی دارند
                var first = pool.submit(() -> {
                    barrier.await();
                    return send(admin, put("/api/admin/schools/1/timetable/periods/" + a),
                            "{\"label\":\"الف\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}")
                            .andReturn().getResponse().getStatus();
                });
                var second = pool.submit(() -> {
                    barrier.await();
                    return send(admin, put("/api/admin/schools/1/timetable/periods/" + b),
                            "{\"label\":\"ب\",\"startTime\":\"10:30\",\"endTime\":\"11:30\"}")
                            .andReturn().getResponse().getStatus();
                });
                assertThat(java.util.List.of(first.get(), second.get())).as("round " + round)
                        .containsExactlyInAnyOrder(200, 409);
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void deactivatedMembershipsNoLongerSeeTheTimetable() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        makeCurrentYear(admin);
        long classId = createClass(admin, "پایه پنجم - عضو سابق", 5);
        long period = createPeriod(admin, "زنگ اول", "08:00", "08:45");
        long math = insertCourse(5, "ریاضی", true);
        String teacherEmail = unique("tt.former.teacher") + "@roshdeandishe.test";
        MockHttpSession teacher = registerAndLogin(teacherEmail, "معلم سابق");
        long teacherId = membership(admin, teacherEmail, "معلم سابق", "TEACHER");
        setEntry(admin, classId, period, "SAT", math, null, teacherId).andExpect(status().isOk());
        String studentEmail = unique("tt.former.student") + "@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز سابق");
        long studentId = enrollStudent(admin, studentEmail, "دانش‌آموز سابق", classId);
        String parentEmail = unique("tt.former.parent") + "@roshdeandishe.test";
        MockHttpSession parent = registerAndLogin(parentEmail, "ولی سابق");
        long parentId = membership(admin, parentEmail, "ولی سابق", "PARENT");
        send(admin, post("/api/admin/schools/1/students/" + studentId + "/guardians"),
                "{\"guardianUserId\":" + parentId + "}").andExpect(status().isOk());

        for (MockHttpSession session : new MockHttpSession[]{student, parent, teacher}) {
            mockMvc.perform(get("/api/me/schools/1/timetable").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.views.length()").value(1));
        }

        // مدرسه عضویت‌ها را غیرفعال می‌کند؛ ثبت‌نام، لینکِ ولی و خانه‌ی معلم می‌مانند
        jdbc.update("update iam.school_memberships set active = false where school_id = 1 and user_id in "
                + "(select id from iam.users where email in (?, ?, ?))", studentEmail, parentEmail, teacherEmail);
        for (MockHttpSession session : new MockHttpSession[]{student, parent, teacher}) {
            mockMvc.perform(get("/api/me/schools/1/timetable").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.academicYear").doesNotExist())
                    .andExpect(jsonPath("$.views").isEmpty());
        }
    }

    @Test
    void anActiveTeacherWithoutCellsStillGetsAnEmptyTeachingView() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        makeCurrentYear(admin);
        createPeriod(admin, "زنگ اول", "08:00", "08:45");
        String email = unique("tt.idle.teacher") + "@roshdeandishe.test";
        MockHttpSession teacher = registerAndLogin(email, "معلم بدون ساعت");
        membership(admin, email, "معلم بدون ساعت", "TEACHER");

        mockMvc.perform(get("/api/me/schools/1/timetable").session(teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value(YEAR))
                .andExpect(jsonPath("$.periods.length()").value(1))
                .andExpect(jsonPath("$.views.length()").value(1))
                .andExpect(jsonPath("$.views[0].kind").value("TEACHING"))
                .andExpect(jsonPath("$.views[0].entries").isEmpty());
    }

    @Test
    void gradesOfACourseThatBecameNonGradedDisappearFromMyGradesButOverallGradesStay() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long classId = createClass(admin, "پایه پنجم - درس بازنشسته", 5);
        String email = unique("tt.retired") + "@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(email, "دانش‌آموز درس بازنشسته");
        long studentId = enrollStudent(admin, email, "دانش‌آموز درس بازنشسته", classId);
        long course = insertCourse(5, "علوم", true);
        for (String courseJson : new String[]{"", ",\"courseId\":" + course}) {
            send(admin, post("/api/admin/schools/1/grades"),
                    "{\"studentId\":" + studentId + ",\"classId\":" + classId + ",\"academicYear\":\"" + YEAR
                            + "\",\"term\":\"FIRST\",\"numericScore\":16" + courseJson + "}")
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/me/schools/1/grades").session(student))
                .andExpect(jsonPath("$.length()").value(2));

        // اپراتور درس را غیرنمره‌پذیر می‌کند: نمره‌ی آن از کارنامه پنهان می‌شود، ردیف می‌ماند
        jdbc.update("update academic.courses set graded = false where id = ?", course);
        mockMvc.perform(get("/api/me/schools/1/grades").session(student))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseId").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from academic.grades where course_id = ?",
                Integer.class, course)).isEqualTo(1);
    }
}
