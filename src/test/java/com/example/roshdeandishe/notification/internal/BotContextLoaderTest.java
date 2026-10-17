package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotContextLoaderTest {

    private static final Long CHAT = 42L;
    private static final Long USER = 7L;

    private BaleLinkRepository links;
    private IamFacade iam;
    private BotContextLoader loader;

    @BeforeEach
    void setUp() {
        links = mock(BaleLinkRepository.class);
        iam = mock(IamFacade.class);
        loader = new BotContextLoader(links, iam);
    }

    private void linked() {
        when(links.findByChatId(CHAT)).thenReturn(Optional.of(new BaleLinkEntity(null, USER, CHAT, Instant.now())));
        when(iam.findUser(new UserId(USER)))
                .thenReturn(Optional.of(new UserProfile(new UserId(USER), "a@b.c", "علی رضایی", "0912")));
    }

    private static MembershipInfo membership(Long schoolId, String role, boolean active) {
        return new MembershipInfo(1L, new UserId(USER), "a@b.c", "علی رضایی", schoolId, role, active, true);
    }

    @Test
    void unlinkedChatHasNoContext() {
        when(links.findByChatId(CHAT)).thenReturn(Optional.empty());

        assertThat(loader.load(CHAT)).isEmpty();
    }

    @Test
    void linkedUserThatNoLongerExistsHasNoContext() {
        when(links.findByChatId(CHAT)).thenReturn(Optional.of(new BaleLinkEntity(null, USER, CHAT, Instant.now())));
        when(iam.findUser(new UserId(USER))).thenReturn(Optional.empty());

        assertThat(loader.load(CHAT)).isEmpty();
    }

    @Test
    void keepsOnlyActiveMemberships() {
        linked();
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(
                membership(1L, "TEACHER", true),
                membership(2L, "TEACHER", false)));

        BotContext ctx = loader.load(CHAT).orElseThrow();

        assertThat(ctx.userId()).isEqualTo(new UserId(USER));
        assertThat(ctx.fullName()).isEqualTo("علی رضایی");
        assertThat(ctx.memberships()).extracting(MembershipInfo::schoolId).containsExactly(1L);
        assertThat(ctx.rolesIn(1L)).containsExactly("TEACHER");
        assertThat(ctx.rolesIn(2L)).isEmpty();
        assertThat(ctx.hasRole("TEACHER")).isTrue();
        assertThat(ctx.hasRole("PARENT")).isFalse();
    }
}
