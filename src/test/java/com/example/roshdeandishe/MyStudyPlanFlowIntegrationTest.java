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
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «برنامه‌ی مطالعه‌ی من» (my.StudyIntervalController، صفحه‌ی
 * /my-study-plan) - سمت دانش‌آموز روی بازه‌های واقعیِ ماژول studyplan.
 * الگوی لاگین/ثبت‌نام/تخصیص از StudyPlanManagementFlowIntegrationTest و
 * الگوی «ثبت‌نام + ثبت‌نامِ دانش‌آموزیِ همان ایمیل» (برای گرفتن سشنِ
 * واقعیِ دانش‌آموز) از ExamFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyStudyPlanFlowIntegrationTest {

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
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private long createTopic(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"parentId\":null,\"sortOrder\":0}"))
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
     * (idempotent روی findOrCreateUser) - یک سشن واقعیِ دانش‌آموز که هم
     * StudentEntity دارد هم می‌تواند لاگین کند (همان الگوی
     * ExamFlowIntegrationTest.enrollAndLoginStudent).
     */
    private long enrollAndLoginStudent(MockHttpSession adminSession, String email, String fullName, long classId)
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("studentId").asLong();
    }

    private long createPlan(MockHttpSession session, String title, long topicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/study-plans")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"weights\":[{\"topicId\":" + topicId
                                + ",\"weight\":1.0,\"priority\":1}]}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void assignPlan(MockHttpSession session, long planId, long studentId) throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/study-plans/" + planId + "/assignments")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isOk());
    }

    private void setRoomySchedule(MockHttpSession session, long studentId) throws Exception {
        // شنبه ۹ تا ۱۳ - ۲۴۰ دقیقه‌ی آزاد، خیلی بیشتر از حداقل ۱۸۰ دقیقه‌ی لازم
        mockMvc.perform(put("/api/admin/schools/1/students/" + studentId + "/schedule/default")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SAT\":[{\"start\":\"09:00\",\"end\":\"13:00\"}],\"SUN\":[],\"MON\":[],\"TUE\":[],"
                                + "\"WED\":[],\"THU\":[],\"FRI\":[]}"))
                .andExpect(status().isOk());
    }

    private static LocalDate nextSaturdayStrictlyAfter(LocalDate date) {
        return date.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    }

    private long suggestWeek(MockHttpSession adminSession, long studentId, LocalDate weekStart) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/study-intervals/suggest")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode intervals = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(intervals.size()).isGreaterThan(0);
        return intervals.get(0).get("id").asLong();
    }

    @Test
    void studentSeesOwnWeekWithTopicNamesAndCanLogItsOwnInterval() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        long topicId = createTopic(adminSession, "جبر و معادلات");
        long classId = createClass(adminSession, "پایه هشتم - آ");
        long studentId = enrollAndLoginStudent(adminSession, "myplan.pupil@roshdeandishe.test", "دانش‌آموز برنامه‌ی من", classId);
        long planId = createPlan(adminSession, "برنامه‌ی من - نمونه", topicId);
        assignPlan(adminSession, planId, studentId);
        setRoomySchedule(adminSession, studentId);

        LocalDate weekStart = nextSaturdayStrictlyAfter(LocalDate.now());
        long intervalId = suggestWeek(adminSession, studentId, weekStart);

        MockHttpSession studentSession = login("myplan.pupil@roshdeandishe.test", "password123");

        MvcResult weekResult = mockMvc.perform(get("/api/me/schools/1/study-intervals")
                        .session(studentSession)
                        .param("date", weekStart.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode week = objectMapper.readTree(weekResult.getResponse().getContentAsString());
        assertThat(week.isArray()).isTrue();
        assertThat(week.size()).isGreaterThan(0);
        for (JsonNode interval : week) {
            assertThat(interval.get("topicId").asLong()).isEqualTo(topicId);
            assertThat(interval.get("topicName").asText()).isEqualTo("جبر و معادلات");
            assertThat(interval.get("done").asBoolean()).isFalse();
        }

        mockMvc.perform(patch("/api/me/schools/1/study-intervals/" + intervalId)
                        .with(csrf())
                        .session(studentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true,\"testCount\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.testCount").value(3))
                .andExpect(jsonPath("$.topicName").value("جبر و معادلات"));

        MvcResult reloadResult = mockMvc.perform(get("/api/me/schools/1/study-intervals")
                        .session(studentSession)
                        .param("date", weekStart.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reloaded = objectMapper.readTree(reloadResult.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode interval : reloaded) {
            if (interval.get("id").asLong() == intervalId) {
                assertThat(interval.get("done").asBoolean()).isTrue();
                assertThat(interval.get("testCount").asInt()).isEqualTo(3);
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void studentCannotEditAnotherStudentsInterval() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        long topicId = createTopic(adminSession, "زیست‌شناسی سلولی");
        long classId = createClass(adminSession, "پایه هشتم - ب");

        long ownerStudentId = enrollAndLoginStudent(adminSession, "myplan.owner@roshdeandishe.test", "دانش‌آموز صاحب بازه", classId);
        long intruderStudentId = enrollAndLoginStudent(adminSession, "myplan.intruder@roshdeandishe.test", "دانش‌آموز مزاحم", classId);

        long planId = createPlan(adminSession, "برنامه‌ی مالکیت", topicId);
        assignPlan(adminSession, planId, ownerStudentId);
        setRoomySchedule(adminSession, ownerStudentId);
        // دانش‌آموز مزاحم هم باید یک بازه‌ی خالی داشته باشد تا واقعاً دانش‌آموز
        // این مدرسه محسوب شود - وگرنه تفاوتی با «اصلاً دانش‌آموز نیست» ندارد.
        setRoomySchedule(adminSession, intruderStudentId);

        LocalDate weekStart = nextSaturdayStrictlyAfter(LocalDate.now());
        long ownerIntervalId = suggestWeek(adminSession, ownerStudentId, weekStart);

        MockHttpSession intruderSession = login("myplan.intruder@roshdeandishe.test", "password123");

        mockMvc.perform(patch("/api/me/schools/1/study-intervals/" + ownerIntervalId)
                        .with(csrf())
                        .session(intruderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true,\"testCount\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("interval-not-found")));
    }

    @Test
    void nonStudentSeesEmptyWeekWithoutError() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        MvcResult weekResult = mockMvc.perform(get("/api/me/schools/1/study-intervals").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode week = objectMapper.readTree(weekResult.getResponse().getContentAsString());
        assertThat(week.isArray()).isTrue();
        assertThat(week.size()).isEqualTo(0);
    }

    @Test
    void myStudyPlanPageRedirectsToLoginWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/my-study-plan"))
                .andExpect(status().is3xxRedirection());
    }
}
