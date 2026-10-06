package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * مسیر مجوز پلتفرم (yearly-plan.md فاز C) بدون Spring و Docker: مدیر پلتفرم فقط
 * PLATFORM_HOLIDAY_MANAGE را در PlatformContext می‌گیرد، هرگز مجوز مدرسه‌ای را، و نقش مدرسه‌ای
 * هرگز مجوز پلتفرم را؛ شاخه‌ی SchoolContext جدول platform_admins را اصلاً نمی‌خواند.
 */
class PlatformPermissionDecisionTest {

    private static final Long USER = 7L;
    private static final Long SCHOOL = 1L;
    private static final String PLATFORM = "PLATFORM_HOLIDAY_MANAGE";

    private SchoolMembershipRepository memberships;
    private PermissionRepository permissions;
    private RolePermissionRepository rolePermissions;
    private PlatformAdminRepository platformAdmins;
    private PermissionDecisionService service;

    @BeforeEach
    void setUp() {
        memberships = mock(SchoolMembershipRepository.class);
        permissions = mock(PermissionRepository.class);
        rolePermissions = mock(RolePermissionRepository.class);
        platformAdmins = mock(PlatformAdminRepository.class);
        service = new PermissionDecisionService(memberships, permissions, rolePermissions, platformAdmins);
    }

    private void markPlatformAdmin() {
        when(platformAdmins.existsById(USER)).thenReturn(true);
    }

    private void catalogHas(String code, long id) {
        PermissionEntity permission = new PermissionEntity(code, code);
        permission.setId(id);
        when(permissions.findByCode(code)).thenReturn(Optional.of(permission));
    }

    @Test
    void platformAdminIsGrantedThePlatformHolidayPermission() {
        markPlatformAdmin();

        AccessDecision decision = service.decide(USER, PLATFORM, new AccessContext.PlatformContext());

        assertThat(decision).isInstanceOf(AccessDecision.Granted.class);
        verifyNoInteractions(memberships, rolePermissions);
    }

    @Test
    void platformAdminIsDeniedEverySchoolPermissionInPlatformContext() {
        markPlatformAdmin();

        for (String code : List.of("CALENDAR_MANAGE", "YEARLY_PLAN_MANAGE", "MEMBERSHIP_MANAGE", "PLATFORM_ADMIN", "")) {
            assertThat(service.decide(USER, code, new AccessContext.PlatformContext()))
                    .as(code)
                    .isInstanceOf(AccessDecision.Denied.class);
        }
        assertThat(service.decide(USER, null, new AccessContext.PlatformContext()))
                .isInstanceOf(AccessDecision.Denied.class);
        verifyNoInteractions(memberships, rolePermissions);
    }

    @Test
    void aNonAdminIsDeniedThePlatformPermission() {
        when(platformAdmins.existsById(anyLong())).thenReturn(false);

        assertThat(service.decide(USER, PLATFORM, new AccessContext.PlatformContext()))
                .isInstanceOf(AccessDecision.Denied.class);
        assertThat(service.decide(null, PLATFORM, new AccessContext.PlatformContext()))
                .isInstanceOf(AccessDecision.Denied.class);
    }

    @Test
    void aSchoolAdminHoldingEveryRoleGrantIsStillDeniedThePlatformPermission() {
        // نقش مدرسه‌ای هر گرنتی داشته باشد (حتی اگر ردیف گرنت PLATFORM_HOLIDAY_MANAGE وجود داشته باشد)
        catalogHas(PLATFORM, 99L);
        when(memberships.findByUserIdAndSchoolIdAndActiveTrue(USER, SCHOOL))
                .thenReturn(List.of(new SchoolMembershipEntity(USER, SCHOOL, 5L)));
        when(rolePermissions.existsByRoleIdAndPermissionId(anyLong(), anyLong())).thenReturn(true);
        when(platformAdmins.existsById(anyLong())).thenReturn(false);

        assertThat(service.decide(USER, PLATFORM, new AccessContext.PlatformContext()))
                .isInstanceOf(AccessDecision.Denied.class);
        verifyNoInteractions(memberships, rolePermissions);
    }

    @Test
    void aPlatformAdminWithNoMembershipIsDeniedASchoolPermissionAndPlatformAdminsIsNeverConsulted() {
        markPlatformAdmin();
        catalogHas("CALENDAR_MANAGE", 10L);
        when(memberships.findByUserIdAndSchoolIdAndActiveTrue(USER, SCHOOL)).thenReturn(List.of());
        when(memberships.findByUserIdAndSchoolId(USER, SCHOOL)).thenReturn(List.of());

        AccessDecision decision = service.decide(USER, "CALENDAR_MANAGE", new AccessContext.SchoolContext(SCHOOL));

        assertThat(decision).isInstanceOf(AccessDecision.Denied.class);
        verifyNoInteractions(platformAdmins);
    }

    @Test
    void aPlatformAdminIsDeniedThePlatformCodeInSchoolContextToo() {
        markPlatformAdmin();
        catalogHas(PLATFORM, 99L);
        when(memberships.findByUserIdAndSchoolIdAndActiveTrue(USER, SCHOOL)).thenReturn(List.of());
        when(memberships.findByUserIdAndSchoolId(USER, SCHOOL)).thenReturn(List.of());

        assertThat(service.decide(USER, PLATFORM, new AccessContext.SchoolContext(SCHOOL)))
                .isInstanceOf(AccessDecision.Denied.class);
        verifyNoInteractions(platformAdmins);
    }

    @Test
    void aSchoolMemberWithoutTheRoleGrantIsDeniedInSchoolContextAndPlatformAdminsIsNeverConsulted() {
        markPlatformAdmin();
        catalogHas(PLATFORM, 99L);
        when(memberships.findByUserIdAndSchoolIdAndActiveTrue(USER, SCHOOL))
                .thenReturn(List.of(new SchoolMembershipEntity(USER, SCHOOL, 5L)));
        when(rolePermissions.existsByRoleIdAndPermissionId(5L, 99L)).thenReturn(false);

        assertThat(service.decide(USER, PLATFORM, new AccessContext.SchoolContext(SCHOOL)))
                .isInstanceOf(AccessDecision.Denied.class);
        verifyNoInteractions(platformAdmins);
    }

    @Test
    void schoolPermissionStillFollowsTheRoleGrantAndNeverTouchesPlatformAdmins() {
        catalogHas("CALENDAR_MANAGE", 10L);
        when(memberships.findByUserIdAndSchoolIdAndActiveTrue(USER, SCHOOL))
                .thenReturn(List.of(new SchoolMembershipEntity(USER, SCHOOL, 5L)));
        when(rolePermissions.existsByRoleIdAndPermissionId(5L, 10L)).thenReturn(true);

        assertThat(service.decide(USER, "CALENDAR_MANAGE", new AccessContext.SchoolContext(SCHOOL)))
                .isInstanceOf(AccessDecision.Granted.class);
        verifyNoInteractions(platformAdmins);
    }

    @Test
    void isPlatformAdminReadsTheTableOnly() {
        markPlatformAdmin();

        assertThat(service.isPlatformAdmin(USER)).isTrue();
        assertThat(service.isPlatformAdmin(8L)).isFalse();
        assertThat(service.isPlatformAdmin(null)).isFalse();
    }
}
