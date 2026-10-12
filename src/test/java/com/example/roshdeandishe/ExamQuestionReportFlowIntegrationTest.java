package com.example.roshdeandishe;

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

import java.time.LocalDateTime;
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
 * گزارش مشکل سؤال از سمت دانش‌آموز، از ماژول exam (moshavere-content-import.md، P6) روی Postgres واقعی:
 * فقط سؤالِ یکی از آزمون‌های در دسترسِ خودِ دانش‌آموز قابل گزارش است و فهرست «گزارش‌های من» فقط گزارش‌های
 * خودش را نشان می‌دهد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExamQuestionReportFlowIntegrationTest {

    private static final String BASE = "/api/student/schools/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void schoolOneHasNoCurrentYear() {
        // همان پیش‌فرض ExamFlowIntegrationTest: تخصیص را می‌سنجیم، نه ثبت‌نام سال جاری را
        jdbc.update("update school.schools set current_academic_year_id = null where id = 1");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from curriculum.question_reports where comment like ?", "EQR-" + tag + "%");
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

    private String register(String label) throws Exception {
        String email = "eqr." + label + "." + tag + "@roshdeandishe.test";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow User\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return email;
    }

    private MockHttpSession adminSession() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

    /** عضو تازه با نقش داده‌شده در مدرسه‌ی نمونه (بدون ثبت‌نام دانش‌آموزی). */
    private MockHttpSession memberSession(String label, String roleCode) throws Exception {
        String email = register(label);
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow User\",\"roleCode\":\"" + roleCode + "\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private long createClass(MockHttpSession admin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"EQR-" + tag + "\",\"teacherId\":null,\"section\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private record Student(long studentId, MockHttpSession session) {
    }

    private Student enrolledStudent(MockHttpSession admin, String label, long classId) throws Exception {
        String email = register(label);
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow Student\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        long studentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("studentId").asLong();
        return new Student(studentId, login(email, "password123"));
    }

    private long insertQuestion(Long schoolId, String text) {
        long topicId = jdbc.queryForObject(
                "insert into curriculum.topics (school_id, parent_id, name, sort_order) values (?, null, ?, 0) returning id",
                Long.class, schoolId, "EQR-" + tag);
        return jdbc.queryForObject(
                "insert into curriculum.questions (school_id, topic_id, text, choice1, choice2, choice3, choice4, "
                        + "correct_choice) values (?, ?, ?, 'الف', 'ب', 'ج', 'د', 1) returning id",
                Long.class, schoolId, topicId, text);
    }

    private long topicOf(long questionId) {
        return jdbc.queryForObject("select topic_id from curriculum.questions where id = ?", Long.class, questionId);
    }

    private Map<String, Object> questionRow(long questionId) {
        return jdbc.queryForMap("select * from curriculum.questions where id = ?", questionId);
    }

    /** آزمونی با یک بخش و یک سؤال، تخصیص‌یافته به assignTo (اگر تهی نباشد)؛ start نسبت به اکنون. */
    private long examWithQuestion(MockHttpSession admin, long questionId, Long assignTo, int startOffsetMinutes)
            throws Exception {
        LocalDateTime start = LocalDateTime.now().plusMinutes(startOffsetMinutes);
        MvcResult exam = mockMvc.perform(post("/api/admin/schools/1/exams")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"EQR-" + tag + "\",\"start\":\"" + start + "\",\"end\":\""
                                + start.plusHours(2) + "\",\"durationMinutes\":30}"))
                .andExpect(status().isOk())
                .andReturn();
        long examId = objectMapper.readTree(exam.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/admin/schools/1/exams/" + examId + "/sections")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicOf(questionId) + ",\"name\":null,\"questionIds\":["
                                + questionId + "]}"))
                .andExpect(status().isOk());
        if (assignTo != null) {
            assign(admin, examId, assignTo);
        }
        return examId;
    }

    private void assign(MockHttpSession admin, long examId, long studentId) throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/exams/" + examId + "/assign")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[" + studentId + "]}"))
                .andExpect(status().isOk());
    }

    private ResultActions report(MockHttpSession session, long schoolId, long questionId, String type, String comment)
            throws Exception {
        return mockMvc.perform(post(BASE + schoolId + "/questions/" + questionId + "/reports")
                .with(csrf())
                .header("X-School-ID", String.valueOf(schoolId))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportType\":\"" + type + "\",\"comment\":\"" + comment + "\"}"));
    }

    private long reportOk(MockHttpSession session, long questionId, String comment) throws Exception {
        MvcResult result = report(session, 1, questionId, "OTHER", comment).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode myReports(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "1/question-reports")
                        .header("X-School-ID", "1").session(session))
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

    private int reportCount() {
        return jdbc.queryForObject("select count(*) from curriculum.question_reports where comment like ?",
                Integer.class, "EQR-" + tag + "%");
    }

    @Test
    void assignedStudentReportsAQuestionOfTheirExamAndSeesItInTheirListAndTheSchoolQueue() throws Exception {
        MockHttpSession admin = adminSession();
        long classId = createClass(admin);
        Student student = enrolledStudent(admin, "reporter", classId);
        long question = insertQuestion(1L, "EQR-" + tag + " سؤال آزمون");
        examWithQuestion(admin, question, student.studentId(), -5);
        Map<String, Object> before = questionRow(question);

        MvcResult created = report(student.session(), 1, question, "TYPO", "EQR-" + tag + " غلط املایی")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(question))
                .andExpect(jsonPath("$.reportType").value("TYPO"))
                .andExpect(jsonPath("$.comment").value("EQR-" + tag + " غلط املایی"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();
        long reportId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // در صف مدرسه (سمت معلم/مدیر) دیده می‌شود و ردیف سؤال دست‌نخورده است
        mockMvc.perform(get("/api/admin/schools/1/question-reports").header("X-School-ID", "1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")]").exists());
        assertThat(questionRow(question)).isEqualTo(before);

        // وضعیت را مدیر جلو می‌برد و دانش‌آموز آن را (با یادداشت) می‌بیند
        mockMvc.perform(patch("/api/admin/schools/1/question-reports/" + reportId + "/status")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\",\"resolutionNote\":\"بررسی شد\"}"))
                .andExpect(status().isOk());
        JsonNode mine = findById(myReports(student.session()), reportId);
        assertThat(mine).isNotNull();
        assertThat(mine.get("status").asText()).isEqualTo("REVIEWED");
        assertThat(mine.get("resolutionNote").asText()).isEqualTo("بررسی شد");
    }

    @Test
    void studentCannotReportQuestionsOutsideTheirAssignedExams() throws Exception {
        MockHttpSession admin = adminSession();
        long classId = createClass(admin);
        Student student = enrolledStudent(admin, "outsider", classId);
        Student other = enrolledStudent(admin, "classmate", classId);

        long notInAnyExam = insertQuestion(1L, "EQR-" + tag + " بدون آزمون");
        long inOthersExam = insertQuestion(1L, "EQR-" + tag + " آزمون همکلاسی");
        examWithQuestion(admin, inOthersExam, other.studentId(), -5);
        long inUnassignedExam = insertQuestion(1L, "EQR-" + tag + " آزمون تخصیص‌نیافته");
        examWithQuestion(admin, inUnassignedExam, null, -5);
        long inFutureExam = insertQuestion(1L, "EQR-" + tag + " آزمون آینده");
        examWithQuestion(admin, inFutureExam, student.studentId(), 60);
        long platformNotInExam = insertQuestion(null, "EQR-" + tag + " پلتفرمی بدون آزمون");

        for (long q : new long[]{notInAnyExam, inOthersExam, inUnassignedExam, inFutureExam, platformNotInExam,
                987654321L}) {
            report(student.session(), 1, q, "TYPO", "EQR-" + tag)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value(endsWith("question-not-found")));
        }
        assertThat(reportCount()).isZero();
    }

    @Test
    void anotherSchoolsStudentCannotReportAndCannotUseThisSchoolsApi() throws Exception {
        MockHttpSession admin = adminSession();
        long classId = createClass(admin);
        Student student = enrolledStudent(admin, "home", classId);
        long question = insertQuestion(1L, "EQR-" + tag + " سؤال مدرسه‌ی ۱");
        examWithQuestion(admin, question, student.studentId(), -5);

        // مدرسه‌ی دیگر با entitlement آزمون و برنامه‌ی درسی، و یک عضو دانش‌آموز بدون ثبت‌نام در مدرسه‌ی ۱
        MockHttpSession founder = login(register("founder"), "password123");
        MvcResult created = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf()).session(founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی گزارش آزمون " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long otherSchool = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        jdbc.update("insert into iam.school_entitlements (active, module_id, school_id) "
                + "select true, id, ? from iam.modules where code in ('EXAM', 'CURRICULUM')", otherSchool);
        String awayEmail = register("away");
        mockMvc.perform(post("/api/admin/schools/" + otherSchool + "/memberships")
                        .with(csrf()).header("X-School-ID", String.valueOf(otherSchool)).session(founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + awayEmail + "\",\"fullName\":\"Away\",\"roleCode\":\"STUDENT\"}"))
                .andExpect(status().isOk());
        MockHttpSession away = login(awayEmail, "password123");

        // دانش‌آموز مدرسه‌ی دیگر: سؤال مدرسه‌ی ۱ در هیچ‌یک از آزمون‌های او نیست
        report(away, otherSchool, question, "TYPO", "EQR-" + tag).andExpect(status().isNotFound());
        // عضو مدرسه‌ی ۱ نیست، پس مسیر مدرسه‌ی ۱ را نمی‌تواند به کار ببرد (و برعکس)
        report(away, 1, question, "TYPO", "EQR-" + tag).andExpect(status().isForbidden());
        report(student.session(), otherSchool, question, "TYPO", "EQR-" + tag).andExpect(status().isForbidden());
        assertThat(reportCount()).isZero();
    }

    @Test
    void userWithoutQuestionReportPermissionGetsForbidden() throws Exception {
        MockHttpSession admin = adminSession();
        long classId = createClass(admin);
        Student student = enrolledStudent(admin, "perm", classId);
        long question = insertQuestion(1L, "EQR-" + tag + " مجوز");
        examWithQuestion(admin, question, student.studentId(), -5);
        MockHttpSession parent = memberSession("parent", "PARENT");

        report(parent, 1, question, "TYPO", "EQR-" + tag)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        mockMvc.perform(get(BASE + "1/question-reports").header("X-School-ID", "1").session(parent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        assertThat(reportCount()).isZero();
    }

    @Test
    void myReportsListsOnlyTheCallersOwnReports() throws Exception {
        MockHttpSession admin = adminSession();
        long classId = createClass(admin);
        Student first = enrolledStudent(admin, "first", classId);
        Student second = enrolledStudent(admin, "second", classId);
        long question = insertQuestion(1L, "EQR-" + tag + " مشترک");
        long examId = examWithQuestion(admin, question, first.studentId(), -5);
        assign(admin, examId, second.studentId());

        long firstReport = reportOk(first.session(), question, "EQR-" + tag + " a");
        long secondReport = reportOk(second.session(), question, "EQR-" + tag + " b");
        MvcResult adminCreated = mockMvc.perform(post("/api/admin/schools/1/questions/" + question + "/reports")
                        .with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"OTHER\",\"comment\":\"EQR-" + tag + " c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long adminReport = objectMapper.readTree(adminCreated.getResponse().getContentAsString()).get("id").asLong();

        JsonNode mine = myReports(first.session());
        assertThat(findById(mine, firstReport)).isNotNull();
        assertThat(findById(mine, secondReport)).isNull();
        assertThat(findById(mine, adminReport)).isNull();
        assertThat(findById(myReports(second.session()), firstReport)).isNull();
    }

    @Test
    void invalidReportTypeOrLongCommentIsRejected() throws Exception {
        MockHttpSession admin = adminSession();
        long classId = createClass(admin);
        Student student = enrolledStudent(admin, "invalid", classId);
        long question = insertQuestion(1L, "EQR-" + tag + " اعتبارسنجی");
        examWithQuestion(admin, question, student.studentId(), -5);

        report(student.session(), 1, question, "NOPE", "EQR-" + tag).andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "1/questions/" + question + "/reports")
                        .with(csrf()).header("X-School-ID", "1").session(student.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"EQR-" + tag + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-question-report")));
        report(student.session(), 1, question, "TYPO", "EQR-" + tag + "x".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-question-report")));
        assertThat(reportCount()).isZero();
    }
}
