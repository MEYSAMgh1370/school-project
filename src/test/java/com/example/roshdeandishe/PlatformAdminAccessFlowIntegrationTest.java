package com.example.roshdeandishe;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.PermissionCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * مسیر مدیر پلتفرم (yearly-plan.md فاز C) روی Postgres واقعی: seed هرگز مدیر پلتفرم نمی‌سازد و
 * PLATFORM_HOLIDAY_MANAGE به هیچ نقشی گرنت نمی‌شود؛ با ست بودن PLATFORM_ADMIN_EMAILS فقط کاربرِ
 * ثبت‌نام‌شده‌ی فهرست‌شده مدیر پلتفرم می‌شود (پس از اجرای دوباره‌ی provisioner، چون کاربر بعد از
 * بالا آمدن اپ ثبت‌نام می‌کند) و مدیر پلتفرم هیچ مجوز مدرسه‌ای ندارد.
 */
@SpringBootTest(properties = "roshdeandishe.platform-admin.emails=  Platform-Admin-Flow@RoshdeAndishe.test , ghost-nobody@roshdeandishe.test ")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformAdminAccessFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "platform-admin-flow@roshdeandishe.test";
    private static final String NORMAL_EMAIL = "platform-normal-flow@roshdeandishe.test";
    private static final String GHOST_EMAIL = "ghost-nobody@roshdeandishe.test";
    private static final String DEMO_ADMIN_EMAIL = "admin@roshdeandishe.test";
    private static final String PLATFORM_PERMISSION = "PLATFORM_HOLIDAY_MANAGE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamFacade iamFacade;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<ApplicationRunner> runners;

    @AfterEach
    void cleanUp() {
        // دیتابیس بین کلاس‌های تست مشترک است؛ ردیف‌های این تست نباید به تست‌های دیگر نشت کنند
        jdbc.update("delete from iam.platform_admins");
    }

    private void rerunProvisioner() throws Exception {
        ApplicationRunner provisioner = runners.stream()
                .filter(r -> AopUtils.getTargetClass(r).getSimpleName().equals("PlatformAdminProvisioner"))
                .findFirst()
                .orElseThrow();
        provisioner.run(new DefaultApplicationArguments());
    }

    private void registerIfAbsent(String email) throws Exception {
        Integer existing = jdbc.queryForObject("select count(*) from iam.users where email = ?", Integer.class, email);
        if (existing != null && existing > 0) {
            return;
        }
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Flow User\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    private UserId userId(String email) {
        Long id = jdbc.queryForObject("select id from iam.users where email = ?", Long.class, email);
        return new UserId(id);
    }

    private int platformAdminRows(String email) {
        Integer count = jdbc.queryForObject(
                "select count(*) from iam.platform_admins pa join iam.users u on u.id = pa.user_id where u.email = ?",
                Integer.class, email);
        return count == null ? 0 : count;
    }

    private AccessDecision check(UserId user, String code, AccessContext context) {
        return iamFacade.checkPermission(user, new PermissionCode(code), context);
    }

    @Test
    void theSeedNeverCreatesAPlatformAdminAndNoRoleHoldsThePlatformPermission() {
        assertThat(platformAdminRows(DEMO_ADMIN_EMAIL)).isZero();

        Integer catalogRows = jdbc.queryForObject(
                "select count(*) from iam.permissions where code = ?", Integer.class, PLATFORM_PERMISSION);
        assertThat(catalogRows).isEqualTo(1);

        Integer grants = jdbc.queryForObject(
                "select count(*) from iam.role_permissions rp join iam.permissions p on p.id = rp.permission_id "
                        + "where p.code = ?", Integer.class, PLATFORM_PERMISSION);
        assertThat(grants).isZero();
    }

    @Test
    void theSchoolAdminRolePickerDoesNotOfferThePlatformPermission() throws Exception {
        MockHttpSession admin = login(DEMO_ADMIN_EMAIL, "password");

        MvcResult result = mockMvc.perform(get("/api/admin/schools/1/permissions")
                        .header("X-School-ID", "1")
                        .session(admin))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(PLATFORM_PERMISSION);
    }

    @Test
    void onlyTheListedRegisteredUserBecomesPlatformAdminAndGetsNoSchoolPermission() throws Exception {
        registerIfAbsent(ADMIN_EMAIL);
        registerIfAbsent(NORMAL_EMAIL);
        assertThat(platformAdminRows(ADMIN_EMAIL)).isZero();

        rerunProvisioner();

        assertThat(platformAdminRows(ADMIN_EMAIL)).isEqualTo(1);
        assertThat(platformAdminRows(NORMAL_EMAIL)).isZero();
        // ایمیل ناشناخته‌ی فهرست‌شده هرگز کاربر نمی‌سازد
        assertThat(jdbc.queryForObject("select count(*) from iam.users where email = ?", Integer.class, GHOST_EMAIL))
                .isZero();

        UserId admin = userId(ADMIN_EMAIL);
        UserId normal = userId(NORMAL_EMAIL);
        UserId demoSchoolAdmin = userId(DEMO_ADMIN_EMAIL);
        AccessContext platform = new AccessContext.PlatformContext();
        AccessContext school = new AccessContext.SchoolContext(1L);

        assertThat(iamFacade.isPlatformAdmin(admin)).isTrue();
        assertThat(iamFacade.isPlatformAdmin(normal)).isFalse();
        assertThat(iamFacade.isPlatformAdmin(demoSchoolAdmin)).isFalse();

        assertThat(check(admin, PLATFORM_PERMISSION, platform).isGranted()).isTrue();
        assertThat(check(normal, PLATFORM_PERMISSION, platform).isGranted()).isFalse();
        // مدیر مدرسه (SCHOOL_ADMIN) مجوز پلتفرم ندارد - نه در PlatformContext و نه در SchoolContext
        assertThat(check(demoSchoolAdmin, PLATFORM_PERMISSION, platform).isGranted()).isFalse();
        assertThat(check(demoSchoolAdmin, PLATFORM_PERMISSION, school).isGranted()).isFalse();

        // مدیر پلتفرم بدون عضویت مدرسه هیچ مجوز مدرسه‌ای ندارد
        assertThat(check(admin, "CALENDAR_MANAGE", platform).isGranted()).isFalse();
        assertThat(check(admin, "YEARLY_PLAN_MANAGE", platform).isGranted()).isFalse();
        assertThat(check(admin, "CALENDAR_MANAGE", school).isGranted()).isFalse();
        assertThat(check(admin, PLATFORM_PERMISSION, school).isGranted()).isFalse();
    }

    @Test
    void aSuspendedListedUserLosesTheRowAtTheNextRun() throws Exception {
        registerIfAbsent(ADMIN_EMAIL);
        rerunProvisioner();
        assertThat(platformAdminRows(ADMIN_EMAIL)).isEqualTo(1);

        jdbc.update("update iam.users set status = 'SUSPENDED' where email = ?", ADMIN_EMAIL);
        try {
            rerunProvisioner();
            assertThat(platformAdminRows(ADMIN_EMAIL)).isZero();
            assertThat(iamFacade.isPlatformAdmin(userId(ADMIN_EMAIL))).isFalse();
        } finally {
            jdbc.update("update iam.users set status = 'ACTIVE' where email = ?", ADMIN_EMAIL);
        }
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
}
