package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.EnrollmentInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.example.roshdeandishe.notification.internal.BotIntent.EVENTS;
import static com.example.roshdeandishe.notification.internal.BotIntent.GRADES;
import static com.example.roshdeandishe.notification.internal.BotIntent.INVOICES;
import static com.example.roshdeandishe.notification.internal.BotIntent.TEACHER_TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubjectResolverTest {

    private static final Long USER = 7L;

    private AcademicFacade academic;
    private SchoolFacade school;
    private IamFacade iam;
    private SubjectResolver resolver;

    @BeforeEach
    void setUp() {
        academic = mock(AcademicFacade.class);
        school = mock(SchoolFacade.class);
        iam = mock(IamFacade.class);
        resolver = new SubjectResolver(academic, school, iam);
        when(school.findSchool(any())).thenReturn(Optional.empty());
        when(iam.findUser(any())).thenReturn(Optional.empty());
        when(academic.listWards(any(), any())).thenReturn(List.of());
        when(academic.findMyEnrollment(any(), any())).thenReturn(Optional.empty());
    }

    private static MembershipInfo m(Long schoolId, String role, boolean active) {
        return new MembershipInfo(1L, new UserId(USER), "a@b.c", "علی", schoolId, role, active, true);
    }

    private static BotContext ctx(MembershipInfo... memberships) {
        List<MembershipInfo> active = java.util.Arrays.stream(memberships).filter(MembershipInfo::active).toList();
        return new BotContext(42L, new UserId(USER), "علی رضایی", active);
    }

    @Test
    void studentSeesOwnEnrolment() {
        when(academic.findMyEnrollment(USER, 10L)).thenReturn(Optional.of(
                new EnrollmentInfo(new StudentId(100L), 10L, new ClassId(5L), "اول", "الف")));

        List<BotTarget> targets = resolver.targets(ctx(m(10L, "STUDENT", true)), GRADES);

        assertThat(targets).containsExactly(
                new BotTarget.Student(10L, 100L, USER, 5L, "علی رضایی", "مدرسه #10", false));
        assertThat(targets.get(0).id()).isEqualTo(100L);
    }

    @Test
    void parentWhoIsAlsoAStudentGetsBoth() {
        when(academic.listWards(USER, 10L)).thenReturn(List.of(
                new StudentInfo(new StudentId(200L), 2000L, 10L, null)));
        when(iam.findUser(new UserId(2000L)))
                .thenReturn(Optional.of(new UserProfile(new UserId(2000L), "s@b.c", "سارا", null)));
        when(academic.findMyEnrollment(USER, 10L)).thenReturn(Optional.of(
                new EnrollmentInfo(new StudentId(100L), 10L, null, null, null)));

        List<BotTarget> targets = resolver.targets(ctx(m(10L, "PARENT", true), m(10L, "STUDENT", true)), GRADES);

        assertThat(targets).containsExactlyInAnyOrder(
                new BotTarget.Student(10L, 200L, 2000L, null, "سارا", "مدرسه #10", true),
                new BotTarget.Student(10L, 100L, USER, null, "علی رضایی", "مدرسه #10", false));
    }

    @Test
    void invoicesNeverTargetTheUsersOwnEnrolment() {
        when(academic.findMyEnrollment(USER, 10L)).thenReturn(Optional.of(
                new EnrollmentInfo(new StudentId(100L), 10L, null, null, null)));

        assertThat(resolver.targets(ctx(m(10L, "STUDENT", true)), INVOICES)).isEmpty();
    }

    @Test
    void invoicesTargetWardsOnly() {
        when(academic.listWards(USER, 10L)).thenReturn(List.of(
                new StudentInfo(new StudentId(200L), 2000L, 10L, null)));
        when(academic.findMyEnrollment(USER, 10L)).thenReturn(Optional.of(
                new EnrollmentInfo(new StudentId(100L), 10L, null, null, null)));

        List<BotTarget> targets = resolver.targets(ctx(m(10L, "PARENT", true), m(10L, "STUDENT", true)), INVOICES);

        assertThat(targets).extracting(BotTarget::id).containsExactly(200L);
        assertThat(targets.get(0)).isInstanceOfSatisfying(BotTarget.Student.class,
                s -> assertThat(s.name()).isEqualTo("دانش‌آموز #2000"));
    }

    @Test
    void inactiveMembershipsGiveNoTargets() {
        // BotContext keeps only active memberships; the resolver must not look at inactive ones either
        BotContext c = new BotContext(42L, new UserId(USER), "علی",
                List.of(m(10L, "PARENT", false), m(10L, "STUDENT", false), m(10L, "TEACHER", false)));

        for (BotIntent intent : BotIntent.values()) {
            assertThat(resolver.targets(c, intent)).as(intent.name()).isEmpty();
        }
    }

    @Test
    void teacherSchoolsComeFromActiveTeacherMemberships() {
        when(school.findSchool(any())).thenAnswer(inv -> Optional.empty());

        List<BotTarget> teacher = resolver.targets(
                ctx(m(20L, "TEACHER", true), m(10L, "TEACHER", true), m(30L, "PARENT", true)), TEACHER_TODAY);
        List<BotTarget> member = resolver.targets(
                ctx(m(20L, "TEACHER", true), m(10L, "PARENT", true), m(10L, "STUDENT", true)), EVENTS);

        assertThat(teacher).containsExactly(new BotTarget.School(10L, "مدرسه #10"), new BotTarget.School(20L, "مدرسه #20"));
        assertThat(member).containsExactly(new BotTarget.School(10L, "مدرسه #10"), new BotTarget.School(20L, "مدرسه #20"));
    }
}
