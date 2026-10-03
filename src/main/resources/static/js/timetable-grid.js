// Shared helpers for the class timetable pages (/timetable, /my-timetable) and the
// student dashboard section: weekday labels, time display, and cell lookup.
// The school days belong to the school (W9): Saturday to Wednesday for a Saturday-week
// school, Monday to Friday for a Monday-week one (Calendar.schoolDayCodes, from the
// navbar's data-week-starts; the server enforces the same set). The codes are the
// API's, never Jalali. Needs /js/calendar.js on the page.
window.TimetableGrid = {
    LABELS: {
        SAT: 'شنبه', SUN: 'یکشنبه', MON: 'دوشنبه', TUE: 'سه‌شنبه', WED: 'چهارشنبه', THU: 'پنجشنبه', FRI: 'جمعه'
    },

    // JS Date.getDay() (Sunday = 0) -> API weekday
    DAY_BY_JS_INDEX: { 6: 'SAT', 0: 'SUN', 1: 'MON', 2: 'TUE', 3: 'WED', 4: 'THU', 5: 'FRI' },

    // روزهای درسیِ مدرسه‌ی انتخاب‌شده، به ترتیب
    days() {
        return window.Calendar.schoolDayCodes().map((code) => ({ code, label: this.LABELS[code] }));
    },

    // «شنبه تا چهارشنبه» / «دوشنبه تا جمعه» برای متن‌ها
    schoolDaysLabel() {
        const days = this.days();
        return days[0].label + ' تا ' + days[days.length - 1].label;
    },

    // امروز (کدِ API) یا null در روزی که برای این مدرسه روز درسی نیست
    todayCode() {
        const code = this.DAY_BY_JS_INDEX[new Date().getDay()];
        return window.Calendar.schoolDayCodes().includes(code) ? code : null;
    },

    // onChange(code) را بعد از هر نیمه‌شبِ محلی و وقتی تب دوباره دیده می‌شود (تایمرِ تبِ پس‌زمینه
    // کند می‌شود) صدا می‌زند، فقط اگر «امروز» واقعاً عوض شده باشد؛ صفحه‌ی بازمانده از دیشب
    // درسِ دیروز را نشان نمی‌داد.
    watchToday(onChange) {
        let current = this.todayCode();
        const check = () => {
            const now = this.todayCode();
            if (now !== current) {
                current = now;
                onChange(now);
            }
        };
        const schedule = () => {
            const now = new Date();
            const nextMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 1);
            setTimeout(() => {
                check();
                schedule();
            }, nextMidnight.getTime() - now.getTime());
        };
        schedule();
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) {
                check();
            }
        });
    },

    dayLabel(code) {
        return this.LABELS[code] || code;
    },

    // "08:00" (or "08:00:00") -> Persian digits "۰۸:۰۰"
    timeLabel(time) {
        return window.Calendar.digits(String(time).slice(0, 5));
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
