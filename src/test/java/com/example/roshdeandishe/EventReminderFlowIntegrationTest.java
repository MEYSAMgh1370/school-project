package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * یادآوری رویدادهای برنامه‌ی سالانه (openspec/changes/event-reminders.md) روی Postgres واقعی: زمانِ یادآوری روی
 * رویداد، انتخابِ نقش‌ها توسط مدیر (و ۴۰۳ برای دانش‌آموز)، گیرنده‌ی جعبه‌ی «رویدادهای نزدیک» و ادعای اتمیکِ دفتر
 * ارسال (SQL بومی {@code insert … on conflict do nothing}). هر تست ردیف‌های خودش را پاک می‌کند چون دیتابیس بین
 * تست‌ها ریست نمی‌شود. تاریخِ رویدادِ نزدیک مستقیم در جدول درج می‌شود (نه از مسیر API) تا به «امروز» و بازه‌ی سالِ
 * تحصیلی وابسته نباشد؛ پنجره‌ی یادآوری آنقدر بزرگ است که اختلافِ یک‌روزه‌ی منطقه‌ی زمانی اثر نداشته باشد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventReminderFlowIntegrationTest {

    private static final String LEDGER_REPOSITORY = "com.example.roshdeandishe.notification.internal.EventReminderSentRepository";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> insertedEntries = new ArrayList<>();
    private final List<Long> claimedEntries = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : insertedEntries) {
            jdbc.update("delete from school.calendar_entries where id = ?", id);
        }
        for (Long id : claimedEntries) {
            jdbc.update("delete from bale.event_reminders_sent where calendar_entry_id = ?", id);
        }
        // انتخابِ نقش‌های مدرسه‌ی ۱ (API راهی برای «برگشت به همه» ندارد)
        jdbc.update("delete from school.reminder_roles where school_id = 1");
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

    private MockHttpSession enrolledStudent(MockHttpSession admin, String email) throws Exception {
        MockHttpSession student = registerAndLogin(email, "دانش‌آموز یادآوری");
        MvcResult cls = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"کلاس یادآوری\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}"))
                .andExpect(status().isOk()).andReturn();
        long classId = objectMapper.readTree(cls.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"دانش‌آموز یادآوری\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk());
        return student;
    }

    private ResultActions putRoles(MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(put("/api/admin/schools/1/yearly-plan/reminder-roles")
                .with(csrf()).header("X-School-ID", "1").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode upcoming(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me/schools/1/upcoming-events").session(session))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("events");
    }

    /** رویدادِ دو روز بعد با پنجره‌ی ۵ روزه، مستقیم در جدول (فقط EVENT دارای زمان یادآوری). */
    private long insertNearEvent(String title) {
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Tehran")).plusDays(2);
        Long id = jdbc.queryForObject(
                "insert into school.calendar_entries (school_id, day, kind, title, remind_days_before) "
                        + "values (1, ?, 'EVENT', ?, 5) returning id", Long.class, day, title);
        insertedEntries.add(id);
        return id;
    }

    // ---------------------------------------------------------------- زمان یادآوری روی رویداد

    @Test
    void theLeadTimeIsValidatedAndReportedBackOnTheItem() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        MvcResult years = mockMvc.perform(get("/api/admin/schools/1/academic-year")
                        .header("X-School-ID", "1").session(admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode first = objectMapper.readTree(years.getResponse().getContentAsString()).get("years").get(0);
        long yearId = first.get("id").asLong();
        LocalDate day = LocalDate.parse(first.get("startDate").asText()).plusDays(12);

        for (String lead : new String[]{"-1", "31"}) {
            mockMvc.perform(post("/api/admin/schools/1/yearly-plan")
                            .with(csrf()).header("X-School-ID", "1").session(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"academicYearId\":" + yearId + ",\"day\":\"" + day + "\",\"title\":\"بد\",\"remindDaysBefore\":" + lead + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-calendar-entry")));
        }

        MvcResult created = mockMvc.perform(post("/api/admin/schools/1/yearly-plan")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYearId\":" + yearId + ",\"day\":\"" + day + "\",\"title\":\"اردوی یادآوری\",\"remindDaysBefore\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindDaysBefore").value(3))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        insertedEntries.add(id);

        mockMvc.perform(get("/api/me/schools/1/yearly-plan").param("academicYearId", String.valueOf(yearId)).session(admin)
                        .header("X-School-ID", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id==" + id + ")].remindDaysBefore").value(3));
    }

    // ---------------------------------------------------------------- نقش‌های دریافت‌کننده

    @Test
    void adminSeesTheSchoolsRolesWithoutPlatformRolesAndSavesASelection() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(get("/api/admin/schools/1/yearly-plan/reminder-roles")
                        .header("X-School-ID", "1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[*].code").value(hasItems("STUDENT", "PARENT", "TEACHER", "SCHOOL_ADMIN")))
                .andExpect(jsonPath("$.roles[*].code").value(not(hasItem("PLATFORM_ADMIN"))))
                .andExpect(jsonPath("$.selected").doesNotExist());

        putRoles(admin, "{\"roleCodes\":[\"PARENT\",\"STUDENT\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected[0]").value("PARENT"))
                .andExpect(jsonPath("$.selected[1]").value("STUDENT"));

        // جایگزینی (نه افزودن)
        putRoles(admin, "{\"roleCodes\":[\"TEACHER\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected.length()").value(1))
                .andExpect(jsonPath("$.selected[0]").value("TEACHER"));
    }

    @Test
    void unknownPlatformOrEmptySelectionsAreRejected() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        for (String json : new String[]{"{\"roleCodes\":[\"NOPE\"]}", "{\"roleCodes\":[\"PLATFORM_ADMIN\"]}",
                "{\"roleCodes\":[]}", "{\"roleCodes\":null}"}) {
            putRoles(admin, json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-setting")));
        }
    }

    @Test
    void aStudentCannotReadOrChangeTheReminderRoles() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        MockHttpSession student = enrolledStudent(admin, "reminder.denied@roshdeandishe.test");

        mockMvc.perform(get("/api/admin/schools/1/yearly-plan/reminder-roles")
                        .header("X-School-ID", "1").session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        putRoles(student, "{\"roleCodes\":[\"STUDENT\"]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
    }

    // ---------------------------------------------------------------- جعبه‌ی «رویدادهای نزدیک»

    @Test
    void theDashboardBoxFollowsTheSelectedRolesAndNeverShowsAnEventWithoutALeadTime() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        MockHttpSession student = enrolledStudent(admin, "reminder.student@roshdeandishe.test");
        long near = insertNearEvent("اردوی نزدیک");
        // رویداد بدون زمان یادآوری هرگز نمی‌آید
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Tehran")).plusDays(2);
        Long silent = jdbc.queryForObject(
                "insert into school.calendar_entries (school_id, day, kind, title) values (1, ?, 'EVENT', 'بی‌صدا') returning id",
                Long.class, day);
        insertedEntries.add(silent);

        // بدون انتخاب: همه‌ی اعضا (دانش‌آموز) می‌بینند
        JsonNode all = upcoming(student);
        assertThat(all).anySatisfy(e -> {
            assertThat(e.get("id").asLong()).isEqualTo(near);
            assertThat(e.get("daysLeft").asInt()).isBetween(1, 3);
        });
        assertThat(all).noneSatisfy(e -> assertThat(e.get("id").asLong()).isEqualTo(silent));

        // فقط معلم‌ها: دانش‌آموز فهرست خالی می‌گیرد
        putRoles(admin, "{\"roleCodes\":[\"TEACHER\"]}").andExpect(status().isOk());
        assertThat(upcoming(student)).isEmpty();

        // دانش‌آموز هم انتخاب شد: دوباره می‌بیند
        putRoles(admin, "{\"roleCodes\":[\"TEACHER\",\"STUDENT\"]}").andExpect(status().isOk());
        assertThat(upcoming(student)).anySatisfy(e -> assertThat(e.get("id").asLong()).isEqualTo(near));
    }

    @Test
    void aNonMemberOfTheSchoolCannotReadItsUpcomingEvents() throws Exception {
        MockHttpSession stranger = registerAndLogin("reminder.stranger@roshdeandishe.test", "غریبه");

        mockMvc.perform(get("/api/me/schools/1/upcoming-events").session(stranger))
                .andExpect(status().is4xxClientError());
    }

    // ---------------------------------------------------------------- دفتر ارسال

    @Test
    void theLedgerClaimInsertsOnceAndNeverTwiceForTheSameEventAndUser() throws Exception {
        Class<?> repositoryType = Class.forName(LEDGER_REPOSITORY);
        Object repository = context.getBean(repositoryType);
        Method claim = repositoryType.getDeclaredMethod("claim", long.class, long.class);
        claim.setAccessible(true);
        long entry = 880_000_000L + (System.nanoTime() % 1_000_000L);
        claimedEntries.add(entry);

        assertThat(claim.invoke(repository, entry, 1L)).isEqualTo(1);
        assertThat(claim.invoke(repository, entry, 1L)).isEqualTo(0);
        // کاربرِ دیگر برای همان رویداد، و همان کاربر برای رویدادِ دیگر، مستقل‌اند
        assertThat(claim.invoke(repository, entry, 2L)).isEqualTo(1);
        long other = entry + 1;
        claimedEntries.add(other);
        assertThat(claim.invoke(repository, other, 1L)).isEqualTo(1);

        Integer rows = jdbc.queryForObject(
                "select count(*) from bale.event_reminders_sent where calendar_entry_id in (?, ?)", Integer.class, entry, other);
        assertThat(rows).isEqualTo(3);
    }
}
