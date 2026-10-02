package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.dto.MyAcademicYearResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * «هر عضو فعالِ مدرسه فقط سال جاری را می‌بیند» بدون Spring و Docker. یک کاربر
 * می‌تواند چند نقشِ جدا در یک مدرسه داشته باشد و هر عضویت مستقلاً غیرفعال شود؛
 * پس «عضو فعال» یعنی هر عضویتی فعال باشد، نه فقط اولین.
 */
class AcademicYearControllerTest {

    private static final UserId USER = new UserId(10L);
    private static final Long SCHOOL = 5L;

    private IamFacade iam;
    private SchoolFacade school;
    private AcademicYearController controller;

    @BeforeEach
    void setUp() {
        iam = mock(IamFacade.class);
        school = mock(SchoolFacade.class);
        controller = new AcademicYearController(school, iam);
        when(iam.currentUserId()).thenReturn(USER);
        when(school.findCurrentAcademicYear(new SchoolId(SCHOOL))).thenReturn(Optional.of(
                new AcademicYearInfo(new AcademicYearId(2L), "1405-1406", 1405)));
    }

    private static MembershipInfo membership(Long schoolId, String role, boolean active) {
        return new MembershipInfo(1L, USER, "u@example.com", "کاربر", schoolId, role, active, true);
    }

    @Test
    void aMemberWhoseFirstMembershipIsInactiveButAnotherRoleIsActiveSeesTheCurrentYear() {
        when(iam.findMembershipsByUser(USER)).thenReturn(List.of(
                membership(SCHOOL, "STUDENT", false), membership(SCHOOL, "TEACHER", true)));

        MyAcademicYearResponse response = controller.myAcademicYear(SCHOOL);

        assertThat(response.current().code()).isEqualTo("1405-1406");
    }

    @Test
    void aUserWithOnlyInactiveMembershipsInTheSchoolIsNotAMember() {
        when(iam.findMembershipsByUser(USER)).thenReturn(List.of(
                membership(SCHOOL, "STUDENT", false), membership(SCHOOL, "TEACHER", false)));

        assertThatThrownBy(() -> controller.myAcademicYear(SCHOOL))
                .isInstanceOfSatisfying(IamException.class, e ->
                        assertThat(e.error()).isInstanceOf(IamError.MembershipNotFound.class));
    }

    @Test
    void anActiveMembershipInAnotherSchoolDoesNotCount() {
        when(iam.findMembershipsByUser(USER)).thenReturn(List.of(membership(6L, "TEACHER", true)));

        assertThatThrownBy(() -> controller.myAcademicYear(SCHOOL))
                .isInstanceOf(IamException.class);
    }
}
