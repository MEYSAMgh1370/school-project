package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ماژول exam (پورت moshavere، قدم ۴ و آخر - MOSHAVERE_PORT_SPEC.md §5):
 * آزمون جمعیِ تألیف‌شده + آزمون هفتگیِ خودکار + جلسه‌ی آزمون‌دهیِ توکن‌محور
 * + امتیازدهی برخط. الگوی لاگین/عضویت از StudyPlan/CurriculumFlowIntegrationTest
 * کپی شده؛ الگوی ثبت‌نام+ثبت‌نام‌دانش‌آموزی (برای گرفتن یک سشن واقعیِ
 * دانش‌آموز) از MyEnrollmentFlowIntegrationTest.
 * <p>
 * چند تست به زمان واقعی (Thread.sleep) وابسته‌اند: این کدبیس هیچ انتزاع
 * Clock ندارد (ExamFacadeImpl هم مثل StudyPlanFacadeImpl مستقیم
 * LocalDateTime.now()/Instant.now() صدا می‌زند)، پس تنها راه تست «آزمون
 * تمام شده» همان صبر واقعی روی یک پنجره‌ی زمانی کوتاه است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExamFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
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
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private long createTopic(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"name\":\"" + name + "\",\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createQuestion(MockHttpSession session, long topicId, String text) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"text\":\"" + text + "\","
                                + "\"choice1\":\"الف\",\"choice2\":\"ب\",\"choice3\":\"ج\",\"choice4\":\"د\","
                                + "\"correctChoice\":2,\"answer\":\"پاسخ تشریحی\",\"difficulty\":null}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createClass(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"teacherId\":null,\"section\":null,\"gradeLevel\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    /**
     * ثبت‌نام self-service (برای رمز واقعی) + ثبت‌نامِ دانش‌آموزیِ همان ایمیل
     * (idempotent روی findOrCreateUser، رجوع به MyEnrollmentFlowIntegrationTest) +
     * ورود - یک سشن واقعیِ دانش‌آموز که هم StudentEntity دارد هم می‌تواند
     * لاگین کند.
     */
    private long[] enrollAndLoginStudent(MockHttpSession adminSession, String email, String fullName, long classId)
            throws Exception {
        registerAndLogin(email, fullName);
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        long studentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("studentId").asLong();
        return new long[]{studentId};
    }

    private long createExam(MockHttpSession session, String title, LocalDateTime start, LocalDateTime end,
                            int durationMinutes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/exams")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"start\":\"" + start + "\",\"end\":\"" + end
                                + "\",\"durationMinutes\":" + durationMinutes + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createManualSection(MockHttpSession session, long examId, long topicId, long questionId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/exams/" + examId + "/sections")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"name\":null,\"questionIds\":[" + questionId + "]}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void assignStudent(MockHttpSession session, long examId, long studentId) throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/exams/" + examId + "/assign")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[" + studentId + "]}"))
                .andExpect(status().isOk());
    }

    private String startAttempt(MockHttpSession studentSession, long examId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/me/schools/1/exams/" + examId + "/attempts")
                        .with(csrf())
                        .session(studentSession))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private static LocalDate nextSaturdayStrictlyAfter(LocalDate date) {
        return date.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    }

    @Test
    void adminCanAuthorExamAndStudentCanAttemptTakeSubmitAndSeeResult() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        long topicId = createTopic(adminSession, "سرفصل آزمون جمعی");
        long questionId = createQuestion(adminSession, topicId, "سؤال آزمون جمعی");
        long classId = createClass(adminSession, "کلاس آزمون جمعی");
        long studentId = enrollAndLoginStudent(adminSession, "exam.taker@roshdeandishe.test",
                "دانش‌آموز آزمون‌دهنده", classId)[0];
        MockHttpSession studentSession = login("exam.taker@roshdeandishe.test", "password123");

        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusSeconds(6);
        // durationMinutes=1: باید <= بازه‌ی زمان‌بندی‌شده‌ی آزمون (~۱ دقیقه، پس
        // از truncation دقیقه‌ای toMinutes) باشد؛ در startAttempt همین مقدار
        // (۱ دقیقه) از فاصله‌ی واقعیِ تا پایان آزمون (~۶ ثانیه) بزرگ‌تر است، پس
        // expireAt همان exam.end می‌شود - دقیقاً پنجره‌ی کوتاهی که این تست می‌خواهد.
        long examId = createExam(adminSession, "آزمون جمعی نمونه", start, end, 1);
        long sectionId = createManualSection(adminSession, examId, topicId, questionId);
        assignStudent(adminSession, examId, studentId);

        // دانش‌آموزِ تخصیص‌نیافته آزمونش را در فهرست «آزمون‌های من» می‌بیند
        mockMvc.perform(get("/api/me/schools/1/exams").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + examId + ")]").exists());

        String token = startAttempt(studentSession, examId);
        assertThat(token).isNotBlank();

        // شروع دوباره‌ی تلاش تا وقتی منقضی نشده idempotent است - همان توکن
        // معتبر دوباره صادر می‌شود، نه cannot-attempt/attempt-expired
        String tokenAgain = startAttempt(studentSession, examId);
        assertThat(tokenAgain).isNotBlank();

        mockMvc.perform(get("/api/exam-take/exam").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(examId))
                .andExpect(jsonPath("$.sections[0].id").value(sectionId));

        mockMvc.perform(get("/api/exam-take/sections/" + sectionId + "/questions").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].questionId").value(questionId))
                .andExpect(jsonPath("$[0].selectedChoice").doesNotExist());

        mockMvc.perform(post("/api/exam-take/submit").param("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":" + sectionId + ",\"questionId\":" + questionId + ",\"choice\":2}"))
                .andExpect(status().isOk());

        // پاسخ ثبت‌شده در فراخوانی بعدی برمی‌گردد (correctChoice=2 هم بود، پس درست است)
        mockMvc.perform(get("/api/exam-take/sections/" + sectionId + "/questions").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].selectedChoice").value(2));

        // قبل از پایان آزمون، نتیجه هنوز قابل مشاهده نیست
        mockMvc.perform(get("/api/me/schools/1/exams/" + examId + "/result").session(studentSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("exam-not-ended")));

        Thread.sleep(7000);

        mockMvc.perform(get("/api/me/schools/1/exams/" + examId + "/result").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.sections[0].correctCount").value(1))
                .andExpect(jsonPath("$.sections[0].incorrectCount").value(0))
                .andExpect(jsonPath("$.sections[0].unansweredCount").value(0))
                .andExpect(jsonPath("$.overallScore").value(100.0));

        // سمت مدرس/ادمین هم همان نتیجه و فهرست تلاش‌کنندگان
        mockMvc.perform(get("/api/admin/schools/1/exams/" + examId + "/results")
                        .header("X-School-ID", "1")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")]").exists());
        mockMvc.perform(get("/api/admin/schools/1/exams/" + examId + "/results/" + studentId)
                        .header("X-School-ID", "1")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore").value(100.0));

        // آزمون تمام شده - تلاش تازه دیگر ممکن نیست (چک پایان‌یافتنِ خودِ آزمون
        // قبل از چک انقضای تلاش اجرا می‌شود، همان اولویتِ ExamFacadeImpl.startAttempt)
        mockMvc.perform(post("/api/me/schools/1/exams/" + examId + "/attempts")
                        .with(csrf())
                        .session(studentSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("cannot-attempt")));

        // توکنِ (حالا منقضی‌شده) دیگر برای exam-take معتبر نیست
        mockMvc.perform(get("/api/exam-take/exam").param("token", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-exam-token")));
    }

    @Test
    void autoSectionRespectsLazyFlagForInsufficientQuestions() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        long topicId = createTopic(adminSession, "سرفصل کم‌سؤال");
        createQuestion(adminSession, topicId, "تنها سؤال این سرفصل");

        long examId = createExam(adminSession, "آزمون auto-pick",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), 30);

        mockMvc.perform(post("/api/admin/schools/1/exams/" + examId + "/sections/auto")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"name\":null,\"count\":3,\"lazy\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("insufficient-questions")));

        mockMvc.perform(post("/api/admin/schools/1/exams/" + examId + "/sections/auto")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"name\":null,\"count\":3,\"lazy\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionCount").value(1));
    }

    @Test
    void studentCannotAttemptExamTheyAreNotAssignedTo() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        long classId = createClass(adminSession, "کلاس بدون تخصیص");
        long studentId = enrollAndLoginStudent(adminSession, "unassigned.exam.student@roshdeandishe.test",
                "دانش‌آموز بدون تخصیص", classId)[0];
        MockHttpSession studentSession = login("unassigned.exam.student@roshdeandishe.test", "password123");

        long examId = createExam(adminSession, "آزمونی که به کسی تخصیص نیافته",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(30), 10);

        mockMvc.perform(post("/api/me/schools/1/exams/" + examId + "/attempts")
                        .with(csrf())
                        .session(studentSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("cannot-attempt")));

        assertThat(studentId).isPositive();
    }

    @Test
    void weeklyExamIsGeneratedFromRecentStudyTopics() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        long topicId = createTopic(adminSession, "سرفصل مطالعه‌شده‌ی اخیر");
        for (int i = 0; i < 3; i++) {
            createQuestion(adminSession, topicId, "سؤال هفتگی " + i);
        }
        long classId = createClass(adminSession, "کلاس آزمون هفتگی");
        long studentId = enrollAndLoginStudent(adminSession, "weekly.exam.student@roshdeandishe.test",
                "دانش‌آموز آزمون هفتگی", classId)[0];

        // یک بازه‌ی مطالعه‌ی واقعی در همین هفته - همان چیزی که
        // StudyPlanFacade.getRecentTopics می‌خواند
        LocalDateTime intervalStart = LocalDateTime.now().minusHours(2);
        LocalDateTime intervalEnd = LocalDateTime.now().minusHours(1);
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/study-intervals")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"subTopicIds\":[],\"start\":\"" + intervalStart
                                + "\",\"end\":\"" + intervalEnd + "\"}"))
                .andExpect(status().isOk());

        LocalDate weekStart = LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY
                ? LocalDate.now()
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));

        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/exams/weekly")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .param("studentId", String.valueOf(studentId))
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WEEKLY"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.sections[0].topicId").value(topicId))
                .andReturn();
        JsonNode exam = objectMapper.readTree(result.getResponse().getContentAsString());
        int questionCount = exam.get("sections").get(0).get("questionCount").asInt();
        // lazy=true: تا ۵ تا، ولی نه بیشتر از سؤال‌های موجود (اینجا ۳)
        assertThat(questionCount).isEqualTo(3);

        // بازتولید (بدون تلاشِ ثبت‌شده) مجاز است - جایگزینیِ کامل
        MvcResult regenerated = mockMvc.perform(post("/api/admin/schools/1/exams/weekly")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .param("studentId", String.valueOf(studentId))
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(regenerated.getResponse().getContentAsString()).get("id").asLong())
                .isNotEqualTo(exam.get("id").asLong());
    }

    @Test
    void teacherCanAuthorButStudentGetsPermissionDeniedOnAdminEndpoints() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        MockHttpSession teacherSession = registerAndLogin("exam.teacher@roshdeandishe.test", "معلم آزمون‌ساز");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"exam.teacher@roshdeandishe.test\",\"fullName\":\"معلم آزمون‌ساز\","
                                + "\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());

        long examId = createExam(teacherSession, "آزمون تألیف‌شده‌ی معلم",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1), 20);
        assertThat(examId).isPositive();

        MockHttpSession studentSession = registerAndLogin("exam.student.noaccess@roshdeandishe.test",
                "دانش‌آموز بدون دسترسی مدیریتی");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"exam.student.noaccess@roshdeandishe.test\","
                                + "\"fullName\":\"دانش‌آموز بدون دسترسی مدیریتی\",\"roleCode\":\"STUDENT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/exams")
                        .header("X-School-ID", "1")
                        .session(studentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void schoolWithoutExamEntitlementGetsModuleNotEntitled() throws Exception {
        MockHttpSession founderSession =
                registerAndLogin("exam.founder@roshdeandishe.test", "بنیان‌گذار بدون ماژول آزمون");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بدون آزمون\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/exams")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }
}
