package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * کاربرِ بدونِ نقش (ثبت‌نامِ خودخواسته، بدونِ مدرسه): پیشخوانِ او دو مسیرِ روشن و ایمیلِ حسابش را نشان می‌دهد،
 * و وقتی مدیر همان ایمیل را به مدرسه اضافه کند ایمیلِ «اضافه شدید» می‌گیرد (برای او دعوت‌نامه‌ی فعال‌سازی
 * صادر نمی‌شود، پس بدونِ این خبر، منتظرِ چیزی می‌ماند که نمی‌آید).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RolelessUserFlowIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theRolelessDashboardOffersTwoPathsAndTheAccountEmailToShare() throws Exception {
        String email = "roleless-" + System.nanoTime() + "@example.test";
        MockHttpSession session = registerAndLogin(email);

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("هنوز عضو هیچ مدرسه‌ای نیستید")))
                .andExpect(content().string(containsString("مدیر یا صاحب مدرسه‌ام")))
                .andExpect(content().string(containsString("href=\"/schools/new\"")))
                .andExpect(content().string(containsString("از طرف مدرسه دعوت شده‌ام")))
                .andExpect(content().string(containsString("value=\"" + email + "\"")))
                .andExpect(content().string(containsString("href=\"/activate\"")))
                .andExpect(content().string(containsString("/js/copy-field.js")))
                .andExpect(content().string(not(containsString("منتظر دعوت‌نامه‌ی مدرسه‌تان بمانید"))));
    }

    @Test
    void aMemberDoesNotSeeTheRolelessPaths() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        mockMvc.perform(get("/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("هنوز عضو هیچ مدرسه‌ای نیستید"))));
    }

    @Test
    void addingAnAlreadyActiveUserEmailsThemAndAddingANewUserDoesNot() throws Exception {
        String existing = "existing-" + System.nanoTime() + "@example.test";
        registerAndLogin(existing);
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");

        String addedBody = addMember(admin, existing, "TEACHER");
        assertThat(objectMapper.readTree(addedBody).get("hasPassword").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(addedBody).get("notified").asBoolean()).isTrue();

        MimeMessage mail = Arrays.stream(greenMail.getReceivedMessages())
                .filter(m -> recipient(m).equals(existing)).findFirst().orElseThrow();
        String text = (String) mail.getContent();
        assertThat(text).contains("شما به‌عنوان معلم در ").contains("/login");

        String fresh = "fresh-" + System.nanoTime() + "@example.test";
        String freshBody = addMember(admin, fresh, "TEACHER");
        assertThat(objectMapper.readTree(freshBody).get("hasPassword").asBoolean()).isFalse();
        assertThat(objectMapper.readTree(freshBody).get("notified").asBoolean()).isFalse();
        assertThat(Arrays.stream(greenMail.getReceivedMessages()).filter(m -> recipient(m).equals(fresh))).isEmpty();
    }

    @Test
    void theRegisterPageTellsInviteesNotToRegisterExceptOnTheSchoolRegistrationPath() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ثبت‌نام لازم نیست")))
                .andExpect(content().string(containsString("href=\"/activate\"")));
        mockMvc.perform(get("/register").param("next", "/schools/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ثبت‌نام لازم نیست"))));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private String addMember(MockHttpSession admin, String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .session(admin)
                        .header("X-School-ID", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"عضو آزمایشی\",\"roleCode\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"کاربر بدون نقش\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
