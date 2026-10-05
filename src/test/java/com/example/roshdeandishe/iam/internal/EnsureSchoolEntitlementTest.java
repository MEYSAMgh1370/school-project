package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.internal.ModuleEntity.ModuleScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code ensureSchoolEntitlement} (پایه‌ی backfillِ حضور و غیاب، attendance.md تصمیم ۶) بدون Spring و Docker:
 * فقط وقتی ردیفی نیست می‌سازد، ردیفِ موجود را (حتی غیرفعال) دست نمی‌زند و دوباره‌اجرا idempotent است.
 */
class EnsureSchoolEntitlementTest {

    private static final ModuleCode ATTENDANCE = new ModuleCode("ATTENDANCE");

    private SchoolEntitlementRepository entitlements;
    private ModuleRepository modules;
    private IamFacadeImpl facade;
    private final List<SchoolEntitlementEntity> table = new ArrayList<>();

    @BeforeEach
    void setUp() {
        entitlements = mock(SchoolEntitlementRepository.class);
        modules = mock(ModuleRepository.class);
        facade = new IamFacadeImpl(
                mock(UserRepository.class), mock(RoleRepository.class), modules, mock(PermissionRepository.class),
                mock(RolePermissionRepository.class), mock(SchoolMembershipRepository.class), entitlements,
                mock(InviteTokenRepository.class), mock(VerificationCodeRepository.class),
                mock(AccessDecisionService.class), mock(PermissionDecisionService.class),
                mock(PasswordEncoder.class), mock(InviteEmailSender.class), mock(SmsDriver.class));

        ModuleEntity module = new ModuleEntity("ATTENDANCE", "حضور و غیاب", ModuleScope.SCHOOL);
        module.setId(3L);
        when(modules.findByCode("ATTENDANCE")).thenReturn(Optional.of(module));
        // یک جدولِ درون‌حافظه‌ای تا idempotency واقعاً دیده شود
        when(entitlements.findBySchoolIdAndModuleId(anyLong(), anyLong())).thenAnswer(invocation ->
                table.stream()
                        .filter(e -> e.getSchoolId().equals(invocation.getArgument(0))
                                && e.getModuleId().equals(invocation.getArgument(1)))
                        .findFirst());
        when(entitlements.save(any(SchoolEntitlementEntity.class))).thenAnswer(invocation -> {
            SchoolEntitlementEntity entity = invocation.getArgument(0);
            if (!table.contains(entity)) {
                table.add(entity);
            }
            return entity;
        });
    }

    @Test
    void createsAnActiveRowWhenTheSchoolHasNone() {
        assertThat(facade.ensureSchoolEntitlement(10L, ATTENDANCE)).isTrue();

        assertThat(table).hasSize(1);
        assertThat(table.get(0).getSchoolId()).isEqualTo(10L);
        assertThat(table.get(0).getModuleId()).isEqualTo(3L);
        assertThat(table.get(0).isActive()).isTrue();
    }

    @Test
    void runningItAgainChangesNothing() {
        assertThat(facade.ensureSchoolEntitlement(10L, ATTENDANCE)).isTrue();
        assertThat(facade.ensureSchoolEntitlement(10L, ATTENDANCE)).isFalse();
        assertThat(facade.ensureSchoolEntitlement(10L, ATTENDANCE)).isFalse();

        assertThat(table).hasSize(1);
    }

    @Test
    void anExistingInactiveRowIsLeftAloneSoAnAdminsSwitchOffSurvivesARestart() {
        SchoolEntitlementEntity off = new SchoolEntitlementEntity(10L, 3L, false);
        table.add(off);

        assertThat(facade.ensureSchoolEntitlement(10L, ATTENDANCE)).isFalse();

        assertThat(off.isActive()).isFalse();
        verify(entitlements, never()).save(any());
    }

    @Test
    void eachSchoolIsHandledOnItsOwn() {
        table.add(new SchoolEntitlementEntity(10L, 3L, true));

        assertThat(facade.ensureSchoolEntitlement(10L, ATTENDANCE)).isFalse();
        assertThat(facade.ensureSchoolEntitlement(11L, ATTENDANCE)).isTrue();

        assertThat(table).hasSize(2);
    }
}
