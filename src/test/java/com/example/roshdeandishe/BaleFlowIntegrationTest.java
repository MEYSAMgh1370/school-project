package com.example.roshdeandishe;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * یکپارچه‌سازی بات بله (فاز ۱): وب‌هوک ورودی (لینک کردن یک chat به کاربر از
 * روی شماره تلفن + دستورهای متنی) و اطلاع‌رسانی خروجی
 * (finance.InvoiceIssuedEvent → notify دانش‌آموز + همه‌ی ولی‌های لینک‌شده).
 * این تست هنگام ساخت ماژول bale نوشته نشد (CLAUDE.md §7 Done، «Bale bot
 * integration, Phase 1» - نبود Docker/Testcontainers در آن نشست) - این
 * گپ همان‌جا مستند شده بود؛ حالا که Docker در دسترس است، نوشته می‌شود.
 * <p>
 * چون roshdeandishe.bale.bot-token در dev/test پیش‌فرض خالی است (BaleClient
 * فقط لاگ می‌کند، هیچ HTTP واقعی نمی‌زند)، این تست - برخلاف بقیه‌ی
 * FlowIntegrationTestها - یک سرور HTTP محلی جعلی (JDK's
 * {@link HttpServer}, بدون وابستگی تازه به pom.xml) بالا می‌آورد و
 * roshdeandishe.bale.api-base-url/bot-token را با {@link DynamicPropertySource}
 * به آن هدایت می‌کند - دقیقاً همان نقشی که GreenMail برای
 * InviteTokenFlowIntegrationTest (یک کانال best-effort مشابه) بازی می‌کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BaleFlowIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String BOT_TOKEN = "test-bot-token";
    private static final List<String> SENT_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    // فیلد static + مقداردهی در همان‌جا (نه @BeforeAll) عمداً: متد
    // @DynamicPropertySource باید پیش از بالا آمدن ApplicationContext مقدار
    // پورت را بداند، و بارگذاریِ کلاس (که فیلدهای static را می‌سازد) همیشه
    // زودتر از فراخوانیِ هر متد static دیگری (چه @DynamicPropertySource، چه
    // @BeforeAll) اتفاق می‌افتد - همان الگویی که Testcontainers برای فیلدهای
    // static container توصیه می‌کند.
    private static final HttpServer FAKE_BALE_API = startFakeBaleApi();

    private static HttpServer startFakeBaleApi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/bot" + BOT_TOKEN + "/sendMessage", exchange -> {
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                SENT_MESSAGES.add(new String(requestBody, StandardCharsets.UTF_8));
                byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void baleProperties(DynamicPropertyRegistry registry) {
        registry.add("roshdeandishe.bale.api-base-url",
                () -> "http://localhost:" + FAKE_BALE_API.getAddress().getPort());
        registry.add("roshdeandishe.bale.bot-token", () -> BOT_TOKEN);
        registry.add("roshdeandishe.bale.webhook-secret", () -> WEBHOOK_SECRET);
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
    private AcademicFacade academicFacade;

    @Autowired
    private SchoolFacade schoolFacade;

    @Autowired
    private com.example.roshdeandishe.finance.FinanceFacade financeFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCapturedMessages() {
        SENT_MESSAGES.clear();
    }

    @Test
    void webhookReturns404ForWrongSecret() throws Exception {
        mockMvc.perform(post("/api/bale/webhook/wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJsonForText(uniqueChatId(), "سلام")))
                .andExpect(status().isNotFound());

        assertThat(SENT_MESSAGES).isEmpty();
    }

    @Test
    void startCommandPromptsForContactShare() throws Exception {
        long chatId = uniqueChatId();

        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJsonForText(chatId, "/start")))
                .andExpect(status().isOk());

        awaitSentMessageCount(1);
        assertThat(SENT_MESSAGES.get(0)).contains("خوش آمدید").contains(chatIdField(chatId));
    }

    @Test
    void sameUpdatePostedTwiceIsAnsweredOnce() throws Exception {
        long chatId = uniqueChatId();
        String body = updateJsonForText(chatId, "/start");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        awaitSentMessageCount(1);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(SENT_MESSAGES).hasSize(1));
    }

    @Test
    void textMessageFromUnlinkedChatGetsNotLinkedPrompt() throws Exception {
        long chatId = uniqueChatId();

        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJsonForText(chatId, "راهنما")))
                .andExpect(status().isOk());

        awaitSentMessageCount(1);
        assertThat(SENT_MESSAGES.get(0)).contains("متصل نشده");
    }

    @Test
    void contactShareWithUnregisteredPhoneNumberIsRejectedAndLinksNothing() throws Exception {
        long chatId = uniqueChatId();

        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactShareUpdateJson(chatId, "09000000099")))
                .andExpect(status().isOk());

        awaitSentMessageCount(1);
        assertThat(SENT_MESSAGES.get(0)).contains("مطابقت ندارد");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer linkCount = jdbcTemplate.queryForObject(
                    "select count(*) from bale.bale_links where chat_id = ?", Integer.class, chatId);
            assertThat(linkCount).isZero();
        });
    }

    @Test
    void contactShareLinksMatchingPhoneNumberAndConfirms() throws Exception {
        String email = "bale-user-" + System.nanoTime() + "@roshdeandishe.test";
        String phoneNumber = uniquePhoneNumber();
        MockHttpSession session = registerAndLogin(email, "کاربر آزمایشی بله");
        setPhoneNumber(session, phoneNumber);
        long chatId = uniqueChatId();

        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactShareUpdateJson(chatId, phoneNumber)))
                .andExpect(status().isOk());

        awaitSentMessageCount(1);
        assertThat(SENT_MESSAGES.get(0)).contains("متصل شد").contains("کاربر آزمایشی بله");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Long linkedChatId = jdbcTemplate.queryForObject(
                    "select chat_id from bale.bale_links where user_id = (select id from iam.users where email = ?)",
                    Long.class, email);
            assertThat(linkedChatId).isEqualTo(chatId);
        });

        // اشتراک‌گذاری دوباره (مثلاً از یک chat دیگر) رکورد تازه نمی‌سازد -
        // همان ردیف upsert می‌شود (BaleUpdateHandler.handleContactShared)
        long secondChatId = uniqueChatId();
        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactShareUpdateJson(secondChatId, phoneNumber)))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer rowCount = jdbcTemplate.queryForObject(
                    "select count(*) from bale.bale_links where user_id = (select id from iam.users where email = ?)",
                    Integer.class, email);
            assertThat(rowCount).isEqualTo(1);
            Long updatedChatId = jdbcTemplate.queryForObject(
                    "select chat_id from bale.bale_links where user_id = (select id from iam.users where email = ?)",
                    Long.class, email);
            assertThat(updatedChatId).isEqualTo(secondChatId);
        });
    }

    @Test
    void linkedParentCanQuerySchoolsAndWardsThroughTextCommands() throws Exception {
        // academicFacade.createClass اکنون schoolId را در برابر یک SchoolEntity
        // واقعی اعتبارسنجی می‌کند (CLAUDE.md §4) - uniqueSchoolId ساختگی دیگر کافی نیست.
        long schoolId = schoolFacade.createSchool(
                "مدرسه‌ی تست بله ۱", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null).value();
        String parentEmail = "bale-parent-" + System.nanoTime() + "@roshdeandishe.test";
        String parentPhone = uniquePhoneNumber();
        MockHttpSession parentSession = registerAndLogin(parentEmail, "والد فرمانده");
        setPhoneNumber(parentSession, parentPhone);
        IamFacade.UserId parentUserId = iamFacade.findOrCreateUser(parentEmail, "والد فرمانده");
        iamFacade.createMembership(parentUserId, schoolId, "PARENT");

        AcademicFacade.ClassId classId = academicFacade.createClass(schoolId, "کلاس آزمایشی بله", null, null, 5, GradingScheme.NUMERIC);
        AcademicFacade.StudentId studentId = academicFacade.enrollStudent(
                "bale-ward-" + System.nanoTime() + "@roshdeandishe.test", "فرزند آزمایشی", schoolId, classId.value());
        academicFacade.linkGuardian(schoolId, studentId.value(), parentUserId.value());

        long chatId = uniqueChatId();
        linkChatToPhoneNumber(chatId, parentPhone);

        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJsonForText(chatId, "مدرسه‌های من")))
                .andExpect(status().isOk());
        awaitSentMessageCount(1);
        assertThat(SENT_MESSAGES.get(0)).contains("والد");
        SENT_MESSAGES.clear();

        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJsonForText(chatId, "فرزندان من")))
                .andExpect(status().isOk());
        awaitSentMessageCount(1);
        assertThat(SENT_MESSAGES.get(0)).contains("فرزند آزمایشی");
    }

    @Test
    void linkedParentAsksGradesFromTheMenu() throws Exception {
        long schoolId = schoolFacade.createSchool(
                "مدرسه‌ی تست بله ۳", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null).value();
        String parentEmail = "bale-menu-parent-" + System.nanoTime() + "@roshdeandishe.test";
        String parentPhone = uniquePhoneNumber();
        MockHttpSession parentSession = registerAndLogin(parentEmail, "والد منو");
        setPhoneNumber(parentSession, parentPhone);
        IamFacade.UserId parentUserId = iamFacade.findOrCreateUser(parentEmail, "والد منو");
        iamFacade.createMembership(parentUserId, schoolId, "PARENT");

        AcademicFacade.ClassId classId = academicFacade.createClass(schoolId, "کلاس منو", null, null, 5, GradingScheme.NUMERIC);
        AcademicFacade.StudentId studentId = academicFacade.enrollStudent(
                "bale-menu-ward-" + System.nanoTime() + "@roshdeandishe.test", "فرزند منو", schoolId, classId.value());
        academicFacade.linkGuardian(schoolId, studentId.value(), parentUserId.value());

        long chatId = uniqueChatId();
        linkChatToPhoneNumber(chatId, parentPhone);

        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJsonForText(chatId, "نمره‌ها")))
                .andExpect(status().isOk());

        awaitSentMessageCount(1);
        assertThat(SENT_MESSAGES.get(0)).contains("فرزند منو");
    }

    @Test
    void invoiceIssuedNotifiesLinkedStudentAndLinkedGuardians() throws Exception {
        // همان دلیل تست بالا: schoolId باید یک SchoolEntity واقعی داشته باشد.
        long schoolId = schoolFacade.createSchool(
                "مدرسه‌ی تست بله ۲", "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null).value();

        String parentEmail = "bale-guardian-" + System.nanoTime() + "@roshdeandishe.test";
        String parentPhone = uniquePhoneNumber();
        MockHttpSession parentSession = registerAndLogin(parentEmail, "ولیِ فاکتور");
        setPhoneNumber(parentSession, parentPhone);
        IamFacade.UserId parentUserId = iamFacade.findOrCreateUser(parentEmail, "ولیِ فاکتور");
        iamFacade.createMembership(parentUserId, schoolId, "PARENT");
        long parentChatId = uniqueChatId();
        linkChatToPhoneNumber(parentChatId, parentPhone);

        AcademicFacade.ClassId classId = academicFacade.createClass(schoolId, "کلاس فاکتور", null, null, 5, GradingScheme.NUMERIC);
        AcademicFacade.StudentId studentId = academicFacade.enrollStudent(
                "bale-student-invoice-" + System.nanoTime() + "@roshdeandishe.test",
                "دانش‌آموز فاکتور", schoolId, classId.value());
        AcademicFacade.StudentInfo studentInfo = academicFacade.getStudentInfo(studentId.value());
        String studentPhone = uniquePhoneNumber();
        iamFacade.setPhoneNumber(new IamFacade.UserId(studentInfo.userId()), studentPhone);
        long studentChatId = uniqueChatId();
        linkChatToPhoneNumber(studentChatId, studentPhone);

        academicFacade.linkGuardian(schoolId, studentId.value(), parentUserId.value());

        SENT_MESSAGES.clear();
        financeFacade.setTuitionFee(schoolId, new BigDecimal("1500000"));
        financeFacade.issueTuitionInvoice(schoolId, studentId.value());

        awaitSentMessageCount(2);
        List<String> recipientChatIds = SENT_MESSAGES.stream().map(BaleFlowIntegrationTest::extractChatId).toList();
        assertThat(recipientChatIds).containsExactlyInAnyOrder(
                String.valueOf(studentChatId), String.valueOf(parentChatId));
        assertThat(SENT_MESSAGES).allMatch(message -> message.contains("فاکتور جدید"));
    }

    private void linkChatToPhoneNumber(long chatId, String phoneNumber) throws Exception {
        mockMvc.perform(post("/api/bale/webhook/" + WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactShareUpdateJson(chatId, phoneNumber)))
                .andExpect(status().isOk());
        awaitSentMessageCount(1); // پردازش ناهمگام است؛ تا رسیدن تأییدیه صبر می‌کنیم
        // تأییدیه پیش از commit تراکنشِ handle() ارسال می‌شود؛ تا ثبتِ ردیفِ لینک صبر می‌کنیم
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bale.bale_links where chat_id = ?", Integer.class, chatId)).isEqualTo(1));
        SENT_MESSAGES.clear(); // پیام تأییدیه‌ی همین لینک را دور می‌ریزیم، سناریوی اصلی تست چیز دیگری‌ست
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private void setPhoneNumber(MockHttpSession session, String phoneNumber) throws Exception {
        mockMvc.perform(put("/api/me/phone")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phoneNumber + "\"}"))
                .andExpect(status().isOk());
    }

    private static void awaitSentMessageCount(int expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(SENT_MESSAGES).hasSize(expected));
    }

    private static String updateJsonForText(long chatId, String text) {
        return "{\"update_id\":%d,\"message\":{\"message_id\":1,\"chat\":{\"id\":%d},\"text\":\"%s\"}}"
                .formatted(SEQ.incrementAndGet(), chatId, text);
    }

    private static String contactShareUpdateJson(long chatId, String phoneNumber) {
        return "{\"update_id\":%d,\"message\":{\"message_id\":1,\"chat\":{\"id\":%d},\"contact\":{\"phone_number\":\"%s\"}}}"
                .formatted(SEQ.incrementAndGet(), chatId, phoneNumber);
    }

    private static String chatIdField(long chatId) {
        return "\"chat_id\":" + chatId;
    }

    private static String extractChatId(String outgoingSendMessageBody) {
        Matcher matcher = Pattern.compile("\"chat_id\":(\\d+)").matcher(outgoingSendMessageBody);
        assertThat(matcher.find()).as("chat_id باید در بدنه‌ی درخواست ارسالی باشد: %s", outgoingSendMessageBody).isTrue();
        return matcher.group(1);
    }

    private static long uniqueChatId() {
        return SEQ.incrementAndGet();
    }

    private static long uniqueSchoolId() {
        return SEQ.incrementAndGet();
    }

    private static String uniquePhoneNumber() {
        String digits = String.valueOf(SEQ.incrementAndGet());
        return "0912" + digits.substring(Math.max(0, digits.length() - 7));
    }
}
