package com.example.roshdeandishe;

import com.example.roshdeandishe.academic.AcademicFacade;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * endpointهای خودسرویس ({@code /api/me/**}، بدون مجوز) فقط به کسی داده می‌دهند که در همین
 * مدرسه حداقل یک عضویتِ فعال دارد: ثبت‌نام و لینکِ ولی با غیرفعال‌شدنِ عضویت پاک نمی‌شوند.
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
        when(iam.hasActiveMembership(new UserId(USER), SCHOOL)).thenReturn(false);
    }

    @Test
    void aFormerMemberGetsNoGradesAndNothingIsLookedUp() {
        assertThat(new MyGradesController(academic, iam).myGrades(SCHOOL)).isEmpty();

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
                new com.example.roshdeandishe.studyplan.dto.UpdateMyIntervalRequest(true, null)))
                .isInstanceOf(StudyPlanException.class);

        verifyNoInteractions(academic, plans);
    }
}
