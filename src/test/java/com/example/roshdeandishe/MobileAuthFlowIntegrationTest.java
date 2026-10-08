package com.example.roshdeandishe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * مسیر احراز هویت اپ موبایل (POST /api/auth/token + JwtAuthenticationFilter)
 * - additive به session cookie که LoginDashboardFlowIntegrationTest از قبل
 * پوشش می‌دهد. اثبات اصلی این تست: یک Bearer token می‌تواند بدون هیچ
 * HttpSession ای، همان endpointهای محافظت‌شده‌ی موجود را باز کند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MobileAuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tokenEndpointIssuesJwtForValidCredentials() throws Exception {
        // عمداً بدون with(csrf()): برخلاف /api/auth/login، این endpoint
        // صریحاً از CSRF مستثناست (پاسخش یک JWT در بدنه‌ی JSON است، نه یک
        // کوکی - مدل تهدید CSRF اینجا صدق نمی‌کند؛ رجوع به SecurityConfig)
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(43200));
    }

    @Test
    void tokenEndpointRejectsInvalidCredentialsWith401NotServerError() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-credentials")));
    }

    @Test
    void loginEndpointAlsoRejectsInvalidCredentialsWith401NotServerError() throws Exception {
        // همان رفتار، برای login هم - رجوع به AuthController.authenticate
        // مشترک؛ قبل از این تغییر، این حالت ۵۰۰ عمومی برمی‌گرداند.
        mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenAuthenticatesProtectedMeEndpointWithNoSession() throws Exception {
        String token = issueToken("admin@roshdeandishe.test", "password");

        // بدون .session(...): یعنی هیچ HttpSession ای اینجا وجود ندارد -
        // فقط هدر Authorization کل احراز هویت را انجام می‌دهد.
        MvcResult meResult = mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@roshdeandishe.test"))
                .andReturn();

        // ادعای بالا باید راستی‌آزمایی شود، نه فقط نوشته شود: یک درخواست Bearer
        // نباید HttpSession بسازد. تا پیش از openspec/changes/stateless-bearer-auth.md
        // این assertion شکست می‌خورد، چون SecurityContext در نشست ذخیره می‌شد.
        assertThat(meResult.getRequest().getSession(false))
                .as("درخواست Bearer نباید HttpSession بسازد - JWT باید stateless بماند")
                .isNull();

        mockMvc.perform(get("/api/me/memberships").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.schoolId == 1 && @.roleCode == 'SCHOOL_ADMIN' && @.active == true)]").exists());
    }

    @Test
    void invalidBearerTokenIsRejectedSameAsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage.not.a.jwt"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingAuthorizationHeaderStaysForbiddenAsToday() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isForbidden());
    }

    private String issueToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        String token = json.get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
