package com.example.roshdeandishe;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.CalendarEntryInfo;
import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import com.example.roshdeandishe.school.SchoolFacade.PlatformHolidayInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanItem;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanSource;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * برنامه‌ی سالانه (openspec/changes/yearly-plan.md، فاز A و B) روی Postgres واقعی: مدیر (مجوز
 * YEARLY_PLAN_MANAGE) رویداد می‌افزاید/حذف می‌کند؛ دانش‌آموز فقط می‌خواند؛ رویداد روز را تعطیل
 * نمی‌کند ولی تعطیلیِ سراسری در همه‌ی مدرسه‌ها هم در برنامه می‌آید و هم روز را می‌بندد. روزها از
 * startDate/endDate سالِ انتخابی محاسبه می‌شوند تا تست به تاریخ امروز بستگی نداشته باشد؛ هر تست
 * ورودی‌های سراسریِ خودش را حذف می‌کند چون تعطیلیِ سراسری همه‌ی مدرسه‌ها را اثر می‌دهد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class YearlyPlanFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchoolFacade schoolFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * سالِ جاریِ مدرسه‌ی ۱ اگر انتخاب شده، وگرنه اولین سالِ فهرست. این تست سالِ جاریِ مدرسه را عوض نمی‌کند
     * (تست‌های دیگر به آن تکیه می‌کنند)؛ شناسه همیشه صریح فرستاده می‌شود و مدیر هر سالی را می‌بیند.
     */
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

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private long createClass(MockHttpSession adminSession, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/classes")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void enrollStudent(MockHttpSession adminSession, String email, String fullName, long classId) throws Exception {
        mockMvc.perform(post("/api/admin/schools/1/students")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"classId\":" + classId + "}"))
                .andExpect(status().isOk());
    }

    private String body(long yearId, LocalDate day, String title) {
        return "{\"academicYearId\":" + yearId + ",\"day\":\"" + day + "\",\"title\":\"" + title + "\"}";
    }

    private long addEvent(MockHttpSession admin, long yearId, LocalDate day, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/yearly-plan")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, day, title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").value(day.toString()))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.source").value("SCHOOL"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void adminAddsListsAndRemovesAnEventAndItNeverClosesTheDay() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();
        LocalDate day = year.startDate().plusDays(40);

        long eventId = addEvent(admin, yearId, day, "جشن آغاز سال");

        mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                        .param("academicYearId", String.valueOf(yearId))
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year.id").value(yearId))
                .andExpect(jsonPath("$.year.startDate").value(year.startDate().toString()))
                .andExpect(jsonPath("$.year.endDate").value(year.endDate().toString()))
                .andExpect(jsonPath("$.items[?(@.id == " + eventId + ")]").exists())
                .andExpect(jsonPath("$.items[?(@.id == " + eventId + ")].source")
                        .value(org.hamcrest.Matchers.hasItem("SCHOOL")));

        // بدون academicYearId همان سالِ جاری (یا حالتِ خالی) با ۲۰۰ می‌آید
        mockMvc.perform(get("/api/me/schools/1/yearly-plan").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        // EVENT روز را تعطیل نمی‌کند
        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), day)).isFalse();

        mockMvc.perform(delete("/api/admin/schools/1/yearly-plan/" + eventId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/schools/1/yearly-plan/" + eventId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("calendar-entry-not-found")));
        mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                        .param("academicYearId", String.valueOf(yearId))
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + eventId + ")]").doesNotExist());
    }

    @Test
    void outOfRangeOrInvalidEventsAreRejectedWithBadRequest() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();

        for (String body : new String[]{
                body(yearId, year.startDate().minusDays(1), "زودهنگام"),
                body(yearId, year.endDate().plusDays(1), "دیرهنگام"),
                body(yearId, year.startDate(), "   "),
                body(yearId, year.startDate(), "x".repeat(129)),
                "{\"academicYearId\":" + yearId + ",\"day\":null,\"title\":\"رویداد\"}"}) {
            mockMvc.perform(post("/api/admin/schools/1/yearly-plan")
                            .with(csrf())
                            .header("X-School-ID", "1")
                            .session(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-calendar-entry")));
        }

        // اولین و آخرین روزِ سال پذیرفته می‌شوند (بازه‌ی بسته)
        long first = addEvent(admin, yearId, year.startDate(), "اولین روز");
        long last = addEvent(admin, yearId, year.endDate(), "آخرین روز");
        for (long id : new long[]{first, last}) {
            mockMvc.perform(delete("/api/admin/schools/1/yearly-plan/" + id)
                            .with(csrf())
                            .header("X-School-ID", "1")
                            .session(admin))
                    .andExpect(status().isNoContent());
        }

        // سالِ ناموجود 404 است
        mockMvc.perform(post("/api/admin/schools/1/yearly-plan")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999L, year.startDate(), "رویداد")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("academic-year-not-found")));
    }

    @Test
    void aStudentCanReadThePlanButNotManageIt() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();
        LocalDate day = year.startDate().plusDays(41);
        long eventId = addEvent(admin, yearId, day, "رویداد برای دانش‌آموز");

        long classId = createClass(admin, "پایه پنجم - برنامه‌ی سالانه");
        String studentEmail = "yearlyplan.student@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز برنامه‌ی سالانه");
        enrollStudent(admin, studentEmail, "دانش‌آموز برنامه‌ی سالانه", classId);

        // شناسه‌ی سالِ جاری (یا اولین سال برای مدرسه‌ی بدون سالِ جاری، که محدود نیست) مجاز است
        mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                        .param("academicYearId", String.valueOf(yearId))
                        .session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + eventId + ")]").exists());

        mockMvc.perform(post("/api/admin/schools/1/yearly-plan")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, day.plusDays(1), "رویداد جعلی")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
        mockMvc.perform(delete("/api/admin/schools/1/yearly-plan/" + eventId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));

        // رویداد دست‌نخورده مانده است
        assertThat(schoolFacade.yearlyPlan(new SchoolId(1L), year.id()).items())
                .extracting(YearlyPlanItem::id).contains(eventId);
        schoolFacade.removeYearlyPlanEvent(new SchoolId(1L), eventId);
    }

    private String rangeBody(long yearId, LocalDate start, LocalDate end) {
        return "{\"academicYearId\":" + yearId + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions putRange(MockHttpSession session, String body)
            throws Exception {
        return mockMvc.perform(put("/api/admin/schools/1/yearly-plan/range")
                .with(csrf())
                .header("X-School-ID", "1")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void adminExtendsTheRangeAndEventsInTheExtendedPartAreAccepted() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();
        LocalDate start = year.startDate().minusDays(20);
        LocalDate end = year.endDate().plusDays(20);
        SchoolId one = new SchoolId(1L);
        SchoolId other = schoolFacade.createSchool(
                "مدرسه‌ی بازه‌ی برنامه‌ی سالانه دو", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null);
        long earlyId = 0;
        long lateId = 0;
        try {
            putRange(admin, rangeBody(yearId, start, end))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value(start.toString()))
                    .andExpect(jsonPath("$.endDate").value(end.toString()))
                    .andExpect(jsonPath("$.custom").value(true));

            mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                            .param("academicYearId", String.valueOf(yearId))
                            .session(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.year.startDate").value(year.startDate().toString()))
                    .andExpect(jsonPath("$.year.endDate").value(year.endDate().toString()))
                    .andExpect(jsonPath("$.range.startDate").value(start.toString()))
                    .andExpect(jsonPath("$.range.endDate").value(end.toString()))
                    .andExpect(jsonPath("$.range.custom").value(true));

            earlyId = addEvent(admin, yearId, start, "رویداد پیش از مهر");
            lateId = addEvent(admin, yearId, end, "رویداد پس از خرداد");
            long early = earlyId;
            long late = lateId;
            assertThat(schoolFacade.yearlyPlan(one, year.id()).items())
                    .extracting(YearlyPlanItem::id).contains(early, late);

            // فقط یک روز بیرون از بازه‌ی گسترش‌یافته هنوز رد می‌شود
            mockMvc.perform(post("/api/admin/schools/1/yearly-plan")
                            .with(csrf())
                            .header("X-School-ID", "1")
                            .session(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(yearId, start.minusDays(1), "زود")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-calendar-entry")));

            // مدرسه‌ی دیگر دست‌نخورده است: بازه‌ی پیش‌فرض و بدون رویدادهای مدرسه‌ی ۱
            assertThat(schoolFacade.yearlyPlan(other, year.id()).range().custom()).isFalse();
            assertThat(schoolFacade.yearlyPlan(other, year.id()).range().startDate()).isEqualTo(year.startDate());
            assertThatThrownBy(() -> schoolFacade.addYearlyPlanEvent(other, year.id(), start, "خارج از بازه"))
                    .isInstanceOfSatisfying(SchoolException.class,
                            e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidCalendarEntry.class));
            assertThat(schoolFacade.yearlyPlan(other, year.id()).items())
                    .extracting(YearlyPlanItem::id).doesNotContain(early, late);
        } finally {
            // بازگشت به پیش‌فرض: بازه‌ی اختصاصی پاک می‌شود تا تست‌های دیگر دست‌نخورده بمانند
            putRange(admin, rangeBody(yearId, year.startDate(), year.endDate()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.custom").value(false));
            if (earlyId != 0) {
                schoolFacade.removeYearlyPlanEvent(one, earlyId);
            }
            if (lateId != 0) {
                schoolFacade.removeYearlyPlanEvent(one, lateId);
            }
        }
        assertThat(schoolFacade.yearlyPlan(one, year.id()).range().custom()).isFalse();
    }

    @Test
    void extendingMoreThanOneMonthOnEitherSideIsRejected() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();

        // سقف: ۱ شهریور (۳۱ روز پیش از ۱ مهر) تا ۳۱ تیر (۳۱ روز پس از ۳۱ خرداد)؛ یک روز بیشتر رد می‌شود
        for (String body : new String[]{
                rangeBody(yearId, year.startDate().minusDays(32), year.endDate()),
                rangeBody(yearId, year.startDate(), year.endDate().plusDays(32))}) {
            putRange(admin, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-year-range")));
        }
        assertThat(schoolFacade.yearlyPlan(new SchoolId(1L), year.id()).range().custom()).isFalse();
    }

    @Test
    void narrowingOrBrokenRangesAreRejectedWithInvalidYearRange() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();

        for (String body : new String[]{
                rangeBody(yearId, year.startDate().plusDays(1), year.endDate()),
                rangeBody(yearId, year.startDate(), year.endDate().minusDays(1)),
                rangeBody(yearId, year.endDate().plusDays(2), year.endDate().plusDays(1)),
                "{\"academicYearId\":" + yearId + ",\"startDate\":null,\"endDate\":\"" + year.endDate() + "\"}"}) {
            putRange(admin, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-year-range")));
        }
        assertThat(schoolFacade.yearlyPlan(new SchoolId(1L), year.id()).range().custom()).isFalse();

        putRange(admin, rangeBody(999999L, year.startDate(), year.endDate()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("academic-year-not-found")));
    }

    @Test
    void aStudentCannotSetTheRange() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long classId = createClass(admin, "پایه پنجم - بازه‌ی برنامه‌ی سالانه");
        String studentEmail = "yearlyplan.range.student@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز بازه‌ی برنامه");
        enrollStudent(admin, studentEmail, "دانش‌آموز بازه‌ی برنامه", classId);

        putRange(student, rangeBody(year.id().value(), year.startDate().minusDays(5), year.endDate()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));

        assertThat(schoolFacade.yearlyPlan(new SchoolId(1L), year.id()).range().custom()).isFalse();
    }

    @Test
    void aStrangerWhoIsNotAMemberCannotReadThePlan() throws Exception {
        MockHttpSession stranger = registerAndLogin("yearlyplan.stranger@roshdeandishe.test", "کاربر بی‌ربط");

        mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                        .param("academicYearId", String.valueOf(year().id().value()))
                        .session(stranger))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aClosedEntryIsNotRemovableThroughTheYearlyPlan() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        LocalDate day = year().startDate().plusDays(50);
        CalendarEntryInfo closed = schoolFacade.addCalendarEntry(new SchoolId(1L), day, CalendarKind.CLOSED, "تعطیلی مدرسه");

        mockMvc.perform(delete("/api/admin/schools/1/yearly-plan/" + closed.id())
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("calendar-entry-not-found")));

        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), day)).isTrue();
        schoolFacade.removeCalendarEntry(new SchoolId(1L), closed.id());
        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), day)).isFalse();
    }

    @Test
    void aPlatformHolidayAppearsInEverySchoolsPlanAndClosesTheDayEverywhere() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        AcademicYearInfo year = year();
        long yearId = year.id().value();
        // آخرین روزِ سال: دور از «امروز»ِ تست‌های حضور و غیاب، چون تعطیلیِ سراسری همه‌ی مدرسه‌ها را می‌بندد
        LocalDate day = year.endDate();
        SchoolId one = new SchoolId(1L);
        SchoolId other = schoolFacade.createSchool(
                "مدرسه‌ی برنامه‌ی سالانه دو", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null);

        long eventId = addEvent(admin, yearId, year.startDate().plusDays(60), "رویداد مدرسه‌ی یک");
        PlatformHolidayInfo holiday = schoolFacade.addPlatformHoliday(year.id(), day, "تعطیلی سراسری آزمایشی");
        try {
            assertThat(schoolFacade.isClosedDay(one, day)).isTrue();
            assertThat(schoolFacade.isClosedDay(other, day)).isTrue();

            for (SchoolId school : new SchoolId[]{one, other}) {
                assertThat(schoolFacade.yearlyPlan(school, year.id()).items())
                        .filteredOn(item -> item.source() == YearlyPlanSource.PLATFORM)
                        .extracting(YearlyPlanItem::id).contains(holiday.id());
            }

            // مدیرِ مدرسه‌ی ۱ آن را در پاسخ HTTP هم با source=PLATFORM می‌بیند
            mockMvc.perform(get("/api/me/schools/1/yearly-plan")
                            .param("academicYearId", String.valueOf(yearId))
                            .session(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.title == 'تعطیلی سراسری آزمایشی')].source")
                            .value(org.hamcrest.Matchers.hasItem("PLATFORM")));

            // رویدادهای مدرسه‌ی ۱ در مدرسه‌ی ۲ دیده یا حذف نمی‌شوند
            assertThat(schoolFacade.yearlyPlan(other, year.id()).items())
                    .extracting(YearlyPlanItem::id).doesNotContain(eventId);
            assertThatThrownBy(() -> schoolFacade.removeYearlyPlanEvent(other, eventId))
                    .isInstanceOfSatisfying(SchoolException.class,
                            e -> assertThat(e.error()).isInstanceOf(SchoolError.CalendarEntryNotFound.class));
            mockMvc.perform(delete("/api/admin/schools/" + other.value() + "/yearly-plan/" + eventId)
                            .with(csrf())
                            .header("X-School-ID", "1")
                            .session(admin))
                    .andExpect(status().is4xxClientError());
            assertThat(schoolFacade.yearlyPlan(one, year.id()).items())
                    .extracting(YearlyPlanItem::id).contains(eventId);
        } finally {
            schoolFacade.removePlatformHoliday(holiday.id());
            schoolFacade.removeYearlyPlanEvent(one, eventId);
        }

        assertThat(schoolFacade.isClosedDay(one, day)).isFalse();
        assertThat(schoolFacade.isClosedDay(other, day)).isFalse();
        assertThatThrownBy(() -> schoolFacade.removePlatformHoliday(holiday.id()))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.PlatformHolidayNotFound.class));
    }
}
