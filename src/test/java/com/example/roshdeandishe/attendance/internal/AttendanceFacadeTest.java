package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.ClassInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.ClassAbsentees;
import com.example.roshdeandishe.attendance.AttendanceFacade.ClassDay;
import com.example.roshdeandishe.attendance.AttendanceFacade.DaySummary;
import com.example.roshdeandishe.attendance.AttendanceFacade.MarkInput;
import com.example.roshdeandishe.attendance.AttendanceFacade.ReportRow;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * قوانینِ حضور و غیاب (openspec/changes/attendance.md) بدون Spring و Docker: اعتبارسنجیِ روز (روزِ درسی، تقویم،
 * آینده)، عضویتِ دانش‌آموز در کلاس (۴۰۹)، جایگزینیِ یک‌جای مُهرها، «کلاسِ خود»/«روزِ گذشته»، و حساب‌های رقمِ
 * مدیر و گزارش از روی داده‌ی ساختگی. ایندکس‌های یکتا و SQL در تست یکپارچه می‌آیند.
 */
class AttendanceFacadeTest {

    private static final Long SCHOOL = 5L;
    private static final Long CLASS_A = 7L;
    private static final Long CLASS_B = 8L;
    private static final Long CLASS_C = 9L;
    private static final Long TEACHER = 90L;
    private static final Long OTHER_TEACHER = 91L;
    private static final String TEHRAN = "Asia/Tehran";
    /** دوشنبه؛ روزِ درسیِ هر دو الگوی هفته و در گذشته‌ی دور. */
    private static final LocalDate MONDAY = LocalDate.of(2024, 10, 7);
    private static final LocalDate FRIDAY = LocalDate.of(2024, 10, 4);

    private RollRepository rolls;
    private MarkRepository marks;
    private RollStudentRepository rollStudents;
    private AcademicFacade academic;
    private TimetableFacade timetable;
    private SchoolFacade school;
    private IamFacade iam;
    private AttendanceFacadeImpl facade;

    @BeforeEach
    void setUp() {
        rolls = mock(RollRepository.class);
        marks = mock(MarkRepository.class);
        rollStudents = mock(RollStudentRepository.class);
        academic = mock(AcademicFacade.class);
        timetable = mock(TimetableFacade.class);
        school = mock(SchoolFacade.class);
        iam = mock(IamFacade.class);
        facade = new AttendanceFacadeImpl(rolls, marks, rollStudents, mock(ExcuseRepository.class), academic, timetable, school, iam,
                mock(org.springframework.context.ApplicationEventPublisher.class));

        when(school.findSchool(new SchoolId(SCHOOL))).thenReturn(Optional.of(schoolInfo()));
        when(school.findCurrentAcademicYear(new SchoolId(SCHOOL))).thenReturn(Optional.empty());
        when(academic.findClass(SCHOOL, CLASS_A)).thenReturn(Optional.of(classInfo(CLASS_A, "پنجم الف", null)));
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(CLASS_A, students(CLASS_A, 1, 3)));
        when(iam.findMembershipsBySchool(SCHOOL)).thenReturn(List.of(
                member(1001L, "علی رضایی"), member(1002L, "محمد احمدی"), member(1003L, "رضا علوی")));
    }

    // ---- ثبت ----

    @Test
    void saveReplacesTheClassDayInOneGoWithTheOldMarksDeletedFirst() {
        ClassDay saved = facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of(
                new MarkInput(1L, AttendanceStatus.ABSENT, true, "  سرماخوردگی "),
                new MarkInput(2L, AttendanceStatus.LATE, true, "  ")));

        InOrder order = inOrder(rolls, marks);
        order.verify(rolls).lockClassDay(anyInt(), anyInt());
        order.verify(marks).deleteByClassAndDay(CLASS_A, MONDAY);
        order.verify(rolls).save(any(RollEntity.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarkEntity>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(marks).saveAll(captor.capture());
        List<MarkEntity> written = captor.getValue();
        assertThat(written).hasSize(2);
        MarkEntity absent = written.get(0);
        assertThat(absent.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(absent.isExcused()).isTrue();
        assertThat(absent.getNote()).isEqualTo("سرماخوردگی");
        // «موجه» فقط برای غیبت معنا دارد؛ یادداشتِ خالی ذخیره نمی‌شود
        MarkEntity late = written.get(1);
        assertThat(late.isExcused()).isFalse();
        assertThat(late.getNote()).isNull();
        assertThat(saved.classId()).isEqualTo(CLASS_A);
    }

    @Test
    void anEmptyListMeansEveryoneIsPresentAndStillRecordsTheRoll() {
        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of());

        verify(marks).deleteByClassAndDay(CLASS_A, MONDAY);
        verify(rolls).save(any(RollEntity.class));
    }

    @Test
    void savingAgainReusesTheExistingRollRow() {
        RollEntity existing = new RollEntity(SCHOOL, CLASS_A, MONDAY, OTHER_TEACHER, Instant.EPOCH);
        when(rolls.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(Optional.of(existing));

        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of());

        verify(rolls).save(existing);
        assertThat(existing.getTakenByUserId()).isEqualTo(TEACHER);
        assertThat(existing.getTakenAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void aNonSchoolWeekdayIsRejectedAndNothingIsWritten() {
        assertThatThrownBy(() -> facade.saveRoll(SCHOOL, CLASS_A, FRIDAY, TEACHER, List.of()))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidAttendanceDay.class));
        verifyNothingWritten();
    }

    @Test
    void aClosedCalendarDayIsRejected() {
        when(school.isClosedDay(new SchoolId(SCHOOL), MONDAY)).thenReturn(true);

        assertThatThrownBy(() -> facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of()))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidAttendanceDay.class));
        verifyNothingWritten();
    }

    @Test
    void aFutureDayIsRejected() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of(TEHRAN)).plusDays(1);

        assertThatThrownBy(() -> facade.saveRoll(SCHOOL, CLASS_A, tomorrow, TEACHER, List.of()))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidAttendanceDay.class));
        verifyNothingWritten();
    }

    @Test
    void aStudentWhoIsNotInTheClassIsAConflictAndNothingIsWritten() {
        assertThatThrownBy(() -> facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER,
                List.of(new MarkInput(99L, AttendanceStatus.ABSENT, false, null))))
                .isInstanceOfSatisfying(AttendanceException.class, e -> {
                    assertThat(e.error()).isInstanceOf(AttendanceError.StudentNotInClass.class);
                    assertThat(((AttendanceError.StudentNotInClass) e.error()).studentId()).isEqualTo(99L);
                });
        verifyNothingWritten();
    }

    @Test
    void aStudentWhoLeftButAlreadyHasAMarkThatDayCanBeKeptOnAResave() {
        MarkEntity old = new MarkEntity(SCHOOL, CLASS_A, MONDAY, 99L, AttendanceStatus.ABSENT, false, null,
                TEACHER, Instant.EPOCH);
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(old));

        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER,
                List.of(new MarkInput(99L, AttendanceStatus.ABSENT, false, null)));

        verify(marks).saveAll(any());
    }

    @Test
    void invalidMarksAreRejectedBeforeAnythingIsWritten() {
        List<List<MarkInput>> bad = new ArrayList<>();
        bad.add(null);
        bad.add(List.of(new MarkInput(1L, null, false, null)));
        bad.add(List.of(new MarkInput(null, AttendanceStatus.ABSENT, false, null)));
        bad.add(List.of(new MarkInput(1L, AttendanceStatus.ABSENT, false, null),
                new MarkInput(1L, AttendanceStatus.LATE, false, null)));
        bad.add(List.of(new MarkInput(1L, AttendanceStatus.ABSENT, false, "x".repeat(256))));

        for (List<MarkInput> input : bad) {
            assertThatThrownBy(() -> facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, input))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidMark.class));
        }
        verifyNothingWritten();
    }

    @Test
    void aNoteOfExactlyTheMaximumLengthIsAccepted() {
        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER,
                List.of(new MarkInput(1L, AttendanceStatus.LATE, false, "x".repeat(255))));

        verify(marks).saveAll(any());
    }

    @Test
    void aClassOfAnotherSchoolIsNotFound() {
        when(academic.findClass(SCHOOL, 500L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.saveRoll(SCHOOL, 500L, MONDAY, TEACHER, List.of()))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.ClassNotFound.class));
        verifyNothingWritten();
    }

    @Test
    void aClassOfAnotherYearCannotBeRecordedButAnUnattributedOneCan() {
        when(school.findCurrentAcademicYear(new SchoolId(SCHOOL)))
                .thenReturn(Optional.of(new AcademicYearInfo(new AcademicYearId(1L), "1405-1406", 1405)));
        when(academic.findClass(SCHOOL, CLASS_B)).thenReturn(Optional.of(classInfo(CLASS_B, "ششم", "1404-1405")));

        assertThatThrownBy(() -> facade.saveRoll(SCHOOL, CLASS_B, MONDAY, TEACHER, List.of()))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.ClassNotInCurrentYear.class));
        verifyNothingWritten();

        // کلاسِ بدون سال (CLASS_A) همچنان ثبت می‌شود
        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of());
        verify(rolls).save(any(RollEntity.class));
    }

    // ---- «کلاسِ خود» و «روزِ گذشته» ----

    @Test
    void aTeacherMayRecordOnlyForClassesTheyTeach() {
        when(timetable.listClassesTaughtBy(SCHOOL, TEACHER)).thenReturn(List.of(classInfo(CLASS_A, "پنجم الف", null)));

        facade.requireMayAccessClass(SCHOOL, CLASS_A, TEACHER, false);

        when(academic.findClass(SCHOOL, CLASS_B)).thenReturn(Optional.of(classInfo(CLASS_B, "ششم", null)));
        assertThatThrownBy(() -> facade.requireMayAccessClass(SCHOOL, CLASS_B, TEACHER, false))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.NotYourClass.class));
    }

    @Test
    void recordAnyMayRecordForAnyClassButStillOnlyOfThisSchool() {
        when(academic.findClass(SCHOOL, CLASS_B)).thenReturn(Optional.of(classInfo(CLASS_B, "ششم", null)));

        facade.requireMayAccessClass(SCHOOL, CLASS_B, 1L, true);

        when(academic.findClass(SCHOOL, 500L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.requireMayAccessClass(SCHOOL, 500L, 1L, true))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.ClassNotFound.class));
    }

    @Test
    void withoutEditPastOnlyTodayMayBeEdited() {
        LocalDate today = LocalDate.now(ZoneId.of(TEHRAN));

        facade.requireMayEditDay(SCHOOL, today, false);
        assertThatThrownBy(() -> facade.requireMayEditDay(SCHOOL, today.minusDays(1), false))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.error()).isInstanceOf(AttendanceError.PastEditNotAllowed.class));
        // با ATTENDANCE_EDIT_PAST هر روزِ گذشته مجاز است
        facade.requireMayEditDay(SCHOOL, today.minusDays(30), true);
    }

    @Test
    void recordableClassesForATeacherAreTheirOwnAndForAnAdminAllOfTheCurrentYear() {
        when(timetable.listClassesTaughtBy(SCHOOL, TEACHER)).thenReturn(List.of(classInfo(CLASS_A, "پنجم الف", null)));
        when(academic.listClasses(SCHOOL)).thenReturn(List.of(
                classInfo(CLASS_A, "پنجم الف", null), classInfo(CLASS_B, "ششم", null)));
        when(rolls.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(List.of(roll(CLASS_A, MONDAY)));

        var mine = facade.listRecordableClasses(SCHOOL, TEACHER, false, MONDAY);
        var all = facade.listRecordableClasses(SCHOOL, 1L, true, MONDAY);

        assertThat(mine).extracting(c -> c.classId()).containsExactly(CLASS_A);
        assertThat(mine.get(0).taken()).isTrue();
        assertThat(all).extracting(c -> c.classId()).containsExactlyInAnyOrder(CLASS_A, CLASS_B);
        assertThat(all.stream().filter(c -> c.classId().equals(CLASS_B)).findFirst().orElseThrow().taken()).isFalse();
    }

    // ---- خواندنِ یک کلاس ----

    @Test
    void theClassDayShowsEveryRosterStudentWithTheirMarkAndWhetherTheRollWasTaken() {
        when(rolls.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(Optional.of(roll(CLASS_A, MONDAY)));
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(
                mark(CLASS_A, MONDAY, 2L, AttendanceStatus.ABSENT, true)));

        ClassDay day = facade.getClassDay(SCHOOL, CLASS_A, MONDAY);

        assertThat(day.taken()).isTrue();
        assertThat(day.recordable()).isTrue();
        assertThat(day.students()).hasSize(3);
        var marked = day.students().stream().filter(s -> s.studentId().equals(2L)).findFirst().orElseThrow();
        assertThat(marked.status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(marked.excused()).isTrue();
        assertThat(marked.studentName()).isEqualTo("محمد احمدی");
        assertThat(day.students().stream().filter(s -> s.status() == null)).hasSize(2);
    }

    @Test
    void aNonSchoolDayStillReadsButIsNotRecordable() {
        ClassDay day = facade.getClassDay(SCHOOL, CLASS_A, FRIDAY);

        assertThat(day.taken()).isFalse();
        assertThat(day.recordable()).isFalse();
        assertThat(day.students()).hasSize(3);
    }

    // ---- رقمِ مدیر و غایبین ----

    @Test
    void theSummaryCountsOnlyTakenClassesAndRoundsDown() {
        stubThreeClassesWithTwoTaken();

        DaySummary summary = facade.summaryForDay(SCHOOL, MONDAY);

        assertThat(summary.schoolDay()).isTrue();
        assertThat(summary.classesTotal()).isEqualTo(3);
        assertThat(summary.classesTaken()).isEqualTo(2);
        // الف: ۳ نفر، ۱ غایبِ موجه + ۱ دیرآمده؛ ب: ۳ نفر، ۱ غایب؛ ج: ثبت نشده (۳ نفر، خارج)
        assertThat(summary.expected()).isEqualTo(6);
        assertThat(summary.absent()).isEqualTo(2);
        assertThat(summary.excusedAbsent()).isEqualTo(1);
        assertThat(summary.presentPercent()).isEqualTo(66);
    }

    @Test
    void theSummaryOfANonSchoolDayHasNoFigure() {
        DaySummary summary = facade.summaryForDay(SCHOOL, FRIDAY);

        assertThat(summary.schoolDay()).isFalse();
        assertThat(summary.presentPercent()).isNull();
        assertThat(summary.classesTotal()).isZero();
    }

    @Test
    void absenteesListEveryClassWithUntakenOnesMarkedAsNotRecorded() {
        stubThreeClassesWithTwoTaken();

        List<ClassAbsentees> result = facade.absenteesForDay(SCHOOL, MONDAY);

        assertThat(result).hasSize(3);
        ClassAbsentees a = byClass(result, CLASS_A);
        assertThat(a.taken()).isTrue();
        assertThat(a.absent()).isEqualTo(1);
        assertThat(a.late()).isEqualTo(1);
        assertThat(a.excusedAbsent()).isEqualTo(1);
        assertThat(a.expected()).isEqualTo(3);
        assertThat(a.students()).hasSize(2);
        ClassAbsentees c = byClass(result, CLASS_C);
        assertThat(c.taken()).isFalse();
        assertThat(c.students()).isEmpty();
        assertThat(facade.absenteesForDay(SCHOOL, FRIDAY)).isEmpty();
    }

    // ---- گزارش ----

    @Test
    void theReportCountsDaysOnWhichTheClassHadARollAndTreatsLateAsPresent() {
        LocalDate d1 = LocalDate.of(2024, 10, 5);
        LocalDate d2 = LocalDate.of(2024, 10, 6);
        LocalDate d3 = LocalDate.of(2024, 10, 7);
        when(rolls.findBySchoolIdAndDayBetween(SCHOOL, d1, d3)).thenReturn(List.of(
                roll(CLASS_A, d1), roll(CLASS_A, d2), roll(CLASS_A, d3)));
        when(marks.findBySchoolIdAndDayBetween(SCHOOL, d1, d3)).thenReturn(List.of(
                mark(CLASS_A, d1, 1L, AttendanceStatus.ABSENT, false),
                mark(CLASS_A, d2, 1L, AttendanceStatus.LATE, false)));
        when(academic.findClass(SCHOOL, CLASS_A)).thenReturn(Optional.of(classInfo(CLASS_A, "پنجم الف", null)));

        List<ReportRow> rows = facade.report(SCHOOL, d1, d3, null, null);

        assertThat(rows).hasSize(3);
        ReportRow ali = rows.stream().filter(r -> r.studentId().equals(1L)).findFirst().orElseThrow();
        assertThat(ali.absentDays()).isEqualTo(1);
        assertThat(ali.lateDays()).isEqualTo(1);
        assertThat(ali.presentDays()).isEqualTo(2);
        ReportRow other = rows.stream().filter(r -> r.studentId().equals(2L)).findFirst().orElseThrow();
        assertThat(other.absentDays()).isZero();
        assertThat(other.presentDays()).isEqualTo(3);
        assertThat(other.className()).isEqualTo("پنجم الف");
    }

    @Test
    void theReportFiltersByNameAndSkipsClassesWithoutARollInTheRange() {
        LocalDate d1 = LocalDate.of(2024, 10, 5);
        when(rolls.findBySchoolIdAndDayBetween(SCHOOL, d1, d1)).thenReturn(List.of(roll(CLASS_A, d1)));
        when(marks.findBySchoolIdAndDayBetween(SCHOOL, d1, d1)).thenReturn(List.of());

        List<ReportRow> byName = facade.report(SCHOOL, d1, d1, null, " رضا ");

        // «علی رضایی» و «رضا علوی» شاملِ «رضا» هستند، «محمد احمدی» نه
        assertThat(byName).extracting(ReportRow::studentName).containsExactlyInAnyOrder("علی رضایی", "رضا علوی");

        // بازه‌ای که هیچ حضور و غیابی در آن گرفته نشده: خالی
        LocalDate other = LocalDate.of(2024, 11, 2);
        assertThat(facade.report(SCHOOL, other, other, null, null)).isEmpty();
    }

    @Test
    void theReportCanBeNarrowedToOneClass() {
        LocalDate d1 = LocalDate.of(2024, 10, 5);
        when(rolls.findBySchoolIdAndDayBetween(SCHOOL, d1, d1)).thenReturn(List.of(
                roll(CLASS_A, d1), roll(CLASS_B, d1)));

        List<ReportRow> rows = facade.report(SCHOOL, d1, d1, CLASS_A, null);

        assertThat(rows).extracting(ReportRow::classId).containsOnly(CLASS_A);
    }

    @Test
    void theReportRangeIsValidatedAndCapped() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        for (LocalDate[] range : new LocalDate[][]{
                {null, from}, {from, null}, {from.plusDays(1), from}, {from, from.plusDays(366)}}) {
            assertThatThrownBy(() -> facade.report(SCHOOL, range[0], range[1], null, null))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            e -> assertThat(e.error()).isInstanceOf(AttendanceError.InvalidRange.class));
        }
        // ۳۶۶ روز دقیقاً مجاز است
        assertThat(facade.report(SCHOOL, from, from.plusDays(365), null, null)).isEmpty();
    }

    // ---- فهرستِ ذخیره‌شده‌ی هر roll (snapshot) ----

    @Test
    void aStudentEnrolledAfterARollIsNotExpectedInThatRollsSummaryOrClassDay() {
        // فهرستِ زنده‌ی کلاس اکنون ۴ نفر است، ولی roll با ۳ نفر گرفته شده
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(CLASS_A, students(CLASS_A, 1, 4)));
        when(academic.listClasses(SCHOOL)).thenReturn(List.of(classInfo(CLASS_A, "الف", null)));
        when(rolls.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(List.of(roll(CLASS_A, MONDAY)));
        when(rolls.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(Optional.of(roll(CLASS_A, MONDAY)));
        when(marks.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(List.of());
        List<RollStudentEntity> snapshot = snap(CLASS_A, MONDAY, 1, 2, 3);
        when(rollStudents.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(snapshot);
        when(rollStudents.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(snapshot);

        assertThat(facade.summaryForDay(SCHOOL, MONDAY).expected()).isEqualTo(3);
        assertThat(byClass(facade.absenteesForDay(SCHOOL, MONDAY), CLASS_A).expected()).isEqualTo(3);
        assertThat(facade.getClassDay(SCHOOL, CLASS_A, MONDAY).students())
                .extracting(s -> s.studentId()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void aClassDayWithoutARollStillUsesTheLiveRoster() {
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(CLASS_A, students(CLASS_A, 1, 4)));

        assertThat(facade.getClassDay(SCHOOL, CLASS_A, MONDAY).students()).hasSize(4);
    }

    @Test
    void aRollWithoutSnapshotRowsFallsBackToTheLiveRoster() {
        // rollِ پیش از V14: ردیفی در roll_students ندارد
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(CLASS_A, students(CLASS_A, 1, 4)));
        when(academic.listClasses(SCHOOL)).thenReturn(List.of(classInfo(CLASS_A, "الف", null)));
        when(rolls.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(List.of(roll(CLASS_A, MONDAY)));
        when(rolls.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(Optional.of(roll(CLASS_A, MONDAY)));
        when(marks.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(List.of());

        assertThat(facade.summaryForDay(SCHOOL, MONDAY).expected()).isEqualTo(4);
        assertThat(facade.getClassDay(SCHOOL, CLASS_A, MONDAY).students()).hasSize(4);
    }

    @Test
    void theReportBaseOfAStudentExcludesRollsTakenBeforeTheyJoinedTheClass() {
        LocalDate d1 = LocalDate.of(2024, 10, 5);
        LocalDate d2 = LocalDate.of(2024, 10, 6);
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(CLASS_A, students(CLASS_A, 1, 4)));
        when(rolls.findBySchoolIdAndDayBetween(SCHOOL, d1, d2)).thenReturn(List.of(
                roll(CLASS_A, d1), roll(CLASS_A, d2)));
        when(marks.findBySchoolIdAndDayBetween(SCHOOL, d1, d2)).thenReturn(List.of());
        List<RollStudentEntity> snapshots = new ArrayList<>(snap(CLASS_A, d1, 1, 2, 3));
        snapshots.addAll(snap(CLASS_A, d2, 1, 2, 3, 4));
        when(rollStudents.findBySchoolIdAndDayBetween(SCHOOL, d1, d2)).thenReturn(snapshots);

        List<ReportRow> rows = facade.report(SCHOOL, d1, d2, null, null);

        assertThat(rows).hasSize(4);
        assertThat(rows.stream().filter(r -> r.studentId().equals(1L)).findFirst().orElseThrow().presentDays())
                .isEqualTo(2);
        // دانش‌آموزِ ۴ فقط در roll دومِ بعد از ثبت‌نامش حساب می‌شود
        assertThat(rows.stream().filter(r -> r.studentId().equals(4L)).findFirst().orElseThrow().presentDays())
                .isEqualTo(1);
    }

    @Test
    void aTransferredStudentKeepsTheEarlierUnmarkedDayInTheOldClassAndAppearsUnderBothClasses() {
        LocalDate d1 = LocalDate.of(2024, 10, 5);
        LocalDate d2 = LocalDate.of(2024, 10, 6);
        when(academic.findClass(SCHOOL, CLASS_B)).thenReturn(Optional.of(classInfo(CLASS_B, "ششم", null)));
        // دانش‌آموزِ ۳ پس از roll روزِ اول به کلاس B منتقل شده است
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(
                CLASS_A, students(CLASS_A, 1, 2),
                CLASS_B, List.of(studentIn(3, CLASS_B), studentIn(4, CLASS_B))));
        when(academic.findStudentInfo(3L)).thenReturn(Optional.of(studentIn(3, CLASS_B)));
        when(rolls.findBySchoolIdAndDayBetween(SCHOOL, d1, d2)).thenReturn(List.of(
                roll(CLASS_A, d1), roll(CLASS_B, d2)));
        when(marks.findBySchoolIdAndDayBetween(SCHOOL, d1, d2)).thenReturn(List.of());
        // rollِ B بدونِ snapshot (پیش از V14) و با فهرستِ زنده حساب می‌شود
        when(rollStudents.findBySchoolIdAndDayBetween(SCHOOL, d1, d2)).thenReturn(snap(CLASS_A, d1, 1, 2, 3));

        List<ReportRow> rows = facade.report(SCHOOL, d1, d2, null, null);

        ReportRow inOld = rows.stream()
                .filter(r -> r.studentId().equals(3L) && r.classId().equals(CLASS_A)).findFirst().orElseThrow();
        ReportRow inNew = rows.stream()
                .filter(r -> r.studentId().equals(3L) && r.classId().equals(CLASS_B)).findFirst().orElseThrow();
        assertThat(inOld.presentDays()).isEqualTo(1);
        assertThat(inOld.studentName()).isEqualTo("رضا علوی");
        assertThat(inNew.presentDays()).isEqualTo(1);
        assertThat(rows).hasSize(5);
    }

    @Test
    void savingARollReplacesItsSnapshotWithTheLiveRosterPlusEveryMarkedStudent() {
        // دانش‌آموزِ ۹۹ دیگر در فهرستِ کلاس نیست ولی مُهر دارد
        MarkEntity old = new MarkEntity(SCHOOL, CLASS_A, MONDAY, 99L, AttendanceStatus.ABSENT, false, null,
                TEACHER, Instant.EPOCH);
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenReturn(List.of(old));

        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER,
                List.of(new MarkInput(99L, AttendanceStatus.ABSENT, false, null)));

        InOrder order = inOrder(rolls, rollStudents);
        order.verify(rolls).lockClassDay(anyInt(), anyInt());
        order.verify(rollStudents).deleteByClassAndDay(CLASS_A, MONDAY);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RollStudentEntity>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(rollStudents).saveAll(captor.capture());
        assertThat(captor.getValue().stream().map(RollStudentEntity::getStudentId).collect(Collectors.toSet()))
                .isEqualTo(Set.of(1L, 2L, 3L, 99L));
        assertThat(captor.getValue()).allSatisfy(r -> {
            assertThat(r.getClassId()).isEqualTo(CLASS_A);
            assertThat(r.getDay()).isEqualTo(MONDAY);
            assertThat(r.getSchoolId()).isEqualTo(SCHOOL);
        });
    }

    @Test
    void theLockIsTakenBeforeAnyReadThatFeedsTheDiffOrTheSnapshot() {
        AtomicBoolean locked = new AtomicBoolean();
        List<Boolean> lockedAtRead = new ArrayList<>();
        when(rolls.lockClassDay(anyInt(), anyInt())).thenAnswer(inv -> {
            locked.set(true);
            return 1;
        });
        when(marks.findByClassIdAndDay(CLASS_A, MONDAY)).thenAnswer(inv -> {
            lockedAtRead.add(locked.get());
            return List.of();
        });
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenAnswer(inv -> {
            lockedAtRead.add(locked.get());
            return Map.of(CLASS_A, students(CLASS_A, 1, 3));
        });

        facade.saveRoll(SCHOOL, CLASS_A, MONDAY, TEACHER, List.of());

        assertThat(lockedAtRead).isNotEmpty().containsOnly(true);
        InOrder order = inOrder(rolls, marks);
        order.verify(rolls).lockClassDay(anyInt(), anyInt());
        order.verify(marks).deleteByClassAndDay(CLASS_A, MONDAY);
    }

    // ---- کمکی‌ها ----

    private static List<RollStudentEntity> snap(Long classId, LocalDate day, long... studentIds) {
        List<RollStudentEntity> list = new ArrayList<>();
        for (long id : studentIds) {
            list.add(new RollStudentEntity(SCHOOL, classId, day, id));
        }
        return list;
    }

    private static StudentInfo studentIn(long id, Long classId) {
        return new StudentInfo(new StudentId(id), 1000 + id, SCHOOL, new ClassId(classId));
    }

    private void stubThreeClassesWithTwoTaken() {
        when(academic.listClasses(SCHOOL)).thenReturn(List.of(
                classInfo(CLASS_A, "الف", null), classInfo(CLASS_B, "ب", null), classInfo(CLASS_C, "ج", null)));
        when(rolls.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(List.of(
                roll(CLASS_A, MONDAY), roll(CLASS_B, MONDAY)));
        when(marks.findBySchoolIdAndDay(SCHOOL, MONDAY)).thenReturn(List.of(
                mark(CLASS_A, MONDAY, 1L, AttendanceStatus.ABSENT, true),
                mark(CLASS_A, MONDAY, 2L, AttendanceStatus.LATE, false),
                mark(CLASS_B, MONDAY, 4L, AttendanceStatus.ABSENT, false)));
        when(academic.listStudentsByClass(eq(SCHOOL), any())).thenReturn(Map.of(
                CLASS_A, students(CLASS_A, 1, 3),
                CLASS_B, students(CLASS_B, 4, 3),
                CLASS_C, students(CLASS_C, 7, 3)));
    }

    private void verifyNothingWritten() {
        verify(marks, never()).deleteByClassAndDay(anyLong(), any());
        verify(marks, never()).saveAll(any());
        verify(rolls, never()).save(any());
    }

    private static ClassAbsentees byClass(List<ClassAbsentees> list, Long classId) {
        return list.stream().filter(c -> c.classId().equals(classId)).findFirst().orElseThrow();
    }

    private static SchoolInfo schoolInfo() {
        return new SchoolInfo(new SchoolId(SCHOOL), "مدرسه", SchoolStatus.ACTIVE, TEHRAN, "fa-IR",
                SchoolLevel.ELEMENTARY, null, WeekStart.SATURDAY);
    }

    private static ClassInfo classInfo(Long id, String name, String year) {
        return new ClassInfo(new ClassId(id), SCHOOL, name, TEACHER, null, 5, GradingScheme.NUMERIC, year);
    }

    /** شناسه‌های {@code firstId..firstId+count-1}؛ کاربر = ۱۰۰۰ + شناسه. */
    private static List<StudentInfo> students(Long classId, int firstId, int count) {
        List<StudentInfo> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = firstId + i;
            list.add(new StudentInfo(new StudentId(id), 1000 + id, SCHOOL, new ClassId(classId)));
        }
        return list;
    }

    private static MembershipInfo member(Long userId, String name) {
        return new MembershipInfo(userId, new UserId(userId), "u" + userId + "@x.test", name, SCHOOL,
                "STUDENT", true, true);
    }

    private static RollEntity roll(Long classId, LocalDate day) {
        return new RollEntity(SCHOOL, classId, day, TEACHER, Instant.EPOCH);
    }

    private static MarkEntity mark(Long classId, LocalDate day, Long studentId, AttendanceStatus status,
                                   boolean excused) {
        return new MarkEntity(SCHOOL, classId, day, studentId, status, excused, null, TEACHER, Instant.EPOCH);
    }
}
