// منطقِ خالصِ «برگه‌ی حضور و غیابِ دانش‌آموز»: بازه از آدرس، مسیر API، ترتیب و برچسبِ روزها، متنِ درصد (بدون مرورگر).
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
require('../../main/resources/static/js/attendance-sheet-logic.js');

const L = AttendanceSheetLogic;

// قالب‌بندیِ ساختگی: خوانا و بی‌وابستگی به Calendar
const FMT = {
    date: (iso) => 'D' + iso,
    long: (iso) => 'L' + iso,
    weekday: (iso) => 'W' + iso,
    digits: (value) => 'N' + value
};

test('the range comes from ?from=&to= and must be real, ordered ISO dates', () => {
    assert.deepEqual(L.rangeFromSearch('?from=2026-09-23&to=2026-10-14'), { from: '2026-09-23', to: '2026-10-14' });
    assert.deepEqual(L.rangeFromSearch('?to=2026-10-14&from=2026-10-14'), { from: '2026-10-14', to: '2026-10-14' });
    assert.equal(L.rangeFromSearch(''), null);
    assert.equal(L.rangeFromSearch('?from=2026-09-23'), null);
    assert.equal(L.rangeFromSearch('?from=2026-10-14&to=2026-09-23'), null);
    assert.equal(L.rangeFromSearch('?from=2026-02-30&to=2026-03-01'), null);
    assert.equal(L.rangeFromSearch('?from=۱۴۰۵/۰۷/۰۱&to=۱۴۰۵/۰۷/۳۰'), null);
    assert.equal(L.rangeFromSearch('?from=2026-9-23&to=2026-10-14'), null);
});

test('the API path carries the school, the student and the ISO range', () => {
    assert.equal(L.apiPath(3, 41, { from: '2026-09-23', to: '2026-10-14' }),
        '/api/admin/schools/3/attendance/report/students/41?from=2026-09-23&to=2026-10-14');
});

test('status codes have Persian labels and an unknown code shows as-is', () => {
    assert.equal(L.statusLabel('ABSENT'), 'غایب');
    assert.equal(L.statusLabel('LATE'), 'تأخیر');
    assert.equal(L.statusLabel('SOMETHING'), 'SOMETHING');
});

test('days are listed oldest first, then by class name, without mutating the input', () => {
    const days = [
        { day: '2026-10-05', classId: 2, className: 'ب', status: 'LATE', excused: false, note: null },
        { day: '2026-10-01', classId: 2, className: 'ب', status: 'ABSENT', excused: true, note: 'سرما‌خوردگی' },
        { day: '2026-10-05', classId: 1, className: 'الف', status: 'ABSENT', excused: false, note: 'x' }
    ];
    const lines = L.dayLines(days, FMT);
    assert.deepEqual(lines.map((l) => l.date), ['D2026-10-01', 'D2026-10-05', 'D2026-10-05']);
    assert.deepEqual(lines.map((l) => l.className), ['ب', 'الف', 'ب']);
    assert.equal(days[0].day, '2026-10-05');
    assert.equal(lines[0].weekday, 'W2026-10-01');
    assert.equal(lines[0].statusLabel, 'غایب');
    assert.equal(lines[0].excused, true);
    assert.equal(lines[0].note, 'سرما‌خوردگی');
    assert.equal(lines[2].note, '');
    assert.equal(new Set(lines.map((l) => l.key)).size, 3);
    assert.deepEqual(L.dayLines(null, FMT), []);
});

test('only an absence can be excused', () => {
    const [late] = L.dayLines([{ day: '2026-10-05', classId: 1, className: 'الف', status: 'LATE', excused: true, note: null }], FMT);
    assert.equal(late.excused, false);
});

test('the percent shows as text with a Persian percent sign, a dash when unknown', () => {
    assert.equal(L.percentLabel(92, FMT), 'N92٪');
    assert.equal(L.percentLabel(0, FMT), 'N0٪');
    assert.equal(L.percentLabel(null, FMT), '—');
    assert.equal(L.percentLabel(undefined, FMT), '—');
});

test('the bar value stays within 0..100', () => {
    assert.equal(L.barValue(null), 0);
    assert.equal(L.barValue(55), 55);
    assert.equal(L.barValue(140), 100);
    assert.equal(L.barValue(-3), 0);
});

test('class rows add up to absent, late and present totals', () => {
    const rows = [
        { absentDays: 2, lateDays: 1, presentDays: 18 },
        { absentDays: 1, lateDays: 0, presentDays: 9 }
    ];
    assert.deepEqual(L.totals(rows), { absentDays: 3, lateDays: 1, presentDays: 27 });
    assert.deepEqual(L.totals([]), { absentDays: 0, lateDays: 0, presentDays: 0 });
});

test('the range is written from the long date format', () => {
    assert.equal(L.rangeText({ from: '2026-09-23', to: '2026-10-14' }, FMT), 'L2026-09-23 تا L2026-10-14');
    assert.equal(L.rangeText(null, FMT), '');
});
