package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotContextLoaderTest {

    private static final Long CHAT = 42L;
    private static final Long USER = 7L;

    private BaleLinkRepository links;
    private IamFacade iam;
    private AcademicFacade academic;
    private BotContextLoader loader;

    @BeforeEach
    void setUp() {
        links = mock(BaleLinkRepository.class);
        iam = mock(IamFacade.class);
        academic = mock(AcademicFacade.class);
        loader = new BotContextLoader(links, iam, academic);
    }

    private void linked() {
        when(links.findByChatId(CHAT)).thenReturn(Optional.of(new BaleLinkEntity(USER, CHAT, Instant.now())));
        when(iam.findUser(new UserId(USER)))
                .thenReturn(Optional.of(new UserProfile(new UserId(USER), "a@b.c", "علی رضایی", "0912")));
    }

    private static MembershipInfo membership(Long schoolId, String role, boolean active) {
        return new MembershipInfo(1L, new UserId(USER), "a@b.c", "علی رضایی", schoolId, role, active, true);
    }

    private static StudentInfo student(long id, long userId, long schoolId, Long classId) {
        return new StudentInfo(new StudentId(id), userId, schoolId, classId == null ? null : new ClassId(classId));
    }

    @Test
    void unlinkedChatHasNoContext() {
        when(links.findByChatId(CHAT)).thenReturn(Optional.empty());

        assertThat(loader.load(CHAT)).isEmpty();
    }

    @Test
    void linkedUserThatNoLongerExistsHasNoContext() {
        when(links.findByChatId(CHAT)).thenReturn(Optional.of(new BaleLinkEntity(USER, CHAT, Instant.now())));
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

    @Test
    void wardsComeOnlyFromActiveParentMemberships() {
        linked();
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(
                membership(1L, "STUDENT", true),
                membership(2L, "TEACHER", true),
                membership(3L, "PARENT", false)));

        BotContext ctx = loader.load(CHAT).orElseThrow();

        assertThat(ctx.wards()).isEmpty();
        verify(academic, never()).listWards(anyLong(), anyLong());
    }

    @Test
    void wardOfAnotherSchoolIsNotListedUnderThisSchool() {
        linked();
        when(iam.findMembershipsByUser(new UserId(USER))).thenReturn(List.of(
                membership(10L, "PARENT", true),
                membership(20L, "PARENT", true)));
        when(academic.listWards(USER, 10L)).thenReturn(List.of(student(100L, 1000L, 10L, 5L)));
        when(academic.listWards(USER, 20L)).thenReturn(List.of(student(200L, 2000L, 20L, null)));
        when(iam.findUser(new UserId(1000L)))
                .thenReturn(Optional.of(new UserProfile(new UserId(1000L), "s@b.c", "سارا", null)));
        when(iam.findUser(new UserId(2000L))).thenReturn(Optional.empty());

        BotContext ctx = loader.load(CHAT).orElseThrow();

        assertThat(ctx.wardsIn(10L)).containsExactly(new BotContext.Ward(10L, 100L, 1000L, 5L, "سارا"));
        assertThat(ctx.wardsIn(20L))
                .containsExactly(new BotContext.Ward(20L, 200L, 2000L, null, "دانش‌آموز #2000"));
        assertThat(ctx.wards()).hasSize(2);
    }
}
