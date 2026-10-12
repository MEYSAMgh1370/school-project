package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * گزارش مشکل سؤال (moshavere-content-import.md، D8-D10، P6) روی Postgres واقعی: ثبت روی سؤال پلتفرمی و
 * سؤال اختصاصی، صف مدرسه، صف پلتفرم، گردش وضعیت و این‌که گزارش هرگز ردیف سؤال را تغییر نمی‌دهد.
 */
@SpringBootTest(properties = "roshdeandishe.platform-admin.emails=question-report-flow@roshdeandishe.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QuestionReportFlowIntegrationTest {

    private static final String PLATFORM_EMAIL = "question-report-flow@roshdeandishe.test";
    private static final String PLATFORM_QUEUE = "/api/platform/question-reports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<ApplicationRunner> runners;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from curriculum.question_reports where comment like ?", "QRF-" + tag + "%");
        jdbc.update("delete from iam.platform_admins");
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

    private void registerIfAbsent(String email) throws Exception {
        Integer existing = jdbc.queryForObject("select count(*) from iam.users where email = ?", Integer.class, email);
        if (existing != null && existing > 0) {
            return;
        }
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow User\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    private MockHttpSession platformAdminSession() throws Exception {
        registerIfAbsent(PLATFORM_EMAIL);
        ApplicationRunner provisioner = runners.stream()
                .filter(r -> AopUtils.getTargetClass(r).getSimpleName().equals("PlatformAdminProvisioner"))
                .findFirst()
                .orElseThrow();
        provisioner.run(new DefaultApplicationArguments());
        return login(PLATFORM_EMAIL, "password123");
    }

    private MockHttpSession adminSession() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

    /** عضو تازه‌ای با نقش داده‌شده در مدرسه‌ی نمونه (id=1). */
    private MockHttpSession memberSession(String label, String roleCode) throws Exception {
        String email = "qrf." + label + "." + tag + "@roshdeandishe.test";
        registerIfAbsent(email);
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow User\",\"roleCode\":\"" + roleCode + "\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private record OtherSchool(long id, MockHttpSession admin) {
    }

    /** مدرسه‌ی تازه با بنیان‌گذارش (SCHOOL_ADMIN) و entitlement ماژول CURRICULUM (ماژول پولی از راه HTTP اعطا نمی‌شود). */
    private OtherSchool createOtherSchool() throws Exception {
        String email = "qrf.other." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
        registerIfAbsent(email);
        MockHttpSession session = login(email, "password123");
        MvcResult created = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی گزارش " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        jdbc.update("insert into iam.school_entitlements (active, module_id, school_id) "
                + "select true, id, ? from iam.modules where code = 'CURRICULUM'", schoolId);
        return new OtherSchool(schoolId, session);
    }

    private long insertTopic(Long schoolId) {
        return jdbc.queryForObject(
                "insert into curriculum.topics (school_id, parent_id, name, sort_order) values (?, null, ?, 0) returning id",
                Long.class, schoolId, "QRF-" + tag);
    }

    private long insertQuestion(Long schoolId, String text) {
        long topicId = insertTopic(schoolId);
        return jdbc.queryForObject(
                "insert into curriculum.questions (school_id, topic_id, text, choice1, choice2, choice3, choice4, "
                        + "correct_choice) values (?, ?, ?, 'الف', 'ب', 'ج', 'د', 1) returning id",
                Long.class, schoolId, topicId, text);
    }

    private Map<String, Object> questionRow(long questionId) {
        return jdbc.queryForMap("select * from curriculum.questions where id = ?", questionId);
    }

    private ResultActions report(MockHttpSession session, long schoolId, long questionId, String type, String comment)
            throws Exception {
        return mockMvc.perform(post("/api/admin/schools/" + schoolId + "/questions/" + questionId + "/reports")
                .with(csrf())
                .header("X-School-ID", String.valueOf(schoolId))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportType\":\"" + type + "\",\"comment\":\"" + comment + "\"}"));
    }

    private long createReport(MockHttpSession session, long schoolId, long questionId, String type) throws Exception {
        MvcResult result = report(session, schoolId, questionId, type, "QRF-" + tag + " " + UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions patchStatus(MockHttpSession session, String url, Long schoolId, String status, String note)
            throws Exception {
        var request = patch(url)
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"resolutionNote\":" + (note == null ? "null" : "\"" + note + "\"") + "}");
        if (schoolId != null) {
            request.header("X-School-ID", String.valueOf(schoolId));
        }
        return mockMvc.perform(request);
    }

    private ResultActions patchSchool(MockHttpSession session, long schoolId, long reportId, String status)
            throws Exception {
        return patchStatus(session, "/api/admin/schools/" + schoolId + "/question-reports/" + reportId + "/status",
                schoolId, status, null);
    }

    private ResultActions patchPlatform(MockHttpSession session, long reportId, String status, String note)
            throws Exception {
        return patchStatus(session, PLATFORM_QUEUE + "/" + reportId + "/status", null, status, note);
    }

    private JsonNode schoolQueue(MockHttpSession session, long schoolId, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/schools/" + schoolId + "/question-reports" + query)
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode platformQueue(MockHttpSession session, String query) throws Exception {
        MvcResult result = mockMvc.perform(get(PLATFORM_QUEUE + query).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    @Test
    void reportsOnPlatformAndOwnQuestionsAreStoredAndNeverChangeTheQuestion() throws Exception {
        MockHttpSession student = memberSession("student", "STUDENT");
        MockHttpSession admin = adminSession();
        long platformQuestion = insertQuestion(null, "QRF-" + tag + " سؤال پلتفرمی");
        long ownQuestion = insertQuestion(1L, "QRF-" + tag + " سؤال مدرسه");
        Map<String, Object> platformBefore = questionRow(platformQuestion);
        Map<String, Object> ownBefore = questionRow(ownQuestion);

        // دانش‌آموز روی سؤال پلتفرمی، مدیر روی سؤال اختصاصی؛ گزارش تکراری هم مجاز است
        MvcResult created = report(student, 1, platformQuestion, "WRONG_ANSWER", "QRF-" + tag + " پاسخ غلط")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformQuestion").value(true))
                .andExpect(jsonPath("$.schoolId").value(1))
                .andExpect(jsonPath("$.reportType").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        long platformReport = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        long duplicate = createReport(student, 1, platformQuestion, "WRONG_ANSWER");
        assertThat(duplicate).isNotEqualTo(platformReport);
        long ownReport = createReport(admin, 1, ownQuestion, "TYPO");

        // مدیر پلتفرم و مدیر مدرسه هر کدام گزارش خود را به ترتیب تا پایان می‌برند
        MockHttpSession platform = platformAdminSession();
        patchPlatform(platform, platformReport, "REVIEWED", null).andExpect(status().isOk());
        patchPlatform(platform, platformReport, "FIXED", "اصلاح شد").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FIXED"))
                .andExpect(jsonPath("$.resolutionNote").value("اصلاح شد"));
        patchSchool(admin, 1, ownReport, "REVIEWED").andExpect(status().isOk());
        patchSchool(admin, 1, ownReport, "CLOSED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(questionRow(platformQuestion)).isEqualTo(platformBefore);
        assertThat(questionRow(ownQuestion)).isEqualTo(ownBefore);
    }

    @Test
    void reportingAnInvisibleQuestionIsNotFound() throws Exception {
        OtherSchool other = createOtherSchool();
        long foreignQuestion = insertQuestion(other.id(), "QRF-" + tag + " سؤال مدرسه‌ی دیگر");

        report(adminSession(), 1, foreignQuestion, "OTHER", "QRF-" + tag)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("question-not-found")));
        report(adminSession(), 1, 987654321L, "OTHER", "QRF-" + tag)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("question-not-found")));
        assertThat(jdbc.queryForObject(
                "select count(*) from curriculum.question_reports where question_id = ?", Integer.class, foreignQuestion))
                .isZero();

        // صاحب سؤال می‌تواند گزارش کند
        createReport(other.admin(), other.id(), foreignQuestion, "UNCLEAR_TEXT");
    }

    @Test
    void invalidTypeAndLongCommentAreRejected() throws Exception {
        long question = insertQuestion(null, "QRF-" + tag + " سؤال اعتبارسنجی");
        MockHttpSession admin = adminSession();

        mockMvc.perform(post("/api/admin/schools/1/questions/" + question + "/reports")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"QRF-" + tag + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-question-report")));
        report(admin, 1, question, "TYPO", "QRF-" + tag + "x".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-question-report")));
        report(admin, 1, question, "NOPE", "QRF-" + tag).andExpect(status().is4xxClientError());
    }

    @Test
    void schoolQueueShowsOnlyItsOwnSchoolsReportsAndFiltersByStatus() throws Exception {
        MockHttpSession admin = adminSession();
        OtherSchool other = createOtherSchool();
        long platformQuestion = insertQuestion(null, "QRF-" + tag + " مشترک");
        long otherOwn = insertQuestion(other.id(), "QRF-" + tag + " اختصاصی دیگر");

        long fromSchool1 = createReport(admin, 1, platformQuestion, "TYPO");
        long fromOther = createReport(other.admin(), other.id(), platformQuestion, "TYPO");
        long otherOwnReport = createReport(other.admin(), other.id(), otherOwn, "OTHER");

        JsonNode queue1 = schoolQueue(admin, 1, "");
        assertThat(findById(queue1, fromSchool1)).isNotNull();
        assertThat(findById(queue1, fromOther)).isNull();
        assertThat(findById(queue1, otherOwnReport)).isNull();

        JsonNode queueOther = schoolQueue(other.admin(), other.id(), "");
        assertThat(findById(queueOther, fromOther)).isNotNull();
        assertThat(findById(queueOther, otherOwnReport)).isNotNull();
        assertThat(findById(queueOther, fromSchool1)).isNull();

        patchSchool(other.admin(), other.id(), otherOwnReport, "REVIEWED").andExpect(status().isOk());
        assertThat(findById(schoolQueue(other.admin(), other.id(), "?status=REVIEWED"), otherOwnReport)).isNotNull();
        assertThat(findById(schoolQueue(other.admin(), other.id(), "?status=OPEN"), otherOwnReport)).isNull();
        assertThat(findById(schoolQueue(other.admin(), other.id(), "?status=OPEN"), fromOther)).isNotNull();
    }

    @Test
    void platformQueueListsPlatformQuestionReportsAcrossSchoolsOnly() throws Exception {
        MockHttpSession admin = adminSession();
        OtherSchool other = createOtherSchool();
        long platformQuestion = insertQuestion(null, "QRF-" + tag + " پلتفرمی صف");
        long ownQuestion = insertQuestion(1L, "QRF-" + tag + " اختصاصی صف");

        long fromSchool1 = createReport(admin, 1, platformQuestion, "WRONG_ANSWER");
        long fromOther = createReport(other.admin(), other.id(), platformQuestion, "TYPO");
        long onOwn = createReport(admin, 1, ownQuestion, "TYPO");

        MockHttpSession platform = platformAdminSession();
        JsonNode queue = platformQueue(platform, "");
        assertThat(findById(queue, fromSchool1).get("schoolId").asLong()).isEqualTo(1);
        assertThat(findById(queue, fromOther).get("schoolId").asLong()).isEqualTo(other.id());
        assertThat(findById(queue, onOwn)).isNull();

        patchPlatform(platform, fromOther, "REVIEWED", null).andExpect(status().isOk());
        assertThat(findById(platformQueue(platform, "?status=REVIEWED"), fromOther)).isNotNull();
        assertThat(findById(platformQueue(platform, "?status=REVIEWED"), fromSchool1)).isNull();

        // مدرسه‌ی گزارش‌دهنده وضعیت را می‌بیند (D9)
        assertThat(findById(schoolQueue(other.admin(), other.id(), ""), fromOther).get("status").asText())
                .isEqualTo("REVIEWED");

        // مدیر پلتفرم گزارشِ سؤال اختصاصی را نمی‌بندد
        patchPlatform(platform, onOwn, "REVIEWED", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("question-report-not-found")));
    }

    @Test
    void platformQueueRefusesSchoolAdminTeacherAndAnonymous() throws Exception {
        long platformQuestion = insertQuestion(null, "QRF-" + tag + " ممنوع");
        MockHttpSession admin = adminSession();
        MockHttpSession teacher = memberSession("teacher", "TEACHER");
        long reportId = createReport(admin, 1, platformQuestion, "OTHER");

        for (MockHttpSession session : new MockHttpSession[]{admin, teacher}) {
            mockMvc.perform(get(PLATFORM_QUEUE).session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
            patchPlatform(session, reportId, "REVIEWED", null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        }
        mockMvc.perform(get(PLATFORM_QUEUE)).andExpect(status().is4xxClientError());
        mockMvc.perform(patch(PLATFORM_QUEUE + "/" + reportId + "/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject(
                "select status from curriculum.question_reports where id = ?", String.class, reportId)).isEqualTo("OPEN");
    }

    @Test
    void schoolQueuePatchWorksForOwnQuestionsAndIsRefusedForPlatformQuestions() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession teacher = memberSession("handler", "TEACHER");
        MockHttpSession student = memberSession("reporter", "STUDENT");
        long platformQuestion = insertQuestion(null, "QRF-" + tag + " پلتفرمی پچ");
        long ownQuestion = insertQuestion(1L, "QRF-" + tag + " اختصاصی پچ");
        long onPlatform = createReport(admin, 1, platformQuestion, "TYPO");
        long onOwn = createReport(student, 1, ownQuestion, "TYPO");

        patchSchool(admin, 1, onPlatform, "REVIEWED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("question-report-not-found")));
        patchSchool(teacher, 1, onOwn, "REVIEWED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));
        // دانش‌آموز CURRICULUM_MANAGE ندارد
        patchSchool(student, 1, onOwn, "FIXED")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        assertThat(jdbc.queryForObject(
                "select status from curriculum.question_reports where id = ?", String.class, onPlatform)).isEqualTo("OPEN");

        // مدرسه‌ی دیگر گزارش این مدرسه را نمی‌بیند
        OtherSchool other = createOtherSchool();
        patchSchool(other.admin(), other.id(), onOwn, "FIXED")
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyOpenToReviewedThenFixedOrClosedIsAllowed() throws Exception {
        MockHttpSession admin = adminSession();
        long question = insertQuestion(1L, "QRF-" + tag + " گردش");

        long toFixed = createReport(admin, 1, question, "TYPO");
        patchSchool(admin, 1, toFixed, "FIXED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-report-transition")));
        patchSchool(admin, 1, toFixed, "CLOSED").andExpect(status().isConflict());
        patchSchool(admin, 1, toFixed, "OPEN").andExpect(status().isConflict());
        patchSchool(admin, 1, toFixed, "REVIEWED").andExpect(status().isOk());
        patchSchool(admin, 1, toFixed, "REVIEWED").andExpect(status().isConflict());
        patchSchool(admin, 1, toFixed, "OPEN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-report-transition")));
        patchSchool(admin, 1, toFixed, "FIXED").andExpect(status().isOk());
        // وضعیت پایانی است
        patchSchool(admin, 1, toFixed, "CLOSED").andExpect(status().isConflict());
        patchSchool(admin, 1, toFixed, "REVIEWED").andExpect(status().isConflict());

        long toClosed = createReport(admin, 1, question, "OTHER");
        patchSchool(admin, 1, toClosed, "REVIEWED").andExpect(status().isOk());
        patchSchool(admin, 1, toClosed, "CLOSED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        patchSchool(admin, 1, toClosed, "FIXED").andExpect(status().isConflict());

        patchStatus(admin, "/api/admin/schools/1/question-reports/" + toClosed + "/status", 1L, "null", null)
                .andExpect(status().is4xxClientError());
    }

    @Test
    void creatingAReportNeedsTheQuestionReportPermission() throws Exception {
        long question = insertQuestion(null, "QRF-" + tag + " مجوز");
        MockHttpSession parent = memberSession("parent", "PARENT");
        MockHttpSession student = memberSession("perm-student", "STUDENT");
        MockHttpSession teacher = memberSession("perm-teacher", "TEACHER");

        report(parent, 1, question, "TYPO", "QRF-" + tag)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        assertThat(jdbc.queryForObject(
                "select count(*) from curriculum.question_reports where comment = ?", Integer.class, "QRF-" + tag))
                .isZero();

        report(student, 1, question, "TYPO", "QRF-" + tag).andExpect(status().isOk());
        report(teacher, 1, question, "TYPO", "QRF-" + tag).andExpect(status().isOk());
        report(adminSession(), 1, question, "TYPO", "QRF-" + tag).andExpect(status().isOk());
    }
}
