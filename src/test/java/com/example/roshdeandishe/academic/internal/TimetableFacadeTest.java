package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.TimetableDay;
import com.example.roshdeandishe.academic.TimetableFacade.ActivityInfo;
import com.example.roshdeandishe.academic.TimetableFacade.EntryInfo;
import com.example.roshdeandishe.academic.TimetableFacade.PeriodInfo;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * قوانینِ برنامهٔ کلاسی (openspec/changes/class-timetable.md) بدون Spring و Docker:
 * زنگ (پایان بعد از شروع، هم‌پوشانی، حذفِ زنگِ پُر)، فعالیت (یکتایی نام، پیش‌فرضِ
 * پلتفرم)، خانه (دقیقاً یکی از درس/فعالیت، هم‌پایه بودنِ درس، معلمِ فعال، رزرو
 * دوباره‌ی معلم). ایندکس‌های یکتا و مهاجرت در تست یکپارچه‌ی CI می‌آیند.
 */
class TimetableFacadeTest {

    private static final Long SCHOOL = 5L;
    private static final Long OTHER_SCHOOL = 6L;
    private static final Long CLASS_ID = 7L;
    private static final Long OTHER_CLASS = 8L;
    private static final Long PERIOD = 40L;
    private static final Long TEACHER = 90L;
    private static final int GRADE_LEVEL = 5;
    private static final String YEAR = "1405-1406";

    private TimetablePeriodRepository periods;
    private TimetableEntryRepository entries;
    private ActivityRepository activities;
    private CourseRepository courses;
    private ClassRepository classes;
    private IamFacade iam;
    private SchoolFacade school;
    private TimetableFacadeImpl facade;

    @BeforeEach
    void setUp() {
        periods = mock(TimetablePeriodRepository.class);
        entries = mock(TimetableEntryRepository.class);
        activities = mock(ActivityRepository.class);
        courses = mock(CourseRepository.class);
        classes = mock(ClassRepository.class);
        iam = mock(IamFacade.class);
        school = mock(SchoolFacade.class);
        facade = new TimetableFacadeImpl(periods, entries, activities, courses, classes, iam, school);

        when(school.findSchool(new SchoolId(SCHOOL))).thenReturn(Optional.of(schoolInfo(WeekStart.SATURDAY)));
        when(school.listAcademicYears()).thenReturn(List.of(new AcademicYearInfo(new AcademicYearId(1L), YEAR, 1405,
                java.time.LocalDate.of(2026, 9, 23), java.time.LocalDate.of(2027, 6, 21))));
        when(classes.findById(CLASS_ID)).thenReturn(Optional.of(classEntity(CLASS_ID, SCHOOL)));
        when(classes.findById(OTHER_CLASS)).thenReturn(Optional.of(classEntity(OTHER_CLASS, SCHOOL)));
        when(periods.findById(PERIOD)).thenReturn(Optional.of(period(PERIOD, SCHOOL, 1, "08:00", "08:45")));
        when(periods.save(any(TimetablePeriodEntity.class))).thenAnswer(i -> {
            TimetablePeriodEntity p = i.getArgument(0);
            if (p.getId() == null) {
                ReflectionTestUtils.setField(p, "id", 41L);
            }
            return p;
        });
        when(entries.saveAndFlush(any(TimetableEntryEntity.class))).thenAnswer(i -> {
            TimetableEntryEntity e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(500L);
            }
            return e;
        });
        when(activities.saveAndFlush(any(ActivityEntity.class))).thenAnswer(i -> {
            ActivityEntity a = i.getArgument(0);
            if (a.getId() == null) {
                a.setId(60L);
            }
            return a;
        });
    }

    private static SchoolInfo schoolInfo(WeekStart weekStart) {
        return new SchoolInfo(new SchoolId(SCHOOL), "مدرسه", SchoolStatus.ACTIVE, "UTC", "fa-IR",
                SchoolLevel.ELEMENTARY, null, weekStart);
    }

    private static ClassEntity classEntity(Long id, Long schoolId) {
        ClassEntity c = new ClassEntity(schoolId, "پنجم", null, "الف", GRADE_LEVEL, GradingScheme.DESCRIPTIVE);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static TimetablePeriodEntity period(Long id, Long schoolId, int position, String start, String end) {
        TimetablePeriodEntity p = new TimetablePeriodEntity(schoolId, YEAR, position, "زنگ " + position,
                LocalTime.parse(start), LocalTime.parse(end));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static CourseEntity course(Long id, int gradeLevel, boolean graded) {
        CourseEntity c = new CourseEntity();
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "gradeLevel", gradeLevel);
        ReflectionTestUtils.setField(c, "name", "درس " + id);
        ReflectionTestUtils.setField(c, "graded", graded);
        return c;
    }

    private static ActivityEntity activity(Long id, Long schoolId, String name) {
        ActivityEntity a = new ActivityEntity(schoolId, name);
        a.setId(id);
        return a;
    }

    private void teacherIsActive(boolean active) {
        when(iam.findMembershipsByUser(new UserId(TEACHER))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(TEACHER), "t@x", "معلم", SCHOOL, "TEACHER", active, true)));
    }

    private static void rejectedWith(Runnable call, AcademicError expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AcademicException.class,
                e -> assertThat(e.error()).isEqualTo(expected));
    }

    // ---- زنگ‌ها ----

    @Test
    void periodWritesTakeTheSchoolYearLockBeforeReadingForOverlap() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR)).thenReturn(List.of());

        facade.createPeriod(SCHOOL, YEAR, "زنگ", LocalTime.of(8, 0), LocalTime.of(8, 45));
        facade.updatePeriod(SCHOOL, PERIOD, "زنگ", LocalTime.of(8, 0), LocalTime.of(8, 50));

        // قفل پیش از خواندنِ زنگ‌ها برای بررسیِ هم‌پوشانی، برای ساخت و ویرایش
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(periods);
        order.verify(periods).lockSchoolYear(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(periods).findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR);
        order.verify(periods).lockSchoolYear(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(periods).findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR);
    }

    @Test
    void deletingAMiddlePeriodRenumbersTheRestContiguouslyInTwoFlushedPhases() {
        TimetablePeriodEntity first = period(40L, SCHOOL, 1, "08:00", "08:45");
        TimetablePeriodEntity third = period(42L, SCHOOL, 3, "10:00", "10:45");
        TimetablePeriodEntity fourth = period(43L, SCHOOL, 4, "11:00", "11:45");
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR))
                .thenReturn(List.of(first, third, fourth));

        facade.deletePeriod(SCHOOL, PERIOD);

        assertThat(List.of(first.getPosition(), third.getPosition(), fourth.getPosition())).containsExactly(1, 2, 3);
        // دو مرحله، هر کدام flush: اول به بازه‌ی بی‌تصادم، بعد به شماره‌ی نهایی
        verify(periods, org.mockito.Mockito.times(2)).saveAllAndFlush(any());
    }

    @Test
    void aContiguousSequenceIsNotRewritten() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR)).thenReturn(List.of(
                period(40L, SCHOOL, 1, "08:00", "08:45"), period(42L, SCHOOL, 2, "10:00", "10:45")));

        facade.deletePeriod(SCHOOL, PERIOD);

        verify(periods, never()).saveAllAndFlush(any());
    }

    @Test
    void aPeriodGetsTheNextPositionOfItsSchoolAndYear() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR)).thenReturn(List.of());
        when(periods.maxPosition(SCHOOL, YEAR)).thenReturn(3);

        PeriodInfo created = facade.createPeriod(SCHOOL, YEAR, " زنگ چهارم ", LocalTime.of(10, 0), LocalTime.of(10, 45));

        assertThat(created.position()).isEqualTo(4);
        assertThat(created.label()).isEqualTo("زنگ چهارم");
    }

    @Test
    void aYearOutsideTheGlobalListIsRejected() {
        rejectedWith(() -> facade.createPeriod(SCHOOL, "1399-1400", "زنگ", LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new AcademicError.InvalidAcademicYear("1399-1400"));
        rejectedWith(() -> facade.listPeriods(SCHOOL, null), new AcademicError.InvalidAcademicYear(null));
        verify(periods, never()).save(any());
    }

    @Test
    void aPeriodMustEndAfterItStartsAndHaveALabel() {
        assertThatThrownBy(() -> facade.createPeriod(SCHOOL, YEAR, "زنگ", LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidPeriod.class));
        assertThatThrownBy(() -> facade.createPeriod(SCHOOL, YEAR, "  ", LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidPeriod.class));
        assertThatThrownBy(() -> facade.createPeriod(SCHOOL, YEAR, "زنگ", null, LocalTime.of(10, 0)))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidPeriod.class));
        verify(periods, never()).save(any());
    }

    @Test
    void overlappingPeriodsAreRejectedButBackToBackOnesAreFine() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR))
                .thenReturn(List.of(period(40L, SCHOOL, 1, "08:00", "08:45")));

        rejectedWith(() -> facade.createPeriod(SCHOOL, YEAR, "زنگ", LocalTime.of(8, 30), LocalTime.of(9, 15)),
                new AcademicError.PeriodOverlaps(40L));
        assertThat(facade.createPeriod(SCHOOL, YEAR, "زنگ", LocalTime.of(8, 45), LocalTime.of(9, 30)).position())
                .isEqualTo(1); // maxPosition(mock)=0, پس 1 - فقط نشان می‌دهد پذیرفته شد
    }

    @Test
    void updatingAPeriodMayKeepItsOwnTimeRangeButNotTakeAnotherPeriods() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR)).thenReturn(List.of(
                period(40L, SCHOOL, 1, "08:00", "08:45"), period(42L, SCHOOL, 2, "09:00", "09:45")));

        PeriodInfo same = facade.updatePeriod(SCHOOL, 40L, "اول", LocalTime.of(8, 0), LocalTime.of(8, 50));
        assertThat(same.label()).isEqualTo("اول");
        rejectedWith(() -> facade.updatePeriod(SCHOOL, 40L, "اول", LocalTime.of(8, 30), LocalTime.of(9, 30)),
                new AcademicError.PeriodOverlaps(42L));
    }

    @Test
    void aPeriodOfAnotherSchoolIsNotFound() {
        rejectedWith(() -> facade.updatePeriod(OTHER_SCHOOL, PERIOD, "زنگ", LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new AcademicError.PeriodNotFound(PERIOD));
        rejectedWith(() -> facade.deletePeriod(OTHER_SCHOOL, PERIOD), new AcademicError.PeriodNotFound(PERIOD));
    }

    @Test
    void aPeriodWithEntriesCannotBeDeleted() {
        when(entries.existsByPeriodId(PERIOD)).thenReturn(true);

        rejectedWith(() -> facade.deletePeriod(SCHOOL, PERIOD), new AcademicError.PeriodHasEntries(PERIOD));
        verify(periods, never()).delete(any());
    }

    @Test
    void anEmptyPeriodIsDeleted() {
        facade.deletePeriod(SCHOOL, PERIOD);

        verify(periods).delete(any(TimetablePeriodEntity.class));
    }

    // ---- فعالیت‌ها ----

    @Test
    void anActivityNameMustBeFreeInTheSchoolAndAmongPlatformDefaults() {
        when(activities.existsBySchoolIdAndName(SCHOOL, "ورزش")).thenReturn(true);
        when(activities.existsBySchoolIdIsNullAndName("نقاشی")).thenReturn(true);

        rejectedWith(() -> facade.createActivity(SCHOOL, "ورزش"), new AcademicError.ActivityNameTaken("ورزش"));
        rejectedWith(() -> facade.createActivity(SCHOOL, "نقاشی"), new AcademicError.ActivityNameTaken("نقاشی"));
        ActivityInfo ok = facade.createActivity(SCHOOL, " قرآن ");
        assertThat(ok.name()).isEqualTo("قرآن");
        assertThat(ok.platformDefault()).isFalse();
    }

    @Test
    void aRaceOnTheActivityNameIndexIsReportedAsANameClashForCreateAndRename() {
        when(activities.saveAndFlush(any(ActivityEntity.class))).thenThrow(new DataIntegrityViolationException(
                "x", new RuntimeException("duplicate key value violates unique constraint \"activities_school_name_unique\"")));
        rejectedWith(() -> facade.createActivity(SCHOOL, "شطرنج"), new AcademicError.ActivityNameTaken("شطرنج"));

        when(activities.findById(63L)).thenReturn(Optional.of(activity(63L, SCHOOL, "نقاشی")));
        rejectedWith(() -> facade.renameActivity(SCHOOL, 63L, "شطرنج"), new AcademicError.ActivityNameTaken("شطرنج"));

        // خطای یکپارچگیِ دیگر به نامِ تکراری تعبیر نمی‌شود
        when(activities.saveAndFlush(any(ActivityEntity.class))).thenThrow(
                new DataIntegrityViolationException("some other constraint"));
        assertThatThrownBy(() -> facade.createActivity(SCHOOL, "کارگاه"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPlatformDefaultActivityIsVisibleButCannotBeRenamedOrDeleted() {
        when(activities.findById(61L)).thenReturn(Optional.of(activity(61L, null, "ورزش")));

        rejectedWith(() -> facade.renameActivity(SCHOOL, 61L, "نام تازه"), new AcademicError.ActivityNotEditable(61L));
        rejectedWith(() -> facade.deleteActivity(SCHOOL, 61L), new AcademicError.ActivityNotEditable(61L));
        verify(activities, never()).saveAndFlush(any());
        verify(activities, never()).delete(any());
    }

    @Test
    void anotherSchoolsActivityIsNotFound() {
        when(activities.findById(62L)).thenReturn(Optional.of(activity(62L, OTHER_SCHOOL, "شطرنج")));

        rejectedWith(() -> facade.renameActivity(SCHOOL, 62L, "شطرنج ۲"), new AcademicError.ActivityNotFound(62L));
        rejectedWith(() -> facade.deleteActivity(SCHOOL, 62L), new AcademicError.ActivityNotFound(62L));
    }

    @Test
    void anActivityInUseCannotBeDeletedAndAFreeOneCan() {
        when(activities.findById(63L)).thenReturn(Optional.of(activity(63L, SCHOOL, "شطرنج")));
        when(entries.existsByActivityId(63L)).thenReturn(true);
        rejectedWith(() -> facade.deleteActivity(SCHOOL, 63L), new AcademicError.ActivityInUse(63L));

        when(entries.existsByActivityId(63L)).thenReturn(false);
        facade.deleteActivity(SCHOOL, 63L);
        verify(activities).delete(any(ActivityEntity.class));
    }

    @Test
    void renamingToTheSameNameIsNotACollision() {
        when(activities.findById(63L)).thenReturn(Optional.of(activity(63L, SCHOOL, "شطرنج")));
        when(activities.existsBySchoolIdAndName(SCHOOL, "شطرنج")).thenReturn(true);

        assertThat(facade.renameActivity(SCHOOL, 63L, "شطرنج").name()).isEqualTo("شطرنج");
    }

    // ---- خانه‌ها ----

    private static CourseEntity ranged(Long id, Integer from, Integer until) {
        CourseEntity c = course(id, GRADE_LEVEL, true);
        ReflectionTestUtils.setField(c, "validFromYear", from);
        ReflectionTestUtils.setField(c, "validUntilYear", until);
        return c;
    }

    @Test
    void theTimetableCourseListForAYearHidesCoursesOutsideTheirRange() {
        when(courses.findByGradeLevelOrderByNameAsc(GRADE_LEVEL)).thenReturn(List.of(
                ranged(30L, null, null), ranged(31L, 1406, null), ranged(32L, null, 1404), ranged(33L, 1405, 1405)));

        assertThat(facade.listCoursesForTimetable(SCHOOL, CLASS_ID, YEAR)).extracting(c -> c.id().value())
                .containsExactly(30L, 33L);
        // ارقام فارسی همان سال را می‌دهد (مثل انتخابگرِ دفتر نمرات)
        assertThat(facade.listCoursesForTimetable(SCHOOL, CLASS_ID, " ۱۴۰۵-۱۴۰۶ ")).extracting(c -> c.id().value())
                .containsExactly(30L, 33L);
        // بدونِ سال: همه، بازه نادیده (رفتارِ پیشین)
        assertThat(facade.listCoursesForTimetable(SCHOOL, CLASS_ID)).hasSize(4);
        assertThatThrownBy(() -> facade.listCoursesForTimetable(SCHOOL, CLASS_ID, "1999-2000"))
                .isInstanceOfSatisfying(AcademicException.class,
                        e -> assertThat(e.error()).isInstanceOf(AcademicError.InvalidAcademicYear.class));
    }

    @Test
    void aCourseOutsideItsRangeCannotBePlacedInAPeriodOfThatYear() {
        when(courses.findById(30L)).thenReturn(Optional.of(ranged(30L, 1406, null)));

        assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, null))
                .isInstanceOfSatisfying(AcademicException.class,
                        e -> assertThat(e.error()).isEqualTo(new AcademicError.CourseNotValidForYear(30L, YEAR)));

        verify(entries, never()).saveAndFlush(any(TimetableEntryEntity.class));
    }

    @Test
    void theTimetableCourseListIncludesNonGradedCourses() {
        when(courses.findByGradeLevelOrderByNameAsc(GRADE_LEVEL))
                .thenReturn(List.of(course(30L, GRADE_LEVEL, true), course(31L, GRADE_LEVEL, false)));

        assertThat(facade.listCoursesForTimetable(SCHOOL, CLASS_ID)).hasSize(2);
        verify(courses, never()).findByGradeLevelAndGradedTrueOrderByNameAsc(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aMissingPeriodIdIsAValidationErrorBeforeAnyLockOrLookup() {
        assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, null, TimetableDay.SAT, 30L, null, null))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidTimetableEntry.class));
        assertThatThrownBy(() -> facade.clearEntry(SCHOOL, CLASS_ID, null, TimetableDay.SAT))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidTimetableEntry.class));
        verify(periods, never()).lockPeriod(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(periods, never()).findById(null);
    }

    @Test
    void aCellNeedsExactlyOneOfCourseOrActivity() {
        assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, null, null, null))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidTimetableEntry.class));
        assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, 60L, null))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidTimetableEntry.class));
        assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, null, 30L, null, null))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidTimetableEntry.class));
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void aCourseMustExistAndMatchTheClassGradeLevel() {
        when(courses.findById(31L)).thenReturn(Optional.empty());
        when(courses.findById(32L)).thenReturn(Optional.of(course(32L, GRADE_LEVEL + 1, true)));

        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 31L, null, null),
                new AcademicError.CourseNotFound(31L));
        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 32L, null, null),
                new AcademicError.CourseNotForClass(32L, CLASS_ID));
    }

    @Test
    void aCellIsLockedBeforeItsExistingRowIsRead() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));

        facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, null);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(entries);
        order.verify(entries).lockCell(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(entries).findByClassIdAndWeekdayAndPeriodId(CLASS_ID, TimetableDay.SAT, PERIOD);
    }

    @Test
    void aSaturdayWeekSchoolHasSaturdayToWednesdayAndNothingElse() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));
        for (TimetableDay day : List.of(TimetableDay.SAT, TimetableDay.SUN, TimetableDay.MON, TimetableDay.TUE,
                TimetableDay.WED)) {
            facade.setEntry(SCHOOL, CLASS_ID, PERIOD, day, 30L, null, null);
        }
        for (TimetableDay day : List.of(TimetableDay.THU, TimetableDay.FRI)) {
            assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, day, 30L, null, null))
                    .isInstanceOfSatisfying(AcademicException.class,
                            e -> assertThat(e.error()).isInstanceOf(AcademicError.InvalidTimetableEntry.class));
            assertThatThrownBy(() -> facade.clearEntry(SCHOOL, CLASS_ID, PERIOD, day))
                    .isInstanceOf(AcademicException.class);
        }
    }

    @Test
    void aMondayWeekSchoolHasMondayToFridayAndNothingElse() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));
        when(school.findSchool(new SchoolId(SCHOOL))).thenReturn(Optional.of(schoolInfo(WeekStart.MONDAY)));

        for (TimetableDay day : List.of(TimetableDay.MON, TimetableDay.TUE, TimetableDay.WED, TimetableDay.THU,
                TimetableDay.FRI)) {
            facade.setEntry(SCHOOL, CLASS_ID, PERIOD, day, 30L, null, null);
        }
        for (TimetableDay day : List.of(TimetableDay.SAT, TimetableDay.SUN)) {
            assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, day, 30L, null, null))
                    .isInstanceOfSatisfying(AcademicException.class,
                            e -> assertThat(e.error()).isInstanceOf(AcademicError.InvalidTimetableEntry.class));
            assertThatThrownBy(() -> facade.clearEntry(SCHOOL, CLASS_ID, PERIOD, day))
                    .isInstanceOf(AcademicException.class);
        }
        // پیش از هر نوشتن رد می‌شود: برای روز غیردرسی هیچ خانه‌ای ذخیره نشد
        verify(entries, org.mockito.Mockito.never()).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(e -> e.getWeekday() == TimetableDay.SAT));
    }

    @Test
    void timetableDayMapsEveryRealDayBothWays() {
        for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
            assertThat(TimetableDay.of(day).dayOfWeek()).isEqualTo(day);
        }
        assertThat(TimetableDay.values()).hasSize(7);
    }

    @Test
    void clearingACellTakesTheSameCellLockBeforeReadingTheRow() {
        facade.clearEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.MON);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(entries);
        order.verify(entries).lockCell(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(entries).findByClassIdAndWeekdayAndPeriodId(CLASS_ID, TimetableDay.MON, PERIOD);
    }

    @Test
    void settingACellLocksItsPeriodAndActivityBeforeReadingThem() {
        when(activities.findById(61L)).thenReturn(Optional.of(activity(61L, null, "ورزش")));

        facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, null, 61L, null);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(periods, activities, entries);
        order.verify(periods).lockPeriod(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(periods).findById(PERIOD);
        order.verify(activities).lockActivity(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(activities).findById(61L);
        order.verify(entries).lockCell(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void deletingAPeriodLocksItBeforeCheckingForEntries() {
        facade.deletePeriod(SCHOOL, PERIOD);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(periods, entries);
        order.verify(periods).lockPeriod(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(periods).findById(PERIOD);
        order.verify(entries).existsByPeriodId(PERIOD);
    }

    @Test
    void updatingAPeriodLocksItsRowBeforeReadingItAndThenTheSchoolYear() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR)).thenReturn(List.of());

        facade.updatePeriod(SCHOOL, PERIOD, "زنگ", LocalTime.of(8, 0), LocalTime.of(8, 50));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(periods);
        order.verify(periods).lockPeriod(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(periods).findById(PERIOD);
        order.verify(periods).lockSchoolYear(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void renamingAnActivityLocksItBeforeReadingIt() {
        when(activities.findById(63L)).thenReturn(Optional.of(activity(63L, SCHOOL, "شطرنج")));

        facade.renameActivity(SCHOOL, 63L, "شطرنج پیشرفته");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(activities);
        order.verify(activities).lockActivity(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(activities).findById(63L);
    }

    @Test
    void deletingAnActivityLocksItBeforeCheckingForEntries() {
        when(activities.findById(63L)).thenReturn(Optional.of(activity(63L, SCHOOL, "شطرنج")));

        facade.deleteActivity(SCHOOL, 63L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(activities, entries);
        order.verify(activities).lockActivity(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        order.verify(activities).findById(63L);
        order.verify(entries).existsByActivityId(63L);
    }

    @Test
    void aNonGradedCourseCanBeTimetabled() {
        when(courses.findById(33L)).thenReturn(Optional.of(course(33L, GRADE_LEVEL, false)));

        facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 33L, null, null);

        verify(entries).saveAndFlush(any(TimetableEntryEntity.class));
    }

    @Test
    void anActivityMustBeTheSchoolsOwnOrAPlatformDefault() {
        when(activities.findById(62L)).thenReturn(Optional.of(activity(62L, OTHER_SCHOOL, "شطرنج")));
        when(activities.findById(61L)).thenReturn(Optional.of(activity(61L, null, "ورزش")));

        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SUN, null, 62L, null),
                new AcademicError.ActivityNotFound(62L));
        facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SUN, null, 61L, null);
        verify(entries).saveAndFlush(any(TimetableEntryEntity.class));
    }

    @Test
    void aClassOrPeriodOfAnotherSchoolIsNotFound() {
        rejectedWith(() -> facade.setEntry(OTHER_SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, null),
                new AcademicError.ClassNotFound(CLASS_ID));
        when(classes.findById(9L)).thenReturn(Optional.of(classEntity(9L, OTHER_SCHOOL)));
        rejectedWith(() -> facade.setEntry(SCHOOL, 9L, PERIOD, TimetableDay.SAT, 30L, null, null),
                new AcademicError.ClassNotFound(9L));
        when(periods.findById(43L)).thenReturn(Optional.of(period(43L, OTHER_SCHOOL, 1, "08:00", "09:00")));
        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, 43L, TimetableDay.SAT, 30L, null, null),
                new AcademicError.PeriodNotFound(43L));
    }

    @Test
    void theTeacherMustBeAnActiveTeacherOfThisSchool() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));
        teacherIsActive(false);
        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, TEACHER),
                new AcademicError.TeacherNotEligible(TEACHER, SCHOOL));

        when(iam.findMembershipsByUser(new UserId(TEACHER))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(TEACHER), "t@x", "والد", SCHOOL, "PARENT", true, true),
                new MembershipInfo(2L, new UserId(TEACHER), "t@x", "معلم", OTHER_SCHOOL, "TEACHER", true, true)));
        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, TEACHER),
                new AcademicError.TeacherNotEligible(TEACHER, SCHOOL));
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void aTeacherCannotBeInTwoClassesInTheSameSlot() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));
        teacherIsActive(true);
        TimetableEntryEntity elsewhere = new TimetableEntryEntity(SCHOOL, OTHER_CLASS, PERIOD, TimetableDay.SAT);
        elsewhere.setId(501L);
        when(entries.findByTeacherUserIdAndWeekdayAndPeriodId(TEACHER, TimetableDay.SAT, PERIOD))
                .thenReturn(Optional.of(elsewhere));

        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, TEACHER),
                new AcademicError.TeacherDoubleBooked(TEACHER, "SAT", PERIOD));
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void replacingTheSameCellKeepingItsTeacherIsNotADoubleBooking() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));
        teacherIsActive(true);
        TimetableEntryEntity existing = new TimetableEntryEntity(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT);
        existing.setId(502L);
        existing.setCourseId(31L);
        existing.setTeacherUserId(TEACHER);
        when(entries.findByClassIdAndWeekdayAndPeriodId(CLASS_ID, TimetableDay.SAT, PERIOD))
                .thenReturn(Optional.of(existing));
        when(entries.findByTeacherUserIdAndWeekdayAndPeriodId(TEACHER, TimetableDay.SAT, PERIOD))
                .thenReturn(Optional.of(existing));

        facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, TEACHER);

        // همان ردیف در جا به‌روز می‌شود: درسِ قبلی جایگزین و فعالیتِ احتمالی پاک می‌شود
        verify(entries).saveAndFlush(existing);
        assertThat(existing.getCourseId()).isEqualTo(30L);
        assertThat(existing.getActivityId()).isNull();
    }

    @Test
    void aRaceOnTheTeacherSlotIndexIsReportedAsADoubleBooking() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));
        teacherIsActive(true);
        when(entries.saveAndFlush(any(TimetableEntryEntity.class))).thenThrow(new DataIntegrityViolationException(
                "x", new RuntimeException("duplicate key value violates unique constraint "
                + "\"timetable_entries_teacher_slot_unique\"")));

        rejectedWith(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, TEACHER),
                new AcademicError.TeacherDoubleBooked(TEACHER, "SAT", PERIOD));
    }

    @Test
    void anotherIntegrityViolationIsNotMislabelledAsADoubleBooking() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, true)));
        teacherIsActive(true);
        when(entries.saveAndFlush(any(TimetableEntryEntity.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"timetable_entries_class_slot_unique\""));

        assertThatThrownBy(() -> facade.setEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT, 30L, null, TEACHER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clearingAnEmptyCellIsANoOp() {
        facade.clearEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.MON);

        verify(entries, never()).delete(any());
    }

    @Test
    void clearingAFilledCellDeletesIt() {
        TimetableEntryEntity existing = new TimetableEntryEntity(SCHOOL, CLASS_ID, PERIOD, TimetableDay.MON);
        when(entries.findByClassIdAndWeekdayAndPeriodId(CLASS_ID, TimetableDay.MON, PERIOD))
                .thenReturn(Optional.of(existing));

        facade.clearEntry(SCHOOL, CLASS_ID, PERIOD, TimetableDay.MON);

        verify(entries).delete(existing);
    }

    // ---- خواندن ----

    @Test
    void entriesOfAnotherSchoolsClassAreNeverReturned() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR))
                .thenReturn(List.of(period(PERIOD, SCHOOL, 1, "08:00", "08:45")));
        when(classes.findAllById(List.of(9L))).thenReturn(List.of(classEntity(9L, OTHER_SCHOOL)));

        assertThat(facade.listEntriesForClasses(SCHOOL, YEAR, List.of(9L))).isEmpty();
        verify(entries, never()).findByClassIdInAndPeriodIdIn(any(), any());
    }

    @Test
    void entriesCarryCourseActivityAndClassNames() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR))
                .thenReturn(List.of(period(PERIOD, SCHOOL, 1, "08:00", "08:45")));
        when(classes.findAllById(List.of(CLASS_ID))).thenReturn(List.of(classEntity(CLASS_ID, SCHOOL)));
        TimetableEntryEntity math = new TimetableEntryEntity(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SAT);
        math.setId(1L);
        math.setCourseId(30L);
        TimetableEntryEntity sport = new TimetableEntryEntity(SCHOOL, CLASS_ID, PERIOD, TimetableDay.SUN);
        sport.setId(2L);
        sport.setActivityId(61L);
        when(entries.findByClassIdInAndPeriodIdIn(java.util.Set.of(CLASS_ID), List.of(PERIOD)))
                .thenReturn(List.of(math, sport));
        when(courses.findAllById(List.of(30L))).thenReturn(List.of(course(30L, GRADE_LEVEL, true)));
        when(activities.findAllById(List.of(61L))).thenReturn(List.of(activity(61L, null, "ورزش")));
        when(classes.findAllById(List.of(CLASS_ID))).thenReturn(List.of(classEntity(CLASS_ID, SCHOOL)));

        List<EntryInfo> result = facade.listEntriesForClasses(SCHOOL, YEAR, List.of(CLASS_ID));

        assertThat(result).extracting(EntryInfo::courseName, EntryInfo::activityName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("درس 30", null),
                        org.assertj.core.groups.Tuple.tuple(null, "ورزش"));
        assertThat(result).extracting(EntryInfo::className).containsOnly("پنجم");
    }

    @Test
    void ayearWithoutPeriodsHasNoEntriesForAnyone() {
        when(periods.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(SCHOOL, YEAR)).thenReturn(List.of());

        assertThat(facade.listEntriesForClasses(SCHOOL, YEAR, List.of(CLASS_ID))).isEmpty();
        assertThat(facade.listEntriesForTeacher(SCHOOL, YEAR, TEACHER)).isEmpty();
    }
}
