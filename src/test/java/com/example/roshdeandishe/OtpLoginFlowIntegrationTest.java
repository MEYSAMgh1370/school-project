package com.example.roshdeandishe;

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

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ورود با کد تأیید پیامکی (POST /api/auth/otp/request + /verify) - فاکتور
 * دوم لاگین، additive به session/JWT موجود (MOSHAVERE_PORT_SPEC.md §3). چون
 * SmsDriver در dev فقط لاگ می‌کند (بدون roshdeandishe.sms.api-key واقعی)،
 * کد تولیدشده از جدول iam.verification_codes مستقیم با JdbcTemplate خوانده
 * می‌شود - همان الگوی ExpenseManagementFlowIntegrationTest/
 * FinancePaymentFlowIntegrationTest برای رسیدن به داده‌ای که Facade عمداً
 * برنمی‌گرداند (کد واقعی هرگز در پاسخ HTTP نمی‌آید - همان دلیلی که
 * requestOtp چیزی جز یک پیام موفقیت برنمی‌گرداند).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OtpLoginFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requestThenVerifyOtpEstablishesASession() throws Exception {
        String email = "otp-user-" + System.nanoTime() + "@roshdeandishe.test";
        String phoneNumber = uniquePhoneNumber();
        MockHttpSession session = registerAndLogin(email, "کاربر آزمایشی");
        setPhoneNumber(session, phoneNumber);

        requestOtp(phoneNumber).andExpect(status().isOk());

        String code = latestOtpCodeFor(phoneNumber);
        assertThat(code).hasSize(6);

        verifyOtp(phoneNumber, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void persianDigitPhoneIsStoredCanonicallyAndCanCompleteOtpLogin() throws Exception {
        String email = "otp-persian-" + System.nanoTime() + "@roshdeandishe.test";
        String asciiPhoneNumber = uniquePhoneNumber();
        String persianPhoneNumber = toPersianDigits(asciiPhoneNumber);
        MockHttpSession session = registerAndLogin(email, "کاربر ارقام فارسی");

        setPhoneNumber(session, persianPhoneNumber);
        requestOtp(persianPhoneNumber).andExpect(status().isOk());

        String code = latestOtpCodeFor(asciiPhoneNumber);
        verifyOtp(asciiPhoneNumber, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        String storedPhoneNumber = jdbcTemplate.queryForObject(
                "SELECT phone_number FROM iam.users WHERE email = ?",
                String.class,
                email
        );
        assertThat(storedPhoneNumber).isEqualTo(asciiPhoneNumber);
    }

    @Test
    void unicodeDigitVariantsCannotBypassPhoneNumberUniqueness() throws Exception {
        String asciiPhoneNumber = uniquePhoneNumber();
        MockHttpSession firstSession = registerAndLogin(
                "otp-owner-" + System.nanoTime() + "@roshdeandishe.test",
                "مالک شماره");
        MockHttpSession secondSession = registerAndLogin(
                "otp-duplicate-" + System.nanoTime() + "@roshdeandishe.test",
                "کاربر دوم");

        setPhoneNumber(firstSession, toPersianDigits(asciiPhoneNumber));

        mockMvc.perform(put("/api/me/phone")
                        .session(secondSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + asciiPhoneNumber + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("phone-number-already-claimed")));
    }

    @Test
    void requestOtpForUnregisteredPhoneReturns404() throws Exception {
        requestOtp("09000000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("phone-not-registered")));
    }

    @Test
    void verifyOtpForUnregisteredPhoneReturns404() throws Exception {
        verifyOtp("09000000001", "123456")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("phone-not-registered")));
    }

    @Test
    void verifyOtpWithNoCodeEverRequestedReturns410Expired() throws Exception {
        String email = "otp-user-" + System.nanoTime() + "@roshdeandishe.test";
        String phoneNumber = uniquePhoneNumber();
        MockHttpSession session = registerAndLogin(email, "کاربر آزمایشی");
        setPhoneNumber(session, phoneNumber);

        verifyOtp(phoneNumber, "000000")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value(endsWith("otp-expired")));
    }

    @Test
    void verifyOtpWithWrongCodeReturns400AndAllowsRetry() throws Exception {
        String email = "otp-user-" + System.nanoTime() + "@roshdeandishe.test";
        String phoneNumber = uniquePhoneNumber();
        MockHttpSession session = registerAndLogin(email, "کاربر آزمایشی");
        setPhoneNumber(session, phoneNumber);
        requestOtp(phoneNumber).andExpect(status().isOk());
        String code = latestOtpCodeFor(phoneNumber);

        verifyOtp(phoneNumber, "000000".equals(code) ? "111111" : "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("otp-incorrect")));

        // کد اشتباه، کد را مصرف‌شده علامت نمی‌زند - تلاش درست هنوز جواب می‌دهد
        verifyOtp(phoneNumber, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void verifyOtpWithExpiredCodeReturns410() throws Exception {
        String email = "otp-user-" + System.nanoTime() + "@roshdeandishe.test";
        String phoneNumber = uniquePhoneNumber();
        MockHttpSession session = registerAndLogin(email, "کاربر آزمایشی");
        setPhoneNumber(session, phoneNumber);
        requestOtp(phoneNumber).andExpect(status().isOk());
        expireLatestOtpFor(phoneNumber);

        String code = latestOtpCodeFor(phoneNumber);
        verifyOtp(phoneNumber, code)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value(endsWith("otp-expired")));
    }

    private org.springframework.test.web.servlet.ResultActions requestOtp(String phoneNumber) throws Exception {
        return mockMvc.perform(post("/api/auth/otp/request")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + phoneNumber + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions verifyOtp(String phoneNumber, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/otp/verify")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + code + "\"}"));
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

    private String latestOtpCodeFor(String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM iam.verification_codes WHERE user_id = " +
                "(SELECT id FROM iam.users WHERE phone_number = ?) " +
                "ORDER BY created_at DESC LIMIT 1",
                String.class, phoneNumber
        );
    }

    private void expireLatestOtpFor(String phoneNumber) {
        jdbcTemplate.update(
                "UPDATE iam.verification_codes SET expires_at = ? WHERE id = (" +
                "  SELECT vc.id FROM iam.verification_codes vc" +
                "  JOIN iam.users u ON u.id = vc.user_id" +
                "  WHERE u.phone_number = ? AND vc.consumed_at IS NULL" +
                "  ORDER BY vc.created_at DESC LIMIT 1" +
                ")",
                Timestamp.from(Instant.now().minusSeconds(60)), phoneNumber
        );
    }

    private static String uniquePhoneNumber() {
        String digits = String.valueOf(System.nanoTime());
        return "0912" + digits.substring(digits.length() - 7);
    }

    private static String toPersianDigits(String value) {
        return value.chars()
                .mapToObj(character -> character >= '0' && character <= '9'
                        ? String.valueOf((char) ('۰' + character - '0'))
                        : String.valueOf((char) character))
                .collect(java.util.stream.Collectors.joining());
    }
}
