package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code PlatformExceptionHandler.handleUnhandled(Exception.class)} گرفت
 * می‌کند - از جمله {@code NoResourceFoundException} که
 * {@code ResourceHttpRequestHandler} برای هر فایل استاتیک گم‌شده زیر
 * {@code /css/**}/{@code /js/**}/{@code /webjars/**} پرتاب می‌کند (مثلاً
 * درخواست خودکار مرورگر/DevTools برای یک فایل {@code .map} که هرگز وجود
 * نداشته - رجوع به CLAUDE.md §7 Done). پیش از این fix، چون
 * {@code @ExceptionHandler(Exception.class)} همه‌چیز را می‌گرفت،
 * ۴۰۴ درست این Exception به یک ۵۰۰ عمومی تبدیل می‌شد - این تست همان
 * رگرسیون را قفل می‌کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformExceptionHandlerFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingStaticResourceIsNotFoundNotInternalServerError() throws Exception {
        mockMvc.perform(get("/css/this-file-does-not-exist-12345.css"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("resource-not-found")));
    }

    @Test
    void missingWebjarSourceMapIsNotFoundNotInternalServerError() throws Exception {
        // یک درخواست sourcemap برای فایلی که واقعاً در وب‌جار وجود دارد - خود
        // .js.map هیچ‌وقت publish نمی‌شود (رجوع به CLAUDE.md §7 Done)
        mockMvc.perform(get("/webjars/alpinejs/3.15.12/dist/cdn.min.js.map"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("resource-not-found")));
    }

    @Test
    void existingStaticResourceStillServesNormally() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk());
    }

    @Test
    void webFontServesWithoutSchoolHeaderOrAuthentication() throws Exception {
        // رگرسیون: app.css فونت را به‌عنوان زیرمنبع CSS می‌گیرد و مرورگر برای آن
        // X-School-ID نمی‌فرستد؛ پیش از اضافه‌شدن /fonts به EXCLUDED_PATHS
        // فیلتر tenant با ۴۰۰ جوابش می‌داد (و Security هم لاگین می‌خواست).
        mockMvc.perform(get("/fonts/Vazirmatn-Variable.woff2"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    if (result.getResponse().getContentAsByteArray().length == 0) {
                        throw new AssertionError("بدنه‌ی پاسخ فونت نباید خالی باشد");
                    }
                });
    }
}
