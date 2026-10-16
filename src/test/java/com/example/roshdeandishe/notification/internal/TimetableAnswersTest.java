package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.TimetableDay;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.academic.TimetableFacade.EntryInfo;
import com.example.roshdeandishe.academic.TimetableFacade.PeriodInfo;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimetableAnswersTest {

    private static final UserId USER = new UserId(7L);
    /** شنبه ۲۰۲۶-۱۰-۱۷، ساعت ۰۹:۳۰ تهران. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private TimetableFacade timetable;
    private SchoolFacade school;
    private TimetableAnswers answers;
    private final BotContext ctx = new BotContext(1L, USER, "معلم", List.of());
    private final BotTarget.Student ward = new BotTarget.Student(5L, 11L, 70L, 3L, "علی رضایی", "مدرسه‌ی نمونه", true);
    private final BotTarget.School teacherSchool = new BotTarget.School(5L, "مدرسه‌ی نمونه");

    @BeforeEach
    void setUp() {
        timetable = mock(TimetableFacade.class);
        school = mock(SchoolFacade.class);
        // ساعتِ UTC عمداً نزدیکِ نیمه‌شب: امروزِ مدرسه از منطقه‌ی خودش می‌آید
        answers = new TimetableAnswers(timetable, school,
                Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneOffset.UTC));
        SchoolInfo info = new SchoolInfo(new SchoolId(5L), "مدرسه‌ی نمونه", null, "Asia/Tehran", null, null, null, null);
        when(school.findSchool(new SchoolId(5L))).thenReturn(Optional.of(info));
        when(school.findCurrentAcademicYear(new SchoolId(5L))).thenReturn(Optional.of(
                new AcademicYearInfo(new AcademicYearId(1L), "1405-1406", 1405, TODAY, TODAY)));
        when(school.isClosedDay(any(), any())).thenReturn(false);
        when(timetable.listPeriods(5L, "1405-1406")).thenReturn(List.of(
                new PeriodInfo(100L, 5L, "1405-1406", 1, "زنگ ۱", LocalTime.of(8, 0), LocalTime.of(8, 45)),
                new PeriodInfo(101L, 5L, "1405-1406", 2, "زنگ ۲", LocalTime.of(9, 0), LocalTime.of(9, 45))));
    }

    private static EntryInfo entry(Long periodId, TimetableDay day, String course, String activity, String className) {
        return new EntryInfo(1L, 3L, className, periodId, day, course == null ? null : 1L, course,
                activity == null ? null : 2L, activity, 7L);
    }

    @Test
    void timetableIsSortedByPeriodAndFilteredToToday() {
        when(timetable.listEntriesForClass(5L, 3L, "1405-1406")).thenReturn(List.of(
                entry(101L, TimetableDay.SAT, null, "ورزش", "هفتم"),
                entry(100L, TimetableDay.SAT, "ریاضی", null, "هفتم"),
                entry(100L, TimetableDay.SUN, "علوم", null, "هفتم")));
        String text = answers.answer(BotIntent.TIMETABLE_TODAY, ctx, ward);
        assertThat(text).isEqualTo("«علی رضایی — مدرسه‌ی نمونه»\n"
                + "زنگ ۱ (۸:۰۰ تا ۸:۴۵): ریاضی\n"
                + "زنگ ۲ (۹:۰۰ تا ۹:۴۵): ورزش");
    }

    @Test
    void closedDaySaysHoliday() {
        when(school.isClosedDay(new SchoolId(5L), TODAY)).thenReturn(true);
        assertThat(answers.answer(BotIntent.TIMETABLE_TODAY, ctx, ward)).contains("امروز تعطیل است.");
        assertThat(answers.answer(BotIntent.TEACHER_TODAY, ctx, teacherSchool)).contains("امروز تعطیل است.");
    }

    @Test
    void wardWithoutClassGetsAClearSentence() {
        BotTarget.Student noClass = new BotTarget.Student(5L, 11L, 70L, null, "علی رضایی", "مدرسه‌ی نمونه", true);
        assertThat(answers.answer(BotIntent.TIMETABLE_TODAY, ctx, noClass))
                .contains("کلاسی برای این دانش‌آموز ثبت نشده است.");
    }

    @Test
    void noRowsTodaySaysSo() {
        when(timetable.listEntriesForClass(5L, 3L, "1405-1406")).thenReturn(List.of(
                entry(100L, TimetableDay.SUN, "علوم", null, "هفتم")));
        assertThat(answers.answer(BotIntent.TIMETABLE_TODAY, ctx, ward)).contains("امروز کلاسی ثبت نشده است.");
    }

    @Test
    void noCurrentYearSaysNoClasses() {
        when(school.findCurrentAcademicYear(new SchoolId(5L))).thenReturn(Optional.empty());
        assertThat(answers.answer(BotIntent.TIMETABLE_TODAY, ctx, ward)).contains("امروز کلاسی ثبت نشده است.");
    }

    @Test
    void teacherLinesIncludeClassName() {
        when(timetable.listEntriesForTeacher(5L, "1405-1406", 7L)).thenReturn(List.of(
                entry(100L, TimetableDay.SAT, "ریاضی", null, "هفتم الف")));
        assertThat(answers.answer(BotIntent.TEACHER_TODAY, ctx, teacherSchool))
                .isEqualTo("«مدرسه‌ی نمونه»\nزنگ ۱ (۸:۰۰ تا ۸:۴۵): ریاضی — هفتم الف");
    }

    @Test
    void facadeErrorBecomesNotAvailableSentence() {
        when(timetable.listEntriesForClass(eq(5L), eq(3L), any())).thenThrow(
                new AcademicException(new AcademicError.ClassNotFound(3L)));
        assertThat(answers.answer(BotIntent.TIMETABLE_TODAY, ctx, ward)).contains("برنامه‌ی امروز در دسترس نیست.");
    }
}
