package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
    }

    // ---- خطاهای کلاینتِ Spring MVC باید ۴xx باشند، نه ۵۰۰ ----

    private MockHttpSession loginAsAdmin() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions classesPost(MockHttpSession admin, MediaType type,
                                                                          String body) throws Exception {
        return mockMvc.perform(post("/api/admin/schools/1/classes")
                .with(csrf()).header("X-School-ID", "1").session(admin)
                .contentType(type).content(body));
    }

    @Test
    void malformedJsonAndUnknownEnumValuesAreBadRequestsNotServerErrors() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        classesPost(admin, MediaType.APPLICATION_JSON, "{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("malformed-request-body")));
        classesPost(admin, MediaType.APPLICATION_JSON,
                "{\"name\":\"x\",\"gradeLevel\":5,\"gradingScheme\":\"BOGUS\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("malformed-request-body")))
                // جزئیاتِ داخلی (نامِ کلاس/پیامِ Jackson) لو نمی‌رود
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("GradingScheme"))));
    }

    @Test
    void invalidAndMissingParametersAreBadRequests() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/grades").header("X-School-ID", "1").session(admin)
                        .param("classId", "abc").param("academicYear", "1405-1406").param("term", "FIRST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-parameter")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("classId")));
        mockMvc.perform(get("/api/admin/schools/1/grades").header("X-School-ID", "1").session(admin)
                        .param("academicYear", "1405-1406").param("term", "FIRST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("missing-parameter")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("classId")));
        mockMvc.perform(get("/api/admin/schools/1/grades").header("X-School-ID", "1").session(admin)
                        .param("classId", "1").param("academicYear", "1405-1406").param("term", "THIRD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("invalid-parameter")));
    }

    @Test
    void anUnsupportedMethodOrContentTypeIsA4xxNotA500() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(delete("/api/admin/schools/1/classes").with(csrf()).header("X-School-ID", "1").session(admin))
                .andExpect(status().isMethodNotAllowed())
                // Allow همراه پاسخ است و متدهای واقعیِ این مسیر را می‌گوید
                .andExpect(header().string("Allow", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("GET"), org.hamcrest.Matchers.containsString("POST"))))
                .andExpect(jsonPath("$.type").value(endsWith("method-not-allowed")));
        classesPost(admin, MediaType.TEXT_PLAIN, "name=x")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().exists("Accept"))
                .andExpect(jsonPath("$.type").value(endsWith("unsupported-media-type")));
    }
}
