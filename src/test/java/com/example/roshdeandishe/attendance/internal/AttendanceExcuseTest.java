package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.ClassInfo;
import com.example.roshdeandishe.academic.AcademicFacade.EnrollmentInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.attendance.AbsenceRecordedEvent;
import com.example.roshdeandishe.attendance.AttendanceFacade.ExcuseRow;
import com.example.roshdeandishe.attendance.AttendanceFacade.MarkInput;
import com.example.roshdeandishe.attendance.AttendanceFacade.MyStudentAttendance;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.attendance.ExcuseStatus;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * عذرها و رویدادِ غیبت (attendance.md «Excuses»/«Notifications») بدون Spring و Docker: ثبت/جایگزینی/تصمیم،
 * ولیِ بیگانه، تأییدِ «موجه»، تفاضلِ «غیبتِ تازه» برای رویداد، و ماندنِ عذر پس از ذخیره‌ی دوباره‌ی حضور و غیاب.
 */
class AttendanceExcuseTest {

    private static final Long SCHOOL = 5L;
    private static final Long CLASS_A = 7L;
    private static final Long PARENT = 500L;
    private static final Long OTHER_PARENT = 501L;
    private static final Long TEACHER = 90L;
    private static final Long ADMIN = 1L;
    private static final Long CHILD = 1L;
    private static final Long STRANGER_CHILD = 2L;
    private static final LocalDate MONDAY = LocalDate.of(2024, 10, 7);

    private RollRepository rolls;
    private MarkRepository marks;
    private ExcuseRepository excuses;
    private AcademicFacade academic;
    private IamFacade iam;
    private ApplicationEventPublisher events;
    private AttendanceFacadeImpl facade;

    @BeforeEach
    void setUp() {
        rolls = mock(RollRepository.class);
        marks = mock(MarkRepository.class);
        excuses = mock(ExcuseRepository.class);
        academic = mock(AcademicFacade.class);
        iam = mock(IamFacade.class);
        events = mock(ApplicationEventPublisher.class);
        SchoolFacade school = mock(SchoolFacade.class);
        facade = new AttendanceFacadeImpl(rolls, marks, excuses, academic, mock(TimetableFacade.class), school, iam,
                events);

        when(school.findSchool(new SchoolId(SCHOOL))).thenReturn(Optional.of(new SchoolInfo(new SchoolId(SCHOOL),
                "مدرسه", SchoolStatus.ACTIVE, "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null,
                WeekStart.SATURDAY)));
        when(school.findCurrentAcademicYear(new SchoolId(SCHOOL))).thenReturn(Optional.empty());
        when(academic.findClass(SCHOOL, CLASS_A)).thenReturn(Optional.of(
                new ClassInfo(new ClassId(CLASS_A), SCHOOL, "پنجم الف", TEACHER, null, 5, GradingScheme.NUMERIC, null)));
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(CLASS_A,
                List.of(student(CHILD), student(STRANGER_CHILD), student(3L))));
        when(iam.findMembershipsBySchool(SCHOOL)).thenReturn(List.of(
                member(1001L, "علی رضایی"), member(1002L, "محمد احمدی")));
        when(iam.findActiveRoleCodes(new UserId(PARENT), SCHOOL)).thenReturn(Set.of("PARENT"));
        when(iam.findActiveRoleCodes(new UserId(OTHER_PARENT), SCHOOL)).thenReturn(Set.of("PARENT"));
        when(academic.listWards(PARENT, SCHOOL)).thenReturn(List.of(student(CHILD)));
        when(academic.listWards(OTHER_PARENT, SCHOOL)).thenReturn(List.of());
        when(excuses.save(any(ExcuseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(marks.findByStudentIdAndDay(CHILD, MONDAY)).thenReturn(List.of(mark(CHILD, AttendanceStatus.ABSENT, false)));
    }

    // ---- ثبتِ عذر ----

    @Test
    void anActiveParentSubmitsAPendingExcuseForTheChildsAbsence() {
        ExcuseRow row = facade.submitExcuse(SCHOOL, CHILD, MONDAY, PARENT, "  سرماخوردگی ");

        ArgumentCaptor<ExcuseEntity> saved = ArgumentCaptor.forClass(ExcuseEntity.class);
        verify(excuses).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ExcuseStatus.PENDING);
        assertThat(saved.getValue().getNote()).isEqualTo("سرماخوردگی");
        assertThat(saved.getValue().getClassId()).isEqualTo(CLASS_A);
        assertThat(saved.getValue().getSubmittedByUserId()).isEqualTo(PARENT);
        assertThat(row.studentName()).isEqualTo("علی رضایی");
        assertThat(row.className()).isEqualTo("پنجم الف");
        assertThat(row.status()).isEqualTo(ExcuseStatus.PENDING);
    }

    @Test
    void resubmittingWhilePendingReplacesTheNoteInsteadOfAddingAnotherExcuse() {
        ExcuseEntity existing = excuse(ExcuseStatus.PENDING, "قدیمی");
        when(excuses.findByStudentIdAndDay(CHILD, MONDAY)).thenReturn(Optional.of(existing));

        facade.submitExcuse(SCHOOL, CHILD, MONDAY, PARENT, "تازه");

        assertThat(existing.getNote()).isEqualTo("تازه");
        assertThat(existing.getStatus()).isEqualTo(ExcuseStatus.PENDING);
        verify(excuses).save(existing);
    }

    @Test
    void resubmittingAfterADecisionIsAConflict() {
        for (ExcuseStatus decided : List.of(ExcuseStatus.APPROVED, ExcuseStatus.REJECTED)) {
            ExcuseEntity existing = excuse(decided, "قدیمی");
            when(excuses.findByStudentIdAndDay(CHILD, MONDAY)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> facade.submitExcuse(SCHOOL, CHILD, MONDAY, PARENT, "تازه"))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            e -> assertThat(e.error()).isInstanceOf(AttendanceError.ExcuseAlreadyDecided.class));
            assertThat(existing.getNote()).isEqualTo("قدیمی");
        }
        verify(excuses, never()).save(any());
    }

    @Test
    void thereMustBeAnAbsentMarkThatDay() {
        // دیرآمده غیبت نیست؛ روزِ بدون مُهر هم نه
        when(marks.findByStudentIdAndDay(CHILD, MONDAY)).thenReturn(List.of(mark(CHILD, AttendanceStatus.LATE, false)));
        assertNoAbsence(MONDAY);
        when(marks.findByStudentIdAndDay(CHILD, MONDAY)).thenReturn(List.of());
        assertNoAbsence(MONDAY);
        verify(excuses, never()).save(any());
    }

    @Test
    void onlyAnActiveParentOfThatStudentMaySubmit() {
        // ولیِ دانش‌آموزِ دیگر
        assertNotYourChild(OTHER_PARENT, CHILD);
        // ولیِ این دانش‌آموز ولی برای دانش‌آموزِ بیگانه
        assertNotYourChild(PARENT, STRANGER_CHILD);
        // لینک هست ولی نقشِ PARENT دیگر فعال نیست
        when(iam.findActiveRoleCodes(new UserId(PARENT), SCHOOL)).thenReturn(Set.of("TEACHER"));
        assertNotYourChild(PARENT, CHILD);
        verify(excuses, never()).save(any());
    }

    @Test
    void theNoteIsRequiredAndAtMostFiveHundredCharacters() {
        for (String bad : new String[]{null, "", "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> facade.submitExcuse(SCHOOL, CHILD, MONDAY, PARENT, bad))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidExcuse.class));
        }
        facade.submitExcuse(SCHOOL, CHILD, MONDAY, PARENT, "x".repeat(500));
        verify(excuses).save(any(ExcuseEntity.class));
    }

    // ---- تصمیمِ مدیر ----

    @Test
    void approvingSetsTheMarkExcusedInTheSameTransactionAndRecordsWhoDecided() {
        ExcuseEntity pending = excuse(ExcuseStatus.PENDING, "تب");
        MarkEntity absence = mark(CHILD, AttendanceStatus.ABSENT, false);
        when(excuses.findByIdAndSchoolId(9L, SCHOOL)).thenReturn(Optional.of(pending));
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(absence));

        ExcuseRow row = facade.decideExcuse(SCHOOL, 9L, ExcuseStatus.APPROVED, ADMIN);

        assertThat(absence.isExcused()).isTrue();
        verify(marks).save(absence);
        assertThat(pending.getStatus()).isEqualTo(ExcuseStatus.APPROVED);
        assertThat(pending.getDecidedByUserId()).isEqualTo(ADMIN);
        assertThat(pending.getDecidedAt()).isNotNull();
        assertThat(row.status()).isEqualTo(ExcuseStatus.APPROVED);
    }

    @Test
    void rejectingLeavesTheMarkAlone() {
        ExcuseEntity pending = excuse(ExcuseStatus.PENDING, "تب");
        MarkEntity absence = mark(CHILD, AttendanceStatus.ABSENT, false);
        when(excuses.findByIdAndSchoolId(9L, SCHOOL)).thenReturn(Optional.of(pending));
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(absence));

        facade.decideExcuse(SCHOOL, 9L, ExcuseStatus.REJECTED, ADMIN);

        assertThat(absence.isExcused()).isFalse();
        verify(marks, never()).save(any());
        assertThat(pending.getStatus()).isEqualTo(ExcuseStatus.REJECTED);
    }

    @Test
    void decidingTwiceUnknownIdsAndBadDecisionsAreRejected() {
        ExcuseEntity decided = excuse(ExcuseStatus.REJECTED, "تب");
        when(excuses.findByIdAndSchoolId(9L, SCHOOL)).thenReturn(Optional.of(decided));
        when(excuses.findByIdAndSchoolId(10L, SCHOOL)).thenReturn(Optional.of(excuse(ExcuseStatus.PENDING, "تب")));

        assertThatThrownBy(() -> facade.decideExcuse(SCHOOL, 9L, ExcuseStatus.APPROVED, ADMIN))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.ExcuseAlreadyDecided.class));
        assertThatThrownBy(() -> facade.decideExcuse(SCHOOL, 404L, ExcuseStatus.APPROVED, ADMIN))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.ExcuseNotFound.class));
        for (ExcuseStatus bad : new ExcuseStatus[]{null, ExcuseStatus.PENDING}) {
            assertThatThrownBy(() -> facade.decideExcuse(SCHOOL, 10L, bad, ADMIN))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidExcuse.class));
        }
        assertThat(decided.getStatus()).isEqualTo(ExcuseStatus.REJECTED);
    }

    // ---- رویدادِ «غیبتِ تازه» ----

    @Test
    void theDiffOfNewlyAbsentStudentsIgnoresUnchangedAndLate() {
        List<MarkInput> incoming = List.of(
                new MarkInput(1L, AttendanceStatus.ABSENT, false, null),
                new MarkInput(2L, AttendanceStatus.ABSENT, false, null),
                new MarkInput(3L, AttendanceStatus.LATE, false, null));

        // ۱ از قبل غایب بود؛ ۲ تازه؛ ۳ دیرآمده
        assertThat(AttendanceRules.newlyAbsent(Set.of(1L), incoming)).containsExactly(2L);
        // دیرآمده‌ی قبلی که حالا غایب شده «تازه» است (در مجموعه‌ی غایبانِ قبلی نیست)
        assertThat(AttendanceRules.newlyAbsent(Set.of(), incoming)).containsExactly(1L, 2L);
        assertThat(AttendanceRules.newlyAbsent(Set.of(1L, 2L), incoming)).isEmpty();
    }

    @Test
    void savingTheRollPublishesOnlyForStudentsNewlyMarkedAbsent() {
        // قبلاً: ۱ غایب، ۲ دیرآمده
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(
                mark(1L, AttendanceStatus.ABSENT, false), mark(2L, AttendanceStatus.LATE, false)));

        // ذخیره‌ی دوباره‌ی بی‌تغییر: هیچ رویدادی
        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of(
                new MarkInput(1L, AttendanceStatus.ABSENT, false, null),
                new MarkInput(2L, AttendanceStatus.LATE, false, null)));
        verify(events, never()).publishEvent(any(Object.class));

        // دیرآمده‌ی تازه رویداد ندارد، ولی دیرآمده‌ای که غایب شد دارد
        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of(
                new MarkInput(1L, AttendanceStatus.ABSENT, false, null),
                new MarkInput(2L, AttendanceStatus.ABSENT, false, null),
                new MarkInput(3L, AttendanceStatus.LATE, false, null)));
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        AbsenceRecordedEvent event = (AbsenceRecordedEvent) published.getValue();
        assertThat(event.studentId()).isEqualTo(2L);
        assertThat(event.schoolId()).isEqualTo(SCHOOL);
        assertThat(event.classId()).isEqualTo(CLASS_A);
        assertThat(event.day()).isEqualTo(MONDAY);
        assertThat(event.actorUserId()).isEqualTo(TEACHER);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void removingAnAbsenceAndAddingItBackPublishesAgain() {
        // حالتِ بعد از «حذف»: مُهری در پایگاه‌داده نیست؛ افزودنِ دوباره تازه است
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of());

        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of(new MarkInput(1L, AttendanceStatus.ABSENT, false, null)));

        verify(events).publishEvent(any(AbsenceRecordedEvent.class));
    }

    @Test
    void aFailedSaveNeverPublishes() {
        assertThatThrownBy(() -> facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER,
                List.of(new MarkInput(99L, AttendanceStatus.ABSENT, false, null))))
                .isInstanceOf(AttendanceException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    // ---- عذر و ذخیره‌ی دوباره‌ی حضور و غیاب ----

    @Test
    void aPendingExcuseIsRemovedWhenTheAbsenceIsRemovedButDecidedOnesStayAsHistory() {
        ExcuseEntity pending = excuse(ExcuseStatus.PENDING, "تب");
        ExcuseEntity approved = excuseFor(STRANGER_CHILD, ExcuseStatus.APPROVED);
        ExcuseEntity rejected = excuseFor(3L, ExcuseStatus.REJECTED);
        when(excuses.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(pending, approved, rejected));

        // هیچ‌کس دیگر غایب نیست
        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExcuseEntity>> removed = ArgumentCaptor.forClass(List.class);
        verify(excuses).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactly(pending);
    }

    @Test
    void aStillAbsentStudentKeepsAPendingExcuseAndAnApprovedExcuseWinsOverTheIncomingFlag() {
        ExcuseEntity pending = excuse(ExcuseStatus.PENDING, "تب");
        ExcuseEntity approved = excuseFor(STRANGER_CHILD, ExcuseStatus.APPROVED);
        when(excuses.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(pending, approved));

        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of(
                new MarkInput(CHILD, AttendanceStatus.ABSENT, false, null),
                new MarkInput(STRANGER_CHILD, AttendanceStatus.ABSENT, false, null)));

        verify(excuses, never()).deleteAll(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarkEntity>> written = ArgumentCaptor.forClass(List.class);
        verify(marks).saveAll(written.capture());
        assertThat(written.getValue()).filteredOn(m -> m.getStudentId().equals(CHILD))
                .singleElement().satisfies(m -> assertThat(m.isExcused()).isFalse());
        assertThat(written.getValue()).filteredOn(m -> m.getStudentId().equals(STRANGER_CHILD))
                .singleElement().satisfies(m -> assertThat(m.isExcused()).isTrue());
    }

    // ---- خواندنِ «من» ----

    @Test
    void myAttendanceListsOwnStudentAndLinkedChildrenOnlyWithExcuseState() {
        LocalDate to = MONDAY.plusDays(3);
        when(iam.findActiveRoleCodes(new UserId(PARENT), SCHOOL)).thenReturn(Set.of("PARENT", "STUDENT"));
        when(academic.findMyEnrollment(PARENT, SCHOOL)).thenReturn(Optional.of(
                new EnrollmentInfo(new StudentId(3L), SCHOOL, new ClassId(CLASS_A), "پنجم الف", null)));
        when(marks.findByStudentIdInAndDayBetween(any(), eq(MONDAY), eq(to))).thenReturn(List.of(
                mark(CHILD, AttendanceStatus.ABSENT, true), mark(3L, AttendanceStatus.LATE, false)));
        ExcuseEntity approved = excuse(ExcuseStatus.APPROVED, "تب");
        when(excuses.findByStudentIdInAndDayBetween(any(), eq(MONDAY), eq(to))).thenReturn(List.of(approved));

        List<MyStudentAttendance> mine = facade.listMyAttendance(SCHOOL, PARENT, MONDAY, to);

        assertThat(mine).extracting(MyStudentAttendance::studentId).containsExactly(3L, CHILD);
        assertThat(mine.get(0).own()).isTrue();
        assertThat(mine.get(0).days()).singleElement().satisfies(d -> {
            assertThat(d.status()).isEqualTo(AttendanceStatus.LATE);
            assertThat(d.excuse()).isNull();
        });
        assertThat(mine.get(1).own()).isFalse();
        assertThat(mine.get(1).days()).singleElement().satisfies(d -> {
            assertThat(d.excused()).isTrue();
            assertThat(d.excuse().status()).isEqualTo(ExcuseStatus.APPROVED);
            assertThat(d.excuse().note()).isEqualTo("تب");
        });
    }

    @Test
    void aParentOfAnUnrelatedStudentGetsNothingAndSoDoesSomeoneWithoutTheRoles() {
        assertThat(facade.listMyAttendance(SCHOOL, OTHER_PARENT, MONDAY, MONDAY)).isEmpty();
        assertThat(facade.listMyAttendance(SCHOOL, 777L, MONDAY, MONDAY)).isEmpty();
        verify(marks, never()).findByStudentIdInAndDayBetween(any(), any(), any());
    }

    @Test
    void myAttendanceRangeIsRequiredAndCappedLikeTheReport() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        for (LocalDate[] range : new LocalDate[][]{{null, from}, {from, null}, {from.plusDays(1), from},
                {from, from.plusDays(366)}}) {
            assertThatThrownBy(() -> facade.listMyAttendance(SCHOOL, PARENT, range[0], range[1]))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidRange.class));
        }
        assertThat(facade.listMyAttendance(SCHOOL, PARENT, from, from.plusDays(365))).hasSize(1);
    }

    // ---- کمکی‌ها ----

    private void assertNoAbsence(LocalDate day) {
        assertThatThrownBy(() -> facade.submitExcuse(SCHOOL, CHILD, day, PARENT, "تب"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.NoAbsenceToExcuse.class));
    }

    private void assertNotYourChild(Long user, Long studentId) {
        assertThatThrownBy(() -> facade.submitExcuse(SCHOOL, studentId, MONDAY, user, "تب"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.NotYourChild.class));
    }

    private static ExcuseEntity excuse(ExcuseStatus status, String note) {
        return excuseFor(CHILD, status, note);
    }

    private static ExcuseEntity excuseFor(Long studentId, ExcuseStatus status) {
        return excuseFor(studentId, status, "تب");
    }

    private static ExcuseEntity excuseFor(Long studentId, ExcuseStatus status, String note) {
        ExcuseEntity e = new ExcuseEntity(SCHOOL, CLASS_A, MONDAY, studentId, note, PARENT, Instant.EPOCH);
        e.setStatus(status);
        return e;
    }

    private static MarkEntity mark(Long studentId, AttendanceStatus status, boolean excused) {
        return new MarkEntity(SCHOOL, CLASS_A, MONDAY, studentId, status, excused, null, TEACHER, Instant.EPOCH);
    }

    private static StudentInfo student(Long id) {
        return new StudentInfo(new StudentId(id), 1000 + id, SCHOOL, new ClassId(CLASS_A));
    }

    private static MembershipInfo member(Long userId, String name) {
        return new MembershipInfo(userId, new UserId(userId), "u" + userId + "@x.test", name, SCHOOL,
                "STUDENT", true, true);
    }
}
