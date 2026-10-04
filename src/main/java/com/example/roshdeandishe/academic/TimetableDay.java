package com.example.roshdeandishe.academic;

import java.time.DayOfWeek;

/**
 * روزهای برنامه‌ی کلاسی. نامِ ثابتِ enum همان مقدارِ ستونِ
 * {@code timetable_entries.weekday} و کلیدِ JSON است.
 * <p>
 * کدام روزها «روز درسی»اند را مدرسه تعیین می‌کند، نه این enum: شنبه تا چهارشنبه
 * برای مدرسه‌ی شنبه‌محور و دوشنبه تا جمعه برای مدرسه‌ی دوشنبه‌محور
 * ({@code SchoolFacade.WeekStart.schoolDays()}؛ W9). {@code TimetableFacade.setEntry}
 * روزِ بیرون از آن مجموعه را رد می‌کند. هر هفت روز اینجا هستند تا یک نام برای هر روز واقعی باشد.
 */
public enum TimetableDay {
    SAT(DayOfWeek.SATURDAY), SUN(DayOfWeek.SUNDAY), MON(DayOfWeek.MONDAY), TUE(DayOfWeek.TUESDAY),
    WED(DayOfWeek.WEDNESDAY), THU(DayOfWeek.THURSDAY), FRI(DayOfWeek.FRIDAY);

    private final DayOfWeek dayOfWeek;

    TimetableDay(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    public static TimetableDay of(DayOfWeek dayOfWeek) {
        for (TimetableDay day : values()) {
            if (day.dayOfWeek == dayOfWeek) {
                return day;
            }
        }
        throw new IllegalArgumentException("Unknown day: " + dayOfWeek);
    }
}
