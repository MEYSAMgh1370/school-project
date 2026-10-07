// منطقِ خالصِ «رویدادهای نزدیک» پیشخوان (بدون مرورگر و Alpine).
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
const JS = '../../main/resources/static/js/';
require(JS + 'jalali.js');
require(JS + 'calendar.js');
require(JS + 'date-input.js');
require(JS + 'upcoming-events-logic.js');

const iso = (jalali) => DateInput.parse(jalali);

test('relative labels: today, tomorrow, N days with Persian digits', () => {
    assert.equal(UpcomingEventsLogic.relativeLabel(0), 'امروز');
    assert.equal(UpcomingEventsLogic.relativeLabel(1), 'فردا');
    assert.equal(UpcomingEventsLogic.relativeLabel(3), '۳ روز دیگر');
    assert.equal(UpcomingEventsLogic.relativeLabel(14), '۱۴ روز دیگر');
    assert.equal(UpcomingEventsLogic.relativeLabel(-1), '');
    assert.equal(UpcomingEventsLogic.relativeLabel(null), '');
    assert.equal(UpcomingEventsLogic.relativeLabel('2'), '');
});

test('rows show a Jalali short date, a relative label, and keep day order', () => {
    const rows = UpcomingEventsLogic.rows({
        events: [
            { id: 2, day: iso('1405/07/20'), title: 'اردو', daysLeft: 3 },
            { id: 1, day: iso('1405/07/18'), title: 'جلسه‌ی اولیا', daysLeft: 1 }
        ]
    });
    assert.deepEqual(rows, [
        { id: 1, title: 'جلسه‌ی اولیا', date: '۱۴۰۵/۰۷/۱۸', relative: 'فردا' },
        { id: 2, title: 'اردو', date: '۱۴۰۵/۰۷/۲۰', relative: '۳ روز دیگر' }
    ]);
});

test('rows tolerate empty, missing and malformed payloads', () => {
    assert.deepEqual(UpcomingEventsLogic.rows({ events: [] }), []);
    assert.deepEqual(UpcomingEventsLogic.rows(null), []);
    assert.deepEqual(UpcomingEventsLogic.rows({}), []);
    assert.deepEqual(UpcomingEventsLogic.rows({ events: [{ id: 1, day: 'x', title: 'الف', daysLeft: 0 }, null] }), []);
});
