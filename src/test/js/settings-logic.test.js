// منطقِ خالصِ صفحه‌ی تنظیمات مدرسه: تبدیل پاسخ GET به حالت فرم، «فهرست خالی = همه‌ی نقش‌ها» و ساختِ بدنه‌ی PUTها.
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
require('../../main/resources/static/js/settings-logic.js');
const L = window.SettingsLogic;

const FULL = {
    currentYear: { id: 2, code: '1405-1406' }, autoPromotion: true,
    installmentsEnabled: false, reminderRoles: ['TEACHER'], bot: { enabled: true, notificationsEnabled: false, roles: ['STUDENT', 'TEACHER'] }
};

test('fromResponse shows a section only when its part of the response is non-null', () => {
    const all = L.fromResponse(FULL);
    assert.deepEqual([all.hasYear, all.hasFinance, all.hasReminders, all.hasBot], [true, true, true, true]);
    assert.equal(all.current.id, 2);
    assert.equal(all.autoPromotion, true);
    assert.equal(all.installmentsEnabled, false);
    assert.deepEqual(all.reminderRoles, ['TEACHER']);
    assert.equal(all.botEnabled, true);
    // «اعلان‌ها» فیلدی جدا از «پرسش و پاسخ» است
    assert.equal(all.botNotificationsEnabled, false);
    assert.deepEqual(all.botRoles, ['STUDENT', 'TEACHER']);

    const financeOnly = L.fromResponse({ currentYear: null, autoPromotion: null, installmentsEnabled: true,
        reminderRoles: null, bot: null });
    assert.deepEqual([financeOnly.hasYear, financeOnly.hasFinance, financeOnly.hasReminders, financeOnly.hasBot],
        [false, true, false, false]);
    assert.equal(financeOnly.installmentsEnabled, true);
    assert.equal(financeOnly.current, null);
});

test('an admin without a current year still gets the year section (autoPromotion decides, not currentYear)', () => {
    const state = L.fromResponse({ currentYear: null, autoPromotion: false, installmentsEnabled: null,
        reminderRoles: null, bot: null });
    assert.equal(state.hasYear, true);
    assert.equal(state.current, null);
    assert.equal(state.autoPromotion, false);
});

test('an empty role list means every role and a missing section reads as empty', () => {
    assert.equal(L.isAllRoles([]), true);
    assert.equal(L.isAllRoles(null), true);
    assert.equal(L.isAllRoles(['TEACHER']), false);
    const state = L.fromResponse({ currentYear: null, autoPromotion: null, installmentsEnabled: null,
        reminderRoles: [], bot: { enabled: false, roles: [] } });
    assert.deepEqual(state.reminderRoles, []);
    // پاسخِ قدیمی بدون فیلد: اعلان‌ها روشن (پیش‌فرض)
    assert.equal(state.botNotificationsEnabled, true);
    assert.deepEqual(state.botRoles, []);
    assert.equal(state.botEnabled, false);
});

test('knownSelected drops codes missing from a loaded role list but keeps them when the list is unavailable', () => {
    const roles = [{ code: 'TEACHER', title: 'معلم' }, { code: 'STUDENT', title: 'دانش‌آموز' }];
    assert.deepEqual(L.knownSelected(['TEACHER', 'GONE'], roles), ['TEACHER']);
    assert.deepEqual(L.knownSelected(['TEACHER', 'GONE'], []), ['TEACHER', 'GONE']);
});

test('reminder roles need at least one role and the body follows the role list order', () => {
    const roles = [{ code: 'TEACHER' }, { code: 'STUDENT' }, { code: 'PARENT' }];
    assert.notEqual(L.rolesError([]), '');
    assert.equal(L.rolesError(['PARENT']), '');
    assert.deepEqual(L.reminderBody(['PARENT', 'TEACHER', 'UNKNOWN'], roles), { roleCodes: ['TEACHER', 'PARENT'] });
});

test('bot body: an empty selection stays empty (all roles) and the order follows the role list', () => {
    const roles = [{ code: 'TEACHER' }, { code: 'STUDENT' }];
    assert.deepEqual(L.botBody(true, [], roles), { enabled: true, roles: [] });
    assert.deepEqual(L.botBody(false, ['STUDENT', 'TEACHER'], roles), { enabled: false, roles: ['TEACHER', 'STUDENT'] });
    // فهرست نقش‌ها بارگذاری نشده: انتخابِ فعلی دست‌نخورده می‌رود تا ذخیره آن را پاک نکند
    assert.deepEqual(L.botBody(true, ['STUDENT'], []), { enabled: true, roles: ['STUDENT'] });
});

test('bot body: notificationsEnabled is a separate field and is left out when the state does not know it', () => {
    const roles = [{ code: 'TEACHER' }];
    assert.deepEqual(L.botBody(true, [], roles, false), { enabled: true, notificationsEnabled: false, roles: [] });
    assert.deepEqual(L.botBody(false, [], roles, true), { enabled: false, notificationsEnabled: true, roles: [] });
    assert.equal('notificationsEnabled' in L.botBody(true, [], roles), false);
});

test('yearBody sends the chosen id and the current year seen for the optimistic-concurrency check', () => {
    assert.deepEqual(L.yearBody('3', { id: 2 }), { academicYearId: 3, expectedCurrentAcademicYearId: 2 });
    assert.deepEqual(L.yearBody('3', null), { academicYearId: 3, expectedCurrentAcademicYearId: null });
});

test('attributableYears lists years up to and including the current one', () => {
    const years = [{ id: 1, code: 'a' }, { id: 2, code: 'b' }, { id: 3, code: 'c' }];
    assert.deepEqual(L.attributableYears(years, { id: 2 }).map((y) => y.code), ['a', 'b']);
    assert.deepEqual(L.attributableYears(years, null), []);
    assert.deepEqual(L.attributableYears(years, { id: 99 }), []);
});

test('section save counters stay monotonic across school switches (A -> B -> A)', () => {
    let sec = L.emptySections();
    const first = ++sec.year.seq; // save started on school A
    sec = L.emptySections(sec); // switch to B (load resets state)
    sec = L.emptySections(sec); // switch back to A
    const second = ++sec.year.seq; // new save on A
    assert.notEqual(second, first);
    assert.equal(L.isLatest(sec, { key: 'year', n: first }), false, 'late response of the first save is stale');
    assert.equal(L.isLatest(sec, { key: 'year', n: second }), true);
    for (const key of ['year', 'finance', 'reminders', 'bot']) {
        assert.deepEqual([sec[key].error, sec[key].notice, sec[key].saving], ['', '', false]);
    }
});
