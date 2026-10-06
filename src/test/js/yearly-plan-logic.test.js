// منطقِ خالصِ «برنامه‌ی سالانه»: گروه‌بندیِ ماهانه و متن‌های بازه‌ی سال (بدون مرورگر و Alpine).
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
const JS = '../../main/resources/static/js/';
require(JS + 'jalali.js');
require(JS + 'calendar.js');
require(JS + 'date-input.js');
require(JS + 'yearly-plan-logic.js');

const iso = (jalali) => DateInput.parse(jalali);
const YEAR = { id: 7, code: '1405-1406', startDate: iso('1405/07/01'), endDate: iso('1406/03/31') };

test('item keys combine source and id so platform and school ids never collide', () => {
    assert.notEqual(
        YearlyPlanLogic.itemKey({ source: 'PLATFORM', id: 3 }),
        YearlyPlanLogic.itemKey({ source: 'SCHOOL', id: 3 }));
    assert.equal(YearlyPlanLogic.itemKey({ source: 'SCHOOL', id: 3 }), 'SCHOOL:3');
});

test('groups items by Jalali month in school-year order and skips empty months', () => {
    const items = [
        { id: 1, source: 'SCHOOL', day: iso('1406/03/10'), title: 'پایان سال' },
        { id: 2, source: 'SCHOOL', day: iso('1405/08/15'), title: 'اردو' },
        { id: 3, source: 'PLATFORM', day: iso('1405/07/05'), title: 'تعطیلی ب' },
        { id: 3, source: 'SCHOOL', day: iso('1405/07/05'), title: 'تعطیلی الف' },
        { id: 4, source: 'SCHOOL', day: iso('1405/07/02'), title: 'جلسه' }
    ];

    const groups = YearlyPlanLogic.groupByMonth(items);

    assert.deepEqual(groups.map((g) => g.title), ['مهر ۱۴۰۵', 'آبان ۱۴۰۵', 'خرداد ۱۴۰۶']);
    assert.deepEqual(groups[0].items.map((i) => i.title), ['جلسه', 'تعطیلی الف', 'تعطیلی ب']);
    assert.equal(new Set(groups.map((g) => g.key)).size, 3);
    assert.deepEqual(YearlyPlanLogic.groupByMonth([]), []);
});

test('the year line and range help use Persian digits and Jalali long dates', () => {
    assert.equal(YearlyPlanLogic.yearLine(YEAR), 'سال تحصیلی ۱۴۰۵-۱۴۰۶ (۱ مهر ۱۴۰۵ تا ۳۱ خرداد ۱۴۰۶)');
    const help = YearlyPlanLogic.rangeHelp(YEAR);
    assert.ok(help.includes('۱ مهر ۱۴۰۵') && help.includes('۳۱ خرداد ۱۴۰۶'), help);
});

test('range check is inclusive and the example date always lies inside the year', () => {
    assert.ok(YearlyPlanLogic.inRange(YEAR.startDate, YEAR));
    assert.ok(YearlyPlanLogic.inRange(YEAR.endDate, YEAR));
    assert.ok(!YearlyPlanLogic.inRange(iso('1405/06/31'), YEAR));
    assert.ok(!YearlyPlanLogic.inRange(iso('1406/04/01'), YEAR));

    assert.equal(YearlyPlanLogic.exampleDate(YEAR, iso('1405/09/10')), '۱۴۰۵/۰۹/۱۰');
    assert.equal(YearlyPlanLogic.exampleDate(YEAR, iso('1407/01/01')), '۱۴۰۵/۰۷/۰۱');
});
