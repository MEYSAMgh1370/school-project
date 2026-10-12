package com.example.roshdeandishe;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * بازیابی رمز با ایمیل (openspec/changes/password-reset-by-email.md): درخواست → ایمیل → تعیین رمز تازه،
 * پاسخ یکسان برای حسابِ ناشناخته، مهلتِ بینِ دو درخواست، یک‌بارمصرف‌بودن/انقضا/جایگزینی، ذخیره‌ی هش،
 * (بسته‌شدنِ نشست‌ها در PasswordResetSessionInvalidationIntegrationTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetFlowIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    private static final String GENERIC_REPLY =
            "اگر حسابی با این ایمیل وجود داشته باشد، لینک بازیابی رمز عبور برایش فرستاده شد.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requestEmailsALinkAndConfirmSetsTheNewPasswordOnce() throws Exception {
        String email = register("reset-flow");

        requestReset(email);
        String token = awaitTokenFromEmail(email);

        confirm(token, "brandNewPassw0rd").andExpect(status().isOk());

        login(email, "brandNewPassw0rd").andExpect(status().isOk());
        login(email, "password123").andExpect(status().isUnauthorized());

        // یک‌بارمصرف: همان لینک دوباره کار نمی‌کند
        confirm(token, "yetAnotherPassw0rd")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("password-reset-token-invalid")));
    }

    @Test
    void requestAnswersIdenticallyForKnownAndUnknownAccountsAndSendsOnlyToTheKnownOne() throws Exception {
        String known = register("reset-known");
        String unknown = "nobody-" + System.nanoTime() + "@example.test";

        String knownReply = requestReset(known).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknownReply = requestReset(unknown).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(knownReply).isEqualTo(unknownReply).contains(GENERIC_REPLY);
        awaitTokenFromEmail(known);
        assertThat(greenMail.getReceivedMessagesForDomain(unknown.substring(unknown.indexOf('@') + 1)))
                .noneMatch(m -> recipient(m).equals(unknown));
    }

    @Test
    void anInvitedButNotActivatedAccountGetsNoResetEmail() throws Exception {
        String email = "invited-" + System.nanoTime() + "@example.test";
        jdbcTemplate.update("insert into iam.users (email, full_name, status) values (?, 'دعوت‌شده', 'ACTIVE')", email);

        requestReset(email).andExpect(status().isOk());

        Thread.sleep(500);
        assertThat(tokenCount(email)).isZero();
    }

    @Test
    void aSuspendedAccountGetsNoResetEmail() throws Exception {
        String email = register("reset-suspended");
        jdbcTemplate.update("update iam.users set status = 'SUSPENDED' where email = ?", email);

        requestReset(email).andExpect(status().isOk());

        Thread.sleep(500);
        assertThat(tokenCount(email)).isZero();
        assertThat(emailsTo(email)).isEmpty();
    }

    @Test
    void aSecondRequestInsideTheCooldownSendsNothingNew() throws Exception {
        String email = register("reset-cooldown");

        requestReset(email);
        awaitTokenFromEmail(email);
        requestReset(email).andExpect(status().isOk());

        Thread.sleep(500);
        assertThat(tokenCount(email)).isEqualTo(1);
        assertThat(emailsTo(email)).hasSize(1);
    }

    @Test
    void aNewRequestAfterTheCooldownSupersedesTheEarlierLink() throws Exception {
        String email = register("reset-supersede");

        requestReset(email);
        String first = awaitTokenFromEmail(email);
        jdbcTemplate.update("""
                update iam.password_reset_tokens set created_at = created_at - interval '10 minutes'
                where user_id = (select id from iam.users where email = ?)
                """, email);

        requestReset(email);
        waitForEmailCount(email, 2);
        String second = tokenFrom(emailsTo(email).get(1));

        assertThat(second).isNotEqualTo(first);
        confirm(first, "brandNewPassw0rd").andExpect(status().isBadRequest());
        confirm(second, "brandNewPassw0rd").andExpect(status().isOk());
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        String email = register("reset-hash");

        requestReset(email);
        String token = awaitTokenFromEmail(email);

        Integer raw = jdbcTemplate.queryForObject(
                "select count(*) from iam.password_reset_tokens where token_hash = ?", Integer.class, token);
        Integer hashed = jdbcTemplate.queryForObject(
                "select count(*) from iam.password_reset_tokens where token_hash = ?", Integer.class, sha256Hex(token));
        assertThat(raw).isZero();
        assertThat(hashed).isEqualTo(1);
    }

    @Test
    void anExpiredTokenAndAnUnknownTokenAreRejectedTheSameWay() throws Exception {
        String email = register("reset-expired");

        requestReset(email);
        String token = awaitTokenFromEmail(email);
        jdbcTemplate.update("""
                update iam.password_reset_tokens set expires_at = now() - interval '1 minute'
                where user_id = (select id from iam.users where email = ?)
                """, email);

        String expired = confirm(token, "brandNewPassw0rd").andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String unknown = confirm("not-a-real-token", "brandNewPassw0rd").andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(expired).isEqualTo(unknown);
        login(email, "password123").andExpect(status().isOk());
    }

    @Test
    void aTooShortPasswordIsRejectedWithoutConsumingTheToken() throws Exception {
        String email = register("reset-short");

        requestReset(email);
        String token = awaitTokenFromEmail(email);

        confirm(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-password")));
        confirm(token, "longEnoughPassw0rd").andExpect(status().isOk());
    }

    @Test
    void thePagesRenderAndTheLoginPageLinksToRecovery() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/forgot-password\"")));
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("forgotPasswordPage")))
                .andExpect(content().string(containsString("/js/forgot-password.js")));

        String probe = "'+(document.documentElement.dataset.probe='executed')+'";
        mockMvc.perform(get("/reset-password").param("token", probe))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("resetPasswordPage")))
                .andExpect(content().string(containsString("data-token=")))
                .andExpect(content().string(not(containsString(probe))));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private String register(String prefix) throws Exception {
        String email = prefix + "-" + System.nanoTime() + "@example.test";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"کاربر آزمایشی\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return email;
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/request")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String awaitTokenFromEmail(String email) throws Exception {
        waitForEmailCount(email, 1);
        return tokenFrom(emailsTo(email).get(0));
    }

    private void waitForEmailCount(String email, int count) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (emailsTo(email).size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(emailsTo(email)).hasSizeGreaterThanOrEqualTo(count);
    }

    private java.util.List<MimeMessage> emailsTo(String email) {
        return java.util.Arrays.stream(greenMail.getReceivedMessages())
                .filter(m -> recipient(m).equals(email))
                .toList();
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenFrom(MimeMessage message) throws Exception {
        // getContent() بدنه را طبق Content-Transfer-Encoding رمزگشایی می‌کند (متن فارسی base64 می‌شود؛
        // GreenMailUtil.getBody خام برمی‌گرداند) - همان کاری که InviteTokenFlowIntegrationTest می‌کند
        Matcher matcher = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_%-]+)")
                .matcher(message.getContent().toString());
        assertThat(matcher.find()).as("reset link in the email body").isTrue();
        return java.net.URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private int tokenCount(String email) {
        return jdbcTemplate.queryForObject(
                "select count(*) from iam.password_reset_tokens where user_id = (select id from iam.users where email = ?)",
                Integer.class, email);
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
