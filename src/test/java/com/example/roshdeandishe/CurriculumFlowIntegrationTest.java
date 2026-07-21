package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ماژول curriculum (پورت moshavere، قدم ۲ - MOSHAVERE_PORT_SPEC.md §4):
 * تاکسونومی سرفصل‌ها + بانک سؤال. مهم‌ترین سمنتیک تست‌شده: فیلترِ سرفصل
 * در فهرست سؤال‌ها شامل «کل زیر درخت» است (descendants)، همان رفتار
 * QuestionFilter در منبع. الگوی لاگین/دو-طعم-403 از
 * FinanceManagementFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CurriculumFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private long createTopic(MockHttpSession session, Long parentId, String name) throws Exception {
        String parent = parentId == null ? "null" : String.valueOf(parentId);
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + parent + ",\"name\":\"" + name + "\",\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createQuestion(MockHttpSession session, long topicId, String text, String difficulty)
            throws Exception {
        String diff = difficulty == null ? "null" : "\"" + difficulty + "\"";
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"text\":\"" + text + "\","
                                + "\"choice1\":\"الف\",\"choice2\":\"ب\",\"choice3\":\"ج\",\"choice4\":\"د\","
                                + "\"correctChoice\":2,\"answer\":\"پاسخ تشریحی\",\"difficulty\":" + diff + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void listingTopicsWithoutTenantHeaderIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/topics").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanBuildAndReadTheTopicTree() throws Exception {
        MockHttpSession session = loginAsAdmin();

        long rootId = createTopic(session, null, "ریاضی هفتم");
        long childId = createTopic(session, rootId, "جبر");
        long grandchildId = createTopic(session, childId, "معادله‌ی خطی");

        // فهرست تخت
        mockMvc.perform(get("/api/admin/schools/1/topics")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + childId + " && @.parentId == " + rootId + ")]").exists());

        // درخت تو در تو: ریشه → جبر → معادله‌ی خطی
        MvcResult treeResult = mockMvc.perform(get("/api/admin/schools/1/topics/tree")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(treeResult.getResponse().getContentAsString());
        JsonNode root = findNodeById(tree, rootId);
        assertThat(root).isNotNull();
        assertThat(root.get("name").asText()).isEqualTo("ریاضی هفتم");
        JsonNode child = findNodeById(root.get("children"), childId);
        assertThat(child).isNotNull();
        JsonNode grandchild = findNodeById(child.get("children"), grandchildId);
        assertThat(grandchild).isNotNull();
        assertThat(grandchild.get("children")).isEmpty();
    }

    private JsonNode findNodeById(JsonNode arrayNode, long id) {
        for (JsonNode node : arrayNode) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    @Test
    void questionTopicFilterIncludesTheWholeSubtree() throws Exception {
        MockHttpSession session = loginAsAdmin();

        long rootId = createTopic(session, null, "علوم هشتم");
        long childId = createTopic(session, rootId, "فیزیک");
        long grandchildId = createTopic(session, childId, "نور");
        long unrelatedRootId = createTopic(session, null, "ادبیات هشتم");

        long onChild = createQuestion(session, childId, "سؤال فیزیک", "EASY");
        long onGrandchild = createQuestion(session, grandchildId, "سؤال نور", "HARD");
        long onUnrelated = createQuestion(session, unrelatedRootId, "سؤال ادبیات", "EASY");

        // سمنتیک پورت‌شده از moshavere: فیلترِ سرفصلِ ریشه، سؤال‌های کل
        // زیر درخت (فرزند و نوه) را برمی‌گرداند، نه فقط خود ریشه را -
        // و سؤال سرفصل غیرمرتبط را نه
        mockMvc.perform(get("/api/admin/schools/1/questions")
                        .param("topicId", String.valueOf(rootId))
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + onChild + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + onGrandchild + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + onUnrelated + ")]").doesNotExist());

        // فیلتر دشواری روی همان زیر درخت
        mockMvc.perform(get("/api/admin/schools/1/questions")
                        .param("topicId", String.valueOf(rootId))
                        .param("difficulty", "HARD")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + onGrandchild + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + onChild + ")]").doesNotExist());
    }

    @Test
    void invalidQuestionsAreRejectedWithProblemDetail() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long topicId = createTopic(session, null, "سرفصل اعتبارسنجی");

        // correctChoice خارج از بازه‌ی ۱ تا ۴
        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"text\":\"سؤال\","
                                + "\"choice1\":\"الف\",\"choice2\":\"ب\",\"choice3\":\"ج\",\"choice4\":\"د\","
                                + "\"correctChoice\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-question")));

        // گزینه‌ی خالی
        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"text\":\"سؤال\","
                                + "\"choice1\":\"الف\",\"choice2\":\"\",\"choice3\":\"ج\",\"choice4\":\"د\","
                                + "\"correctChoice\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-question")));

        // سرفصل ناموجود
        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":999999,\"text\":\"سؤال\","
                                + "\"choice1\":\"الف\",\"choice2\":\"ب\",\"choice3\":\"ج\",\"choice4\":\"د\","
                                + "\"correctChoice\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("topic-not-found")));

        // نام سرفصل خالی
        mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-topic")));

        // والدِ ناموجود روی ساخت سرفصل
        mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":999999,\"name\":\"سرفصل یتیم\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("topic-not-found")));
    }

    @Test
    void teacherCanAuthorTopicsAndQuestionsButParentCannotViewTheBank() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        // معلم: CURRICULUM_MANAGE دارد - تألیف بانک سؤال کار روزمره‌ی معلم است
        MockHttpSession teacherSession =
                registerAndLogin("curriculum.teacher@roshdeandishe.test", "معلم بانک سؤال");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"curriculum.teacher@roshdeandishe.test\","
                                + "\"fullName\":\"معلم بانک سؤال\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());

        long topicId = createTopic(teacherSession, null, "سرفصل ساخته‌ی معلم");
        createQuestion(teacherSession, topicId, "سؤال ساخته‌ی معلم", null);

        // والد: نه CURRICULUM_VIEW دارد نه CURRICULUM_MANAGE - بانک سؤال
        // گزینه‌ی درست و پاسخ تشریحی را حمل می‌کند (permission-denied،
        // نه module-not-entitled: مدرسه‌ی نمونه CURRICULUM را دارد)
        MockHttpSession parentSession =
                registerAndLogin("curriculum.parent@roshdeandishe.test", "والد کنجکاو");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"curriculum.parent@roshdeandishe.test\","
                                + "\"fullName\":\"والد کنجکاو\",\"roleCode\":\"PARENT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/questions")
                        .header("X-School-ID", "1")
                        .session(parentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void schoolWithoutCurriculumEntitlementGetsModuleNotEntitled() throws Exception {
        MockHttpSession founderSession =
                registerAndLogin("curriculum.founder@roshdeandishe.test", "بنیان‌گذار بدون ماژول بانک سؤال");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بدون بانک سؤال\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\",\"level\":\"ELEMENTARY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // ساخت خودسرویس یک بسته‌ی اشتراک آزمایشی می‌دهد (CLAUDE.md §11 Q10) -
        // برای تست مسیر module-not-entitled، همان entitlement تازه‌اعطاشده را
        // صراحتاً لغو می‌کنیم (facade-only، بدون REST - CLAUDE.md §10)
        iamFacade.setSchoolEntitlement(schoolId, new IamFacade.ModuleCode("CURRICULUM"), false);

        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/topics")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }
}
