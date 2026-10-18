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
        'بازه‌ی پیش‌فرض (از ۱ مهر ۱۴۰۵ تا ۳۱ خرداد ۱۴۰۶) حداقل بازه است؛ فقط می‌توانید آن را گسترش دهید، '
        + 'حداکثر یک ماه از هر طرف (از ۱ شهریور ۱۴۰۵ تا ۳۱ تیر ۱۴۰۶).');
});

test('range validation is extend-only and returns message keys', () => {
    const v = (a, b) => YearlyPlanLogic.validateRange(a, b, YEAR);

    // دقیقاً پیش‌فرض و گسترش، هر دو درست‌اند؛ ارقام فارسی هم پذیرفته می‌شود
    assert.deepEqual(v('۱۴۰۵/۰۷/۰۱', '1406/03/31'),
        { startDate: YEAR.startDate, endDate: YEAR.endDate, startError: '', endError: '' });
    const wide = v('1405/06/15', '۱۴۰۶/۰۴/۲۰');
    assert.equal(wide.startError, '');
    assert.equal(wide.endError, '');
    assert.equal(wide.startDate, iso('1405/06/15'));
    assert.equal(wide.endDate, iso('1406/04/20'));

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

test('reminder labels use Persian digits and the option list starts with "none"', () => {
    assert.equal(YearlyPlanLogic.remindLabel(0), 'همان روز');
    assert.equal(YearlyPlanLogic.remindLabel(3), '۳ روز قبل');
    assert.equal(YearlyPlanLogic.remindLabel(14), '۱۴ روز قبل');
    assert.equal(YearlyPlanLogic.remindLabel(null), '');
    assert.equal(YearlyPlanLogic.remindLabel(-1), '');
    assert.equal(YearlyPlanLogic.remindLabel('3'), '');

    const options = YearlyPlanLogic.remindOptions();
    assert.deepEqual(options.map((o) => o.value), ['', '0', '1', '2', '3', '7', '14']);
    assert.deepEqual(options.map((o) => o.label), [
        'بدون یادآوری', 'همان روز', '۱ روز قبل', '۲ روز قبل', '۳ روز قبل', '۷ روز قبل', '۱۴ روز قبل']);
});

test('the select value becomes a number or null on the wire', () => {
    assert.equal(YearlyPlanLogic.remindValue(''), null);
    assert.equal(YearlyPlanLogic.remindValue(null), null);
    assert.equal(YearlyPlanLogic.remindValue(undefined), null);
    assert.equal(YearlyPlanLogic.remindValue('0'), 0);
    assert.equal(YearlyPlanLogic.remindValue('7'), 7);
    assert.equal(YearlyPlanLogic.remindValue('30'), 30);
    assert.equal(YearlyPlanLogic.remindValue('31'), null);
    assert.equal(YearlyPlanLogic.remindValue('x'), null);
    assert.equal(YearlyPlanLogic.remindValue('1.5'), null);

    assert.deepEqual(YearlyPlanLogic.addBody(7, '2026-10-13', 'جلسه', '3'),
        { academicYearId: 7, day: '2026-10-13', title: 'جلسه', remindDaysBefore: 3 });
    assert.deepEqual(YearlyPlanLogic.addBody(7, '2026-10-13', 'جلسه', ''),
        { academicYearId: 7, day: '2026-10-13', title: 'جلسه', remindDaysBefore: null });
    assert.equal(YearlyPlanLogic.addBody(7, '2026-10-13', 'جلسه', '0').remindDaysBefore, 0);
});

test('the reminder tag shows only for school events that have a lead time', () => {
    assert.equal(YearlyPlanLogic.remindTag({ source: 'SCHOOL', remindDaysBefore: 3 }), 'یادآوری: ۳ روز قبل');
    assert.equal(YearlyPlanLogic.remindTag({ source: 'SCHOOL', remindDaysBefore: 0 }), 'یادآوری: همان روز');
    assert.equal(YearlyPlanLogic.remindTag({ source: 'SCHOOL', remindDaysBefore: null }), '');
    assert.equal(YearlyPlanLogic.remindTag({ source: 'SCHOOL' }), '');
    assert.equal(YearlyPlanLogic.remindTag({ source: 'PLATFORM', remindDaysBefore: 3 }), '');
    assert.equal(YearlyPlanLogic.remindTag(null), '');
});

test('reminder-recipient logic lives in settings, not in the yearly plan', () => {
    for (const name of ['rolesState', 'rolesError', 'rolesBody']) {
        assert.equal(YearlyPlanLogic[name], undefined, name);
    }
});

test('the extension is capped at one Jalali month on each side', () => {
    const limits = YearlyPlanLogic.rangeLimits(YEAR);
    assert.equal(limits.earliestStart, iso('1405/06/01'));
    assert.equal(limits.latestEnd, iso('1406/04/31'));

    const v = (a, b) => YearlyPlanLogic.validateRange(a, b, YEAR);
    // دقیقاً روی سقف مجاز است
    const atCap = v('1405/06/01', '1406/04/31');
    assert.equal(atCap.startError, '');
    assert.equal(atCap.endError, '');
    // یک روز بیرونِ سقف
    assert.equal(v('1405/05/31', '1406/03/31').startError, 'startTooEarly');
    assert.equal(v('1405/07/01', '1406/05/01').endError, 'endTooLate');
    // گسترشِ کوچک‌تر از سقف مجاز است و «کوچک‌کردن» همچنان پیامِ خودش را دارد
    assert.equal(v('1405/06/15', '1406/04/10').startError, '');
    assert.equal(v('1405/07/02', '1406/03/31').startError, 'startAfterDefault');

    assert.ok(YearlyPlanLogic.rangeMessage('startTooEarly', YEAR).includes('۱ شهریور ۱۴۰۵'));
    assert.ok(YearlyPlanLogic.rangeMessage('endTooLate', YEAR).includes('۳۱ تیر ۱۴۰۶'));
});
