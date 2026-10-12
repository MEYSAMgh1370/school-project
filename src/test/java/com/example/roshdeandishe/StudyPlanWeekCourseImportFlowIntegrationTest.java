package com.example.roshdeandishe;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ایمپورت گروهی سرفصل‌های هفتگی با کد منبع (moshavere-content-import، فاز ۴): dry-run اجباریِ پیش‌فرض،
 * ایدمپوتنسی، خطای مستقل هر مورد و سقف ۵۰۰ مورد. سرفصل‌های پلتفرمی مستقیم با SQL ساخته و در پایان پاک می‌شوند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudyPlanWeekCourseImportFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CurriculumFacade curriculumFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String tag = "WCI" + Long.toString(System.nanoTime(), 36);
    private final List<Long> planIds = new ArrayList<>();

    private String courseCode() {
        return tag + "C";
    }

    private String subCode(int n) {
        return tag + "S" + n;
    }

    private String strangerCode() {
        return tag + "X";
    }

    @BeforeEach
    void createPlatformTopics() {
        // درس C با دو زیرسرفصل؛ X یک ریشه‌ی جدا (زیرمجموعه‌ی C نیست)
        long course = insertTopic(courseCode(), null);
        insertTopic(subCode(1), course);
        insertTopic(subCode(2), course);
        insertTopic(strangerCode(), null);
    }

    @AfterEach
    void cleanUp() {
        for (Long planId : planIds) {
            jdbc.update("delete from studyplan.week_course_topics where week_course_id in "
                    + "(select id from studyplan.week_courses where plan_id = ?)", planId);
            jdbc.update("delete from studyplan.week_courses where plan_id = ?", planId);
            jdbc.update("delete from studyplan.plan_course_weights where plan_id = ?", planId);
            jdbc.update("delete from studyplan.plans where id = ?", planId);
        }
        jdbc.update("delete from curriculum.topics where source_code like ?", tag + "%");
    }

    private long insertTopic(String code, Long parentId) {
        return jdbc.queryForObject(
                "insert into curriculum.topics (school_id, source_code, parent_id, name, sort_order) "
                        + "values (null, ?, ?, ?, 0) returning id", Long.class, code, parentId, "موضوع " + code);
    }

    private long topicId(String code) {
        return jdbc.queryForObject("select id from curriculum.topics where source_code = ?", Long.class, code);
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

    private void addMembership(MockHttpSession adminSession, String email, String fullName, String role) throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"roleCode\":\"" + role + "\"}"))
                .andExpect(status().isOk());
    }

    /** برنامه‌ای که وزنِ درسِ پلتفرمیِ C را دارد (سرفصل پلتفرمی برای مدرسه‌ی ۱ قابل‌مشاهده است). */
    private long createPlan(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/study-plans")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"برنامه‌ی ایمپورت " + tag + "\",\"weights\":[{\"topicId\":"
                                + topicId(courseCode()) + ",\"weight\":1.0,\"priority\":1}]}"))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        planIds.add(id);
        return id;
    }

    private ResultActions importRaw(MockHttpSession session, String schoolId, long planId, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/schools/" + schoolId + "/study-plans/" + planId + "/week-courses/import")
                .with(csrf())
                .header("X-School-ID", schoolId)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode importOk(MockHttpSession session, long planId, String body) throws Exception {
        MvcResult result = importRaw(session, "1", planId, body).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String item(LocalDate week, String course, String... subs) {
        StringBuilder sb = new StringBuilder("{\"weekStart\":\"" + week + "\",\"courseTopicCode\":\"" + course
                + "\",\"subTopicCodes\":[");
        for (int i = 0; i < subs.length; i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(subs[i]).append('"');
        }
        return sb.append("]}").toString();
    }

    private static String body(Boolean dryRun, String... items) {
        return "{" + (dryRun == null ? "" : "\"dryRun\":" + dryRun + ",") + "\"items\":[" + String.join(",", items) + "]}";
    }

    private int weekCourseCount(long planId) {
        return jdbc.queryForObject("select count(*) from studyplan.week_courses where plan_id = ?", Integer.class, planId);
    }

    private static LocalDate saturday(int weeksAhead) {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusWeeks(weeksAhead);
    }

    @Test
    void dryRunWritesNothingAndRealRunCreatesThenRerunIsAllExisting() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long planId = createPlan(session);
        String[] items = {
                item(saturday(1), courseCode(), subCode(1), subCode(2)),
                item(saturday(2), courseCode(), subCode(1))};

        // dryRun غایب = پیش‌نمایش
        JsonNode dry = importOk(session, planId, body(null, items));
        assertThat(dry.get("dryRun").asBoolean()).isTrue();
        assertThat(dry.get("total").asInt()).isEqualTo(2);
        assertThat(dry.get("created").asInt()).isEqualTo(2);
        assertThat(dry.get("failed").asInt()).isZero();
        assertThat(dry.get("items").get(0).get("status").asText()).isEqualTo("CREATED");
        assertThat(weekCourseCount(planId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from studyplan.week_course_topics wct join "
                + "studyplan.week_courses wc on wc.id = wct.week_course_id where wc.plan_id = ?", Integer.class, planId))
                .isZero();

        JsonNode real = importOk(session, planId, body(false, items));
        assertThat(real.get("dryRun").asBoolean()).isFalse();
        assertThat(real.get("created").asInt()).isEqualTo(2);
        assertThat(weekCourseCount(planId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from studyplan.week_course_topics wct join "
                + "studyplan.week_courses wc on wc.id = wct.week_course_id where wc.plan_id = ?", Integer.class, planId))
                .isEqualTo(3);

        JsonNode again = importOk(session, planId, body(false, items));
        assertThat(again.get("created").asInt()).isZero();
        assertThat(again.get("existing").asInt()).isEqualTo(2);
        assertThat(again.get("failed").asInt()).isZero();
        assertThat(weekCourseCount(planId)).isEqualTo(2);

        // dry-run پس از ایمپورت هم EXISTING می‌گوید
        JsonNode dryAgain = importOk(session, planId, body(true, items));
        assertThat(dryAgain.get("existing").asInt()).isEqualTo(2);
    }

    @Test
    void duplicateItemsInOneRequestAreReportedTheSameInDryRunAndRealRun() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long planId = createPlan(session);
        String same = item(saturday(1), courseCode(), subCode(1));

        JsonNode dry = importOk(session, planId, body(true, same, same));
        assertThat(dry.get("created").asInt()).isEqualTo(1);
        assertThat(dry.get("existing").asInt()).isEqualTo(1);
        JsonNode real = importOk(session, planId, body(false, same, same));
        assertThat(real.get("created").asInt()).isEqualTo(1);
        assertThat(real.get("existing").asInt()).isEqualTo(1);
        assertThat(weekCourseCount(planId)).isEqualTo(1);
    }

    @Test
    void failingItemsDoNotStopOthersAndCarryTheirOwnReason() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long planId = createPlan(session);
        LocalDate notSaturday = saturday(1).plusDays(1);

        JsonNode result = importOk(session, planId, body(false,
                item(saturday(1), "NOPE" + tag),                          // 0: کد درس ناشناخته
                item(saturday(2), courseCode(), subCode(1), "GHOST" + tag), // 1: کد زیرسرفصل ناشناخته
                item(notSaturday, courseCode()),                          // 2: شروع هفته نادرست
                item(saturday(3), courseCode(), strangerCode()),          // 3: زیرسرفصل زیر درس نیست
                item(saturday(4), courseCode(), subCode(2))));            // 4: سالم

        assertThat(result.get("total").asInt()).isEqualTo(5);
        assertThat(result.get("created").asInt()).isEqualTo(1);
        assertThat(result.get("failed").asInt()).isEqualTo(4);
        JsonNode items = result.get("items");
        assertThat(items.get(0).get("status").asText()).isEqualTo("FAILED");
        assertThat(items.get(0).get("reason").asText()).contains("NOPE" + tag);
        assertThat(items.get(1).get("status").asText()).isEqualTo("FAILED");
        assertThat(items.get(1).get("reason").asText()).contains("GHOST" + tag);
        assertThat(items.get(2).get("status").asText()).isEqualTo("FAILED");
        assertThat(items.get(2).get("reason").asText()).contains("شروع هفته باید");
        assertThat(items.get(3).get("status").asText()).isEqualTo("FAILED");
        assertThat(items.get(3).get("reason").asText()).contains(strangerCode()).contains(courseCode());
        assertThat(items.get(4).get("status").asText()).isEqualTo("CREATED");
        assertThat(items.get(4).get("index").asInt()).isEqualTo(4);
        assertThat(weekCourseCount(planId)).isEqualTo(1);
    }

    @Test
    void moreThanFiveHundredItemsIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long planId = createPlan(session);
        String[] items = new String[501];
        java.util.Arrays.fill(items, item(saturday(1), courseCode()));

        importRaw(session, "1", planId, body(true, items))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-week-course")));
        assertThat(weekCourseCount(planId)).isZero();
    }

    @Test
    void unknownPlanIsNotFound() throws Exception {
        MockHttpSession session = loginAsAdmin();
        importRaw(session, "1", 999999L, body(true, item(saturday(1), courseCode())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("plan-not-found")));
    }

    @Test
    void teacherMayImportButStudentAndNonMemberAreRefused() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        long planId = createPlan(adminSession);
        String payload = body(true, item(saturday(1), courseCode()));

        MockHttpSession teacher = registerAndLogin("wci.teacher@roshdeandishe.test", "معلم ایمپورت");
        addMembership(adminSession, "wci.teacher@roshdeandishe.test", "معلم ایمپورت", "TEACHER");
        // معلم مثل بقیه‌ی endpointهای برنامه‌ریزی STUDYPLAN_MANAGE دارد (teacherCanAuthorPlans)
        importRaw(teacher, "1", planId, payload).andExpect(status().isOk());

        MockHttpSession student = registerAndLogin("wci.student@roshdeandishe.test", "دانش‌آموز ایمپورت");
        addMembership(adminSession, "wci.student@roshdeandishe.test", "دانش‌آموز ایمپورت", "STUDENT");
        importRaw(student, "1", planId, payload)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));

        // ادمین مدرسه‌ی ۱ در مدرسه‌ی ۲ عضو نیست
        importRaw(adminSession, "2", planId, payload).andExpect(status().isForbidden());
    }

    @Test
    void schoolWithoutStudyPlannerEntitlementIsRefused() throws Exception {
        MockHttpSession founder = registerAndLogin("wci.founder@roshdeandishe.test", "بنیان‌گذار ایمپورت");
        MvcResult created = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بدون برنامه‌ریز ایمپورت\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String schoolId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        importRaw(founder, schoolId, 1L, body(true, item(saturday(1), courseCode())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }

    @Test
    void platformTopicsResolveForASchoolWithNoTopicsOfItsOwn() {
        Map<String, Long> resolved = curriculumFacade.resolveTopicIdsBySourceCode(
                987654L, List.of(courseCode(), " " + subCode(1) + " ", "UNKNOWN" + tag));

        assertThat(resolved).containsOnlyKeys(courseCode(), subCode(1));
        assertThat(resolved.get(courseCode())).isEqualTo(topicId(courseCode()));
        assertThat(curriculumFacade.resolveTopicIdsBySourceCode(987654L, Set.of())).isEmpty();
    }

    @Test
    void schoolTopicWithSameSourceCodeOfAnotherSchoolIsNotVisible() {
        // سرفصل اختصاصی مدرسه‌ی ۱ (کد منبع یکتاست، پس فقط همین یکی وجود دارد)
        String code = tag + "P";
        jdbc.update("insert into curriculum.topics (school_id, source_code, parent_id, name, sort_order) "
                + "values (1, ?, null, 'خصوصی', 0)", code);

        assertThat(curriculumFacade.resolveTopicIdsBySourceCode(1L, List.of(code))).containsKey(code);
        assertThat(curriculumFacade.resolveTopicIdsBySourceCode(2L, List.of(code))).isEmpty();
    }
}
