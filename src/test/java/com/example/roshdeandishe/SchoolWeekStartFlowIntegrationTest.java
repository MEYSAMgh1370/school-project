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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * روز شروع هفته‌ی هر مدرسه (openspec/changes/eu-market-readiness.md، W9) روی Postgres واقعی:
 * ساخت مدرسه با/بدون آن، پیش‌فرض شنبه‌محور برای مدرسه‌ی بدون انتخاب، و اجرای قاعده در
 * studyplan (روز شروع هفته) و academic (روزهای درسی) برای یک مدرسه‌ی دوشنبه‌محور. رفتار
 * مدرسه‌ی شنبه‌محور را تست‌های موجودِ مدرسه‌ی ۱ (StudyPlanManagement، Timetable) پوشش می‌دهند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SchoolWeekStartFlowIntegrationTest {

    private static final String YEAR = "1405-1406";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8);
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

    private ResultActions createSchoolRaw(MockHttpSession founder, String weekStartField) throws Exception {
        return mockMvc.perform(post("/api/admin/schools")
                .with(csrf())
                .session(founder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"مدرسه‌ی هفته " + UUID.randomUUID() + "\",\"timezone\":\"Europe/Amsterdam\","
                        + "\"locale\":\"en\",\"level\":\"ELEMENTARY\"" + weekStartField + "}"));
    }

    private long createSchool(MockHttpSession founder, String weekStartField) throws Exception {
        return json(createSchoolRaw(founder, weekStartField).andExpect(status().isOk())).get("id").asLong();
    }

    private String storedWeekStart(long schoolId) {
        return jdbc.queryForObject("select week_start_day from school.schools where id = ?", String.class, schoolId);
    }

    private void grantModules(long schoolId, String... codes) {
        for (String code : codes) {
            jdbc.update("insert into iam.school_entitlements (active, module_id, school_id) "
                    + "select true, id, ? from iam.modules where code = ?", schoolId, code);
        }
    }

    // ---- ساخت مدرسه ----

    @Test
    void aSchoolWithoutAWeekStartKeepsTheIranianSaturdayWeek() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("week.default") + "@roshdeandishe.test", "بنیان‌گذار پیش‌فرض");

        JsonNode created = json(createSchoolRaw(founder, "").andExpect(status().isOk()));

        assertThat(created.get("weekStart").asText()).isEqualTo("SATURDAY");
        assertThat(storedWeekStart(created.get("id").asLong())).isEqualTo("SAT");
        // مدرسه‌ی نمونه‌ی seed (قبل از V8 ساخته شده) هم همان شنبه‌محور است
        assertThat(storedWeekStart(1L)).isEqualTo("SAT");
    }

    @Test
    void aSchoolCanChooseTheMondayWeekWhenItIsCreated() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("week.monday") + "@roshdeandishe.test", "بنیان‌گذار دوشنبه");

        JsonNode created = json(createSchoolRaw(founder, ",\"weekStart\":\"MONDAY\"").andExpect(status().isOk()));

        assertThat(created.get("weekStart").asText()).isEqualTo("MONDAY");
        assertThat(storedWeekStart(created.get("id").asLong())).isEqualTo("MON");
    }

    @Test
    void anUnknownWeekStartIsRejectedAndCreatesNoSchool() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("week.bad") + "@roshdeandishe.test", "بنیان‌گذار نادرست");
        long before = jdbc.queryForObject("select count(*) from school.schools", Long.class);

        createSchoolRaw(founder, ",\"weekStart\":\"SUNDAY\"").andExpect(status().is4xxClientError());

        assertThat(jdbc.queryForObject("select count(*) from school.schools", Long.class)).isEqualTo(before);
    }

    // ---- studyplan: روز شروع هفته ----

    @Test
    void aMondayWeekSchoolPlansItsWeekFromMonday() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("week.plan") + "@roshdeandishe.test", "مدیر برنامه‌ریزی");
        long school = createSchool(founder, ",\"weekStart\":\"MONDAY\"");
        grantModules(school, "CURRICULUM", "STUDY_PLANNER");
        String base = "/api/admin/schools/" + school;

        long topicId = json(send(founder, school, post(base + "/topics"),
                "{\"name\":\"ریاضی دوشنبه\",\"parentId\":null,\"order\":0}").andExpect(status().isOk())).get("id").asLong();
        long classId = json(send(founder, school, post(base + "/classes"),
                "{\"name\":\"کلاس دوشنبه\",\"teacherId\":null,\"section\":null,\"gradeLevel\":5}")
                .andExpect(status().isOk())).get("id").asLong();
        long studentId = json(send(founder, school, post(base + "/students"),
                "{\"email\":\"" + unique("week.pupil") + "@roshdeandishe.test\",\"fullName\":\"دانش‌آموز دوشنبه\","
                        + "\"classId\":" + classId + "}").andExpect(status().isOk())).get("studentId").asLong();
        long planId = json(send(founder, school, post(base + "/study-plans"),
                "{\"title\":\"برنامه‌ی دوشنبه\",\"weights\":[{\"topicId\":" + topicId
                        + ",\"weight\":1.0,\"priority\":1}]}").andExpect(status().isOk())).get("id").asLong();
        send(founder, school, post(base + "/study-plans/" + planId + "/assignments"),
                "{\"studentId\":" + studentId + "}").andExpect(status().isOk());
        // کلیدها روزهای واقعی‌اند: فقط دوشنبه‌ها ۹ تا ۱۳ آزاد است
        send(founder, school, put(base + "/students/" + studentId + "/schedule/default"),
                "{\"SAT\":[],\"SUN\":[],\"MON\":[{\"start\":\"09:00\",\"end\":\"13:00\"}],\"TUE\":[],"
                        + "\"WED\":[],\"THU\":[],\"FRI\":[]}").andExpect(status().isOk());

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String suggest = base + "/students/" + studentId + "/study-intervals/suggest";

        // شنبه برای مدرسه‌ی دوشنبه‌محور شروع هفته نیست
        send(founder, school, post(suggest).param("weekStart", monday.minusDays(2).toString()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-week-start")));

        JsonNode intervals = json(send(founder, school, post(suggest).param("weekStart", monday.toString()), null)
                .andExpect(status().isOk()));
        assertThat(intervals.size()).isGreaterThan(0);
        for (JsonNode interval : intervals) {
            // برنامه‌ی «MON» همان دوشنبه‌ی واقعی است، نه جایگاه سوم هفته
            assertThat(interval.get("start").asText()).startsWith(monday.toString());
        }
    }

    // ---- academic: روزهای درسی ----

    @Test
    void aMondayWeekSchoolsTimetableHasMondayToFridayOnly() throws Exception {
        MockHttpSession founder = registerAndLogin(unique("week.tt") + "@roshdeandishe.test", "مدیر برنامه‌ی کلاسی");
        long school = createSchool(founder, ",\"weekStart\":\"MONDAY\"");
        String base = "/api/admin/schools/" + school;

        long classId = json(send(founder, school, post(base + "/classes"),
                "{\"name\":\"کلاس برنامه\",\"teacherId\":null,\"section\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}")
                .andExpect(status().isOk())).get("id").asLong();
        long periodId = json(send(founder, school, post(base + "/timetable/periods"),
                "{\"academicYear\":\"" + YEAR + "\",\"label\":\"زنگ اول\",\"startTime\":\"08:00\",\"endTime\":\"08:45\"}")
                .andExpect(status().isOk())).get("id").asLong();
        long courseId = jdbc.queryForObject(
                "insert into academic.courses (grade_level, name, graded) values (5, ?, true) returning id",
                Long.class, "ریاضی هفته " + UUID.randomUUID());
        String entries = base + "/timetable/classes/" + classId + "/entries";

        for (String day : new String[]{"MON", "TUE", "WED", "THU", "FRI"}) {
            send(founder, school, put(entries),
                    "{\"periodId\":" + periodId + ",\"weekday\":\"" + day + "\",\"courseId\":" + courseId + "}")
                    .andExpect(status().isOk());
        }
        for (String day : new String[]{"SAT", "SUN"}) {
            send(founder, school, put(entries),
                    "{\"periodId\":" + periodId + ",\"weekday\":\"" + day + "\",\"courseId\":" + courseId + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-timetable-entry")));
        }
        assertThat(jdbc.queryForObject("select count(*) from academic.timetable_entries where school_id = ?",
                Long.class, school)).isEqualTo(5L);
    }
}
