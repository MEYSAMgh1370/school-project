package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * مسیر REST مدیر پلتفرم برای تعطیلی‌های سراسری (yearly-plan.md فاز C) روی Postgres واقعی: مدیر پلتفرم
 * (کاربرِ ثبت‌نام‌شده‌ی فهرست‌شده در PLATFORM_ADMIN_EMAILS، بدون هیچ عضویت مدرسه) سال‌ها و تعطیلی‌ها را
 * مدیریت می‌کند و اثرش در برنامه‌ی سالانه و isClosedDay همه‌ی مدرسه‌ها دیده می‌شود؛ مدیر مدرسه و
 * دانش‌آموز و ناشناس هرگز به این مسیرها نمی‌رسند و مدیر پلتفرم به مسیرهای مدرسه هم نمی‌رسد.
 */
@SpringBootTest(properties = "roshdeandishe.platform-admin.emails=platform-holiday-flow@roshdeandishe.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformHolidayFlowIntegrationTest {

    private static final String PLATFORM_EMAIL = "platform-holiday-flow@roshdeandishe.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<ApplicationRunner> runners;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdHolidayIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // تعطیلیِ سراسری همه‌ی مدرسه‌ها را اثر می‌دهد و DB بین کلاس‌های تست مشترک است
        for (Long id : createdHolidayIds) {
            jdbc.update("delete from school.platform_holidays where id = ?", id);
        }
        createdHolidayIds.clear();
        jdbc.update("delete from iam.platform_admins");
    }

    private AcademicYearInfo year() {
        return schoolFacade.findCurrentAcademicYear(new SchoolId(1L))
                .orElseGet(() -> schoolFacade.listAcademicYears().get(0));
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

    /** کاربر پس از بالا آمدن اپ ثبت‌نام می‌کند، پس provisioner باید دوباره اجرا شود. */
    private MockHttpSession platformAdminSession() throws Exception {
        registerIfAbsent(PLATFORM_EMAIL);
        ApplicationRunner provisioner = runners.stream()
                .filter(r -> AopUtils.getTargetClass(r).getSimpleName().equals("PlatformAdminProvisioner"))
                .findFirst()
                .orElseThrow();
        provisioner.run(new DefaultApplicationArguments());
        return login(PLATFORM_EMAIL, "password123");
    }

    private String holidayBody(LocalDate day, String title) {
        return "{\"day\":\"" + day + "\",\"title\":\"" + title + "\"}";
    }

    private ResultActions postHoliday(MockHttpSession session, long yearId, String body) throws Exception {
        return mockMvc.perform(post("/api/platform/academic-years/" + yearId + "/holidays")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long addHoliday(MockHttpSession session, long yearId, LocalDate day, String title) throws Exception {
        MvcResult result = postHoliday(session, yearId, holidayBody(day, title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").value(day.toString()))
                .andExpect(jsonPath("$.title").value(title))
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        createdHolidayIds.add(id);
        return id;
    }

    @Test
    void platformAdminManagesAHolidayAndEverySchoolSeesItAndIsClosed() throws Exception {
        MockHttpSession platform = platformAdminSession();
        MockHttpSession schoolAdmin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();
        // آخرین روزِ سال: دور از «امروز»ِ تست‌های حضور و غیاب
        LocalDate day = year.endDate();
        String title = "تعطیلی سراسریِ مسیر پلتفرم";
        SchoolId one = new SchoolId(1L);
        SchoolId other = schoolFacade.createSchool(
                "مدرسه‌ی تعطیلی سراسری دو", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null);

        // فهرست سال‌ها: بازه‌ی پیش‌فرض، از قدیم به جدید
        mockMvc.perform(get("/api/platform/academic-years").session(platform))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + yearId + ")].startDate").value(hasItem(year.startDate().toString())))
                .andExpect(jsonPath("$[?(@.id == " + yearId + ")].endDate").value(hasItem(year.endDate().toString())))
                .andExpect(jsonPath("$[?(@.id == " + yearId + ")].code").value(hasItem(year.code())));

        long holidayId = addHoliday(platform, yearId, day, title);
        try {
            mockMvc.perform(get("/api/platform/academic-years/" + yearId + "/holidays").session(platform))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + holidayId + ")].day").value(hasItem(day.toString())))
                    .andExpect(jsonPath("$[?(@.id == " + holidayId + ")].title").value(hasItem(title)));

            assertThat(schoolFacade.isClosedDay(one, day)).isTrue();
            assertThat(schoolFacade.isClosedDay(other, day)).isTrue();

            // مدیرِ مدرسه‌ی ۱ آن را با source=PLATFORM می‌بیند
            mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                            .param("academicYearId", String.valueOf(yearId))
                            .session(schoolAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.title == '" + title + "')].source").value(hasItem("PLATFORM")));
            // برای مدرسه‌ی دوم هم در سطح facade (کاربرِ عضوِ آن مدرسه در این تست نیست)
            assertThat(schoolFacade.yearlyPlan(other, year.id()).items())
                    .filteredOn(item -> item.source() == SchoolFacade.YearlyPlanSource.PLATFORM)
                    .extracting(SchoolFacade.YearlyPlanItem::id).contains(holidayId);

            mockMvc.perform(delete("/api/platform/holidays/" + holidayId)
                            .with(csrf())
                            .session(platform))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/platform/holidays/" + holidayId)
                            .with(csrf())
                            .session(platform))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value(endsWith("platform-holiday-not-found")));

            mockMvc.perform(get("/api/platform/academic-years/" + yearId + "/holidays").session(platform))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + holidayId + ")]").doesNotExist());
            mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                            .param("academicYearId", String.valueOf(yearId))
                            .session(schoolAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.title == '" + title + "')]").doesNotExist());
            assertThat(schoolFacade.isClosedDay(one, day)).isFalse();
            assertThat(schoolFacade.isClosedDay(other, day)).isFalse();
        } finally {
            createdHolidayIds.forEach(id -> jdbc.update("delete from school.platform_holidays where id = ?", id));
        }
    }

    @Test
    void invalidHolidaysAndUnknownYearsAreRejected() throws Exception {
        MockHttpSession platform = platformAdminSession();
        AcademicYearInfo year = year();
        long yearId = year.id().value();

        for (String body : new String[]{
                holidayBody(year.startDate().minusDays(1), "زودهنگام"),
                holidayBody(year.endDate().plusDays(1), "دیرهنگام"),
                holidayBody(year.startDate(), "   "),
                holidayBody(year.startDate(), "x".repeat(129)),
                "{\"day\":null,\"title\":\"تعطیلی\"}"}) {
            postHoliday(platform, yearId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(endsWith("invalid-calendar-entry")));
        }
        // بدنه‌ی تهی 400 تمیز است، نه 500
        mockMvc.perform(post("/api/platform/academic-years/" + yearId + "/holidays")
                        .with(csrf())
                        .session(platform)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        postHoliday(platform, 999999L, holidayBody(year.startDate(), "تعطیلی"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("academic-year-not-found")));
        mockMvc.perform(get("/api/platform/academic-years/999999/holidays").session(platform))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("academic-year-not-found")));

        mockMvc.perform(get("/api/platform/academic-years/" + yearId + "/holidays").session(platform))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'زودهنگام' || @.title == 'دیرهنگام')]").doesNotExist());
    }

    @Test
    void aSchoolAdminGetsPermissionDeniedOnEveryPlatformEndpoint() throws Exception {
        MockHttpSession schoolAdmin = login("admin@roshdeandishe.test", "password");
        long yearId = year().id().value();
        LocalDate day = year().endDate();

        for (boolean withSchoolHeader : new boolean[]{false, true}) {
            for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                    get("/api/platform/academic-years"),
                    get("/api/platform/academic-years/" + yearId + "/holidays"),
                    post("/api/platform/academic-years/" + yearId + "/holidays")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(holidayBody(day, "نفوذ مدیر مدرسه")),
                    delete("/api/platform/holidays/1")}) {
                request.with(csrf()).session(schoolAdmin);
                if (withSchoolHeader) {
                    request.header("X-School-ID", "1");
                }
                mockMvc.perform(request)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
            }
        }

        // هیچ تعطیلی‌ای ثبت نشده است
        assertThat(schoolFacade.listPlatformHolidays(year().id()))
                .extracting(SchoolFacade.PlatformHolidayInfo::title).doesNotContain("نفوذ مدیر مدرسه");
    }

    @Test
    void aPlatformAdminWithoutMembershipCannotReachSchoolEndpoints() throws Exception {
        MockHttpSession platform = platformAdminSession();
        AcademicYearInfo year = year();

        mockMvc.perform(get("/api/admin/schools/1/calendar")
                        .param("from", year.startDate().toString())
                        .param("to", year.endDate().toString())
                        .header("X-School-ID", "1")
                        .session(platform))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNormalStudentGetsPermissionDeniedOnPlatformEndpoints() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        String email = "platform-holiday.student@roshdeandishe.test";
        MvcResult classResult = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"پایه پنجم - تعطیلی سراسری\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long classId = objectMapper.readTree(classResult.getResponse().getContentAsString()).get("id").asLong();
        registerIfAbsent(email);
        mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"دانش‌آموز تعطیلی\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk());
        MockHttpSession student = login(email, "password123");

        mockMvc.perform(get("/api/platform/academic-years").session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        postHoliday(student, year().id().value(), holidayBody(year().endDate(), "جعلی"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
    }

    @Test
    void anUnauthenticatedCallIsRejected() throws Exception {
        long yearId = year().id().value();

        mockMvc.perform(get("/api/platform/academic-years"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/platform/academic-years/" + yearId + "/holidays"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/platform/academic-years/" + yearId + "/holidays")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holidayBody(year().endDate(), "ناشناس")))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/platform/holidays/1").with(csrf()))
                .andExpect(status().is4xxClientError());
        assertThat(schoolFacade.listPlatformHolidays(year().id()))
                .extracting(SchoolFacade.PlatformHolidayInfo::title).doesNotContain("ناشناس");
    }
}
