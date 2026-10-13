package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * پایه‌ی مشترک تست‌های باکس سؤالات: ساختنِ مدرسه‌ی تازه با مدیر (بنیان‌گذار)، دو معلم و یک دانش‌آموز، و کمک‌های
 * HTTP (الگوی AttendanceFlowIntegrationTest). هر تست مدرسه‌ی خودش را می‌سازد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class QuestionBoxTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected IamFacade iamFacade;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected record Fixture(long school, MockHttpSession admin, MockHttpSession teacher1, MockHttpSession teacher2,
                             MockHttpSession student, long teacher1Id, long teacher1MembershipId) {

        String base() {
            return "/api/admin/schools/" + school + "/question-box";
        }
    }

    protected static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
    }

    protected MockHttpSession login(String email, String password) throws Exception {
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

    protected MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /** درخواست JSON با نشست، هدر مدرسه و CSRF. */
    protected ResultActions send(MockHttpSession session, long schoolId, MockHttpServletRequestBuilder request,
                                 String body) throws Exception {
        var builder = request.with(csrf()).header("X-School-ID", String.valueOf(schoolId)).session(session);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    protected Fixture fixture() throws Exception {
        MockHttpSession admin = registerAndLogin(unique("qb.founder"), "مدیر باکس سؤالات");
        MvcResult school = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی باکس " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\",\"weekStart\":\"SATURDAY\"}"))
                .andExpect(status().isOk()).andReturn();
        long schoolId = objectMapper.readTree(school.getResponse().getContentAsString()).get("id").asLong();

        String t1 = unique("qb.teacher1");
        String t2 = unique("qb.teacher2");
        String st = unique("qb.student");
        MockHttpSession teacher1 = registerAndLogin(t1, "معلم یک");
        MockHttpSession teacher2 = registerAndLogin(t2, "معلم دو");
        MockHttpSession student = registerAndLogin(st, "دانش‌آموز");
        JsonNode m1 = json(send(admin, schoolId, post("/api/admin/schools/" + schoolId + "/memberships"),
                "{\"email\":\"" + t1 + "\",\"fullName\":\"معلم یک\",\"roleCode\":\"TEACHER\"}").andExpect(status().isOk()));
        send(admin, schoolId, post("/api/admin/schools/" + schoolId + "/memberships"),
                "{\"email\":\"" + t2 + "\",\"fullName\":\"معلم دو\",\"roleCode\":\"TEACHER\"}").andExpect(status().isOk());
        send(admin, schoolId, post("/api/admin/schools/" + schoolId + "/memberships"),
                "{\"email\":\"" + st + "\",\"fullName\":\"دانش‌آموز\",\"roleCode\":\"STUDENT\"}").andExpect(status().isOk());
        return new Fixture(schoolId, admin, teacher1, teacher2, student,
                m1.get("userId").asLong(), m1.get("membershipId").asLong());
    }

    protected static String mcq(String text) {
        return "{\"type\":\"MULTIPLE_CHOICE\",\"contentMode\":\"TEXT\",\"text\":\"" + text
                + "\",\"choices\":[\"الف\",\"ب\",\"ج\",\"د\"],\"correctChoice\":2,\"answer\":\"توضیح\"}";
    }

    protected static String essay(String text) {
        return "{\"type\":\"ESSAY\",\"contentMode\":\"TEXT\",\"text\":\"" + text + "\",\"answer\":\"راه‌حل\"}";
    }
}
