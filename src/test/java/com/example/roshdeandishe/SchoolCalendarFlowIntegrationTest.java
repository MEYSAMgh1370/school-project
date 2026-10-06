package com.example.roshdeandishe;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.CalendarEntryInfo;
import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
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
 * تقویم مدرسه (openspec/changes/attendance.md، فاز ۰) روی Postgres واقعی: مدیر (مجوز
 * CALENDAR_MANAGE) روز تعطیل می‌افزاید/حذف می‌کند و فهرست می‌گیرد؛ هر عضو فعال فقط
 * می‌خواند؛ بیگانه و عضوِ بی‌مجوز رد می‌شوند؛ ورودیِ یک مدرسه برای مدرسه‌ی دیگر دیده
 * نمی‌شود. روزها در هر تست یکتا هستند چون دیتابیس بین تست‌ها پاک نمی‌شود.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SchoolCalendarFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchoolFacade schoolFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private long addEntry(MockHttpSession admin, LocalDate day, String kind, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/calendar")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + day + "\",\"kind\":\"" + kind + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").value(day.toString()))
                .andExpect(jsonPath("$.kind").value(kind))
                .andExpect(jsonPath("$.title").value(title))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void adminAddsListsAndRemovesAClosedDayAndIsClosedDayFollows() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        LocalDate day = LocalDate.of(2031, 3, 21);

        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), day)).isFalse();

        long closedId = addEntry(admin, day, "CLOSED", "نوروز");
        // یک روز می‌تواند چند ورودی داشته باشد؛ EVENT روز را تعطیل نمی‌کند
        long eventId = addEntry(admin, day, "EVENT", "جشن بهار");

        mockMvc.perform(get("/api/admin/schools/1/calendar")
                        .param("from", "2031-03-01").param("to", "2031-03-31")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(closedId))
                .andExpect(jsonPath("$[1].id").value(eventId));

        // بازه‌ی بسته: ورودی‌ای خارج از [from, to] نمی‌آید
        mockMvc.perform(get("/api/admin/schools/1/calendar")
                        .param("from", "2031-04-01").param("to", "2031-04-30")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), day)).isTrue();
        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), day.plusDays(1))).isFalse();

        mockMvc.perform(delete("/api/admin/schools/1/calendar/" + closedId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isNoContent());

        // فقط EVENT مانده، پس روز دیگر تعطیل نیست
        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), day)).isFalse();

        mockMvc.perform(delete("/api/admin/schools/1/calendar/" + closedId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("calendar-entry-not-found")));

        mockMvc.perform(delete("/api/admin/schools/1/calendar/" + eventId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidEntriesAreRejectedWithBadRequest() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        for (String body : new String[]{
                "{\"day\":\"2031-05-01\",\"kind\":\"CLOSED\",\"title\":\"   \"}",
                "{\"day\":\"2031-05-01\",\"kind\":\"CLOSED\",\"title\":\"" + "x".repeat(129) + "\"}",
                "{\"day\":\"2031-05-01\",\"kind\":\"HOLIDAY\",\"title\":\"تعطیل\"}",
                "{\"day\":null,\"kind\":\"CLOSED\",\"title\":\"تعطیل\"}"}) {
            mockMvc.perform(post("/api/admin/schools/1/calendar")
                            .with(csrf())
                            .header("X-School-ID", "1")
                            .session(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-calendar-entry")));
        }

        mockMvc.perform(get("/api/admin/schools/1/calendar")
                        .param("from", "2031-05-10").param("to", "2031-05-01")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherSchoolNeverSeesOrRemovesThisSchoolsEntries() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        LocalDate day = LocalDate.of(2031, 6, 15);
        long entryId = addEntry(admin, day, "CLOSED", "تعطیلی مدرسه‌ی یک");

        SchoolId other = schoolFacade.createSchool(
                "مدرسه‌ی تقویم دو", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null);

        assertThat(schoolFacade.isClosedDay(other, day)).isFalse();
        assertThat(schoolFacade.listCalendar(other, day, day)).isEmpty();
        assertThatThrownBy(() -> schoolFacade.removeCalendarEntry(other, entryId))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.CalendarEntryNotFound.class));

        // ورودی دست‌نخورده مانده است
        assertThat(schoolFacade.listCalendar(new SchoolId(1L), day, day))
                .extracting(CalendarEntryInfo::id).contains(entryId);
        assertThat(schoolFacade.listCalendar(new SchoolId(1L), day, day))
                .extracting(CalendarEntryInfo::kind).contains(CalendarKind.CLOSED);

        // مدیرِ مدرسه‌ی ۱ با هدرِ مدرسه‌ی دیگر به آن مدرسه راه ندارد
        mockMvc.perform(delete("/api/admin/schools/" + other.value() + "/calendar/" + entryId)
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void anActiveMemberCanReadTheCalendarButNotManageIt() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        LocalDate day = LocalDate.of(2031, 7, 7);
        addEntry(admin, day, "CLOSED", "تعطیلی تابستان");

        long classId = createClass(admin, "پایه پنجم - تقویم");
        String studentEmail = "calendar.student@roshdeandishe.test";
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز تقویم");
        enrollStudent(admin, studentEmail, "دانش‌آموز تقویم", classId);

        mockMvc.perform(get("/api/me/schools/1/calendar")
                        .param("from", "2031-07-01").param("to", "2031-07-31")
                        .session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'تعطیلی تابستان')]").exists());

        // بدون مجوز CALENDAR_MANAGE: خواندن و نوشتن در API مدیر ۴۰۳ است
        mockMvc.perform(get("/api/admin/schools/1/calendar")
                        .param("from", "2031-07-01").param("to", "2031-07-31")
                        .header("X-School-ID", "1")
                        .session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
        mockMvc.perform(post("/api/admin/schools/1/calendar")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"2031-07-08\",\"kind\":\"CLOSED\",\"title\":\"تعطیلی جعلی\"}"))
                .andExpect(status().isForbidden());
        assertThat(schoolFacade.isClosedDay(new SchoolId(1L), LocalDate.of(2031, 7, 8))).isFalse();
    }

    @Test
    void aStrangerWhoIsNotAMemberCannotReadTheCalendar() throws Exception {
        MockHttpSession stranger = registerAndLogin("calendar.stranger@roshdeandishe.test", "کاربر بی‌ربط");

        mockMvc.perform(get("/api/me/schools/1/calendar")
                        .param("from", "2031-07-01").param("to", "2031-07-31")
                        .session(stranger))
                .andExpect(status().is4xxClientError());
    }
}
