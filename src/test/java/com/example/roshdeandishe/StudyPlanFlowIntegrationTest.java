package com.example.roshdeandishe;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.DaySlot;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.PlanId;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.RecentTopic;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.StudyIntervalInfo;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ماژول studyplan (پورت moshavere، قدم ۳ - MOSHAVERE_PORT_SPEC.md §6):
 * برنامه‌ی مطالعاتی + الگوریتم پیشنهاد خودکار. مهم‌ترین رفتار تست‌شده:
 * پورت table_suggester.py (SliceScheduleInterval + FillSlices) روی یک
 * برنامه‌ی زمانیِ دستچین‌شده که نتیجه‌ی دقیقش از پیش قابل محاسبه است -
 * رجوع به کامنت‌های داخل هر تست برای محاسبه‌ی دستی.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudyPlanFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudyPlanFacade studyPlanFacade;

    @Autowired
    private CurriculumFacade curriculumFacade;

    @Autowired
    private AcademicFacade academicFacade;

    private static final LocalDate SATURDAY = LocalDate.of(2026, 7, 18);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 7, 19);

    private Long newStudent(Long schoolId, String email) {
        Long classId = academicFacade.createClass(schoolId, "کلاس آزمایشی", null, "الف").value();
        return academicFacade.enrollStudent(email, "دانش‌آموز آزمایشی", schoolId, classId).value();
    }

    private Long newTopic(Long schoolId, String name) {
        return curriculumFacade.createTopic(schoolId, null, name, 0).value();
    }

    private Map<String, List<DaySlot>> emptySchedule() {
        Map<String, List<DaySlot>> schedule = new LinkedHashMap<>();
        for (String day : List.of("sat", "sun", "mon", "tue", "wed", "thur", "fri")) {
            schedule.put(day, List.of());
        }
        return schedule;
    }

    @Test
    void suggestWeekAllocatesEveryBlockToTheOnlyWeightedTopic() {
        Long schoolId = 9401L;
        Long studentId = newStudent(schoolId, "studyplan-solo@example.com");
        Long topicId = newTopic(schoolId, "ریاضی");
        Long subTopicId = newTopic(schoolId, "جبر");

        PlanId planId = studyPlanFacade.createPlan(
                schoolId, 555L, "برنامه‌ی نمونه",
                List.of(new CourseWeight(topicId, 1.0, 1))
        );
        studyPlanFacade.assignPlanToStudent(schoolId, studentId, planId.value());
        studyPlanFacade.createWeekCourse(schoolId, planId.value(), SATURDAY, topicId, List.of(subTopicId));

        // شنبه ۰۸:۰۰-۱۲:۰۰ (۲۴۰ دقیقه) + یکشنبه ۰۸:۰۰-۱۰:۰۰ (۱۲۰ دقیقه).
        // محاسبه‌ی دستیِ SliceScheduleInterval: شنبه -> ۲ بلوکِ ۹۰دقیقه‌ای +
        // ۱ بلوکِ ۳۰دقیقه‌ای (۳۰ دقیقه‌ی باقیمانده کمتر از کوچک‌ترین سایز
        // است، مصرف نمی‌شود)؛ یکشنبه -> ۱ بلوکِ ۹۰دقیقه‌ای (۱۵ دقیقه باقی
        // می‌ماند). جمعاً ۴ بلوک، ۳۰۰ دقیقه، همه روی تنها درسِ وزن‌دار.
        Map<String, List<DaySlot>> schedule = emptySchedule();
        schedule.put("sat", List.of(new DaySlot("08:00", "12:00")));
        schedule.put("sun", List.of(new DaySlot("08:00", "10:00")));
        studyPlanFacade.updateCurrentSchedule(schoolId, studentId, schedule);

        List<StudyIntervalInfo> created = studyPlanFacade.suggestWeek(schoolId, studentId, SATURDAY);

        assertThat(created).hasSize(4);
        assertThat(created).allMatch(i -> i.topicId().equals(topicId));
        assertThat(created).allMatch(i -> i.subTopicIds().equals(List.of(subTopicId)));
        long ninetyMinuteBlocks = created.stream()
                .filter(i -> java.time.Duration.between(i.startTime(), i.endTime()).toMinutes() == 90)
                .count();
        long thirtyMinuteBlocks = created.stream()
                .filter(i -> java.time.Duration.between(i.startTime(), i.endTime()).toMinutes() == 30)
                .count();
        assertThat(ninetyMinuteBlocks).isEqualTo(3);
        assertThat(thirtyMinuteBlocks).isEqualTo(1);

        // getWeek باید همان چهار بازه را برگرداند
        assertThat(studyPlanFacade.getWeek(schoolId, studentId, SATURDAY)).hasSize(4);

        // پیشنهاد دوباره روی هفته‌ای که خالی نیست: WeekNotEmpty
        assertThatThrownBy(() -> studyPlanFacade.suggestWeek(schoolId, studentId, SATURDAY))
                .isInstanceOf(StudyPlanException.class);

        // پاک کردن هفته، سپس هفته باید خالی باشد
        studyPlanFacade.clearWeek(schoolId, studentId, SATURDAY);
        assertThat(studyPlanFacade.getWeek(schoolId, studentId, SATURDAY)).isEmpty();
    }

    @Test
    void nonSaturdayWeekStartDateIsRejected() {
        Long schoolId = 9402L;
        Long studentId = newStudent(schoolId, "studyplan-sunday@example.com");

        assertThatThrownBy(() -> studyPlanFacade.suggestWeek(schoolId, studentId, SUNDAY))
                .isInstanceOf(StudyPlanException.class);
        assertThatThrownBy(() -> studyPlanFacade.getWeek(schoolId, studentId, SUNDAY))
                .isInstanceOf(StudyPlanException.class);
    }

    @Test
    void scheduleWithLessThanThreeHoursFreeIsRejected() {
        Long schoolId = 9403L;
        Long studentId = newStudent(schoolId, "studyplan-tooshort@example.com");
        Long topicId = newTopic(schoolId, "شیمی");

        PlanId planId = studyPlanFacade.createPlan(
                schoolId, 555L, "برنامه‌ی کوتاه", List.of(new CourseWeight(topicId, 1.0, 1))
        );
        studyPlanFacade.assignPlanToStudent(schoolId, studentId, planId.value());

        // فقط ۶۰ دقیقه‌ی آزاد در کل هفته - کمتر از حداقل ۱۸۰ دقیقه
        Map<String, List<DaySlot>> schedule = emptySchedule();
        schedule.put("sat", List.of(new DaySlot("08:00", "09:00")));
        studyPlanFacade.updateCurrentSchedule(schoolId, studentId, schedule);

        assertThatThrownBy(() -> studyPlanFacade.suggestWeek(schoolId, studentId, SATURDAY))
                .isInstanceOf(StudyPlanException.class);
    }

    @Test
    void suggestWeekWithoutAssignedPlanIsRejected() {
        Long schoolId = 9404L;
        Long studentId = newStudent(schoolId, "studyplan-noplan@example.com");

        Map<String, List<DaySlot>> schedule = emptySchedule();
        schedule.put("sat", List.of(new DaySlot("08:00", "12:00")));
        studyPlanFacade.updateCurrentSchedule(schoolId, studentId, schedule);

        assertThatThrownBy(() -> studyPlanFacade.suggestWeek(schoolId, studentId, SATURDAY))
                .isInstanceOf(StudyPlanException.class);
    }

    @Test
    void getRecentTopicsReflectsTheCurrentCalendarWeek() {
        Long schoolId = 9405L;
        Long studentId = newStudent(schoolId, "studyplan-recent@example.com");
        Long topicId = newTopic(schoolId, "فیزیک");

        PlanId planId = studyPlanFacade.createPlan(
                schoolId, 555L, "برنامه‌ی هفته‌ی جاری", List.of(new CourseWeight(topicId, 1.0, 1))
        );
        studyPlanFacade.assignPlanToStudent(schoolId, studentId, planId.value());

        Map<String, List<DaySlot>> schedule = emptySchedule();
        schedule.put("sat", List.of(new DaySlot("08:00", "12:00")));
        schedule.put("sun", List.of(new DaySlot("08:00", "10:00")));
        studyPlanFacade.updateCurrentSchedule(schoolId, studentId, schedule);

        LocalDate today = LocalDate.now();
        int daysSinceSaturday = (today.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7;
        LocalDate currentWeekStart = today.minusDays(daysSinceSaturday);

        studyPlanFacade.suggestWeek(schoolId, studentId, currentWeekStart);

        List<RecentTopic> recentTopics = studyPlanFacade.getRecentTopics(schoolId, studentId);
        assertThat(recentTopics).extracting(RecentTopic::topicId).containsExactly(topicId);
    }

    @Test
    void teacherCanAuthorPlansButParentCannotViewThem() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        MockHttpSession teacherSession = registerAndLogin("studyplan.teacher@roshdeandishe.test", "معلم برنامه‌ریز");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"studyplan.teacher@roshdeandishe.test\","
                                + "\"fullName\":\"معلم برنامه‌ریز\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());

        Long topicId = newTopic(1L, "سرفصل برنامه‌ریزِ معلم");
        mockMvc.perform(post("/api/admin/schools/1/plans")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(teacherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":1,\"title\":\"برنامه‌ی معلم\","
                                + "\"courseInfo\":[{\"topicId\":" + topicId + ",\"weight\":1.0,\"priority\":1}]}"))
                .andExpect(status().isOk());

        MockHttpSession parentSession = registerAndLogin("studyplan.parent@roshdeandishe.test", "والد کنجکاو");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"studyplan.parent@roshdeandishe.test\","
                                + "\"fullName\":\"والد کنجکاو\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/plans")
                        .header("X-School-ID", "1")
                        .session(parentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void schoolWithoutStudyPlannerEntitlementGetsModuleNotEntitled() throws Exception {
        MockHttpSession founderSession =
                registerAndLogin("studyplan.founder@roshdeandishe.test", "بنیان‌گذار بدون برنامه‌ریز");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بدون برنامه‌ریز\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/plans")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }

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
}
