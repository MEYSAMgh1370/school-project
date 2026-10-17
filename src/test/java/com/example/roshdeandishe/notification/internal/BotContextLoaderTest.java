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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotContextLoaderTest {

    private static final Long CHAT = 42L;
    private static final Long USER = 7L;

    private BaleLinkRepository links;
    private IamFacade iam;
    private SchoolBotRepository bots;
    private BotContextLoader loader;

    @BeforeEach
    void setUp() {
        links = mock(BaleLinkRepository.class);
        iam = mock(IamFacade.class);
        bots = mock(SchoolBotRepository.class);
        loader = new BotContextLoader(links, iam, bots);
    }

    private void linked() {
        when(links.findByChat(BotRef.SHARED, CHAT)).thenReturn(Optional.of(new BaleLinkEntity(null, USER, CHAT, Instant.now())));
        when(iam.findUser(new UserId(USER)))
                .thenReturn(Optional.of(new UserProfile(new UserId(USER), "a@b.c", "علی رضایی", "0912")));
    }

    private static MembershipInfo membership(Long schoolId, String role, boolean active) {
        return new MembershipInfo(1L, new UserId(USER), "a@b.c", "علی رضایی", schoolId, role, active, true);
    }

    @Test
    void unlinkedChatHasNoContext() {
        when(links.findByChat(BotRef.SHARED, CHAT)).thenReturn(Optional.empty());

        assertThat(loader.load(BotRef.SHARED, CHAT)).isEmpty();
    }

    @Test
    void linkedUserThatNoLongerExistsHasNoContext() {
        when(links.findByChat(BotRef.SHARED, CHAT)).thenReturn(Optional.of(new BaleLinkEntity(null, USER, CHAT, Instant.now())));
        when(iam.findUser(new UserId(USER))).thenReturn(Optional.empty());

        assertThat(loader.load(BotRef.SHARED, CHAT)).isEmpty();
    }

    @Test
    void keepsOnlyActiveMemberships() {
        linked();
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(
                membership(1L, "TEACHER", true),
                membership(2L, "TEACHER", false)));

        BotContext ctx = loader.load(BotRef.SHARED, CHAT).orElseThrow();

        assertThat(ctx.userId()).isEqualTo(new UserId(USER));
        assertThat(ctx.fullName()).isEqualTo("علی رضایی");
        assertThat(ctx.memberships()).extracting(MembershipInfo::schoolId).containsExactly(1L);
        assertThat(ctx.rolesIn(1L)).containsExactly("TEACHER");
        assertThat(ctx.rolesIn(2L)).isEmpty();
        assertThat(ctx.hasRole("TEACHER")).isTrue();
        assertThat(ctx.hasRole("PARENT")).isFalse();
    }

    private static final BotRef SCHOOL_BOT = BotRef.school(3, 7);

    private void linkedIn(BotRef bot) {
        when(links.findByChat(bot, 5L)).thenReturn(Optional.of(new BaleLinkEntity(bot.botId(), USER, 5L, Instant.now())));
        when(iam.findUser(new UserId(USER)))
                .thenReturn(Optional.of(new UserProfile(new UserId(USER), "a@b.c", "علی رضایی", "0912")));
    }

    @Test
    void schoolBotKeepsOnlyItsSchoolMemberships() {
        linkedIn(SCHOOL_BOT);
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(
                membership(7L, "TEACHER", true), membership(8L, "PARENT", true)));

        BotContext ctx = loader.load(SCHOOL_BOT, 5L).orElseThrow();

        assertThat(ctx.memberships()).extracting(MembershipInfo::schoolId).containsExactly(7L);
    }

    @Test
    void sharedBotDropsSchoolsWithActiveBots() {
        linkedIn(BotRef.SHARED);
        when(bots.findActiveSchoolIds()).thenReturn(Set.of(7L));
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(
                membership(7L, "TEACHER", true), membership(8L, "PARENT", true)));

        BotContext ctx = loader.load(BotRef.SHARED, 5L).orElseThrow();

        assertThat(ctx.memberships()).extracting(MembershipInfo::schoolId).containsExactly(8L);
    }

    @Test
    void sharedBotServesSchoolWhoseBotIsDisabled() {
        linkedIn(BotRef.SHARED);
        when(bots.findActiveSchoolIds()).thenReturn(Set.of());
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(
                membership(7L, "TEACHER", true), membership(8L, "PARENT", true)));

        BotContext ctx = loader.load(BotRef.SHARED, 5L).orElseThrow();

        assertThat(ctx.memberships()).extracting(MembershipInfo::schoolId).containsExactly(7L, 8L);
    }

    @Test
    void endedMembershipShowsNoSchoolDataInSchoolBot() {
        linkedIn(SCHOOL_BOT);
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(membership(7L, "TEACHER", false)));

        BotContext ctx = loader.load(SCHOOL_BOT, 5L).orElseThrow();

        assertThat(ctx.memberships()).isEmpty();
    }

    @Test
    void linkIsLookedUpPerBot() {
        linkedIn(SCHOOL_BOT);
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of());

        assertThat(loader.load(SCHOOL_BOT, 5L)).isPresent();
        // همان chat در ربات مشترک لینک ندارد
        assertThat(loader.load(BotRef.SHARED, 5L)).isEmpty();
        verify(links).findByChat(SCHOOL_BOT, 5L);
        verify(links).findByChat(BotRef.SHARED, 5L);
    }
}
