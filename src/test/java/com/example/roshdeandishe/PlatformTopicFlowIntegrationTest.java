package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * سرفصل/سؤال پلتفرمی (school_id خالی، V23): برای همه‌ی مدرسه‌ها قابل‌مشاهده است؛
 * سؤال مدرسه روی سرفصل پلتفرمی فقط برای همان مدرسه دیده می‌شود؛ سرفصلِ
 * اختصاصیِ مدرسه‌ی دیگر «یافت نشد» است. ردیف‌های پلتفرمی با JdbcTemplate
 * درج می‌شوند (هنوز endpoint نوشتن پلتفرمی نیست).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformTopicFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

    /** مدرسه‌ی تازه با بنیان‌گذارش (SCHOOL_ADMIN) و entitlement ماژول CURRICULUM. */
    private record OtherSchool(long id, MockHttpSession admin) {
    }

    private OtherSchool createOtherSchool() throws Exception {
        String email = "platform.topic." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"مدیر مدرسه‌ی دیگر\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MockHttpSession session = login(email, "password123");
        MvcResult created = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی دیگر " + UUID.randomUUID() + "\",\"timezone\":\"Asia/Tehran\","
                                + "\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        jdbc.update("insert into iam.school_entitlements (active, module_id, school_id) "
                + "select true, id, ? from iam.modules where code = 'CURRICULUM'", schoolId);
        return new OtherSchool(schoolId, session);
    }

    private long insertTopic(Long schoolId, Long parentId, String name) {
        return jdbc.queryForObject(
                "insert into curriculum.topics (school_id, parent_id, name, sort_order) values (?, ?, ?, 0) "
                        + "returning id", Long.class, schoolId, parentId, name);
    }

    private long insertPlatformQuestion(long topicId, String text) {
        return jdbc.queryForObject(
                "insert into curriculum.questions (school_id, topic_id, text, choice1, choice2, choice3, choice4, "
                        + "correct_choice) values (null, ?, ?, 'الف', 'ب', 'ج', 'د', 1) returning id",
                Long.class, topicId, text);
    }

    private long createQuestion(MockHttpSession session, long schoolId, long topicId, String text) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/" + schoolId + "/questions")
                        .with(csrf())
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"text\":\"" + text + "\","
                                + "\"choice1\":\"الف\",\"choice2\":\"ب\",\"choice3\":\"ج\",\"choice4\":\"د\","
                                + "\"correctChoice\":2}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode getJson(MockHttpSession session, long schoolId, String path) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/schools/" + schoolId + path)
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    @Test
    void schoolAdminSeesPlatformTopicsInTheTreeAndFlatList() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long root = insertTopic(null, null, "پلتفرم ریشه");
        long child = insertTopic(null, root, "پلتفرم فرزند");

        JsonNode tree = getJson(admin, 1, "/topics/tree");
        JsonNode rootNode = findById(tree, root);
        assertThat(rootNode).isNotNull();
        assertThat(findById(rootNode.get("children"), child)).isNotNull();

        JsonNode flat = getJson(admin, 1, "/topics");
        JsonNode flatChild = findById(flat, child);
        assertThat(flatChild).isNotNull();
        assertThat(flatChild.get("schoolId").isNull()).isTrue();

        // مدرسه‌ی دیگر هم همان سرفصل پلتفرمی را می‌بیند
        OtherSchool other = createOtherSchool();
        assertThat(findById(getJson(other.admin(), other.id(), "/topics/tree"), root)).isNotNull();
    }

    @Test
    void schoolQuestionOnAPlatformTopicIsCreatedListedAndInvisibleToOtherSchools() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long root = insertTopic(null, null, "پلتفرم سؤال ریشه");
        long child = insertTopic(null, root, "پلتفرم سؤال فرزند");
        long platformQuestion = insertPlatformQuestion(child, "سؤال پلتفرمی");

        long ownQuestion = createQuestion(admin, 1, child, "سؤال مدرسه روی سرفصل پلتفرمی");

        // فیلتر سرفصل ریشه: کل زیر درخت - هم سؤال پلتفرمی و هم سؤال خودِ مدرسه
        JsonNode filtered = getJson(admin, 1, "/questions?topicId=" + root);
        assertThat(findById(filtered, ownQuestion)).isNotNull();
        assertThat(findById(filtered, ownQuestion).get("schoolId").asLong()).isEqualTo(1);
        assertThat(findById(filtered, platformQuestion)).isNotNull();
        assertThat(findById(filtered, platformQuestion).get("schoolId").isNull()).isTrue();

        // بدون فیلتر هم هر دو
        JsonNode all = getJson(admin, 1, "/questions");
        assertThat(findById(all, ownQuestion)).isNotNull();
        assertThat(findById(all, platformQuestion)).isNotNull();

        // مدرسه‌ی دیگر: سؤال پلتفرمی را می‌بیند، سؤال مدرسه‌ی ۱ را نه
        OtherSchool other = createOtherSchool();
        JsonNode otherFiltered = getJson(other.admin(), other.id(), "/questions?topicId=" + root);
        assertThat(findById(otherFiltered, platformQuestion)).isNotNull();
        assertThat(findById(otherFiltered, ownQuestion)).isNull();
        assertThat(findById(getJson(other.admin(), other.id(), "/questions"), ownQuestion)).isNull();
    }

    @Test
    void subtreeEndpointReturnsOnlyTheTopicAndItsDescendants() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long root = insertTopic(null, null, "زیر درخت ریشه");
        long child = insertTopic(null, root, "زیر درخت فرزند");
        long grandchild = insertTopic(null, child, "زیر درخت نوه");
        long sibling = insertTopic(null, root, "زیر درخت هم‌نیا");

        JsonNode node = getJson(admin, 1, "/topics/" + child + "/subtree");

        assertThat(node.get("id").asLong()).isEqualTo(child);
        assertThat(node.get("name").asText()).isEqualTo("زیر درخت فرزند");
        assertThat(node.get("children")).hasSize(1);
        JsonNode grandchildNode = node.get("children").get(0);
        assertThat(grandchildNode.get("id").asLong()).isEqualTo(grandchild);
        assertThat(grandchildNode.get("children")).isEmpty();

        // از ریشه: هر سه نسل، با هم‌نیا
        JsonNode full = getJson(admin, 1, "/topics/" + root + "/subtree");
        assertThat(full.get("children")).hasSize(2);
        assertThat(findById(full.get("children"), sibling)).isNotNull();
    }

    @Test
    void aSchoolCannotReadAnotherSchoolsOwnTopicThroughTheSubtreeEndpoint() throws Exception {
        OtherSchool other = createOtherSchool();
        long foreignTopic = insertTopic(other.id(), null, "سرفصل اختصاصی مدرسه‌ی دیگر");

        MockHttpSession admin = loginAsAdmin();
        mockMvc.perform(get("/api/admin/schools/1/topics/" + foreignTopic + "/subtree")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("topic-not-found")));

        // صاحبش آن را می‌بیند
        assertThat(getJson(other.admin(), other.id(), "/topics/" + foreignTopic + "/subtree").get("id").asLong())
                .isEqualTo(foreignTopic);
    }

    @Test
    void subtreeDoesNotLeakAnotherSchoolsTopicsHangingUnderAPlatformTopic() throws Exception {
        OtherSchool other = createOtherSchool();
        long platformRoot = insertTopic(null, null, "پلتفرم ریشه‌ی نشت");
        long foreignChild = insertTopic(other.id(), platformRoot, "فرزند اختصاصی مدرسه‌ی دیگر");

        MockHttpSession admin = loginAsAdmin();
        JsonNode node = getJson(admin, 1, "/topics/" + platformRoot + "/subtree");
        assertThat(node.get("children")).isEmpty();

        JsonNode ownerView = getJson(other.admin(), other.id(), "/topics/" + platformRoot + "/subtree");
        assertThat(findById(ownerView.get("children"), foreignChild)).isNotNull();
    }
}
