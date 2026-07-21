package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
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
 * ماژول studyplan (پورت moshavere، MOSHAVERE_PORT_SPEC.md §6): برنامه‌ی
 * درسی + وزن‌دهی درس‌ها + سرفصل‌های هفتگی + تخصیص به دانش‌آموز + برنامه‌ی
 * زمانی آزاد + پیشنهاد خودکار جدول مطالعه (table_suggester). الگوی
 * لاگین/عضویت از CurriculumManagementFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudyPlanManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

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
                        .content("{\"name\":\"" + name + "\",\"parentId\":null,\"order\":0}"))
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

    private long enrollStudent(MockHttpSession session, String email, String fullName, long classId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
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

    @Test
    void adminCanCreatePlanAssignStudentAndSuggestWeek() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long topicId = createTopic(session, "ریاضی برنامه‌ریزی");
        long classId = createClass(session, "پایه چهارم - پ");
        long studentId = enrollStudent(session, "planner.pupil@roshdeandishe.test", "دانش‌آموز برنامه‌دار", classId);
        long planId = createPlan(session, "برنامه‌ی نمونه", topicId);

        assignPlan(session, planId, studentId);

        mockMvc.perform(get("/api/admin/schools/1/study-plans/" + planId + "/assignments")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")]").exists());

        setRoomySchedule(session, studentId);

        LocalDate weekStart = nextSaturdayStrictlyAfter(LocalDate.now());
        MvcResult suggestResult = mockMvc.perform(post(
                        "/api/admin/schools/1/students/" + studentId + "/study-intervals/suggest")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode intervals = objectMapper.readTree(suggestResult.getResponse().getContentAsString());
        assertThat(intervals.isArray()).isTrue();
        assertThat(intervals.size()).isGreaterThan(0);
        for (JsonNode interval : intervals) {
            assertThat(interval.get("topicId").asLong()).isEqualTo(topicId);
        }

        // هفته دیگر خالی نیست - پیشنهاد دوباره باید رد شود
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/study-intervals/suggest")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("week-not-empty")));

        // خالی کردن هفته، سپس پیشنهاد دوباره باید کار کند
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/study-intervals/clear")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/students/" + studentId + "/study-intervals")
                        .header("X-School-ID", "1")
                        .session(session)
                        .param("date", weekStart.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/study-intervals/suggest")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void suggestingForUnassignedStudentIsConflict() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long classId = createClass(session, "پایه چهارم - ت");
        long studentId = enrollStudent(session, "unassigned.pupil@roshdeandishe.test", "دانش‌آموز بی‌برنامه", classId);
        setRoomySchedule(session, studentId);

        LocalDate weekStart = nextSaturdayStrictlyAfter(LocalDate.now());
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/study-intervals/suggest")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .param("weekStart", weekStart.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("plan-not-assigned")));
    }

    @Test
    void suggestingWithNonSaturdayWeekStartIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long classId = createClass(session, "پایه چهارم - ث");
        long studentId = enrollStudent(session, "badweek.pupil@roshdeandishe.test", "دانش‌آموز هفته‌ی نادرست", classId);

        LocalDate notSaturday = nextSaturdayStrictlyAfter(LocalDate.now()).plusDays(1);
        mockMvc.perform(post("/api/admin/schools/1/students/" + studentId + "/study-intervals/suggest")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .param("weekStart", notSaturday.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-week-start")));
    }

    @Test
    void creatingPlanWithUnknownTopicIsNotFound() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/api/admin/schools/1/study-plans")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"برنامه با سرفصل ناموجود\",\"weights\":[{\"topicId\":999999,\"weight\":1.0,\"priority\":1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("topic-not-found")));
    }

    @Test
    void teacherCanAuthorPlans() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MockHttpSession teacherSession = registerAndLogin("studyplan.teacher@roshdeandishe.test", "معلم برنامه‌ریز");

        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"studyplan.teacher@roshdeandishe.test\",\"fullName\":\"معلم برنامه‌ریز\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());

        long topicId = createTopic(teacherSession, "سرفصل معلم برنامه‌ریز");
        createPlan(teacherSession, "برنامه‌ی معلم", topicId);
    }

    @Test
    void studentCannotAccessStudyPlans() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MockHttpSession studentSession = registerAndLogin("studyplan.student@roshdeandishe.test", "دانش‌آموز کنجکاو برنامه");

        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"studyplan.student@roshdeandishe.test\",\"fullName\":\"دانش‌آموز کنجکاو برنامه\",\"roleCode\":\"STUDENT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/study-plans")
                        .header("X-School-ID", "1")
                        .session(studentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void schoolWithoutStudyPlannerEntitlementGetsModuleNotEntitled() throws Exception {
        MockHttpSession founderSession = registerAndLogin("studyplan.founder@roshdeandishe.test", "بنیان‌گذار بدون ماژول برنامه");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بدون برنامه‌ریز\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // ساخت خودسرویس یک بسته‌ی اشتراک آزمایشی می‌دهد (CLAUDE.md §11 Q10) -
        // برای تست مسیر module-not-entitled، همان entitlement تازه‌اعطاشده را
        // صراحتاً لغو می‌کنیم (facade-only، بدون REST - CLAUDE.md §10)
        iamFacade.setSchoolEntitlement(schoolId, new IamFacade.ModuleCode("STUDY_PLANNER"), false);

        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/study-plans")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }

    @Test
    void listingPlansWithoutTenantHeaderIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/study-plans").session(session))
                .andExpect(status().isBadRequest());
    }
}
