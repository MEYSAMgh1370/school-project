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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.HtmlUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * صفحه‌ی «غیبت‌های من» (/my-attendance) روی Spring واقعی: پشتِ لاگین، بدون نیاز به X-School-ID برای خودِ صفحه،
 * برای دانش‌آموز و والد باز می‌شود، لینکِ سایدبار و کارتِ «دسترسی سریع» از NavCatalog می‌آید و در پیشخوانِ والد
 * هر فرزند لینکِ «غیبت‌ها» با شناسه‌ی مدرسه دارد. داده client-side از /api/me می‌آید که
 * AttendanceExcusesFlowIntegrationTest پوشش کامل دارد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyAttendancePageFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
    }

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

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpSession session, long schoolId, MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mockMvc.perform(request.with(csrf()).header("X-School-ID", String.valueOf(schoolId)).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long addMember(MockHttpSession admin, long school, String email, String name, String role) throws Exception {
        return json(send(admin, school, post("/api/admin/schools/" + school + "/memberships"),
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\",\"roleCode\":\"" + role + "\"}")
                .andExpect(status().isOk())).get("userId").asLong();
    }

    private String page(MockHttpSession session, String path) throws Exception {
        return mockMvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    @Test
    void unauthenticatedVisitorsAreRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/my-attendance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void aStudentAndAParentCanOpenThePageAndSeeTheNavLinkAndTheShortcut() throws Exception {
        MockHttpSession admin = registerAndLogin(unique("myatt.founder"), "مدیر غیبت‌ها");
        MvcResult created = mockMvc.perform(post("/api/admin/schools").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی غیبت‌های من " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\",\"weekStart\":\"SATURDAY\"}"))
                .andExpect(status().isOk()).andReturn();
        long school = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String studentEmail = unique("myatt.student");
        MockHttpSession student = registerAndLogin(studentEmail, "دانش‌آموز غیبت");
        addMember(admin, school, studentEmail, "دانش‌آموز غیبت", "STUDENT");
        String parentEmail = unique("myatt.parent");
        MockHttpSession parent = registerAndLogin(parentEmail, "والد غیبت");
        long parentId = addMember(admin, school, parentEmail, "والد غیبت", "PARENT");

        // یک فرزندِ لینک‌شده به والد، تا کارتِ «فرزندان من» لینکِ «غیبت‌ها» را داشته باشد
        long classId = json(send(admin, school, post("/api/admin/schools/" + school + "/classes"),
                "{\"name\":\"پایه پنجم الف\",\"section\":null,\"teacherId\":null,\"gradeLevel\":5,\"gradingScheme\":\"NUMERIC\"}")
                .andExpect(status().isOk())).get("id").asLong();
        long ward = json(send(admin, school, post("/api/admin/schools/" + school + "/students"),
                "{\"email\":\"" + unique("myatt.ward") + "\",\"fullName\":\"فرزند غیبت\",\"classId\":" + classId + "}")
                .andExpect(status().isOk())).get("studentId").asLong();
        send(admin, school, post("/api/admin/schools/" + school + "/students/" + ward + "/guardians"),
                "{\"guardianUserId\":" + parentId + "}").andExpect(status().isOk());

        for (MockHttpSession session : new MockHttpSession[]{student, parent}) {
            // بدون X-School-ID: مسیرِ صفحه از TenantResolverFilter مستثناست (داده را fetch می‌آورد)
            String html = page(session, "/my-attendance");
            assertThat(html).contains("x-data=\"myAttendancePage()\"", "/js/my-attendance.js", "غیبت‌های من");
            // «هیچ URL یتیمی»: لینکِ سایدبار از NavCatalog
            assertThat(html).contains("href=\"/my-attendance\"");

            // کارتِ «دسترسی سریع» پیشخوان از همان منبع
            assertThat(page(session, "/dashboard")).contains("class=\"box app-shortcut\" href=\"/my-attendance\"");
        }

        String parentDashboard = HtmlUtils.htmlUnescape(page(parent, "/dashboard"));
        assertThat(parentDashboard).contains("href=\"/my-attendance?school=" + school + "\">غیبت‌ها</a>");
    }
}
