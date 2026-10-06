// منطقِ خالصِ «تعطیلی‌های سراسری» (/platform/holidays): انتخابِ سالِ پیش‌فرض و استفاده‌ی دوباره از
// گروه‌بندیِ ماهانه‌ی برنامه‌ی سالانه برای ردیف‌هایی که source ندارند ({ id, day, title }).
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
const JS = '../../main/resources/static/js/';
require(JS + 'jalali.js');
require(JS + 'calendar.js');
require(JS + 'date-input.js');
require(JS + 'yearly-plan-logic.js');

const iso = (jalali) => DateInput.parse(jalali);
const year = (id, code, from, to) => ({ id, code, startDate: iso(from), endDate: iso(to) });
const Y1404 = year(1, '1404-1405', '1404/07/01', '1405/03/31');
const Y1405 = year(2, '1405-1406', '1405/07/01', '1406/03/31');
const Y1406 = year(3, '1406-1407', '1406/07/01', '1407/03/31');

test('the default year is the latest one whose range contains today', () => {
    const years = [Y1404, Y1405, Y1406];
    assert.equal(YearlyPlanLogic.pickYear(years, iso('1405/08/15')), Y1405);
    assert.equal(YearlyPlanLogic.pickYear(years, Y1405.startDate), Y1405);
    assert.equal(YearlyPlanLogic.pickYear(years, Y1405.endDate), Y1405);
    // دو بازه‌ی هم‌پوشان: جدیدتر برنده است
    const overlapping = [Y1405, { ...Y1406, startDate: iso('1406/02/01') }];
    assert.equal(YearlyPlanLogic.pickYear(overlapping, iso('1406/03/10')), overlapping[1]);
});

test('with no year containing today the default is the last year, and none gives null', () => {
    assert.equal(YearlyPlanLogic.pickYear([Y1404, Y1405], iso('1409/01/01')), Y1405);
    // بین دو سال (تابستان)
    assert.equal(YearlyPlanLogic.pickYear([Y1404, Y1405], iso('1405/05/10')), Y1405);
    assert.equal(YearlyPlanLogic.pickYear([Y1404], iso('1300/01/01')), Y1404);
    assert.equal(YearlyPlanLogic.pickYear([], iso('1405/08/15')), null);
});

test('platform holidays group by Jalali month in order without a source field', () => {
    const items = [
        { id: 3, day: iso('1406/01/01'), title: 'نوروز' },
        { id: 1, day: iso('1405/07/10'), title: 'تعطیلی الف' },
        { id: 2, day: iso('1405/07/05'), title: 'تعطیلی ب' }
    ];
    const groups = YearlyPlanLogic.groupByMonth(items);

    assert.deepEqual(groups.map((g) => g.title), ['مهر ۱۴۰۵', 'فروردین ۱۴۰۶']);
    assert.deepEqual(groups[0].items.map((i) => i.id), [2, 1]);
});

test('the year line and range help use the year default range with Persian digits', () => {
    assert.equal(YearlyPlanLogic.yearLine(Y1405), 'سال تحصیلی ۱۴۰۵-۱۴۰۶ (۱ مهر ۱۴۰۵ تا ۳۱ خرداد ۱۴۰۶)');
    assert.ok(YearlyPlanLogic.rangeHelp(Y1405).includes('۱ مهر ۱۴۰۵'));
    assert.ok(YearlyPlanLogic.inRange(iso('1405/10/01'), Y1405));
    assert.ok(!YearlyPlanLogic.inRange(iso('1406/04/01'), Y1405));
});
