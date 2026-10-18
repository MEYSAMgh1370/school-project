package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.web.model.NavCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * سایدبار برای نقش سفارشی (مثلاً معاون با TIMETABLE_MANAGE) روی داده‌ی واقعی:
 * (۱) نگهبانِ بی‌تغییری: برای هر نقشِ سیستمیِ seed، «Permissionِ لینک» دقیقاً همان نتیجه‌ی
 * «نقشِ لینک» را می‌دهد - یعنی افزودنِ شرطِ Permission به NavCatalog هیچ لینکی را برای
 * مدیر/معلم/دانش‌آموز/ولی/مدیر مالی کم یا زیاد نمی‌کند؛ (۲) ناوبارِ صفحه‌ی پیشخوان Permissionهای
 * نقشِ سفارشی را به Alpine می‌دهد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NavPermissionsFlowIntegrationTest {

    private static final List<String> SYSTEM_ROLES =
            List.of("SCHOOL_ADMIN", "TEACHER", "STUDENT", "PARENT", "FINANCE_ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

    @Test
    void permissionVisibilityMatchesRoleVisibilityForEverySystemRole() {
        Map<String, List<String>> permissionsByRole = iamFacade.findPermissionCodesByRole(1L, SYSTEM_ROLES);
        assertThat(permissionsByRole.keySet()).containsExactlyInAnyOrderElementsOf(SYSTEM_ROLES);

        for (NavCatalog.Group group : NavCatalog.groups()) {
            for (NavCatalog.Link link : group.links()) {
                if (link.permissions().isEmpty()) {
                    continue;
                }
                for (String role : SYSTEM_ROLES) {
                    // «همه‌ی یک مجموعه» یا «همه‌ی یکی از جایگزین‌ها» (مثلاً حضور و غیاب: RECORD یا RECORD_ANY)
                    boolean byPermission = link.permissionSets().stream()
                            .filter(set -> !set.isEmpty())
                            .anyMatch(set -> permissionsByRole.get(role).containsAll(set));
                    assertThat(byPermission)
                            .as("%s for %s: link roles %s, link permissions %s, role permissions %s",
                                    link.href(), role, link.roles(), link.permissions(), permissionsByRole.get(role))
                            .isEqualTo(link.roles().contains(role));
                }
            }
        }
    }

    @Test
    void schoolAdminHoldsSettingsManageAndOthersDoNot() {
        Map<String, List<String>> permissionsByRole = iamFacade.findPermissionCodesByRole(1L, SYSTEM_ROLES);

        assertThat(permissionsByRole.get("SCHOOL_ADMIN")).contains("SCHOOL_SETTINGS_MANAGE");
        for (String role : List.of("TEACHER", "PARENT", "STUDENT", "FINANCE_ADMIN")) {
            assertThat(permissionsByRole.get(role)).as(role).doesNotContain("SCHOOL_SETTINGS_MANAGE");
        }
    }

    @Test
    void customRoleDisplayNamesAreSchoolScopedAndExcludeSystemRoles() {
        assertThat(iamFacade.findCustomRoleDisplayNames(1L, List.of("SCHOOL_ADMIN", "NO_SUCH_ROLE", "PLATFORM_ADMIN")))
                .isEmpty();
    }

    @Test
    void unknownAndPlatformRolesAreNotReturned() {
        assertThat(iamFacade.findPermissionCodesByRole(1L, List.of("NO_SUCH_ROLE", "PLATFORM_ADMIN"))).isEmpty();
    }

    @Test
    void aCustomRolesPermissionsReachTheNavbarOfItsHolder() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        String code = "DEPUTY_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase().replace("-", "");
        mockMvc.perform(post("/api/admin/schools/1/roles").with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"displayName\":\"معاون آموزشی\","
                                + "\"permissionCodes\":[\"TIMETABLE_MANAGE\",\"CLASS_VIEW\"]}"))
                .andExpect(status().isOk());
        String email = "nav.deputy." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
        MockHttpSession deputy = registerAndLogin(email, "معاون من");
        mockMvc.perform(post("/api/admin/schools/1/memberships").with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"معاون من\",\"roleCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/dashboard").session(deputy)).andExpect(status().isOk()).andReturn();
        String html = HtmlUtils.htmlUnescape(result.getResponse().getContentAsString());

        assertThat(html).contains("'" + code + "': ['CLASS_VIEW','TIMETABLE_MANAGE',]");
        // سوییچرِ نقش و جدولِ عضویت‌ها عنوانِ نقشِ سفارشی را نشان می‌دهند، نه کدِ خام
        assertThat(html).contains("معاون آموزشی").doesNotContainPattern(">\\s*" + code + "\\s*<");
        assertThat(iamFacade.findCustomRoleDisplayNames(1L, List.of(code, "SCHOOL_ADMIN", "PLATFORM_ADMIN")))
                .containsExactly(Map.entry(code, "معاون آموزشی"));
        assertThat(html).contains("href=\"/timetable\" x-show=\"['SCHOOL_ADMIN'].includes(selectedRole) "
                + "|| ['TIMETABLE_MANAGE'].every(p => permissions.includes(p))\"");
        // و API همان Permission را واقعاً می‌دهد: لینک فقط نمایشِ همین دسترسی است
        mockMvc.perform(get("/api/admin/schools/1/timetable/years").header("X-School-ID", "1").session(deputy))
                .andExpect(status().isOk());
    }

    @Test
    void schoolSettingsLinkShowsForAdminAndFinanceAdminAndForACustomRoleHoldingJustOneOfTheFourPermissions() throws Exception {
        MockHttpSession admin = login("admin@roshdeandishe.test", "password");
        String code = "REMINDER_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase().replace("-", "");
        mockMvc.perform(post("/api/admin/schools/1/roles").with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"displayName\":\"مسئول برنامه\","
                                + "\"permissionCodes\":[\"YEARLY_PLAN_MANAGE\"]}"))
                .andExpect(status().isOk());
        String email = "nav.planner." + UUID.randomUUID().toString().substring(0, 8) + "@roshdeandishe.test";
        MockHttpSession planner = registerAndLogin(email, "مسئول برنامه");
        mockMvc.perform(post("/api/admin/schools/1/memberships").with(csrf()).header("X-School-ID", "1").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"مسئول برنامه\",\"roleCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        String html = HtmlUtils.htmlUnescape(mockMvc.perform(get("/dashboard").session(planner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(html).contains("href=\"/settings\" x-show=\"['SCHOOL_ADMIN', 'FINANCE_ADMIN'].includes(selectedRole)")
                .contains("['YEARLY_PLAN_MANAGE'].every(p => permissions.includes(p))")
                .doesNotContain("href=\"/academic-year\"");
    }

    @Test
    void settingsLinkIsGrantedToExactlyTheAdminAndFinanceAdminSystemRoles() {
        Map<String, List<String>> permissionsByRole = iamFacade.findPermissionCodesByRole(1L, SYSTEM_ROLES);
        NavCatalog.Link settings = NavCatalog.groups().stream().flatMap(g -> g.links().stream())
                .filter(l -> l.href().equals("/settings")).findFirst().orElseThrow();

        for (String role : SYSTEM_ROLES) {
            boolean byPermission = settings.permissionSets().stream()
                    .anyMatch(set -> permissionsByRole.get(role).containsAll(set));
            assertThat(byPermission).as(role).isEqualTo(List.of("SCHOOL_ADMIN", "FINANCE_ADMIN").contains(role));
        }
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private MockHttpSession registerAndLogin(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return login(email, "password123");
    }
}
