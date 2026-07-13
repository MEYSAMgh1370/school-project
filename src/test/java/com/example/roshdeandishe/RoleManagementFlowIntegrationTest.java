package com.example.roshdeandishe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * مدیریت نقش سفارشی (iam.RoleController) - حل‌کننده‌ی Open Question 3 در
 * CLAUDE.md. الگوی لاگین از MembershipManagementFlowIntegrationTest کپی
 * شده است. نکته‌ی اصلی که این تست باید اثبات کند صرفاً CRUD روی جدول
 * roles نیست: باید نشان دهد PermissionDecisionService واقعاً به نقش
 * سفارشی همان رفتاری را می‌دهد که به نقش سیستمی می‌دهد (دقیقاً همان
 * Permissionهایی که انتخاب شده‌اند، نه بیشتر) - و اینکه نقش سفارشیِ یک
 * مدرسه برای مدرسه‌ی دیگر اصلاً قابل‌مشاهده/تخصیص نیست.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleManagementFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin@roshdeandishe.test", "password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }

    @Test
    void unauthenticatedRoleEndpointsAreForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/schools/1/permissions"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/schools/1/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesSystemPermissionsAndRolesButNotPlatformAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/admin/schools/1/permissions").header("X-School-ID", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'ROLE_MANAGE')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'CLASS_VIEW')]").exists());

        mockMvc.perform(get("/api/admin/schools/1/roles").header("X-School-ID", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'SCHOOL_ADMIN' && @.custom == false)]").exists())
                .andExpect(jsonPath("$[?(@.code == 'TEACHER' && @.custom == false)]").exists())
                .andExpect(jsonPath("$[?(@.code == 'PLATFORM_ADMIN')]").doesNotExist());
    }

    @Test
    void nonAdminCannotManageRoles() throws Exception {
        MockHttpSession teacherSession = registerTeacherSessionViaDirectLogin();

        mockMvc.perform(get("/api/admin/schools/1/roles").header("X-School-ID", "1").session(teacherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));

        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(teacherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIBRARIAN\",\"displayName\":\"کتابدار\",\"permissionCodes\":[\"CLASS_VIEW\"]}"))
                .andExpect(status().isForbidden());
    }

    // TEACHER در این تست هیچ کاری با رمز عبور ندارد - یک کاربر جداگانه با
    // رمز مستقل ثبت‌نام و نقش TEACHER به آن در مدرسه‌ی نمونه داده می‌شود
    private MockHttpSession registerTeacherSessionViaDirectLogin() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        String email = "role.teacher2@roshdeandishe.test";
        MockHttpSession teacherSession = registerAndLogin(email, "معلم مستقل");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"معلم مستقل\",\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isOk());
        return teacherSession;
    }

    @Test
    void creatingRoleWithSystemCodeCollisionIsRejected() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEACHER\",\"displayName\":\"معلم تازه\",\"permissionCodes\":[\"CLASS_VIEW\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("role-code-already-exists")));
    }

    @Test
    void creatingRoleWithInvalidCodeBlankFieldsOrUnknownPermissionIsRejected() throws Exception {
        MockHttpSession session = loginAsAdmin();

        // کد با حروف کوچک - فرمت نامعتبر
        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"librarian\",\"displayName\":\"کتابدار\",\"permissionCodes\":[\"CLASS_VIEW\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-role-definition")));

        // عنوان خالی
        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BLANKTITLE\",\"displayName\":\"\",\"permissionCodes\":[\"CLASS_VIEW\"]}"))
                .andExpect(status().isBadRequest());

        // بدون هیچ Permission ای
        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPERMS\",\"displayName\":\"بدون دسترسی\",\"permissionCodes\":[]}"))
                .andExpect(status().isBadRequest());

        // کدِ Permission ناموجود
        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GHOSTPERM\",\"displayName\":\"دسترسی ناموجود\",\"permissionCodes\":[\"NOT_A_REAL_PERMISSION\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-permission-code")));
    }

    @Test
    void customRoleGrantsExactlyItsOwnPermissionsAndIsIsolatedPerSchool() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        // ساخت نقش سفارشی «کتابدار» فقط با CLASS_VIEW - نه MEMBERSHIP_MANAGE
        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIBRARIAN\",\"displayName\":\"کتابدار\",\"permissionCodes\":[\"CLASS_VIEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custom").value(true))
                .andExpect(jsonPath("$.permissionCodes[0]").value("CLASS_VIEW"));

        // ساخت دوباره با همان کد در همان مدرسه - تکراری
        mockMvc.perform(post("/api/admin/schools/1/roles")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIBRARIAN\",\"displayName\":\"کتابدار دوباره\",\"permissionCodes\":[\"CLASS_VIEW\"]}"))
                .andExpect(status().isConflict());

        // تخصیص نقش سفارشی به یک کاربر تازه
        String librarianEmail = "librarian@roshdeandishe.test";
        MockHttpSession librarianSession = registerAndLogin(librarianEmail, "کتابدار تست");
        mockMvc.perform(post("/api/admin/schools/1/memberships")
                        .with(csrf())
                        .header("X-School-ID", "1")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + librarianEmail + "\",\"fullName\":\"کتابدار تست\",\"roleCode\":\"LIBRARIAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCode").value("LIBRARIAN"));

        // این نقش CLASS_VIEW را واقعاً می‌دهد (ماژول ACADEMIC برای مدرسه‌ی
        // نمونه از قبل seed شده)
        mockMvc.perform(get("/api/admin/schools/1/classes").header("X-School-ID", "1").session(librarianSession))
                .andExpect(status().isOk());

        // ولی هیچ Permission دیگری نمی‌دهد - نه حتی MEMBERSHIP_MANAGE
        mockMvc.perform(get("/api/admin/schools/1/memberships").header("X-School-ID", "1").session(librarianSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("permission-denied")));

        // مدرسه‌ی دوم (خودسرویس) نباید نقش LIBRARIAN مدرسه‌ی اول را ببیند یا بتواند تخصیص دهد
        MockHttpSession secondFounderSession = registerAndLogin("second.founder@roshdeandishe.test", "بنیان‌گذار دوم");
        MvcResult schoolResult = mockMvc.perform(post("/api/admin/schools")
                        .with(csrf())
                        .session(secondFounderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"مدرسه‌ی دوم\",\"timezone\":\"Asia/Tehran\",\"locale\":\"fa-IR\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long secondSchoolId = objectMapper.readTree(schoolResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/schools/" + secondSchoolId + "/roles")
                        .header("X-School-ID", secondSchoolId.toString())
                        .session(secondFounderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'LIBRARIAN')]").doesNotExist());

        String strangerEmail = "second.school.member@roshdeandishe.test";
        mockMvc.perform(post("/api/admin/schools/" + secondSchoolId + "/memberships")
                        .with(csrf())
                        .header("X-School-ID", secondSchoolId.toString())
                        .session(secondFounderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + strangerEmail + "\",\"fullName\":\"عضو مدرسه دوم\",\"roleCode\":\"LIBRARIAN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("role-not-found")));
    }
}
