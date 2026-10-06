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

test('months outside Mehr..Khordad appear in calendar order for an extended range', () => {
    const items = [
        { id: 1, source: 'SCHOOL', day: iso('1406/05/10'), title: 'کارگاه تابستانی' },
        { id: 2, source: 'SCHOOL', day: iso('1406/03/10'), title: 'پایان سال' },
        { id: 3, source: 'SCHOOL', day: iso('1406/06/02'), title: 'ثبت‌نام' },
        { id: 4, source: 'SCHOOL', day: iso('1405/07/02'), title: 'جلسه' }
    ];
    assert.deepEqual(YearlyPlanLogic.groupByMonth(items).map((g) => g.title),
        ['مهر ۱۴۰۵', 'خرداد ۱۴۰۶', 'مرداد ۱۴۰۶', 'شهریور ۱۴۰۶']);

    // بازه‌ای که از مرداد شروع می‌شود: مرداد اول می‌آید
    const early = [
        { id: 1, source: 'SCHOOL', day: iso('1405/07/05'), title: 'الف' },
        { id: 2, source: 'SCHOOL', day: iso('1405/05/20'), title: 'ب' }
    ];
    assert.deepEqual(YearlyPlanLogic.groupByMonth(early).map((g) => g.title), ['مرداد ۱۴۰۵', 'مهر ۱۴۰۵']);
});

test('effective range falls back to the year defaults and the custom tag follows range.custom', () => {
    const custom = { startDate: iso('1405/05/20'), endDate: iso('1406/05/31'), custom: true };
    assert.deepEqual(YearlyPlanLogic.effectiveRange(YEAR, null), { startDate: YEAR.startDate, endDate: YEAR.endDate });
    assert.deepEqual(YearlyPlanLogic.effectiveRange(YEAR, undefined), { startDate: YEAR.startDate, endDate: YEAR.endDate });
    assert.deepEqual(YearlyPlanLogic.effectiveRange(YEAR, custom),
        { startDate: custom.startDate, endDate: custom.endDate });

    assert.equal(YearlyPlanLogic.yearLine(YEAR, null), YearlyPlanLogic.yearLine(YEAR));
    assert.equal(YearlyPlanLogic.yearLine(YEAR, custom), 'سال تحصیلی ۱۴۰۵-۱۴۰۶ (۲۰ مرداد ۱۴۰۵ تا ۳۱ مرداد ۱۴۰۶)');
    assert.ok(YearlyPlanLogic.isCustom(custom));
    assert.ok(!YearlyPlanLogic.isCustom({ ...custom, custom: false }));
    assert.ok(!YearlyPlanLogic.isCustom(null));
    assert.equal(YearlyPlanLogic.CUSTOM_TAG, 'بازه‌ی سفارشی');

    const help = YearlyPlanLogic.rangeHelp(YEAR, custom);
    assert.ok(help.includes('۲۰ مرداد ۱۴۰۵') && help.includes('۳۱ مرداد ۱۴۰۶') && !help.includes('۳۱ خرداد'), help);
    assert.ok(YearlyPlanLogic.inRange(iso('1406/05/15'), custom));
    assert.ok(!YearlyPlanLogic.inRange(iso('1406/05/15'), YEAR));
});

test('the range modal help states that the default is the minimum', () => {
    assert.equal(YearlyPlanLogic.defaultRangeHelp(YEAR),
        'بازه‌ی پیش‌فرض (از ۱ مهر ۱۴۰۵ تا ۳۱ خرداد ۱۴۰۶) حداقل بازه است؛ فقط می‌توانید آن را گسترش دهید.');
});

test('range validation is extend-only and returns message keys', () => {
    const v = (a, b) => YearlyPlanLogic.validateRange(a, b, YEAR);

    // دقیقاً پیش‌فرض و گسترش، هر دو درست‌اند؛ ارقام فارسی هم پذیرفته می‌شود
    assert.deepEqual(v('۱۴۰۵/۰۷/۰۱', '1406/03/31'),
        { startDate: YEAR.startDate, endDate: YEAR.endDate, startError: '', endError: '' });
    const wide = v('1405/05/20', '۱۴۰۶/۰۵/۳۱');
    assert.equal(wide.startError, '');
    assert.equal(wide.endError, '');
    assert.equal(wide.startDate, iso('1405/05/20'));
    assert.equal(wide.endDate, iso('1406/05/31'));

    assert.equal(v('', '1406/03/31').startError, 'required');
    assert.equal(v('1405/07/01', '  ').endError, 'required');
    assert.equal(v('abc', '1406/03/31').startError, 'invalid');
    assert.equal(v('1405/07/01', '1406/13/01').endError, 'invalid');
    assert.equal(v('1405/07/02', '1406/03/31').startError, 'startAfterDefault');
    assert.equal(v('1405/07/01', '1406/03/30').endError, 'endBeforeDefault');
    // هر دو فیلد مستقل گزارش می‌شوند
    const both = v('1405/08/01', '1406/02/01');
    assert.equal(both.startError, 'startAfterDefault');
    assert.equal(both.endError, 'endBeforeDefault');

    for (const key of ['required', 'invalid', 'startAfterDefault', 'endBeforeDefault', 'startAfterEnd']) {
        assert.ok(YearlyPlanLogic.rangeMessage(key, YEAR).length > 0, key);
    }
    assert.equal(YearlyPlanLogic.rangeMessage('', YEAR), '');
    assert.ok(YearlyPlanLogic.rangeMessage('startAfterDefault', YEAR).includes('۱ مهر ۱۴۰۵'));
    assert.ok(YearlyPlanLogic.rangeMessage('endBeforeDefault', YEAR).includes('۳۱ خرداد ۱۴۰۶'));
});

test('range check is inclusive and the example date always lies inside the year', () => {
    assert.ok(YearlyPlanLogic.inRange(YEAR.startDate, YEAR));
    assert.ok(YearlyPlanLogic.inRange(YEAR.endDate, YEAR));
    assert.ok(!YearlyPlanLogic.inRange(iso('1405/06/31'), YEAR));
    assert.ok(!YearlyPlanLogic.inRange(iso('1406/04/01'), YEAR));

    assert.equal(YearlyPlanLogic.exampleDate(YEAR, iso('1405/09/10')), '۱۴۰۵/۰۹/۱۰');
    assert.equal(YearlyPlanLogic.exampleDate(YEAR, iso('1407/01/01')), '۱۴۰۵/۰۷/۰۱');
});
