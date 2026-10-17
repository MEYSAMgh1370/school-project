package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.notification.NotificationFacade;
import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * رباتِ بله‌ی اختصاصیِ مدرسه (openspec/changes/bale-school-bots.md، PR 1): ثبت و حذفِ ربات توسط مدیرِ مدرسه،
 * مسیریابیِ وب‌هوک با راز، محدودکردنِ دامنه‌ی هر ربات، بررسیِ عضویت هنگام لینک، مسیریابیِ اطلاع‌رسانی و
 * غیرفعال‌شدن با 401.
 * <p>
 * مثل {@link BaleFlowIntegrationTest} بله با یک {@link HttpServer} جعلی شبیه‌سازی می‌شود، ولی این بار هر دو توکن
 * (ربات مشترک و ربات‌های مدرسه) را می‌شناسد. هر تست مدرسه، کاربر، توکن و chat خودش را می‌سازد (شناسه‌ها از
 * {@link #SEQ} می‌آیند) و فراخوانی‌های جعلی را فقط با توکنِ یکتای خودش فیلتر می‌کند، پس تست‌ها به ترتیب و به
 * پیام‌های هم وابسته نیستند و با BaleFlowIntegrationTest تداخلی ندارند (کلیدِ پیکربندی متفاوت = context متفاوت).
 * <p>
 * مسیرِ مدیر پلتفرم (لینک آزمایشی) و سقفِ ۴۰ تایی صف را این تست نمی‌پوشاند؛ آن‌ها unit test دارند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SchoolBotFlowIntegrationTest {

    private static final String SHARED_SECRET = "school-bot-flow-shared-secret";
    private static final String SHARED_TOKEN = "school-bot-flow-shared-token";
    private static final String WEBHOOK_BASE_URL = "https://school-bot-flow.example.test";
    /** Base64 از ۳۲ بایت ('0123456789abcdef' دو بار)؛ فقط برای تست. */
    private static final String TEST_ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());
    private static final Pattern WEBHOOK_SECRET_IN_URL = Pattern.compile("/api/bale/webhook/([A-Za-z0-9_-]+)");

    /** هر تماسی که اپ به بله جعلی زده است. */
    private record Call(String token, String method, String body) {
    }

    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    /** توکن‌هایی که getMe آن‌ها را می‌پذیرد: توکن → (شناسه‌ی ربات، نام کاربری). */
    private static final Map<String, BotIdentity> KNOWN_BOTS = new ConcurrentHashMap<>();
    /** وضعیت HTTP پاسخ sendMessage به‌ازای هر توکن (پیش‌فرض ۲۰۰). */
    private static final Map<String, Integer> SEND_MESSAGE_STATUS = new ConcurrentHashMap<>();
    /** همه‌ی توکن‌ها و رازهای ربات مدرسه که در این کلاس ساخته شده‌اند؛ هیچ‌کدام نباید لاگ شوند. */
    private static final List<String> SECRET_VALUES = new CopyOnWriteArrayList<>();

    private record BotIdentity(long id, String username) {
    }

    private static final HttpServer FAKE_BALE_API = startFakeBaleApi();

    private static HttpServer startFakeBaleApi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", SchoolBotFlowIntegrationTest::handleFakeBaleRequest);
            server.start();
            return server;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** مسیر: {@code /bot<token>/<method>}. */
    private static void handleFakeBaleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int slash = path.indexOf('/', 1);
        if (!path.startsWith("/bot") || slash < 0) {
            respond(exchange, 404, "{\"ok\":false}");
            return;
        }
        String token = path.substring("/bot".length(), slash);
        String method = path.substring(slash + 1);
        CALLS.add(new Call(token, method, body));

        switch (method) {
            case "getMe" -> {
                BotIdentity bot = KNOWN_BOTS.get(token);
                if (bot == null) {
                    respond(exchange, 401, "{\"ok\":false,\"error_code\":401}");
                } else {
                    respond(exchange, 200, "{\"ok\":true,\"result\":{\"id\":" + bot.id()
                            + ",\"is_bot\":true,\"first_name\":\"x\",\"username\":\"" + bot.username() + "\"}}");
                }
            }
            case "sendMessage" -> {
                int status = SEND_MESSAGE_STATUS.getOrDefault(token, 200);
                respond(exchange, status, status == 200 ? "{\"ok\":true}" : "{\"ok\":false,\"error_code\":" + status + "}");
            }
            default -> respond(exchange, 200, "{\"ok\":true,\"result\":true}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @DynamicPropertySource
    static void baleProperties(DynamicPropertyRegistry registry) {
        registry.add("roshdeandishe.bale.api-base-url",
                () -> "http://localhost:" + FAKE_BALE_API.getAddress().getPort());
        registry.add("roshdeandishe.bale.bot-token", () -> SHARED_TOKEN);
        registry.add("roshdeandishe.bale.webhook-secret", () -> SHARED_SECRET);
        registry.add("roshdeandishe.bale.token-encryption-key", () -> TEST_ENCRYPTION_KEY);
        registry.add("roshdeandishe.bale.webhook-base-url", () -> WEBHOOK_BASE_URL);
    }

    @AfterAll
    static void stopFakeBaleApi() {
        FAKE_BALE_API.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private NotificationFacade notificationFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- acceptance criteria 1, 12 ---------------------------------------------------------------

    @Test
    void schoolAdminRegistersBotWithEncryptedTokenAndSecretWebhook(CapturedOutput output) throws Exception {
        School school = createSchool("ثبت");
        MockHttpSession admin = adminOf(school);
        String token = newBotToken();

        MvcResult result = mockMvc.perform(put(botUrl(school))
                        .with(csrf())
                        .session(admin)
                        .header("X-School-ID", school.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(KNOWN_BOTS.get(token).username()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String secret = capturedWebhookSecret(token);
        SECRET_VALUES.add(secret);

        // getMe و setWebhook با توکن خودِ ربات صدا زده شده‌اند و آدرس وب‌هوک همان راز را دارد
        assertThat(callsTo(token, "getMe")).hasSize(1);
        assertThat(callsTo(token, "setWebhook")).hasSize(1);
        assertThat(callsTo(token, "setWebhook").get(0).body()).contains(WEBHOOK_BASE_URL + "/api/bale/webhook/" + secret);

        // پایگاه داده: توکن رمزشده و راز فقط به‌صورت هش
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select token_ciphertext, webhook_secret_hash, status, bot_username from bale.school_bots where school_id = ?",
                school.id());
        assertThat((String) row.get("token_ciphertext")).isNotBlank().isNotEqualTo(token).doesNotContain(token);
        assertThat((String) row.get("webhook_secret_hash")).isNotEqualTo(secret).doesNotContain(secret);
        assertThat(row.get("status")).isEqualTo("ACTIVE");

        // پاسخ‌ها هرگز توکن یا راز را برنمی‌گردانند
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(token).doesNotContain(secret);
        String getBody = mockMvc.perform(get(botUrl(school)).session(admin).header("X-School-ID", school.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(KNOWN_BOTS.get(token).username()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(getBody).doesNotContain(token).doesNotContain(secret);

        assertNothingSecretLogged(output);
    }

    // ---- acceptance criterion 2 (invalid token, bot taken by another school) -------------------------

    @Test
    void invalidTokenAndBotOfAnotherSchoolAreRefusedWithNothingStored() throws Exception {
        School first = createSchool("اول");
        School second = createSchool("دوم");
        MockHttpSession firstAdmin = adminOf(first);
        MockHttpSession secondAdmin = adminOf(second);

        String unknownToken = "school-token-unknown-" + SEQ.incrementAndGet(); // بله getMe را 401 می‌دهد
        SECRET_VALUES.add(unknownToken);
        putBot(secondAdmin, second, unknownToken).andExpect(status().isBadRequest());
        assertThat(botRowCount(second.id())).isZero();
        assertThat(callsTo(unknownToken, "setWebhook")).isEmpty();

        String token = newBotToken();
        putBot(firstAdmin, first, token).andExpect(status().isOk());
        putBot(secondAdmin, second, token).andExpect(status().isConflict());
        assertThat(botRowCount(second.id())).isZero();
        assertThat(botRowCount(first.id())).isEqualTo(1);
    }

    // ---- acceptance criterion 3 ---------------------------------------------------------------------

    @Test
    void updateToSchoolSecretIsAnsweredWithSchoolTokenAndUnknownSecretIs404() throws Exception {
        School school = createSchool("وب‌هوک");
        Bot bot = registerBot(school);
        long chatId = uniqueId();

        mockMvc.perform(post("/api/bale/webhook/" + bot.secret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textUpdate(chatId, "/start")))
                .andExpect(status().isOk());

        List<Call> replies = awaitSentMessages(bot.token(), chatId, 1);
        assertThat(replies.get(0).body()).contains("خوش آمدید");
        // پاسخ فقط با توکن همان ربات رفته است، نه ربات مشترک
        assertThat(callsTo(SHARED_TOKEN, "sendMessage")).noneMatch(call -> call.body().contains(chatIdField(chatId)));

        mockMvc.perform(post("/api/bale/webhook/unknown-secret-" + SEQ.incrementAndGet())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textUpdate(chatId, "/start")))
                .andExpect(status().isNotFound());
    }

    // ---- acceptance criterion 4 -----------------------------------------------------------------------

    @Test
    void memberOfTwoSchoolsSeesOnlyOwnSchoolInEachBot() throws Exception {
        School schoolA = createSchool("الف"); // ربات اختصاصی دارد
        School schoolB = createSchool("ب");   // ربات ندارد
        Bot botA = registerBot(schoolA);
        Parent parent = createParent("چندمدرسه‌ای", schoolA, schoolB);
        long chatId = uniqueId(); // همان chat در هر دو ربات، پس یکتاییِ V32 (به‌ازای هر ربات) راستی‌آزمایی می‌شود

        linkChat(SHARED_SECRET, SHARED_TOKEN, chatId, parent, null);
        linkChat(botA.secret(), botA.token(), chatId, parent, botA.rowId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from bale.bale_links where chat_id = ?",
                Integer.class, chatId)).isEqualTo(2);

        postText(botA.secret(), chatId, "مدرسه‌های من");
        Call inBotA = awaitMessageContaining(botA.token(), chatId, schoolA.name());
        assertThat(inBotA.body()).doesNotContain(schoolB.name());

        postText(SHARED_SECRET, chatId, "مدرسه‌های من");
        Call inSharedBot = awaitMessageContaining(SHARED_TOKEN, chatId, schoolB.name());
        assertThat(inSharedBot.body()).doesNotContain(schoolA.name());
    }

    // ---- acceptance criterion 5 (non-member) --------------------------------------------------------------

    @Test
    void nonMemberCannotLinkToSchoolBot() throws Exception {
        School school = createSchool("عضویت");
        School otherSchool = createSchool("دیگر");
        Bot bot = registerBot(school);
        Parent outsider = createParent("غیرعضو", otherSchool); // شماره‌اش ثبت است ولی عضو مدرسه‌ی ربات نیست
        long chatId = uniqueId();

        mockMvc.perform(post("/api/bale/webhook/" + bot.secret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactUpdate(chatId, outsider.phone())))
                .andExpect(status().isOk());

        Call reply = awaitMessageContaining(bot.token(), chatId, "شما عضو این مدرسه نیستید.");
        assertThat(reply).isNotNull();
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject("select count(*) from bale.bale_links where chat_id = ?",
                        Integer.class, chatId)).isZero());
    }

    // ---- acceptance criterion 6 ---------------------------------------------------------------------------

    @Test
    void notificationsOfSchoolWithBotUseItsBotAndOthersUseSharedBot() throws Exception {
        School schoolA = createSchool("اطلاع الف");
        School schoolB = createSchool("اطلاع ب");
        Bot botA = registerBot(schoolA);
        Parent parent = createParent("والد اطلاع", schoolA, schoolB);
        long chatId = uniqueId();
        linkChat(SHARED_SECRET, SHARED_TOKEN, chatId, parent, null);
        linkChat(botA.secret(), botA.token(), chatId, parent, botA.rowId());

        // مسیر واقعی «ثبت غیبت» رویداد ماژولیتیِ ناهمگام و جداول حضور و غیاب می‌خواهد؛ AbsenceRecordedEventListener
        // همین متدِ facade را با schoolId رویداد صدا می‌زند، پس مستقیم از خودِ facade می‌گذریم.
        String textForA = "AbsenceNoticeForSchoolA" + SEQ.incrementAndGet();
        String textForB = "AbsenceNoticeForSchoolB" + SEQ.incrementAndGet();

        assertThat(notificationFacade.deliverMessageToUser(parent.userId(), schoolA.id(), textForA)).isEqualTo(DeliveryResult.SENT);
        assertThat(notificationFacade.deliverMessageToUser(parent.userId(), schoolB.id(), textForB)).isEqualTo(DeliveryResult.SENT);

        assertThat(callsTo(botA.token(), "sendMessage")).anyMatch(call -> call.body().contains(textForA));
        assertThat(callsTo(botA.token(), "sendMessage")).noneMatch(call -> call.body().contains(textForB));
        assertThat(callsTo(SHARED_TOKEN, "sendMessage")).anyMatch(call -> call.body().contains(textForB));
        assertThat(callsTo(SHARED_TOKEN, "sendMessage")).noneMatch(call -> call.body().contains(textForA));

        // کاربری که فقط به ربات مشترک لینک است، برای مدرسه‌ای با ربات اختصاصی NOT_LINKED می‌ماند (بدون بازگشت به ربات مشترک)
        Parent sharedOnly = createParent("فقط مشترک", schoolA);
        long sharedOnlyChat = uniqueId();
        linkChat(SHARED_SECRET, SHARED_TOKEN, sharedOnlyChat, sharedOnly, null);
        String textForSharedOnly = "NoFallbackNotice" + SEQ.incrementAndGet();
        assertThat(notificationFacade.deliverMessageToUser(sharedOnly.userId(), schoolA.id(), textForSharedOnly))
                .isEqualTo(DeliveryResult.NOT_LINKED);
        assertThat(callsTo(SHARED_TOKEN, "sendMessage")).noneMatch(call -> call.body().contains(textForSharedOnly));
    }

    // ---- acceptance criteria 8, 3 (DISABLED secret), 12 ---------------------------------------------------

    @Test
    void revokedTokenDisablesBotAndSharedBotServesSchoolAgain(CapturedOutput output) throws Exception {
        School schoolA = createSchool("باطل الف");
        School schoolB = createSchool("باطل ب");
        Bot botA = registerBot(schoolA);
        Parent parent = createParent("والد باطل", schoolA, schoolB);
        long chatId = uniqueId();
        linkChat(SHARED_SECRET, SHARED_TOKEN, chatId, parent, null);
        linkChat(botA.secret(), botA.token(), chatId, parent, botA.rowId());

        // بله توکن مدرسه را باطل می‌کند
        SEND_MESSAGE_STATUS.put(botA.token(), 401);
        String revokedText = "RevokedTokenNotice" + SEQ.incrementAndGet();
        assertThat(notificationFacade.deliverMessageToUser(parent.userId(), schoolA.id(), revokedText))
                .isEqualTo(DeliveryResult.UNDELIVERABLE);
        assertThat(botStatus(schoolA.id())).isEqualTo("DISABLED");

        // ربات DISABLED مثل «مدرسه ربات ندارد» است: وب‌هوکش 404 می‌دهد
        mockMvc.perform(post("/api/bale/webhook/" + botA.secret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textUpdate(chatId, "/start")))
                .andExpect(status().isNotFound());

        // و اطلاع‌رسانیِ مدرسه از ربات مشترک می‌رود (لینک مشترک کاربر دست‌نخورده است)
        String fallbackText = "SharedFallbackNotice" + SEQ.incrementAndGet();
        assertThat(notificationFacade.deliverMessageToUser(parent.userId(), schoolA.id(), fallbackText))
                .isEqualTo(DeliveryResult.SENT);
        assertThat(callsTo(SHARED_TOKEN, "sendMessage")).anyMatch(call -> call.body().contains(fallbackText));

        // ربات مشترک دوباره داده‌ی مدرسه‌ی الف را هم نشان می‌دهد
        postText(SHARED_SECRET, chatId, "مدرسه‌های من");
        Call inSharedBot = awaitMessageContaining(SHARED_TOKEN, chatId, schoolA.name());
        assertThat(inSharedBot.body()).contains(schoolB.name());

        assertNothingSecretLogged(output);
    }

    // ---- acceptance criterion 9 -----------------------------------------------------------------------------

    @Test
    void deletingBotRemovesItsLinksAndSharedBotServesSchoolAgain(CapturedOutput output) throws Exception {
        School schoolA = createSchool("حذف الف");
        School schoolB = createSchool("حذف ب");
        MockHttpSession admin = adminOf(schoolA);
        Bot botA = registerBot(schoolA, admin);
        Parent parent = createParent("والد حذف", schoolA, schoolB);
        long chatId = uniqueId();
        linkChat(SHARED_SECRET, SHARED_TOKEN, chatId, parent, null);
        linkChat(botA.secret(), botA.token(), chatId, parent, botA.rowId());

        mockMvc.perform(delete(botUrl(schoolA))
                        .with(csrf())
                        .session(admin)
                        .header("X-School-ID", schoolA.id()))
                .andExpect(status().isNoContent());

        // وب‌هوک برداشته شده، ردیف و لینک‌های ربات رفته‌اند، لینک مشترکِ کاربر مانده است
        assertThat(callsTo(botA.token(), "deleteWebhook")).hasSize(1);
        assertThat(botRowCount(schoolA.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from bale.bale_links where bot_id = ?",
                Integer.class, botA.rowId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bale.bale_links where user_id = ? and bot_id is null",
                Integer.class, parent.userId())).isEqualTo(1);
        mockMvc.perform(post("/api/bale/webhook/" + botA.secret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textUpdate(chatId, "/start")))
                .andExpect(status().isNotFound());

        // مدرسه‌ی الف بدون contact share تازه از ربات مشترک سرویس می‌گیرد
        String text = "AfterDeleteNotice" + SEQ.incrementAndGet();
        assertThat(notificationFacade.deliverMessageToUser(parent.userId(), schoolA.id(), text)).isEqualTo(DeliveryResult.SENT);
        assertThat(callsTo(SHARED_TOKEN, "sendMessage")).anyMatch(call -> call.body().contains(text));
        postText(SHARED_SECRET, chatId, "مدرسه‌های من");
        Call inSharedBot = awaitMessageContaining(SHARED_TOKEN, chatId, schoolA.name());
        assertThat(inSharedBot.body()).contains(schoolB.name());

        assertNothingSecretLogged(output);
    }

    // ---- fixtures ---------------------------------------------------------------------------------------------

    private record School(long id, String name) {
    }

    private record Bot(String token, String secret, long rowId) {
    }

    private record Parent(long userId, String phone) {
    }

    private School createSchool(String label) {
        String name = "مدرسه‌ی ربات " + label + " " + SEQ.incrementAndGet();
        long id = schoolFacade.createSchool(name, "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null).value();
        return new School(id, name);
    }

    /** مدیر مدرسه: ثبت‌نام، عضویت SCHOOL_ADMIN (پیش از ورود)، سپس ورود. */
    private MockHttpSession adminOf(School school) throws Exception {
        String email = "school-bot-admin-" + SEQ.incrementAndGet() + "@roshdeandishe.test";
        register(email, "مدیر ربات");
        iamFacade.createMembership(iamFacade.findOrCreateUser(email, "مدیر ربات"), school.id(), "SCHOOL_ADMIN");
        return login(email);
    }

    /** والدی با شماره‌ی ثبت‌شده و عضویت فعال PARENT در همه‌ی مدرسه‌های داده‌شده. */
    private Parent createParent(String fullName, School... schools) throws Exception {
        String email = "school-bot-parent-" + SEQ.incrementAndGet() + "@roshdeandishe.test";
        String phone = uniquePhoneNumber();
        register(email, fullName);
        MockHttpSession session = login(email);
        mockMvc.perform(put("/api/me/phone")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        IamFacade.UserId userId = iamFacade.findOrCreateUser(email, fullName);
        for (School school : schools) {
            iamFacade.createMembership(userId, school.id(), "PARENT");
        }
        return new Parent(userId.value(), phone);
    }

    private void register(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private Bot registerBot(School school) throws Exception {
        return registerBot(school, adminOf(school));
    }

    private Bot registerBot(School school, MockHttpSession admin) throws Exception {
        String token = newBotToken();
        putBot(admin, school, token).andExpect(status().isOk());
        String secret = capturedWebhookSecret(token);
        SECRET_VALUES.add(secret);
        Long rowId = jdbcTemplate.queryForObject("select id from bale.school_bots where school_id = ?", Long.class, school.id());
        return new Bot(token, secret, rowId);
    }

    private org.springframework.test.web.servlet.ResultActions putBot(MockHttpSession admin, School school, String token)
            throws Exception {
        return mockMvc.perform(put(botUrl(school))
                .with(csrf())
                .session(admin)
                .header("X-School-ID", school.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    /** توکنِ ربات تازه که بله جعلی آن را می‌شناسد (getMe → شناسه و نام کاربریِ یکتا). */
    private static String newBotToken() {
        long n = SEQ.incrementAndGet();
        String token = "school-token-" + n;
        KNOWN_BOTS.put(token, new BotIdentity(n, "school_bot_" + n + "_bot"));
        SECRET_VALUES.add(token);
        return token;
    }

    private static String botUrl(School school) {
        return "/api/admin/schools/" + school.id() + "/bale-bot";
    }

    private static String capturedWebhookSecret(String token) {
        List<Call> calls = callsTo(token, "setWebhook");
        assertThat(calls).as("setWebhook برای این توکن باید صدا زده شده باشد").isNotEmpty();
        Matcher matcher = WEBHOOK_SECRET_IN_URL.matcher(calls.get(calls.size() - 1).body());
        assertThat(matcher.find()).as("بدنه‌ی setWebhook باید آدرس وب‌هوک را داشته باشد: %s", calls.get(0).body()).isTrue();
        return matcher.group(1);
    }

    // ---- DB / fake-API helpers ---------------------------------------------------------------------------------

    private int botRowCount(long schoolId) {
        return jdbcTemplate.queryForObject("select count(*) from bale.school_bots where school_id = ?", Integer.class, schoolId);
    }

    private String botStatus(long schoolId) {
        return jdbcTemplate.queryForObject("select status from bale.school_bots where school_id = ?", String.class, schoolId);
    }

    private static List<Call> callsTo(String token, String method) {
        return CALLS.stream().filter(call -> call.token().equals(token) && call.method().equals(method)).toList();
    }

    private static List<Call> messagesTo(String token, long chatId) {
        return callsTo(token, "sendMessage").stream().filter(call -> call.body().contains(chatIdField(chatId))).toList();
    }

    private static List<Call> awaitSentMessages(String token, long chatId, int expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(messagesTo(token, chatId)).hasSize(expected));
        return messagesTo(token, chatId);
    }

    private static Call awaitMessageContaining(String token, long chatId, String text) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(messagesTo(token, chatId)).anyMatch(call -> call.body().contains(text)));
        return messagesTo(token, chatId).stream().filter(call -> call.body().contains(text)).findFirst().orElseThrow();
    }

    /** لینک کردنِ chat به کاربر در یک ربات با اشتراک‌گذاری شماره و انتظار تا ثبتِ ردیف و رسیدنِ تأییدیه. */
    private void linkChat(String secret, String token, long chatId, Parent parent, Long botRowId) throws Exception {
        mockMvc.perform(post("/api/bale/webhook/" + secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactUpdate(chatId, parent.phone())))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer rows = botRowId == null
                    ? jdbcTemplate.queryForObject(
                            "select count(*) from bale.bale_links where user_id = ? and chat_id = ? and bot_id is null",
                            Integer.class, parent.userId(), chatId)
                    : jdbcTemplate.queryForObject(
                            "select count(*) from bale.bale_links where user_id = ? and chat_id = ? and bot_id = ?",
                            Integer.class, parent.userId(), chatId, botRowId);
            assertThat(rows).isEqualTo(1);
        });
        awaitMessageContaining(token, chatId, "متصل شد");
    }

    private void postText(String secret, long chatId, String text) throws Exception {
        mockMvc.perform(post("/api/bale/webhook/" + secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textUpdate(chatId, text)))
                .andExpect(status().isOk());
    }

    private static void assertNothingSecretLogged(CapturedOutput output) {
        String logs = output.getAll();
        for (String secret : SECRET_VALUES) {
            assertThat(logs).as("توکن یا رازِ ربات مدرسه نباید در لاگ بیاید").doesNotContain(secret);
        }
    }

    // ---- Bale update payloads ------------------------------------------------------------------------------------

    private static String textUpdate(long chatId, String text) {
        return ("{\"update_id\":%d,\"message\":{\"message_id\":1,\"from\":{\"id\":%d,\"is_bot\":false,\"first_name\":\"t\"},"
                + "\"chat\":{\"id\":%d,\"type\":\"private\"},\"text\":\"%s\"}}")
                .formatted(SEQ.incrementAndGet(), chatId, chatId, text);
    }

    /** مخاطبِ خودِ فرستنده (contact.user_id == from.id == chat.id، مثل چت خصوصی در بله). */
    private static String contactUpdate(long chatId, String phoneNumber) {
        return ("{\"update_id\":%d,\"message\":{\"message_id\":1,\"from\":{\"id\":%d,\"is_bot\":false,\"first_name\":\"t\"},"
                + "\"chat\":{\"id\":%d,\"type\":\"private\"},"
                + "\"contact\":{\"phone_number\":\"%s\",\"first_name\":\"t\",\"user_id\":%d}}}")
                .formatted(SEQ.incrementAndGet(), chatId, chatId, phoneNumber, chatId);
    }

    private static String chatIdField(long chatId) {
        return "\"chat_id\":" + chatId;
    }

    private static long uniqueId() {
        return SEQ.incrementAndGet();
    }

    private static String uniquePhoneNumber() {
        String digits = String.valueOf(SEQ.incrementAndGet());
        return "0936" + digits.substring(Math.max(0, digits.length() - 7));
    }
}
