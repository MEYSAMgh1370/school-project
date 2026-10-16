package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.TimetableDay;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.academic.TimetableFacade.EntryInfo;
import com.example.roshdeandishe.academic.TimetableFacade.PeriodInfo;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * برنامه‌ی امروزِ یک دانش‌آموز ({@code TIMETABLE_TODAY}) یا یک معلم ({@code TEACHER_TODAY}). «امروز» از منطقه‌ی
 * زمانیِ خودِ مدرسه می‌آید (نه UTC یا منطقه‌ی سرور). روزِ تعطیل، دانش‌آموزِ بی‌کلاس و روزِ بدونِ ردیف هرکدام
 * جمله‌ی روشنِ خودشان را دارند. خطای دامنه‌ی facade به «در دسترس نیست» تبدیل و warn لاگ می‌شود.
 */
@Component
class TimetableAnswers {

    private static final Logger log = LoggerFactory.getLogger(TimetableAnswers.class);

    private final TimetableFacade timetableFacade;
    private final SchoolFacade schoolFacade;
    private final Clock clock;

    @Autowired
    TimetableAnswers(TimetableFacade timetableFacade, SchoolFacade schoolFacade) {
        this(timetableFacade, schoolFacade, Clock.systemUTC());
    }

    /** برای آزمون: ساعتِ ثابت. */
    TimetableAnswers(TimetableFacade timetableFacade, SchoolFacade schoolFacade, Clock clock) {
        this.timetableFacade = timetableFacade;
        this.schoolFacade = schoolFacade;
        this.clock = clock;
    }

    /** پاسخ {@code intent} برای {@code target}؛ خط اول نامِ دانش‌آموز/مدرسه است. */
    String answer(BotIntent intent, BotContext ctx, BotTarget target) {
        boolean teacher = intent == BotIntent.TEACHER_TODAY;
        Long schoolId;
        Long classId = null;
        String header;
        if (target instanceof BotTarget.Student s) {
            schoolId = s.schoolId();
            classId = s.classId();
            header = BotTexts.studentHeader(s.name(), s.schoolName());
        } else {
            BotTarget.School s = (BotTarget.School) target;
            schoolId = s.schoolId();
            header = BotTexts.schoolHeader(s.schoolName());
        }
        try {
            return header + "\n" + body(teacher, ctx, schoolId, classId);
        } catch (AcademicException | SchoolException e) {
            log.warn("Bot intent {} failed for school {}: {}", intent, schoolId, e.getClass().getSimpleName());
            return header + "\n" + BotTexts.TIMETABLE_UNAVAILABLE;
        }
    }

    private String body(boolean teacher, BotContext ctx, Long schoolId, Long classId) {
        SchoolId school = new SchoolId(schoolId);
        LocalDate today = LocalDate.now(clock.withZone(schoolFacade.findSchool(school)
                .map(SchoolInfo::zoneId).orElse(SchoolInfo.DEFAULT_ZONE)));
        if (schoolFacade.isClosedDay(school, today)) {
            return BotTexts.HOLIDAY_TODAY;
        }
        if (!teacher && classId == null) {
            return BotTexts.TIMETABLE_NO_CLASS;
        }
        Optional<AcademicYearInfo> year = schoolFacade.findCurrentAcademicYear(school);
        if (year.isEmpty()) {
            return BotTexts.TIMETABLE_EMPTY;
        }
        String yearCode = year.get().code();
        Map<Long, PeriodInfo> periods = new HashMap<>();
        timetableFacade.listPeriods(schoolId, yearCode).forEach(p -> periods.put(p.id(), p));
        List<EntryInfo> entries = teacher
                ? timetableFacade.listEntriesForTeacher(schoolId, yearCode, ctx.userId().value())
                : timetableFacade.listEntriesForClass(schoolId, classId, yearCode);
        TimetableDay day = TimetableDay.of(today.getDayOfWeek());
        List<EntryInfo> todays = entries.stream()
                .filter(e -> e.weekday() == day && periods.containsKey(e.periodId()))
                .sorted(Comparator.comparingInt(e -> periods.get(e.periodId()).position()))
                .toList();
        if (todays.isEmpty()) {
            return BotTexts.TIMETABLE_EMPTY;
        }
        List<String> lines = new ArrayList<>();
        for (EntryInfo e : todays) {
            PeriodInfo p = periods.get(e.periodId());
            String subject = e.courseName() != null ? e.courseName()
                    : e.activityName() != null ? e.activityName() : "—";
            lines.add(BotTexts.timetableLine(p.position(), p.startTime(), p.endTime(), subject,
                    teacher ? e.className() : null));
        }
        return String.join("\n", lines);
    }
}
