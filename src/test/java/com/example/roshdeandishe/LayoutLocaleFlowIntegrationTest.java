package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lang و dir عنصر {@code <html>} در layout/main.jte باید از زبان حل‌شده‌ی
 * Request (هدر Accept-Language) بیایند. صفحه‌ی /login عمومی است و به
 * احراز هویت نیاز ندارد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LayoutLocaleFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageWithoutAcceptLanguageRendersPersianRightToLeft() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"fa\" dir=\"rtl\">")));
    }

    @Test
    void pageWithEnglishAcceptLanguageRendersEnglishLeftToRight() throws Exception {
        mockMvc.perform(get("/login").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"en\" dir=\"ltr\">")));
    }
}
