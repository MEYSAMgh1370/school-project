package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;

import java.time.Clock;
import java.time.LocalDate;

/**
 * «امروزِ مدرسه» در یک جا: تاریخ در منطقه‌ی زمانیِ خودِ مدرسه ({@link SchoolInfo#DEFAULT_ZONE} اگر مدرسه
 * نباشد)، تعطیلیِ تقویم و روزِ تعطیلِ هفتگی (همان قاعده‌ی {@code AttendanceRules}: روز در
 * {@code weekStart.schoolDays()} نباشد).
 *
 * @param closed    تعطیلیِ تقویمی (CLOSED یا تعطیلیِ سراسری)
 * @param weeklyOff روزِ تعطیلِ هفتگی
 */
record SchoolToday(LocalDate date, boolean closed, boolean weeklyOff) {

    static SchoolToday of(SchoolFacade schoolFacade, Clock clock, SchoolId school) {
        SchoolInfo info = schoolFacade.findSchool(school).orElse(null);
        LocalDate date = LocalDate.now(clock.withZone(info == null ? SchoolInfo.DEFAULT_ZONE : info.zoneId()));
        boolean weeklyOff = info != null && info.weekStart() != null
                && !info.weekStart().schoolDays().contains(date.getDayOfWeek());
        return new SchoolToday(date, schoolFacade.isClosedDay(school, date), weeklyOff);
    }
}
