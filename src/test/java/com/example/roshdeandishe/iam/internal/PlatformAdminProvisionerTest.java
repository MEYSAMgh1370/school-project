package com.example.roshdeandishe.iam.internal;

import com.example.roshdeandishe.iam.internal.UserEntity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * هم‌گام‌سازی {@code iam.platform_admins} با PLATFORM_ADMIN_EMAILS بدون Spring و Docker، روی جدول‌های
 * درون‌حافظه‌ای: فقط کاربرِ موجود + ACTIVE + فعال‌سازی‌شده، هرگز ساخت کاربر، و جدول دقیقاً برابر فهرست.
 */
class PlatformAdminProvisionerTest {

    private UserRepository users;
    private PlatformAdminRepository platformAdmins;
    private final List<UserEntity> userTable = new ArrayList<>();
    private final List<PlatformAdminEntity> adminTable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        platformAdmins = mock(PlatformAdminRepository.class);

        when(users.findAllByEmailIgnoreCase(anyString())).thenAnswer(invocation ->
                userTable.stream().filter(u -> u.getEmail().equalsIgnoreCase(invocation.getArgument(0))).toList());
        when(platformAdmins.findAll()).thenAnswer(invocation -> List.copyOf(adminTable));
        when(platformAdmins.saveAll(anyIterable())).thenAnswer(invocation -> {
            Iterable<PlatformAdminEntity> rows = invocation.getArgument(0);
            rows.forEach(adminTable::add);
            return List.copyOf(adminTable);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            ids.forEach(id -> adminTable.removeIf(row -> row.getUserId().equals(id)));
            return null;
        }).when(platformAdmins).deleteAllById(anyIterable());
    }

    private UserEntity user(long id, String email, boolean activated, UserStatus status) {
        UserEntity user = new UserEntity(email, "نام " + id);
        user.setId(id);
        user.setPasswordHash(activated ? "{bcrypt}hash" : null);
        user.setStatus(status);
        userTable.add(user);
        return user;
    }

    private PlatformAdminProvisioner.SyncResult sync(String emails) {
        return new PlatformAdminProvisioner(users, platformAdmins, emails).sync();
    }

    private List<Long> adminIds() {
        return adminTable.stream().map(PlatformAdminEntity::getUserId).sorted().toList();
    }

    @Test
    void aListedActiveActivatedUserIsInserted() {
        user(1, "root@example.com", true, UserStatus.ACTIVE);
        user(2, "other@example.com", true, UserStatus.ACTIVE);

        PlatformAdminProvisioner.SyncResult result = sync("root@example.com");

        assertThat(adminIds()).containsExactly(1L);
        assertThat(result).isEqualTo(new PlatformAdminProvisioner.SyncResult(1, 0, 1));
    }

    @Test
    void aListedUnknownEmailIsIgnoredAndNoUserIsCreated() {
        user(1, "root@example.com", true, UserStatus.ACTIVE);

        sync("nobody@example.com");

        assertThat(adminTable).isEmpty();
        assertThat(userTable).hasSize(1);
        verify(users, never()).save(any());
        verify(users, never()).saveAll(anyIterable());
    }

    @Test
    void aSuspendedUserIsIgnored() {
        user(1, "root@example.com", true, UserStatus.SUSPENDED);

        sync("root@example.com");

        assertThat(adminTable).isEmpty();
    }

    @Test
    void anInvitedButNotActivatedUserIsIgnored() {
        user(1, "root@example.com", false, UserStatus.ACTIVE);

        sync("root@example.com");

        assertThat(adminTable).isEmpty();
    }

    @Test
    void emailsAreTrimmedAndCaseInsensitiveAndEmptyEntriesAreSkipped() {
        user(1, "Root@Example.com", true, UserStatus.ACTIVE);
        user(2, "second@example.com", true, UserStatus.ACTIVE);

        sync("  ROOT@example.COM ,, ,\t second@EXAMPLE.com  ,");

        assertThat(adminIds()).containsExactly(1L, 2L);
    }

    @Test
    void anEmailMatchingSeveralUsersByCaseOnlyIsAmbiguousAndGrantsNobody() {
        user(1, "root@example.com", true, UserStatus.ACTIVE);
        user(2, "ROOT@example.com", true, UserStatus.ACTIVE);

        sync("root@example.com");

        assertThat(adminTable).isEmpty();
    }

    @Test
    void anEmailRemovedFromTheListIsRevoked() {
        user(1, "a@example.com", true, UserStatus.ACTIVE);
        user(2, "b@example.com", true, UserStatus.ACTIVE);
        sync("a@example.com,b@example.com");
        assertThat(adminIds()).containsExactly(1L, 2L);

        PlatformAdminProvisioner.SyncResult result = sync("a@example.com");

        assertThat(adminIds()).containsExactly(1L);
        assertThat(result).isEqualTo(new PlatformAdminProvisioner.SyncResult(0, 1, 1));
    }

    @Test
    void aUserSuspendedLaterIsRevokedAtTheNextSync() {
        UserEntity admin = user(1, "a@example.com", true, UserStatus.ACTIVE);
        sync("a@example.com");
        assertThat(adminIds()).containsExactly(1L);

        admin.setStatus(UserStatus.SUSPENDED);
        sync("a@example.com");

        assertThat(adminTable).isEmpty();
    }

    @Test
    void anEmptyOrBlankOrMissingVariableClearsTheTable() {
        user(1, "a@example.com", true, UserStatus.ACTIVE);
        sync("a@example.com");
        assertThat(adminTable).hasSize(1);

        sync("");
        assertThat(adminTable).isEmpty();

        sync("a@example.com");
        assertThat(adminTable).hasSize(1);
        sync("   ");
        assertThat(adminTable).isEmpty();

        sync("a@example.com");
        sync(null);
        assertThat(adminTable).isEmpty();
    }

    @Test
    void aSecondRunChangesNothing() {
        user(1, "a@example.com", true, UserStatus.ACTIVE);

        assertThat(sync("a@example.com")).isEqualTo(new PlatformAdminProvisioner.SyncResult(1, 0, 1));
        assertThat(sync("a@example.com")).isEqualTo(new PlatformAdminProvisioner.SyncResult(0, 0, 1));

        assertThat(adminIds()).containsExactly(1L);
    }

    @Test
    void anEmptyUserTableIsFine() {
        assertThat(sync("a@example.com")).isEqualTo(new PlatformAdminProvisioner.SyncResult(0, 0, 0));
        assertThat(sync("")).isEqualTo(new PlatformAdminProvisioner.SyncResult(0, 0, 0));
        assertThat(adminTable).isEmpty();
    }
}
