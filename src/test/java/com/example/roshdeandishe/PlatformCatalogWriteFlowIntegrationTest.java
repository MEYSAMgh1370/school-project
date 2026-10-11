package com.example.roshdeandishe;

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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * نوشتنِ کاتالوگِ پلتفرمی (moshavere-content-import.md فاز ۱) روی Postgres واقعی: مدیر پلتفرم سرفصل (با
 * source_code) و درس (با پایه و نام) را upsert می‌کند؛ مدیر مدرسه و معلم و ناشناس هرگز به این مسیرها نمی‌رسند.
 */
@SpringBootTest(properties = "roshdeandishe.platform-admin.emails=platform-catalog-flow@roshdeandishe.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformCatalogWriteFlowIntegrationTest {

    private static final String PLATFORM_EMAIL = "platform-catalog-flow@roshdeandishe.test";
    private static final String TOPICS = "/api/platform/curriculum/topics";
    private static final String COURSES = "/api/platform/academic/courses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<ApplicationRunner> runners;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from curriculum.topics where source_code like ?", "PCW-" + tag + "%");
        jdbc.update("delete from academic.courses where name like ?", "PCW-" + tag + "%");
        jdbc.update("delete from iam.platform_admins");
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

    private MockHttpSession teacherSession() throws Exception {
        String email = "platform-catalog.teacher@roshdeandishe.test";
        registerIfAbsent(email);
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow User\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private ResultActions postJson(MockHttpSession session, String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String topicBody(String code, String parentCode, String name, int sortOrder) {
        return "{\"sourceCode\":\"" + code + "\",\"parentSourceCode\":"
                + (parentCode == null ? "null" : "\"" + parentCode + "\"")
                + ",\"name\":\"" + name + "\",\"sortOrder\":" + sortOrder + "}";
    }

    private String courseBody(int grade, String name, String track, Long topicId) {
        return "{\"gradeLevel\":" + grade + ",\"name\":\"" + name + "\",\"track\":"
                + (track == null ? "null" : "\"" + track + "\"") + ",\"topicId\":" + topicId + "}";
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void platformAdminUpsertsTopicsBySourceCode() throws Exception {
        MockHttpSession platform = platformAdminSession();
        String root = "PCW-" + tag + "-R";
        String child = "PCW-" + tag + "-C";

        long rootId = id(postJson(platform, TOPICS, topicBody(root, null, "ریاضی", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolId").doesNotExist())
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andReturn());
        long childId = id(postJson(platform, TOPICS, topicBody(child, root, "جبر", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(rootId))
                .andReturn());

        // upsert دوباره با همان source_code: به‌روزرسانی، نه ردیف تازه
        postJson(platform, TOPICS, topicBody(child, root, "جبر و معادله", 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(childId))
                .andExpect(jsonPath("$.name").value("جبر و معادله"))
                .andExpect(jsonPath("$.sortOrder").value(5));
        assertThat(count("select count(*) from curriculum.topics where source_code = ?", child)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select school_id from curriculum.topics where id = ?", Long.class, childId)).isNull();

        // والد را عوض نمی‌کنیم مگر به سرفصل پلتفرمیِ دیگر؛ حلقه رد می‌شود
        postJson(platform, TOPICS, topicBody(root, child, "ریاضی", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-topic")));
    }

    @Test
    void unknownParentAndBlankNameAreRejected() throws Exception {
        MockHttpSession platform = platformAdminSession();
        String orphan = "PCW-" + tag + "-O";

        postJson(platform, TOPICS, topicBody(orphan, "PCW-" + tag + "-NOPE", "یتیم", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("parent-topic-not-found")));
        postJson(platform, TOPICS, topicBody(orphan, null, "   ", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-topic")));
        assertThat(count("select count(*) from curriculum.topics where source_code = ?", orphan)).isZero();
    }

    @Test
    void platformAdminUpsertsCoursesByGradeAndName() throws Exception {
        MockHttpSession platform = platformAdminSession();
        String name = "PCW-" + tag + "-درس";

        long first = id(postJson(platform, COURSES, courseBody(10, name, "ریاضی", 77L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradeLevel").value(10))
                .andReturn());
        long second = id(postJson(platform, COURSES, courseBody(10, name, "تجربی", 78L))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(second).isEqualTo(first);
        assertThat(count("select count(*) from academic.courses where grade_level = 10 and name = ?", name)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select track from academic.courses where id = ?", String.class, first))
                .isEqualTo("تجربی");
        assertThat(jdbc.queryForObject("select topic_id from academic.courses where id = ?", Long.class, first))
                .isEqualTo(78L);
        assertThat(jdbc.queryForObject("select graded from academic.courses where id = ?", Boolean.class, first))
                .isTrue();

        // رشته‌ی تهی = درس مشترک
        postJson(platform, COURSES, courseBody(10, name, null, null)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select track from academic.courses where id = ?", String.class, first))
                .isNull();
    }

    @Test
    void invalidTrackAndGradeAreRejected() throws Exception {
        MockHttpSession platform = platformAdminSession();
        String name = "PCW-" + tag + "-بد";

        postJson(platform, COURSES, courseBody(10, name, "هنر", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-track")));
        postJson(platform, COURSES, courseBody(13, name, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-course-grade-level")));
        postJson(platform, COURSES, courseBody(0, name, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-course-grade-level")));
        assertThat(count("select count(*) from academic.courses where name = ?", name)).isZero();
    }

    @Test
    void schoolAdminAndTeacherGetPermissionDenied() throws Exception {
        MockHttpSession schoolAdmin = login("admin@roshdeandishe.test", "password");
        MockHttpSession teacher = teacherSession();
        String code = "PCW-" + tag + "-X";
        String name = "PCW-" + tag + "-ممنوع";

        for (MockHttpSession session : new MockHttpSession[]{schoolAdmin, teacher}) {
            postJson(session, TOPICS, topicBody(code, null, "ممنوع", 1))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
            postJson(session, COURSES, courseBody(5, name, null, null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
        }
        assertThat(count("select count(*) from curriculum.topics where source_code = ?", code)).isZero();
        assertThat(count("select count(*) from academic.courses where name = ?", name)).isZero();
    }

    @Test
    void anUnauthenticatedCallIsRejected() throws Exception {
        mockMvc.perform(post(TOPICS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody("PCW-" + tag + "-A", null, "ناشناس", 1)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post(COURSES).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(courseBody(5, "PCW-" + tag + "-ناشناس", null, null)))
                .andExpect(status().is4xxClientError());
        assertThat(count("select count(*) from curriculum.topics where source_code like ?", "PCW-" + tag + "%")).isZero();
    }
}
