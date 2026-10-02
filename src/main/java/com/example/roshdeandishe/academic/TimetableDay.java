package com.example.roshdeandishe.academic;

/**
 * روزهای هفتهٔ مدرسه: شنبه تا چهارشنبه (openspec/changes/class-timetable.md،
 * تصمیم ۸). نامِ ثابتِ enum همان مقدارِ ستونِ {@code timetable_entries.weekday} و
 * کلیدِ JSON است؛ پنجشنبه/جمعه عمداً نیستند.
 */
public enum TimetableDay {
    SAT, SUN, MON, TUE, WED
}
