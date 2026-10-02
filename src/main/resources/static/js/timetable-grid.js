// Shared helpers for the class timetable pages (/timetable, /my-timetable) and the
// student dashboard section: weekday labels, time display, and cell lookup.
// Weekdays are the school week, Saturday to Wednesday
// (openspec/changes/class-timetable.md); the codes are the API's, never Jalali.
window.TimetableGrid = {
    DAYS: [
        { code: 'SAT', label: 'شنبه' },
        { code: 'SUN', label: 'یکشنبه' },
        { code: 'MON', label: 'دوشنبه' },
        { code: 'TUE', label: 'سه‌شنبه' },
        { code: 'WED', label: 'چهارشنبه' }
    ],

    // JS Date.getDay() (Sunday = 0) -> API weekday; Thursday and Friday are not school days
    DAY_BY_JS_INDEX: { 6: 'SAT', 0: 'SUN', 1: 'MON', 2: 'TUE', 3: 'WED' },

    dayLabel(code) {
        const day = this.DAYS.find((d) => d.code === code);
        return day ? day.label : code;
    },

    // "08:00" (or "08:00:00") -> Persian digits "۰۸:۰۰"
    timeLabel(time) {
        return window.Jalali ? window.Jalali.toPersianDigits(String(time).slice(0, 5)) : String(time).slice(0, 5);
    },

    rangeLabel(period) {
        return this.timeLabel(period.startTime) + ' تا ' + this.timeLabel(period.endTime);
    },

    // the entry of one (weekday, period) cell, or null
    entryAt(entries, weekday, periodId) {
        return entries.find((e) => e.weekday === weekday && e.periodId === periodId) || null;
    },

    subjectOf(entry) {
        return entry.courseName || entry.activityName || '';
    }
};
