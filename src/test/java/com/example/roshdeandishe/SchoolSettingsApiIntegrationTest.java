package com.example.roshdeandishe;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.BotSettings;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API تنظیماتِ مدرسه: GET /settings (بخش‌ها بر پایه‌ی مجوز) و PUT /settings/bot (SCHOOL_SETTINGS_MANAGE).
 * کلاسِ جدا از SchoolSettingsFlowIntegrationTest چون آن فقط facade را می‌آزماید و این یکی MockMvc می‌خواهد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SchoolSettingsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SchoolFacade schoolFacade;

    @AfterEach
    void resetBotSettings() {
        // بازگشت ربات‌ی مدرسه‌ی ۱ به پیش‌فرض (روشن، همه‌ی نقش‌ها)
        jdbc.update("delete from school.bot_roles where school_id = 1");
        jdbc.update("update school.school_settings set bot_enabled = true where school_id = 1");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession admin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

    /** کاربرِ تازه با نقشِ داده‌شده در مدرسه‌ی ۱. */
    private MockHttpSession userWithRole(String roleCode) throws Exception {
        String email = roleCode.toLowerCase() + "-" + System.nanoTime() + "@example.test";
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"کاربر تنظیمات\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MockHttpSession session = login(email, "password123");
        mockMvc.perform(post("/api/admin/schools/1/memberships").with(csrf()).header("X-School-ID", "1")
                        .session(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"کاربر تنظیمات\",\"roleCode\":\"" + roleCode + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private ResultActions getSettings(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/api/admin/schools/1/settings").header("X-School-ID", "1").session(session));
    }

    private ResultActions putBot(MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(put("/api/admin/schools/1/settings/bot").with(csrf()).header("X-School-ID", "1")
                .session(session).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void getReturnsOnlyPermittedSections() throws Exception {
        getSettings(admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoPromotion").isBoolean())
                .andExpect(jsonPath("$.installmentsEnabled").isBoolean())
                .andExpect(jsonPath("$.reminderRoles").isArray())
                .andExpect(jsonPath("$.bot.enabled").value(true))
                .andExpect(jsonPath("$.bot.roles").isArray());

        getSettings(userWithRole("FINANCE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentsEnabled").isBoolean())
                .andExpect(jsonPath("$.currentYear").doesNotExist())
                .andExpect(jsonPath("$.autoPromotion").doesNotExist())
                .andExpect(jsonPath("$.reminderRoles").doesNotExist())
                .andExpect(jsonPath("$.bot").doesNotExist());

        getSettings(userWithRole("TEACHER")).andExpect(status().isForbidden());
    }

    @Test
    void putBotValidatesRoles() throws Exception {
        MockHttpSession admin = admin();
        putBot(admin, "{\"enabled\":true,\"roles\":[\"NOPE\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-setting")));
        putBot(admin, "{\"enabled\":true,\"roles\":[\"PLATFORM_ADMIN\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-setting")));
        putBot(admin, "{\"enabled\":null,\"roles\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-setting")));
        putBot(admin, "{\"roles\":[\"PARENT\"]}").andExpect(status().isBadRequest());
        assertThat(schoolFacade.getBotSettings(new SchoolId(1L))).isEqualTo(new BotSettings(true, Set.of()));
    }

    @Test
    void putBotRoundTrips() throws Exception {
        MockHttpSession admin = admin();
        putBot(admin, "{\"enabled\":false,\"roles\":[\"PARENT\",\"TEACHER\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("PARENT", "TEACHER")));
        getSettings(admin)
                .andExpect(jsonPath("$.bot.enabled").value(false))
                .andExpect(jsonPath("$.bot.roles", containsInAnyOrder("PARENT", "TEACHER")));
        assertThat(schoolFacade.getBotSettings(new SchoolId(1L)))
                .isEqualTo(new BotSettings(false, Set.of("PARENT", "TEACHER")));

        putBot(admin, "{\"enabled\":true,\"roles\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void putBotRequiresPermission() throws Exception {
        putBot(admin(), "{\"enabled\":true,\"roles\":[]}").andExpect(status().isOk());
        putBot(userWithRole("FINANCE_ADMIN"), "{\"enabled\":false,\"roles\":[]}").andExpect(status().isForbidden());
        assertThat(schoolFacade.getBotSettings(new SchoolId(1L)).enabled()).isTrue();
    }

    @Test
    void tenantMismatchIsRejected() throws Exception {
        MockHttpSession admin = admin();
        mockMvc.perform(get("/api/admin/schools/2/settings").header("X-School-ID", "1").session(admin))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(put("/api/admin/schools/2/settings/bot").with(csrf()).header("X-School-ID", "1")
                        .session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"roles\":[]}"))
                .andExpect(status().is4xxClientError());
        assertThat(schoolFacade.getBotSettings(new SchoolId(1L)).enabled()).isTrue();
    }
}
