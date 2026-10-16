// منطقِ خالصِ «گزارش حضور و غیاب»: متنِ درصد، مقدارِ نوار، نامِ معلم و ترتیبِ معلم‌ها، مسیرِ API و پیوندِ برگه (بدون مرورگر).
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
require('../../main/resources/static/js/attendance-report-logic.js');

const L = AttendanceReportLogic;
const digits = (value) => 'N' + value;

test('the percent label is the number with a percent sign, or a dash when there is no base', () => {
    assert.equal(L.percentLabel(87, digits), 'N87٪');
    assert.equal(L.percentLabel(0, digits), 'N0٪');
    assert.equal(L.percentLabel(100, digits), 'N100٪');
    assert.equal(L.percentLabel(null, digits), '—');
    assert.equal(L.percentLabel(undefined, digits), '—');
});

test('the bar value is clamped to 0..100 and a missing percent draws an empty bar', () => {
    assert.equal(L.barValue(42), 42);
    assert.equal(L.barValue(0), 0);
    assert.equal(L.barValue(140), 100);
    assert.equal(L.barValue(-5), 0);
    assert.equal(L.barValue(null), 0);
    assert.equal(L.barValue('x'), 0);
    assert.equal(L.hasPercent(0), true);
    assert.equal(L.hasPercent(null), false);
});

test('a teacher without a name is labelled with the user id', () => {
    assert.equal(L.teacherLabel({ teacherUserId: 9, teacherName: 'علی رضایی' }, digits), 'علی رضایی');
    assert.equal(L.teacherLabel({ teacherUserId: 9, teacherName: null }, digits), 'نامشخص (شناسه N9)');
    assert.equal(L.teacherLabel({ teacherUserId: 9, teacherName: '  ' }, digits), 'نامشخص (شناسه N9)');
});

test('teachers are sorted by Persian name with unknown ones last, without mutating the input', () => {
    const input = [
        { teacherUserId: 30, teacherName: null },
        { teacherUserId: 2, teacherName: 'کاظمی' },
        { teacherUserId: 12, teacherName: null },
        { teacherUserId: 1, teacherName: 'احمدی' }
    ];
    const sorted = L.sortedTeachers(input);
    assert.deepEqual(sorted.map((t) => t.teacherUserId), [1, 2, 12, 30]);
    assert.deepEqual(input.map((t) => t.teacherUserId), [30, 2, 12, 1]);
    assert.deepEqual(L.sortedTeachers(null), []);
});

test('API paths carry the school, the kind and the ISO range; the class filter only when set', () => {
    const range = { from: '2026-09-23', to: '2026-10-14' };
    assert.equal(L.reportPath(3, '', range), '/api/admin/schools/3/attendance/report?from=2026-09-23&to=2026-10-14');
    assert.equal(L.reportPath(3, 'teachers', range), '/api/admin/schools/3/attendance/report/teachers?from=2026-09-23&to=2026-10-14');
    assert.equal(L.reportPath(3, 'classes', range, '7'), '/api/admin/schools/3/attendance/report/classes?from=2026-09-23&to=2026-10-14&classId=7');
    assert.equal(L.reportPath(3, 'classes', range, ''), '/api/admin/schools/3/attendance/report/classes?from=2026-09-23&to=2026-10-14');
});

test('the student sheet link carries the ISO range', () => {
    assert.equal(L.sheetHref(41, { from: '2026-09-23', to: '2026-10-14' }),
        '/attendance/report/student/41?from=2026-09-23&to=2026-10-14');
});
