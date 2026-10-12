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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "roshdeandishe.sms.dev-log-codes=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OtpDeliveryConfigurationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void otpRequestFailsClosedAndDoesNotPersistCodeWhenSmsDeliveryIsUnavailable() throws Exception {
        String email = "otp-delivery-" + System.nanoTime() + "@roshdeandishe.test";
        String phoneNumber = "0912" + String.format("%07d", Math.floorMod(System.nanoTime(), 10_000_000));
        MockHttpSession session = registerAndLogin(email);
        setPhoneNumber(session, phoneNumber);

        mockMvc.perform(post("/api/auth/otp/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phoneNumber + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(endsWith("otp-delivery-unavailable")));

        Integer codeCount = jdbcTemplate.queryForObject(
                """
                select count(*) from iam.verification_codes
                where user_id = (select id from iam.users where phone_number = ?)
                """,
                Integer.class, phoneNumber);
        assertThat(codeCount).isZero();
    }

    @Test
    void loginPageHidesTheOtpTabWhenSmsDeliveryIsUnavailable() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ورود به رشدِاندیشه")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("کد پیامکی"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("role=\"tablist\""))));
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"کاربر پیامک\",\"password\":\"password123\"}"))
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
}
