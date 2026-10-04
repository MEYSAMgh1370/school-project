package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * «چه کسی کدام سال را می‌بیند» (academic-year.md فاز ۱) بدون Spring و Docker: دارنده‌ی
 * ACADEMIC_YEAR_MANAGE هر سالی را می‌بیند، بقیه فقط سالِ جاری، و مدرسه‌ی بدونِ سالِ جاری محدود نیست.
 */
class AcademicYearVisibilityTest {

    private static final Long SCHOOL = 5L;
    private static final UserId USER = new UserId(77L);

    private IamFacade iam;
    private SchoolFacade school;
    private AcademicYearVisibility visibility;

    @BeforeEach
    void setUp() {
        iam = mock(IamFacade.class);
        school = mock(SchoolFacade.class);
        when(iam.currentUserId()).thenReturn(USER);
        visibility = new AcademicYearVisibility(iam, school);
    }

    private void givenCurrentYear(String code) {
        when(school.findCurrentAcademicYear(new SchoolId(SCHOOL))).thenReturn(code == null ? Optional.empty()
                : Optional.of(new AcademicYearInfo(new AcademicYearId(2L), code, 1405)));
    }

    private void givenManager(boolean manager) {
        when(iam.checkPermission(any(), any(), any(AccessContext.class))).thenReturn(manager
                ? new AccessDecision.Granted("ok") : new AccessDecision.Denied("no"));
    }

    @Test
    void aSchoolWithoutACurrentYearIsNotRestrictedAndNoPermissionIsAsked() {
        givenCurrentYear(null);

        assertThat(visibility.restrictedYear(SCHOOL)).isEmpty();
        assertThatCode(() -> visibility.requireVisible(SCHOOL, "1400-1401")).doesNotThrowAnyException();
    }

    @Test
    void aHolderOfTheYearPermissionSeesEveryYear() {
        givenCurrentYear("1405-1406");
        givenManager(true);

        assertThat(visibility.restrictedYear(SCHOOL)).isEmpty();
        assertThatCode(() -> visibility.requireVisible(SCHOOL, "1404-1405")).doesNotThrowAnyException();
    }

    @Test
    void everyoneElseSeesOnlyTheCurrentYearEvenInPersianDigits() {
        givenCurrentYear("1405-1406");
        givenManager(false);

        assertThat(visibility.restrictedYear(SCHOOL)).contains("1405-1406");
        assertThatCode(() -> visibility.requireVisible(SCHOOL, "1405-1406")).doesNotThrowAnyException();
        assertThatCode(() -> visibility.requireVisible(SCHOOL, " ۱۴۰۵-۱۴۰۶ ")).doesNotThrowAnyException();
        assertThatThrownBy(() -> visibility.requireVisible(SCHOOL, "1404-1405"))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isEqualTo(new AcademicError.AcademicYearNotAllowed("1404-1405")));
        // سالِ خالی/تهی خطای خودِ facade است (400)، نه «دسترسی ممنوع»
        assertThatCode(() -> visibility.requireVisible(SCHOOL, null)).doesNotThrowAnyException();
        assertThatCode(() -> visibility.requireVisible(SCHOOL, "  ")).doesNotThrowAnyException();
    }
}
