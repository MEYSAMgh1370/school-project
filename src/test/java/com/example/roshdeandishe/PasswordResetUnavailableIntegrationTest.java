package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * تا وقتی اپراتور {@code roshdeandishe.mail.delivery-available} را روشن نکرده (پیش‌فرضِ production)، صفحه‌ی
 * ورود لینکِ بازیابی نمی‌دهد و /forgot-password به‌جای فرم توضیح می‌دهد (password-reset-by-email.md، سؤال ۷).
 */
@SpringBootTest(properties = "roshdeandishe.mail.delivery-available=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginHidesTheRecoveryLinkAndTheForgotPageExplainsInsteadOfOfferingAForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/forgot-password"))));
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("فعلاً در دسترس نیست")))
                .andExpect(content().string(not(containsString("ارسال لینک بازیابی"))));
    }
}
