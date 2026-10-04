// W9: روز شروع هفته یک مقدار هر مدرسه است. Calendar آن را از navbar می‌خواند
// (data-week-starts + مدرسه‌ی انتخاب‌شده در localStorage) و هفته را از همان می‌چرخاند.
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
require('../../main/resources/static/js/jalali.js');
require('../../main/resources/static/js/calendar.js');

const fa = Calendar.forLanguage('fa');
const en = Calendar.forLanguage('en');

// ظهرِ محلی، تا شکاف نیمه‌شبِ DST و روزهای حذف‌شده‌ی منطقه‌ی زمانی دخالت نکنند
function localNoon(y, m, d) {
    return new Date(y, m - 1, d, 12);
}

// نسخه‌ی عیناً قدیمیِ my-study-plan.js / exams.js / study-plans.js
function legacyMostRecentSaturday(date) {
    const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    d.setDate(d.getDate() - ((d.getDay() + 1) % 7));
    return d;
}

function withNavbar(holderJson, selectedId, body) {
    const hadDocument = 'document' in globalThis;
    const hadStorage = 'localStorage' in globalThis;
    globalThis.document = {
        documentElement: null,
        querySelector: (selector) => selector === '[data-week-starts]' && holderJson !== undefined
            ? { getAttribute: () => holderJson } : null
    };
    globalThis.localStorage = { getItem: (key) => (key === 'selectedSchoolId' ? selectedId : null) };
    try {
        body();
    } finally {
        if (!hadDocument) delete globalThis.document;
        if (!hadStorage) delete globalThis.localStorage;
    }
}

test('without a navbar the week is the Saturday week, as before W9', () => {
    assert.equal(fa.weekStart(), 'SAT');
    assert.equal(en.weekStart(), 'SAT');
    // 2026-10-03 شنبه است
    for (let i = 0; i < 7; i += 1) {
        assert.equal(fa.weekdayIndex(localNoon(2026, 10, 3 + i)), i);
    }
});

test('weekdayIndex is relative to the school week', () => {
    // 2026-10-05 دوشنبه است
    for (let i = 0; i < 7; i += 1) {
        assert.equal(en.weekdayIndex(localNoon(2026, 10, 5 + i), 'MON'), i);
    }
    assert.equal(en.weekdayIndex(localNoon(2026, 10, 3), 'MON'), 5); // شنبه
    assert.equal(en.weekdayIndex(localNoon(2026, 10, 4), 'MON'), 6); // یکشنبه
});

test('weekStartOf: the Saturday week equals the old per-page mostRecentSaturday on every day', () => {
    let checked = 0;
    for (let ms = Date.UTC(2023, 0, 1); ms < Date.UTC(2027, 0, 1); ms += 86400000) {
        const utc = new Date(ms);
        const noon = localNoon(utc.getUTCFullYear(), utc.getUTCMonth() + 1, utc.getUTCDate());
        if (noon.getDate() !== utc.getUTCDate()) continue; // روزِ حذف‌شده در این منطقه
        assert.equal(en.weekStartOf(noon, 'SAT').getTime(), legacyMostRecentSaturday(noon).getTime());
        checked += 1;
    }
    assert.ok(checked > 1400);
});

test('weekStartOf: the Monday week starts on a Monday, never later than the date, less than seven days back', () => {
    for (let ms = Date.UTC(2023, 0, 1); ms < Date.UTC(2027, 0, 1); ms += 86400000) {
        const utc = new Date(ms);
        const noon = localNoon(utc.getUTCFullYear(), utc.getUTCMonth() + 1, utc.getUTCDate());
        if (noon.getDate() !== utc.getUTCDate()) continue;
        const start = en.weekStartOf(noon, 'MON');
        assert.equal(start.getDay(), 1, 'starts on a Monday');
        assert.equal(start.getHours(), 0, 'local midnight');
        const back = Math.round((new Date(noon.getFullYear(), noon.getMonth(), noon.getDate()) - start) / 86400000);
        assert.ok(back >= 0 && back <= 6, `back ${back}`);
    }
    // مرزها: دوشنبه خودش، یکشنبه به دوشنبه‌ی قبل، شنبه به دوشنبه‌ی همان هفته
    assert.equal(en.weekStartOf(localNoon(2026, 10, 5), 'MON').getDate(), 5);
    assert.equal(en.weekStartOf(localNoon(2026, 10, 4), 'MON').getDate(), 28);
    assert.equal(en.weekStartOf(localNoon(2026, 10, 3), 'MON').getDate(), 28);
});

test('school days: Saturday to Wednesday, or Monday to Friday', () => {
    for (const cal of [fa, en]) {
        assert.deepEqual(cal.schoolDayCodes('SAT'), ['SAT', 'SUN', 'MON', 'TUE', 'WED']);
        assert.deepEqual(cal.schoolDayCodes('MON'), ['MON', 'TUE', 'WED', 'THU', 'FRI']);
        assert.deepEqual(cal.weekCodes('SAT'), ['SAT', 'SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI']);
        assert.deepEqual(cal.weekCodes('MON'), ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']);
    }
});

test('weekday names rotate with the week, and the Saturday week is unchanged', () => {
    for (const cal of [fa, en]) {
        assert.deepEqual(cal.weekdayNamesFor('SAT'), cal.weekdayNames);
        assert.deepEqual(cal.weekdayShortFor('SAT'), cal.weekdayShort);
        assert.equal(cal.weekdayNamesFor('MON').length, 7);
        assert.equal(new Set(cal.weekdayShortFor('MON')).size, 7);
        // دوشنبه سومین نامِ فهرستِ شنبه‌اول است
        assert.equal(cal.weekdayNamesFor('MON')[0], cal.weekdayNames[2]);
        assert.equal(cal.weekdayNamesFor('MON')[6], cal.weekdayNames[1]);
    }
    assert.equal(en.weekdayNamesFor('MON')[0], 'Monday');
    assert.equal(en.weekdayNamesFor('MON')[5], 'Saturday');
    assert.equal(fa.weekdayNamesFor('MON')[0], 'دوشنبه');
    assert.equal(fa.weekdayNamesFor('MON')[6], 'یکشنبه');
    // نام‌ها از دادنِ کدِ درست می‌آیند: i‌امین نام همان روزِ weekCodes است
    const ofCode = (code) => en.weekdayNames[['SAT', 'SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI'].indexOf(code)];
    assert.deepEqual(en.weekCodes('MON').map(ofCode), en.weekdayNamesFor('MON'));
});

test('weekStart() reads the selected school from the navbar data', () => {
    const map = JSON.stringify({ 5: 'SAT', 9: 'MON' });
    withNavbar(map, '5', () => assert.equal(Calendar.weekStart(), 'SAT'));
    withNavbar(map, '9', () => {
        assert.equal(Calendar.weekStart(), 'MON');
        // پیش‌فرضِ بی‌آرگومان در بقیه‌ی توابع هم همین مدرسه است
        assert.equal(en.weekdayIndex(localNoon(2026, 10, 5)), 0);
        assert.equal(en.weekStartOf(localNoon(2026, 10, 7)).getDate(), 5);
        assert.deepEqual(en.schoolDayCodes(), ['MON', 'TUE', 'WED', 'THU', 'FRI']);
        assert.equal(en.weekdayNamesFor()[0], 'Monday');
    });
});

test('weekStart() falls back like the navbar: the first school, then Saturday', () => {
    const map = JSON.stringify({ 5: 'MON', 9: 'SAT' });
    withNavbar(map, null, () => assert.equal(Calendar.weekStart(), 'MON'));   // هنوز انتخابی نیست
    withNavbar(map, '77', () => assert.equal(Calendar.weekStart(), 'MON'));   // مدرسه‌ی ناشناخته
    withNavbar(undefined, '5', () => assert.equal(Calendar.weekStart(), 'SAT')); // صفحه‌ی بی‌navbar
    withNavbar('{not json', '5', () => assert.equal(Calendar.weekStart(), 'SAT'));
    withNavbar(JSON.stringify({ 5: 'THU' }), '5', () => assert.equal(Calendar.weekStart(), 'SAT')); // کدِ نامعتبر
});

test('an unknown week code is an error, not a silent Saturday', () => {
    assert.throws(() => en.weekdayIndex(localNoon(2026, 10, 5), 'SUN'), /ناشناخته/);
    assert.throws(() => en.weekCodes('xyz'), /ناشناخته/);
});

test('setWeekStartSource replaces and restores the source', () => {
    Calendar.setWeekStartSource(() => 'MON');
    try {
        assert.equal(fa.weekStart(), 'MON');
    } finally {
        Calendar.setWeekStartSource(null);
    }
    assert.equal(fa.weekStart(), 'SAT');
});
