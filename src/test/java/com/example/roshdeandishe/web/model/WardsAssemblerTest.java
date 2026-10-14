package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.EnrollmentInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.FinanceFacade.InvoiceInfo;
import com.example.roshdeandishe.finance.InvoiceStatus;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.web.model.WardRef;
import com.example.roshdeandishe.web.model.WardRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ترکیبِ «فرزندان من» (iam + school + academic + finance) را بدون Spring و Docker
 * می‌آزماید: فقط والدِ فعال، فقط فرزندانِ لینک‌شده، و شمارشِ وضعیتِ فاکتورها.
 */
class WardsAssemblerTest {

    private static final UserId PARENT = new UserId(10L);

    private final IamFacade iam = mock(IamFacade.class);
    private final SchoolFacade school = mock(SchoolFacade.class);
    private final AcademicFacade academic = mock(AcademicFacade.class);
    private final FinanceFacade finance = mock(FinanceFacade.class);
    private final WardsAssembler assembler = new WardsAssembler(iam, school, academic, finance, mock(WardAttendanceResolver.class),
            mock(StudentSummaryAssembler.class));

    private static MembershipInfo membership(Long schoolId, String role, boolean active) {
        return new MembershipInfo(1L, PARENT, "p@example.com", "والد", schoolId, role, active, true);
    }

    private static InvoiceInfo invoice(Long studentId, InvoiceStatus status) {
        return new InvoiceInfo(1L, 5L, studentId, BigDecimal.TEN, BigDecimal.ZERO, null, null, status, null, BigDecimal.ZERO);
    }

    private void givenWard(Long schoolId, Long studentId, Long wardUserId, String name) {
        when(academic.listWards(PARENT.value(), schoolId))
                .thenReturn(List.of(new StudentInfo(new StudentId(studentId), wardUserId, schoolId, new ClassId(3L))));
        when(school.findSchool(new SchoolId(schoolId)))
                .thenReturn(Optional.of(new SchoolInfo(new SchoolId(schoolId), "مدرسه‌ی نمونه", null, null, null, null, null, null)));
        when(iam.findUser(new UserId(wardUserId)))
                .thenReturn(Optional.of(new UserProfile(new UserId(wardUserId), "w@example.com", name, null)));
        when(academic.findMyEnrollment(wardUserId, schoolId))
                .thenReturn(Optional.of(new EnrollmentInfo(new StudentId(studentId), schoolId, new ClassId(3L), "پنجم", "الف")));
    }

    @Test
    void isParentOnlyForAnActiveParentMembership() {
        assertThat(WardsAssembler.isParent(List.of(membership(5L, "PARENT", true)))).isTrue();
        assertThat(WardsAssembler.isParent(List.of(membership(5L, "PARENT", false)))).isFalse();
        assertThat(WardsAssembler.isParent(List.of(membership(5L, "TEACHER", true)))).isFalse();
        assertThat(WardsAssembler.isParent(List.of())).isFalse();
    }

    @Test
    void returnsNothingForAUserWhoIsNotAnActiveParent() {
        List<WardRow> rows = assembler.forParent(PARENT,
                List.of(membership(5L, "TEACHER", true), membership(6L, "PARENT", false)));

        assertThat(rows).isEmpty();
        verify(academic, never()).listWards(anyLong(), anyLong());
    }

    @Test
    void buildsAWardRowWithNameSchoolClassAndInvoiceCounts() {
        givenWard(5L, 100L, 200L, "آرین رضایی");
        when(finance.listInvoicesForStudents(5L, List.of(100L))).thenReturn(List.of(
                invoice(100L, InvoiceStatus.PAID),
                invoice(100L, InvoiceStatus.UNPAID),
                invoice(100L, InvoiceStatus.PARTIAL),
                invoice(100L, InvoiceStatus.OVERDUE)));

        List<WardRow> rows = assembler.forParent(PARENT, List.of(membership(5L, "PARENT", true)));

        assertThat(rows).hasSize(1);
        WardRow ward = rows.get(0);
        assertThat(ward.studentId()).isEqualTo(100L);
        assertThat(ward.schoolId()).isEqualTo(5L);
        assertThat(ward.name()).isEqualTo("آرین رضایی");
        assertThat(ward.schoolName()).isEqualTo("مدرسه‌ی نمونه");
        assertThat(ward.className()).isEqualTo("پنجم");
        assertThat(ward.section()).isEqualTo("الف");
        assertThat(ward.invoiceCount()).isEqualTo(4);
        assertThat(ward.overdueCount()).isEqualTo(1);
        assertThat(ward.openCount()).isEqualTo(2);
        assertThat(ward.isOverdue()).isTrue();
    }

    @Test
    void invoicesOfOtherStudentsAreNotCountedForThisWard() {
        givenWard(5L, 100L, 200L, "آرین رضایی");
        when(finance.listInvoicesForStudents(5L, List.of(100L))).thenReturn(List.of(
                invoice(100L, InvoiceStatus.PAID), invoice(999L, InvoiceStatus.OVERDUE)));

        WardRow ward = assembler.forParent(PARENT, List.of(membership(5L, "PARENT", true))).get(0);

        assertThat(ward.invoiceCount()).isEqualTo(1);
        assertThat(ward.isSettled()).isTrue();
    }

    @Test
    void aParentWithoutLinkedWardsGetsNoRowsAndNoFinanceCall() {
        when(academic.listWards(PARENT.value(), 5L)).thenReturn(List.of());

        assertThat(assembler.forParent(PARENT, List.of(membership(5L, "PARENT", true)))).isEmpty();
        verify(finance, never()).listInvoicesForStudents(anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void missingEnrollmentAndUserFallBackWithoutFailing() {
        when(academic.listWards(PARENT.value(), 5L))
                .thenReturn(List.of(new StudentInfo(new StudentId(100L), 200L, 5L, null)));
        when(school.findSchool(new SchoolId(5L))).thenReturn(Optional.empty());
        when(iam.findUser(new UserId(200L))).thenReturn(Optional.empty());
        when(academic.findMyEnrollment(200L, 5L)).thenReturn(Optional.empty());
        when(finance.listInvoicesForStudents(5L, List.of(100L))).thenReturn(List.of());

        WardRow ward = assembler.forParent(PARENT, List.of(membership(5L, "PARENT", true))).get(0);

        assertThat(ward.name()).isEqualTo("دانش‌آموز #200");
        assertThat(ward.schoolName()).isEqualTo("مدرسه #5");
        assertThat(ward.hasClass()).isFalse();
        assertThat(ward.hasNoInvoices()).isTrue();
    }

    @Test
    void wardRefsCoverOnlySchoolsWhereTheUserIsAnActiveParentAndCallNoFinance() {
        when(iam.currentUserId()).thenReturn(PARENT);
        // مدرسه‌ی ۵: والدِ فعال؛ ۶: عضویتِ غیرفعال (قابل‌انتخاب نیست)؛
        // ۷: معلمِ فعال - حتی با فرزندِ لینک‌شده، بدونِ نقشِ والد نامِ فرزند برنمی‌گردد (مثلِ MyGradesController)
        when(iam.findMembershipsByUser(PARENT)).thenReturn(List.of(
                membership(5L, "PARENT", true), membership(6L, "PARENT", false), membership(7L, "TEACHER", true)));
        givenWard(5L, 100L, 200L, "آرین رضایی");
        givenWard(7L, 101L, 201L, "سارا کریمی");

        List<WardRef> refs = assembler.wardRefsForCurrentUser();

        assertThat(refs).containsExactly(new WardRef(100L, 5L, "آرین رضایی"));
        assertThat(refs).noneMatch(WardRef::own);
        verify(academic, never()).listWards(PARENT.value(), 6L);
        verify(academic, never()).listWards(PARENT.value(), 7L);
        verify(finance, never()).listInvoicesForStudents(anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void wardRefsIncludeTheUsersOwnStudentEvenWithoutAnyGrade() {
        when(iam.currentUserId()).thenReturn(PARENT);
        when(iam.findMembershipsByUser(PARENT)).thenReturn(List.of(
                membership(5L, "STUDENT", true), membership(6L, "PARENT", true)));
        when(academic.findMyEnrollment(PARENT.value(), 5L)).thenReturn(Optional.of(
                new EnrollmentInfo(new StudentId(300L), 5L, new ClassId(3L), "پنجم", "الف")));
        givenWard(6L, 100L, 200L, "آرین رضایی");

        List<WardRef> refs = assembler.wardRefsForCurrentUser();

        assertThat(refs).containsExactly(
                new WardRef(300L, 5L, "نمره‌های من", true), new WardRef(100L, 6L, "آرین رضایی", false));
    }

    @Test
    void anOwnStudentLinkedAsHisOwnWardAppearsOnlyOnceAsOwn() {
        when(iam.currentUserId()).thenReturn(PARENT);
        when(iam.findMembershipsByUser(PARENT)).thenReturn(List.of(membership(5L, "STUDENT", true)));
        givenWard(5L, 100L, PARENT.value(), "خودم");
        when(academic.findMyEnrollment(PARENT.value(), 5L)).thenReturn(Optional.of(
                new EnrollmentInfo(new StudentId(100L), 5L, new ClassId(3L), "پنجم", "الف")));

        assertThat(assembler.wardRefsForCurrentUser())
                .containsExactly(new WardRef(100L, 5L, "نمره‌های من", true));
    }

    @Test
    void wardRefsAreEmptyForAUserWithoutLinkedWards() {
        when(iam.currentUserId()).thenReturn(PARENT);
        when(iam.findMembershipsByUser(PARENT)).thenReturn(List.of(membership(5L, "STUDENT", true)));

        assertThat(assembler.wardRefsForCurrentUser()).isEmpty();
    }
}
