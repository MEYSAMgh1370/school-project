package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * صفحه‌ی /people (اولین برش UI مدیریتیِ CLAUDE.md بند ۷.۶) + غنی‌سازی
 * IamFacade.MembershipInfo با email/fullName. الگوی لاگین از
 * LoginDashboardFlowIntegrationTest کپی شده است.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MembershipManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@roshdeandishe.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    void unauthenticatedPeoplePageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/people"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void peoplePageRendersSharedNavbarForAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/people").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("اعضای مدرسه")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("مدرسه‌ی نمونه")))
                // لینک‌های ناوبری navbar روی همه‌ی صفحه‌های مدیریتی هستند
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard\"")))
                // نشانگر وضعیت فعال‌سازی (UX_ROADMAP.md §6.3)
                .andExpect(content().string(org.hamcrest.Matchers.containsString("فعال‌سازی شده")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("در انتظار فعال‌سازی")));
    }

    @Test
    void listingMembershipsWithoutTenantHeaderIsBadRequest() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/memberships").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void freshUserWithNoMembershipCannotManageDemoSchool() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider@roshdeandishe.test\",\"fullName\":\"Outsider\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider@roshdeandishe.test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/admin/schools/1/memberships")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAddToggleAndInviteAMember() throws Exception {
        MockHttpSession session = loginAsAdmin();

        // افزودن عضو جدید با نقش TEACHER؛ پاسخ باید email/fullName را هم
        // برگرداند - همان غنی‌سازیِ این برش.
        MvcResult createResult = mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.teacher@roshdeandishe.test\",\"fullName\":\"معلم تازه\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.teacher@roshdeandishe.test"))
                .andExpect(jsonPath("$.fullName").value("معلم تازه"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        Long membershipId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("membershipId").asLong();

        // لیست باید همان عضو تازه را با email/fullName نشان دهد - و چون
        // findOrCreateUser رمز عبور نمی‌سازد، hasPassword باید false باشد
        // (UX_ROADMAP.md §6.3 - نشانگر فعال‌سازی روی /people)
        mockMvc.perform(get("/api/admin/schools/1/memberships")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.email == 'new.teacher@roshdeandishe.test' && @.fullName == 'معلم تازه'"
                                + " && @.hasPassword == false)]"
                ).exists());

        // غیرفعال کردن
        mockMvc.perform(patch("/api/admin/schools/1/memberships/" + membershipId + "/active")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/memberships")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.membershipId == " + membershipId + " && @.active == false)]"
                ).exists());

        // صدور توکن دعوت - کاربر تازه هنوز رمز عبور ندارد پس issued=true
        MvcResult inviteResult = mockMvc.perform(post("/api/admin/schools/1/memberships/" + membershipId + "/invite-token")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(true))
                .andExpect(jsonPath("$.inviteToken").isNotEmpty())
                .andReturn();
        String inviteToken = objectMapper.readTree(inviteResult.getResponse().getContentAsString())
                .get("inviteToken").asText();

        // بعد از فعال‌سازی (redeem توکن)، hasPassword باید true شود
        mockMvc.perform(post("/api/auth/activate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + inviteToken + "\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/schools/1/memberships")
                        .header("X-School-ID", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.membershipId == " + membershipId + " && @.hasPassword == true)]"
                ).exists());
    }
}
