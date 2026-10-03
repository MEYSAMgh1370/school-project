const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
require('../../main/resources/static/js/jalali.js');
require('../../main/resources/static/js/calendar.js');

const fa = Calendar.forLanguage('fa');
const en = Calendar.forLanguage('en');
const DAY_MS = 86400000;

// ------------------------------------------------------------ اوراکل مستقل
// تقویم Persian خودِ ICU. هیچ کدی از jalali.js ندارد، پس توافق با آن یک
// بررسی مستقل است، نه تکرار خودِ الگوریتم.
const icuFormat = new Intl.DateTimeFormat('en-u-ca-persian', {
    year: 'numeric', month: 'numeric', day: 'numeric', timeZone: 'UTC'
});

function icuJalali(utcMs) {
    const out = {};
    for (const part of icuFormat.formatToParts(new Date(utcMs))) {
        if (part.type !== 'literal') out[part.type] = Number(part.value);
    }
    return { y: out.year, m: out.month, d: out.day };
}

// معکوس اوراکل: روز میلادیِ یک تاریخ جلالی را با پویش پیدا می‌کند
function icuGregorian(jy, jm, jd) {
    const start = Date.UTC(jy + 621, 2, 1);
    for (let i = 0; i < 400; i += 1) {
        const ms = start + i * DAY_MS;
        const j = icuJalali(ms);
        if (j.y === jy && j.m === jm && j.d === jd) return new Date(ms).toISOString().slice(0, 10);
    }
    throw new Error(`not found: ${jy}/${jm}/${jd}`);
}

const iso = (date) => date.toISOString().slice(0, 10);

// -------------------------------------------------------------------- fa

test('fa: fromDate agrees with ICU on every day from 1950 to 2100', () => {
    let checked = 0;
    for (let ms = Date.UTC(1950, 0, 1); ms <= Date.UTC(2100, 11, 31); ms += DAY_MS) {
        const utc = new Date(ms);
        // fromDate می‌خواند اجزای محلی را؛ یک Date محلی با همان روز مدنی می‌سازیم
        const local = new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate());
        assert.deepEqual(fa.fromDate(local), icuJalali(ms), iso(utc));
        checked += 1;
    }
    assert.equal(checked, 55152);
});

test('fa: toDate round-trips fromDate and returns UTC midnight', () => {
    for (let ms = Date.UTC(1990, 0, 1); ms <= Date.UTC(2060, 11, 31); ms += DAY_MS) {
        const utc = new Date(ms);
        const j = fa.fromDate(new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate()));
        assert.equal(fa.toDate(j.y, j.m, j.d).getTime(), ms, iso(utc));
    }
});

test('fa: Nowruz anchors and leap-year length of Esfand', () => {
    assert.equal(iso(fa.toDate(1405, 1, 1)), '2026-03-21');
    assert.equal(iso(fa.toDate(1404, 1, 1)), '2025-03-21');
    assert.equal(iso(fa.toDate(1403, 1, 1)), '2024-03-20');
    // 1403 کبیسه است (اسفند ۳۰ روز) و 1404 نیست (۲۹ روز)
    assert.equal(fa.daysInMonth(1403, 12), 30);
    assert.equal(fa.daysInMonth(1404, 12), 29);
    assert.equal(fa.daysInMonth(1405, 1), 31);
    assert.equal(fa.daysInMonth(1405, 7), 30);
});

test('fa: periodRange matches ICU for every month, quarter and year of 1403-1410', () => {
    for (let y = 1403; y <= 1410; y += 1) {
        for (let m = 1; m <= 12; m += 1) {
            const lastDay = fa.daysInMonth(y, m);
            assert.deepEqual(fa.periodRange(y, 'month', m),
                { from: icuGregorian(y, m, 1), to: icuGregorian(y, m, lastDay) }, `${y}/${m}`);
        }
        for (let q = 1; q <= 4; q += 1) {
            const first = (q - 1) * 3 + 1;
            assert.deepEqual(fa.periodRange(y, 'quarter', q),
                { from: icuGregorian(y, first, 1), to: icuGregorian(y, first + 2, fa.daysInMonth(y, first + 2)) },
                `${y} Q${q}`);
        }
        assert.deepEqual(fa.periodRange(y, 'year', 1),
            { from: icuGregorian(y, 1, 1), to: icuGregorian(y, 12, fa.daysInMonth(y, 12)) }, `${y}`);
    }
});

test('fa: output is unchanged - names, digits and date text are exactly what the pages produced before', () => {
    assert.equal(fa.kind, 'jalali');
    assert.deepEqual(fa.monthNames, Jalali.monthNames);
    assert.deepEqual(fa.weekdayNames, Jalali.weekdayNames);
    assert.deepEqual(fa.weekdayShort, Jalali.weekdayShort);
    assert.equal(fa.digits(1405), '۱۴۰۵');
    for (const value of ['2026-10-03T08:15:00Z', '2026-10-05', new Date(2026, 2, 21, 12), 1700000000000]) {
        assert.equal(fa.formatDate(value), new Date(value).toLocaleDateString('fa-IR'));
    }
    assert.equal(fa.formatDate(new Date(2026, 2, 21, 12)), '۱۴۰۵/۱/۱');
});

// منطق قدیمیِ weekRangeLabel در my-study-plan.js، عیناً (فقط نام فیلدها همان jy/jm/jd
// مانده تا مقایسه خط‌به‌خط ممکن باشد). مرجعِ «خروجی فارسی تغییر نکرده» این است.
function legacyWeekRangeLabel(weekStart) {
    const faDigits = (value) => Jalali.toPersianDigits(value);
    const start = Jalali.toJalali(weekStart.getFullYear(), weekStart.getMonth() + 1, weekStart.getDate());
    const endDate = new Date(weekStart);
    endDate.setDate(endDate.getDate() + 6);
    const end = Jalali.toJalali(endDate.getFullYear(), endDate.getMonth() + 1, endDate.getDate());
    const startLabel = faDigits(start.jd) + ' ' + Jalali.monthNames[start.jm - 1];
    if (start.jy === end.jy && start.jm === end.jm) {
        return faDigits(start.jd) + ' تا ' + faDigits(end.jd) + ' ' + Jalali.monthNames[start.jm - 1] + ' ' + faDigits(start.jy);
    }
    if (start.jy === end.jy) {
        return startLabel + ' تا ' + faDigits(end.jd) + ' ' + Jalali.monthNames[end.jm - 1] + ' ' + faDigits(start.jy);
    }
    return startLabel + ' ' + faDigits(start.jy) + ' تا ' + faDigits(end.jd) + ' ' + Jalali.monthNames[end.jm - 1] + ' ' + faDigits(end.jy);
}

test('fa: formatRange is byte-identical to the old weekRangeLabel for every week of 1403-1410', () => {
    // یک شنبه‌ی واقعی: ۳ اکتبر ۲۰۲۶. هفته‌ها را هشت سال به عقب و جلو می‌رویم.
    const anchor = new Date(2026, 9, 3);
    let weeks = 0;
    let sawCrossMonth = false;
    let sawCrossYear = false;
    for (let w = -3 * 52; w <= 5 * 52; w += 1) {
        const start = new Date(anchor);
        start.setDate(start.getDate() + w * 7);
        const end = new Date(start);
        end.setDate(end.getDate() + 6);
        const label = fa.formatRange(start, end);
        assert.equal(label, legacyWeekRangeLabel(start), iso(start));
        const s = fa.fromDate(start);
        const e = fa.fromDate(end);
        sawCrossMonth = sawCrossMonth || s.m !== e.m;
        sawCrossYear = sawCrossYear || s.y !== e.y;
        weeks += 1;
    }
    assert.equal(weeks, 417);
    // مقایسه فقط وقتی معنا دارد که هر سه شاخه‌ی منطق پوشش داده شده باشند
    assert.ok(sawCrossMonth && sawCrossYear, 'every branch of the label logic must have been exercised');
});

// -------------------------------------------------------------------- en

test('en: names come from Intl and start the week on Saturday like the server', () => {
    assert.equal(en.kind, 'gregorian');
    assert.equal(en.monthNames.length, 12);
    assert.equal(en.monthNames[0], 'January');
    assert.equal(en.monthNames[11], 'December');
    assert.equal(en.weekdayNames.length, 7);
    assert.equal(en.weekdayNames[0], 'Saturday');
    assert.equal(en.weekdayNames[6], 'Friday');
});

test('en: short weekday names are unique, because the page uses them as x-for keys', () => {
    assert.equal(new Set(en.weekdayShort).size, 7, JSON.stringify(en.weekdayShort));
    assert.equal(new Set(fa.weekdayShort).size, 7);
});

test('en: weekdayIndex puts Saturday at 0 and Friday at 6, in both calendars', () => {
    const saturday = new Date(2026, 9, 3);
    const friday = new Date(2026, 9, 9);
    for (const calendar of [fa, en]) {
        assert.equal(calendar.weekdayIndex(saturday), 0);
        assert.equal(calendar.weekdayIndex(friday), 6);
    }
});

test('en: fromDate and toDate are the identity on civil dates', () => {
    assert.deepEqual(en.fromDate(new Date(2026, 9, 3)), { y: 2026, m: 10, d: 3 });
    assert.equal(iso(en.toDate(2026, 10, 3)), '2026-10-03');
    assert.equal(en.toDate(2026, 10, 3).getUTCHours(), 0);
    // سال‌های کوچک نباید به ۱۹۰۰ به بعد بپرند (دامی که Date.UTC دارد)
    assert.equal(en.toDate(50, 1, 1).getUTCFullYear(), 50);
});

test('en: daysInMonth knows leap years', () => {
    assert.equal(en.daysInMonth(2026, 2), 28);
    assert.equal(en.daysInMonth(2028, 2), 29);
    assert.equal(en.daysInMonth(2100, 2), 28);
    assert.equal(en.daysInMonth(2026, 12), 31);
    assert.equal(en.daysInMonth(2026, 4), 30);
});

test('en: periodRange gives real Gregorian months, quarters and years', () => {
    assert.deepEqual(en.periodRange(2026, 'month', 2), { from: '2026-02-01', to: '2026-02-28' });
    assert.deepEqual(en.periodRange(2028, 'month', 2), { from: '2028-02-01', to: '2028-02-29' });
    assert.deepEqual(en.periodRange(2026, 'month', 12), { from: '2026-12-01', to: '2026-12-31' });
    assert.deepEqual(en.periodRange(2026, 'quarter', 1), { from: '2026-01-01', to: '2026-03-31' });
    assert.deepEqual(en.periodRange(2026, 'quarter', 4), { from: '2026-10-01', to: '2026-12-31' });
    assert.deepEqual(en.periodRange(2026, 'year', 1), { from: '2026-01-01', to: '2026-12-31' });
});

test('en: dates and digits carry nothing Persian', () => {
    const persian = /[؀-ۿ۰-۹]/;
    assert.equal(en.digits(1405), '1405');
    assert.equal(en.formatDate('2026-10-03T08:15:00Z'), 'Oct 3, 2026');
    assert.ok(!persian.test(en.formatDate(new Date(2026, 2, 21, 12))));
    const range = en.formatRange(new Date(2026, 9, 3), new Date(2026, 9, 9));
    assert.ok(!persian.test(range), range);
    assert.match(range, /Oct/);
    assert.match(range, /2026/);
});

test('en: the Gregorian calendar never calls into the Jalali algorithm', () => {
    const original = { toJalali: Jalali.toJalali, toGregorian: Jalali.toGregorian };
    let calls = 0;
    Jalali.toJalali = (...args) => { calls += 1; return original.toJalali(...args); };
    Jalali.toGregorian = (...args) => { calls += 1; return original.toGregorian(...args); };
    try {
        const local = new Date(2026, 9, 3);
        en.fromDate(local);
        en.toDate(2026, 10, 3);
        en.daysInMonth(2026, 2);
        en.periodRange(2026, 'quarter', 2);
        en.formatDate(local);
        en.formatRange(local, new Date(2026, 9, 9));
        assert.equal(calls, 0);
    } finally {
        Object.assign(Jalali, original);
    }
});

// ------------------------------------------------------ انتخاب بر اساس زبان

test('forLanguage: only fa is Jalali; a missing lang follows the server default', () => {
    assert.equal(Calendar.forLanguage('fa').kind, 'jalali');
    assert.equal(Calendar.forLanguage('fa-IR').kind, 'jalali');
    assert.equal(Calendar.forLanguage('FA').kind, 'jalali');
    assert.equal(Calendar.forLanguage('en').kind, 'gregorian');
    assert.equal(Calendar.forLanguage('en-GB').kind, 'gregorian');
    assert.equal(Calendar.forLanguage('de').kind, 'gregorian');
    assert.equal(Calendar.forLanguage('').kind, 'jalali');
    assert.equal(Calendar.forLanguage(undefined).kind, 'jalali');
    assert.equal(Calendar.forLanguage(null).kind, 'jalali');
});

test('forLanguage: a language other than en works with no extra code', () => {
    const de = Calendar.forLanguage('de');
    assert.equal(de.monthNames[0], 'Januar');
    assert.equal(de.weekdayNames[0], 'Samstag');
    assert.equal(new Set(de.weekdayShort).size, 7);
});

test('the active calendar with no document is the server default, Jalali', () => {
    // در Node هیچ document ای نیست، پس همان پیش‌فرض سرور (فارسی) فعال می‌شود
    assert.equal(Calendar.kind, 'jalali');
});
