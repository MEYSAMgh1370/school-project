package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.HtmlUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * صفحه‌های حضور و غیاب و تقویم مدرسه (/attendance، /attendance/report، /calendar) و بخش «حضور و غیاب امروز»
 * پیشخوان روی Spring واقعی. هر صفحه یک shell نازک است؛ داده client-side از APIهای attendance و school می‌آید که
 * خودشان با AttendanceFlowIntegrationTest پوشش کامل دارند - اینجا فقط مسیرها (پشتِ لاگین، بدون نیاز به
 * X-School-ID برای خودِ صفحه)، ناوبار و گاردِ entitlement روی پیشخوان.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AttendancePagesFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private String page(MockHttpSession session, String path) throws Exception {
        return mockMvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    @Test
    void unauthenticatedVisitorsAreRedirectedToLogin() throws Exception {
        for (String path : new String[]{"/attendance", "/attendance/report", "/calendar"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
        }
    }

    @Test
    void theThreePagesRenderTheirShellForAnAuthenticatedUserWithoutASchoolHeader() throws Exception {
        // مدیرِ مدرسه‌ی ۱: ناوبارِ کاربرِ بدونِ عضویت سایدبار ندارد؛ خودِ صفحه‌ها به X-School-ID نیاز ندارند
        MockHttpSession session = login("admin@roshdeandishe.test", "password");

        String recording = page(session, "/attendance");
        assertThat(recording).contains("x-data=\"attendancePage()\"", "ثبت حضور و غیاب", "/js/attendance.js");

        String report = page(session, "/attendance/report");
        assertThat(report).contains("x-data=\"attendanceReportPage()\"", "نمایش گزارش", "/js/attendance-report.js");

        String calendar = page(session, "/calendar");
        assertThat(calendar).contains("x-data=\"schoolCalendarPage()\"", "افزودن روز تعطیل", "/js/school-calendar.js");

        // «هیچ URL یتیمی»: هر سه در سایدبار هم هستند
        assertThat(recording).contains("href=\"/attendance\"", "href=\"/attendance/report\"", "href=\"/calendar\"");
    }

    @Test
    void theDashboardShowsTodaysAttendanceToTheAdminOnlyWhileTheSchoolHasTheModule() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        IamFacade.ModuleCode attendance = new IamFacade.ModuleCode("ATTENDANCE");

        iamFacade.setSchoolEntitlement(1L, attendance, true);
        String entitled = HtmlUtils.htmlUnescape(page(admin, "/dashboard"));
        assertThat(entitled).contains("حضور و غیاب امروز", "href=\"/attendance/report\">غایبین امروز</a>");
        assertThat(entitled).contains("$store.nav.selectedRole === 'SCHOOL_ADMIN' && $store.nav.selectedSchoolId === 1");

        try {
            iamFacade.setSchoolEntitlement(1L, attendance, false);
            String unentitled = page(admin, "/dashboard");
            // فقط بخشِ مدرسه‌ی ۱: مدیرِ نمونه مدرسه‌های دیگری هم دارد (از تست‌های دیگر) که ماژول را دارند
            assertThat(unentitled).doesNotContain("dashboard-attendance-title-1\"");
        } finally {
            iamFacade.setSchoolEntitlement(1L, attendance, true);
        }
    }

    @Test
    void aNonAdminDashboardHasNoAttendanceBox() throws Exception {
        MockHttpSession member = registerAndLogin(
                "attdash." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test", "کاربر ساده");

        assertThat(page(member, "/dashboard")).doesNotContain("حضور و غیاب امروز");
    }
}
