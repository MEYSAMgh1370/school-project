package com.example.roshdeandishe;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.controller.AcademicYearVisibility;
import com.example.roshdeandishe.academic.controller.MyEnrollmentController;
import com.example.roshdeandishe.academic.controller.MyGradesController;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.exam.ExamFacade;
import com.example.roshdeandishe.exam.controller.MyExamsController;
import com.example.roshdeandishe.exam.shared.ExamException;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.finance.controller.MyInvoicesController;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import com.example.roshdeandishe.studyplan.controller.MyStudyIntervalController;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * endpointهای خودسرویس ({@code /api/me/**}، بدون مجوز) هر کدام نقشِ فعالِ مناسبِ خودشان را می‌خواهند
 * (دانش‌آموز برای داده‌ی خودش، ولی برای فرزندان): ثبت‌نام و لینکِ ولی با غیرفعال‌شدنِ عضویت پاک نمی‌شوند.
 * بدون Spring/Docker: facade ها mock هستند و نشان می‌دهند عضوِ سابق هرگز به academic/finance/
 * exam/studyplan نمی‌رسد. (رفتارِ واقعی روی دیتابیس در SelfServiceMembershipFlowIntegrationTest.)
 */
class SelfServiceMembershipGateTest {

    private static final Long SCHOOL = 5L;
    private static final Long USER = 77L;

    private IamFacade iam;
    private AcademicFacade academic;

    @BeforeEach
    void setUp() {
        iam = mock(IamFacade.class);
        academic = mock(AcademicFacade.class);
        when(iam.currentUserId()).thenReturn(new UserId(USER));
        when(iam.findActiveRoleCodes(new UserId(USER), SCHOOL)).thenReturn(Set.of());
    }

    @Test
    void aFormerMemberGetsNoGradesAndNothingIsLookedUp() {
        assertThat(new MyGradesController(academic, iam, mock(AcademicYearVisibility.class)).myGrades(SCHOOL)).isEmpty();

        verifyNoInteractions(academic);
    }

    @Test
    void aFormerMemberGetsNoInvoicesAndNothingIsLookedUp() {
        FinanceFacade finance = mock(FinanceFacade.class);

        assertThat(new MyInvoicesController(finance, academic, iam).myInvoices(SCHOOL)).isEmpty();

        verifyNoInteractions(academic, finance);
    }

    @Test
    void aFormerMemberHasNoEnrollment() {
        assertThat(new MyEnrollmentController(academic, iam).myEnrollment(SCHOOL).getStatusCode().value())
                .isEqualTo(204);

        verifyNoInteractions(academic);
    }

    @Test
    void aFormerMemberHasNoExamsAndCannotStartAnAttemptOrReadAResult() {
        ExamFacade exams = mock(ExamFacade.class);
        MyExamsController controller = new MyExamsController(exams, academic, iam);

        assertThat(controller.myExams(SCHOOL)).isEmpty();
        assertThatThrownBy(() -> controller.startAttempt(SCHOOL, 1L)).isInstanceOf(ExamException.class);
        assertThatThrownBy(() -> controller.myResult(SCHOOL, 1L)).isInstanceOf(ExamException.class);

        verifyNoInteractions(academic, exams);
    }

    @Test
    void aFormerMemberHasNoStudyIntervalsAndCannotEditOne() {
        StudyPlanFacade plans = mock(StudyPlanFacade.class);
        MyStudyIntervalController controller = new MyStudyIntervalController(
                plans, academic, mock(CurriculumFacade.class), iam);

        assertThat(controller.getWeek(SCHOOL, null)).isEmpty();
        assertThatThrownBy(() -> controller.updateInterval(SCHOOL, 1L,
                new com.example.roshdeandishe.studyplan.dto.request.UpdateMyIntervalRequest(true, null)))
                .isInstanceOf(StudyPlanException.class);

        verifyNoInteractions(academic, plans);
    }

    @Test
    void aParentOnlyLooksUpWardsAndAStudentOnlyLooksUpTheirOwnEnrollment() {
        when(academic.listWards(USER, SCHOOL)).thenReturn(java.util.List.of());
        when(academic.findMyEnrollment(USER, SCHOOL)).thenReturn(java.util.Optional.empty());

        when(iam.findActiveRoleCodes(new UserId(USER), SCHOOL)).thenReturn(Set.of("PARENT"));
        assertThat(new MyGradesController(academic, iam, mock(AcademicYearVisibility.class)).myGrades(SCHOOL)).isEmpty();
        verify(academic).listWards(USER, SCHOOL);
        verify(academic, never()).findMyEnrollment(USER, SCHOOL);

        org.mockito.Mockito.clearInvocations(academic);
        when(iam.findActiveRoleCodes(new UserId(USER), SCHOOL)).thenReturn(Set.of("STUDENT"));
        assertThat(new MyGradesController(academic, iam, mock(AcademicYearVisibility.class)).myGrades(SCHOOL)).isEmpty();
        verify(academic).findMyEnrollment(USER, SCHOOL);
        verify(academic, never()).listWards(USER, SCHOOL);
    }

    @Test
    void aMemberWithOnlyANonMatchingRoleGetsNothing() {
        when(iam.findActiveRoleCodes(new UserId(USER), SCHOOL)).thenReturn(Set.of("TEACHER", "FINANCE_ADMIN"));
        FinanceFacade finance = mock(FinanceFacade.class);

        assertThat(new MyGradesController(academic, iam, mock(AcademicYearVisibility.class)).myGrades(SCHOOL)).isEmpty();
        assertThat(new MyInvoicesController(finance, academic, iam).myInvoices(SCHOOL)).isEmpty();
        assertThat(new MyEnrollmentController(academic, iam).myEnrollment(SCHOOL).getStatusCode().value())
                .isEqualTo(204);

        verifyNoInteractions(academic, finance);
    }
}
