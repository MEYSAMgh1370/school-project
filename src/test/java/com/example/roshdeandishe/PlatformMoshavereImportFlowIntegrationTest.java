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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ایمپورتِ پلتفرمیِ بسته‌ی مشاوره (moshavere-content-import.md §6) روی Postgres واقعی: dry run چیزی نمی‌نویسد،
 * اجرای واقعی درخت را می‌سازد و درس را به سرفصل وصل می‌کند، اجرای دوم چیزی نمی‌سازد، بسته‌ی نامعتبر پیش از هر
 * نوشتنی با 400 رد می‌شود، و مدیر مدرسه و معلم 403 می‌گیرند.
 */
@SpringBootTest(properties = "roshdeandishe.platform-admin.emails=platform-moshavere-import@roshdeandishe.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformMoshavereImportFlowIntegrationTest {

    private static final String PLATFORM_EMAIL = "platform-moshavere-import@roshdeandishe.test";
    private static final String IMPORT = "/api/platform/moshavere/import";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<ApplicationRunner> runners;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private final String prefix = "PMI-" + tag;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from curriculum.topics where source_code like ?", prefix + "%");
        jdbc.update("delete from academic.courses where name like ?", prefix + "%");
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
        String email = "platform-moshavere.teacher@roshdeandishe.test";
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

    private ResultActions importBundle(MockHttpSession session, Map<String, Object> bundle, boolean dryRun)
            throws Exception {
        return mockMvc.perform(post(IMPORT + "?dryRun=" + dryRun)
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bundle)));
    }

    private Map<String, Object> topic(String code, String parent, String name, int sortOrder) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceCode", code);
        m.put("parentSourceCode", parent);
        m.put("name", name);
        m.put("sortOrder", sortOrder);
        return m;
    }

    private Map<String, Object> course(int grade, String name, String track, String topicCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gradeLevel", grade);
        m.put("name", name);
        m.put("track", track);
        m.put("topicSourceCode", topicCode);
        return m;
    }

    private String root() {
        return prefix + "-R";
    }

    private String child() {
        return prefix + "-C";
    }

    private String courseA() {
        return prefix + "-درس-الف";
    }

    private String courseB() {
        return prefix + "-درس-ب";
    }

    /** چهار سرفصل (دو ریشه، فرزند، نوه) و دو درس: یکی به سرفصل وصل، یکی بدون سرفصل. */
    private Map<String, Object> bundle(Consumer<List<Map<String, Object>>> topicTweak,
                                       Consumer<List<Map<String, Object>>> courseTweak) {
        List<Map<String, Object>> topics = new ArrayList<>(List.of(
                topic(root(), null, "ریاضی", 1),
                topic(child(), root(), "جبر", 1),
                topic(prefix + "-G", child(), "معادله", 1),
                topic(prefix + "-S", null, "فیزیک", 2)));
        List<Map<String, Object>> courses = new ArrayList<>(List.of(
                course(10, courseA(), "ریاضی", child()),
                course(10, courseB(), null, null)));
        topicTweak.accept(topics);
        courseTweak.accept(courses);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", 1);
        bundle.put("source", "test");
        bundle.put("topics", topics);
        bundle.put("courses", courses);
        return bundle;
    }

    private Map<String, Object> validBundle() {
        return bundle(t -> { }, c -> { });
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private int topicCount() {
        return count("select count(*) from curriculum.topics where source_code like ?", prefix + "%");
    }

    private int courseCount() {
        return count("select count(*) from academic.courses where name like ?", prefix + "%");
    }

    @Test
    void dryRunReportsCountsAndPersistsNothing() throws Exception {
        MockHttpSession platform = platformAdminSession();

        importBundle(platform, validBundle(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.topicsCreated").value(4))
                .andExpect(jsonPath("$.topicsUpdated").value(0))
                .andExpect(jsonPath("$.coursesCreated").value(2))
                .andExpect(jsonPath("$.coursesUpdated").value(0))
                .andExpect(jsonPath("$.coursesUnlinked").value(1));

        assertThat(topicCount()).isZero();
        assertThat(courseCount()).isZero();
    }

    @Test
    void realRunPersistsTheTreeAndLinksTheCourse() throws Exception {
        MockHttpSession platform = platformAdminSession();

        importBundle(platform, validBundle(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.topicsCreated").value(4))
                .andExpect(jsonPath("$.topicsUpdated").value(0))
                .andExpect(jsonPath("$.coursesCreated").value(2))
                .andExpect(jsonPath("$.coursesUpdated").value(0))
                .andExpect(jsonPath("$.coursesUnlinked").value(1));

        assertThat(topicCount()).isEqualTo(4);
        assertThat(courseCount()).isEqualTo(2);
        Long rootId = jdbc.queryForObject("select id from curriculum.topics where source_code = ?", Long.class, root());
        Long childId = jdbc.queryForObject("select id from curriculum.topics where source_code = ?", Long.class, child());
        assertThat(jdbc.queryForObject("select parent_id from curriculum.topics where id = ?", Long.class, childId))
                .isEqualTo(rootId);
        assertThat(jdbc.queryForObject("select school_id from curriculum.topics where id = ?", Long.class, childId))
                .isNull();
        assertThat(jdbc.queryForObject(
                "select topic_id from academic.courses where grade_level = 10 and name = ?", Long.class, courseA()))
                .isEqualTo(childId);
        assertThat(jdbc.queryForObject(
                "select track from academic.courses where grade_level = 10 and name = ?", String.class, courseA()))
                .isEqualTo("ریاضی");
        assertThat(jdbc.queryForObject(
                "select topic_id from academic.courses where grade_level = 10 and name = ?", Long.class, courseB()))
                .isNull();
    }

    @Test
    void aSecondRealRunCreatesNothing() throws Exception {
        MockHttpSession platform = platformAdminSession();

        importBundle(platform, validBundle(), false).andExpect(status().isOk());
        importBundle(platform, validBundle(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicsCreated").value(0))
                .andExpect(jsonPath("$.topicsUpdated").value(4))
                .andExpect(jsonPath("$.coursesCreated").value(0))
                .andExpect(jsonPath("$.coursesUpdated").value(2))
                .andExpect(jsonPath("$.coursesUnlinked").value(1));

        assertThat(topicCount()).isEqualTo(4);
        assertThat(courseCount()).isEqualTo(2);
    }

    @Test
    void aDryRunAfterARealRunLeavesTheDataAsItWas() throws Exception {
        MockHttpSession platform = platformAdminSession();
        importBundle(platform, validBundle(), false).andExpect(status().isOk());

        Map<String, Object> changed = bundle(
                t -> t.set(1, topic(child(), root(), "جبر خطی", 9)),
                c -> { });
        importBundle(platform, changed, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicsCreated").value(0))
                .andExpect(jsonPath("$.topicsUpdated").value(4));

        assertThat(jdbc.queryForObject("select name from curriculum.topics where source_code = ?", String.class, child()))
                .isEqualTo("جبر");
    }

    @Test
    void invalidBundlesAreRejectedBeforeAnythingIsWritten() throws Exception {
        MockHttpSession platform = platformAdminSession();

        // کد منبع تکراری
        importBundle(platform, bundle(t -> t.add(topic(root(), null, "تکراری", 3)), c -> { }), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        // والد ناشناس (و والدی که دیرتر از فرزند می‌آید)
        importBundle(platform, bundle(t -> t.add(topic(prefix + "-O", prefix + "-NOPE", "یتیم", 3)), c -> { }), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        importBundle(platform, bundle(t -> t.add(0, topic(prefix + "-E", prefix + "-L", "زودرس", 3)), c -> { }), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        // نام خالی
        importBundle(platform, bundle(t -> t.add(topic(prefix + "-B", null, "  ", 3)), c -> { }), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        // پایه‌ی ۱۳
        importBundle(platform, bundle(t -> { }, c -> c.add(course(13, prefix + "-پایه", null, null))), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        // رشته‌ی نامعتبر
        importBundle(platform, bundle(t -> { }, c -> c.add(course(10, prefix + "-رشته", "هنر", null))), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        // سرفصل درس در بسته نیست
        importBundle(platform, bundle(t -> { }, c -> c.add(course(10, prefix + "-بی‌سرفصل", null, prefix + "-NOPE"))), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        // قالب غیر از ۱
        Map<String, Object> wrongFormat = validBundle();
        wrongFormat.put("format", 2);
        importBundle(platform, wrongFormat, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));

        assertThat(topicCount()).isZero();
        assertThat(courseCount()).isZero();
    }

    @Test
    void schoolAdminAndTeacherGetPermissionDenied() throws Exception {
        MockHttpSession schoolAdmin = login("admin@roshdeandishe.test", "password");
        MockHttpSession teacher = teacherSession();

        for (MockHttpSession session : new MockHttpSession[]{schoolAdmin, teacher}) {
            importBundle(session, validBundle(), false)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(endsWith("permission-denied")));
            importBundle(session, validBundle(), true)
                    .andExpect(status().isForbidden());
        }
        assertThat(topicCount()).isZero();
        assertThat(courseCount()).isZero();
    }
    private Map<String, Object> question(String code, String topicCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceCode", code);
        m.put("topicSourceCode", topicCode);
        m.put("text", "کدام یک از گزینه‌های زیر صحیح است.");
        m.put("choices", List.of("الف", "ب", "ج", "د"));
        m.put("correctChoice", 2);
        m.put("answer", null);
        return m;
    }

    private int questionCount() {
        return count("select count(*) from curriculum.questions where source_code like ?", prefix + "%");
    }

    @Test
    void questionsAreUpsertedBySourceCodeAndDryRunPersistsNothing() throws Exception {
        MockHttpSession platform = platformAdminSession();
        Map<String, Object> bundle = validBundle();
        bundle.put("questions", List.of(question(prefix + "-Q1", child())));

        importBundle(platform, bundle, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsCreated").value(1))
                .andExpect(jsonPath("$.questionsUpdated").value(0));
        assertThat(questionCount()).isZero();

        importBundle(platform, bundle, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsCreated").value(1));
        importBundle(platform, bundle, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsCreated").value(0))
                .andExpect(jsonPath("$.questionsUpdated").value(1));
        assertThat(questionCount()).isEqualTo(1);
    }

    @Test
    void questionWithoutFourChoicesIsRefusedBeforeAnyWrite() throws Exception {
        MockHttpSession platform = platformAdminSession();
        Map<String, Object> bad = question(prefix + "-Q2", child());
        bad.put("choices", List.of("الف", "ب"));
        Map<String, Object> bundle = validBundle();
        bundle.put("questions", List.of(bad));

        importBundle(platform, bundle, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("moshavere-bundle-invalid")));
        assertThat(topicCount()).isZero();
    }
}
