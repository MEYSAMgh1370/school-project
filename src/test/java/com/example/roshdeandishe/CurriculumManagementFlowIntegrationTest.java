package com.example.roshdeandishe;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ماژول curriculum (پورت moshavere، MOSHAVERE_PORT_SPEC.md §4): سرفصل‌های
 * سلسله‌مراتبی + بانک سوال. الگوی لاگین/عضویت از
 * ClassManagementFlowIntegrationTest و الگوی دو طعم 403 از
 * FinanceManagementFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CurriculumManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private long createTopic(MockHttpSession session, String name, Long parentId, Integer order) throws Exception {
        String parent = parentId == null ? "null" : String.valueOf(parentId);
        String ord = order == null ? "null" : String.valueOf(order);
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"parentId\":" + parent + ",\"order\":" + ord + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String questionBody(long topicId, String text, int correctChoice, Integer difficulty) {
        return "{\"topicId\":" + topicId + ",\"text\":\"" + text + "\"," +
                "\"choice1\":\"گزینه ۱\",\"choice2\":\"گزینه ۲\",\"choice3\":\"گزینه ۳\",\"choice4\":\"گزینه ۴\"," +
                "\"correctChoice\":" + correctChoice + ",\"difficulty\":" + difficulty + ",\"discriminationIndex\":null}";
    }

    @Test
    void adminCanBuildTopicTreeAndListIt() throws Exception {
        MockHttpSession session = loginAsAdmin();

        long rootId = createTopic(session, "ریاضی پنجم", null, 1);
        long childId = createTopic(session, "کسرها", rootId, 1);
        long grandchildId = createTopic(session, "جمع کسرها", childId, 2);

        // فهرست تخت: هر سه سرفصل با parentId درست
        mockMvc.perform(get("/api/admin/schools/1/topics")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + rootId + " && @.parentId == null)]").exists())
                .andExpect(jsonPath("$[?(@.id == " + childId + " && @.parentId == " + rootId + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + grandchildId + " && @.parentId == " + childId + ")]").exists());

        // درخت: تو در تو بودن سه سطح
        MvcResult treeResult = mockMvc.perform(get("/api/admin/schools/1/topics/tree")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(treeResult.getResponse().getContentAsString());
        JsonNode root = findById(tree, rootId);
        assertThat(root).isNotNull();
        JsonNode child = findById(root.get("children"), childId);
        assertThat(child).isNotNull();
        assertThat(findById(child.get("children"), grandchildId)).isNotNull();
    }

    private JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    @Test
    void creatingTopicWithUnknownParentIsNotFound() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"سرفصل یتیم\",\"parentId\":999999,\"order\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("topic-not-found")));
    }

    @Test
    void creatingTopicWithBlankNameIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/api/admin/schools/1/topics")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"parentId\":null,\"order\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-topic")));
    }

    @Test
    void adminCanCreateAndFilterQuestions() throws Exception {
        MockHttpSession session = loginAsAdmin();

        long topicA = createTopic(session, "هندسه", null, 1);
        long topicB = createTopic(session, "جبر", null, 2);

        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionBody(topicA, "مساحت مربع با ضلع ۲؟", 3, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(topicA))
                .andExpect(jsonPath("$.correctChoice").value(3));

        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionBody(topicB, "حاصل x+x؟", 2, 4)))
                .andExpect(status().isOk());

        // فیلتر بر اساس سرفصل: فقط سوال هندسه
        mockMvc.perform(get("/api/admin/schools/1/questions?topicId=" + topicA)
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topicId == " + topicA + ")]").exists())
                .andExpect(jsonPath("$[?(@.topicId == " + topicB + ")]").doesNotExist());

        // فیلتر بر اساس سختی: فقط سوال جبر (سختی ۴)
        mockMvc.perform(get("/api/admin/schools/1/questions?difficulty=4")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topicId == " + topicB + " && @.difficulty == 4)]").exists())
                .andExpect(jsonPath("$[?(@.difficulty == 1)]").doesNotExist());
    }

    @Test
    void invalidQuestionsAreRejected() throws Exception {
        MockHttpSession session = loginAsAdmin();
        long topicId = createTopic(session, "علوم", null, 1);

        // گزینه‌ی صحیح خارج از ۱..۴
        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionBody(topicId, "سوال با گزینه‌ی صحیح نامعتبر", 5, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-question")));

        // گزینه‌ی خالی
        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"text\":\"سوال با گزینه‌ی خالی\"," +
                                "\"choice1\":\"الف\",\"choice2\":\"\",\"choice3\":\"ج\",\"choice4\":\"د\"," +
                                "\"correctChoice\":1,\"difficulty\":null,\"discriminationIndex\":null}"))
                .andExpect(status().isBadRequest());

        // سرفصل ناموجود
        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionBody(999999, "سوال بی‌سرفصل", 1, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("topic-not-found")));
    }

    @Test
    void teacherCanAuthorTopicsAndQuestions() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MockHttpSession teacherSession = registerAndLogin("curriculum.teacher@roshdeandishe.test", "معلم مولف");

        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"curriculum.teacher@roshdeandishe.test\",\"fullName\":\"معلم مولف\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());

        // CURRICULUM_MANAGE به TEACHER هم داده می‌شود - معلم‌ها صاحب
        // سرفصل/سوال‌های خودشان هستند (MOSHAVERE_PORT_SPEC.md §2)
        long topicId = createTopic(teacherSession, "سرفصل معلم", null, 0);

        mockMvc.perform(post("/api/admin/schools/1/questions")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(teacherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionBody(topicId, "سوال معلم", 1, 2)))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotSeeQuestionBank() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        MockHttpSession studentSession = registerAndLogin("curriculum.student@roshdeandishe.test", "دانش‌آموز کنجکاو");

        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"curriculum.student@roshdeandishe.test\",\"fullName\":\"دانش‌آموز کنجکاو\",\"roleCode\":\"STUDENT\"}"))
                .andExpect(status().isOk());

        // پاسخ صحیح در همین endpoint برمی‌گردد - دیدن بانک سوال برای
        // دانش‌آموز یعنی لو رفتن آزمون؛ باید permission-denied باشد،
        // نه module-not-entitled (مدرسه‌ی نمونه CURRICULUM را دارد)
        mockMvc.perform(get("/api/admin/schools/1/questions")
                        .header("X-School-ID", "1")
                        .session(studentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));
    }

    @Test
    void schoolWithoutCurriculumEntitlementGetsModuleNotEntitled() throws Exception {
        MockHttpSession founderSession = registerAndLogin("curriculum.founder@roshdeandishe.test", "بنیان‌گذار بدون ماژول");

        MvcResult createResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(founderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی بدون بانک سوال\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long schoolId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // مدرسه‌ی تازه‌ی خودسرویس هیچ entitlement ای ندارد - حتی SCHOOL_ADMIN
        // خودش هم باید module-not-entitled بگیرد، نه permission-denied
        mockMvc.perform(get("/api/admin/schools/" + schoolId + "/topics")
                        .header("X-School-ID", String.valueOf(schoolId))
                        .session(founderSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("module-not-entitled")));
    }

    @Test
    void listingTopicsWithoutTenantHeaderIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/topics").session(session))
                .andExpect(status().isBadRequest());
    }
}
