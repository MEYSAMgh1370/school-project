// منطقِ خالصِ صفحه‌ی «سابقه‌ی تغییرات مالی»: برچسب‌ها، خلاصه‌ی یک‌خطی، جزئیات، اعتبارسنجی فیلتر و query string
// (بدون مرورگر و Alpine).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

global.window = globalThis;
const JS = '../../main/resources/static/js/';
require(JS + 'digits.js');
require(JS + 'finance-audit-logic.js');

// فرمتِ ساختگی: خوانا و بی‌وابستگی به Calendar/Money
const FMT = {
    amount: (value) => 'M' + value,
    date: (iso) => 'D' + iso,
    number: (value) => 'N' + value
};
const toAscii = Digits.toAscii;
const L = FinanceAuditLogic;

test('every action code has a Persian label and an unknown code shows as-is', () => {
    assert.equal(L.actionLabel('INVOICE_ISSUED'), 'صدور فاکتور');
    assert.equal(L.actionLabel('PAYMENT_RECORDED'), 'ثبت پرداخت');
    assert.equal(L.actionLabel('INVOICE_DISCOUNTED'), 'ثبت تخفیف');
    assert.equal(L.actionLabel('DISCOUNT_EARNED'), 'تخفیف مشروط قطعی شد');
    assert.equal(L.actionLabel('DISCOUNT_LAPSED'), 'تخفیف مشروط ساقط شد');
    assert.equal(L.actionLabel('INSTALLMENTS_CHANGED'), 'تغییر اقساط');
    assert.equal(L.actionLabel('INSTALLMENTS_SETTING_CHANGED'), 'تغییر تنظیم اقساط');
    ['EXPENSE_RECORDED', 'EXPENSE_APPROVED', 'EXPENSE_PAID', 'EXPENSE_CANCELLED'].forEach((code) => {
        assert.notEqual(L.actionLabel(code), code);
    });
    assert.equal(L.actionLabel('SOMETHING_NEW'), 'SOMETHING_NEW');
    const options = L.actionOptions();
    assert.equal(options.length, 11);
    assert.ok(options.every((o) => o.code && o.label));
});

test('the actor is the name, or «سیستم» when there is no user', () => {
    assert.equal(L.actorLabel({ actorUserId: 4, actorName: 'سارا احمدی' }), 'سارا احمدی');
    assert.equal(L.actorLabel({ actorUserId: null, actorName: null }), 'سیستم');
    assert.equal(L.actorLabel({ actorUserId: 4, actorName: null }), '—');
});

test('summaries are plain Persian sentences built from details', () => {
    assert.equal(L.summarize({ action: 'PAYMENT_RECORDED', details: { method: 'CASH', note: null } }, FMT), 'پرداخت نقدی');
    assert.equal(L.summarize({ action: 'PAYMENT_RECORDED', details: { method: 'CARD', note: 'قسط اول' } }, FMT),
        'پرداخت با کارت — قسط اول');
    assert.equal(L.summarize({ action: 'PAYMENT_RECORDED', details: null }, FMT), 'پرداخت');
    assert.equal(L.summarize({ action: 'INVOICE_DISCOUNTED', amount: 2000000,
        details: { deadline: '2026-10-12', status: 'PENDING', reason: null } }, FMT), 'تخفیف M2000000 تا D2026-10-12 مشروط');
    assert.equal(L.summarize({ action: 'INVOICE_DISCOUNTED', amount: 5,
        details: { deadline: null, status: 'APPLIED', reason: 'برادر' } }, FMT), 'تخفیف M5 — برادر');
    assert.equal(L.summarize({ action: 'DISCOUNT_EARNED', amount: 5, details: { trigger: 'payment' } }, FMT),
        'تخفیف M5 قطعی شد (با ثبت پرداخت)');
    assert.equal(L.summarize({ action: 'DISCOUNT_LAPSED', amount: 5, details: { trigger: 'sweep' } }, FMT),
        'تخفیف M5 ساقط شد (بررسی خودکار)');
    assert.equal(L.summarize({ action: 'INSTALLMENTS_CHANGED', details: { cause: 'EDITED' } }, FMT), 'اقساط: ویرایش دستی');
    assert.equal(L.summarize({ action: 'INSTALLMENTS_CHANGED', details: { cause: 'WHATEVER' } }, FMT), 'اقساط: WHATEVER');
    assert.equal(L.summarize({ action: 'INSTALLMENTS_SETTING_CHANGED', details: { from: false, to: true } }, FMT),
        'تنظیم اقساط: خاموش ← روشن');
    assert.equal(L.summarize({ action: 'EXPENSE_PAID', expenseId: 9, details: null }, FMT), 'هزینه‌ی شماره‌ی N9');
    assert.equal(L.summarize({ action: 'INVOICE_ISSUED', details: null }, FMT), 'فاکتور جدید صادر شد');
    assert.equal(L.summarize({ action: 'SOMETHING_NEW', details: null }, FMT), '');
});

test('details may arrive as a JSON string; broken JSON is treated as no details', () => {
    assert.deepEqual(L.detailsOf({ details: '{"a":1}' }), { a: 1 });
    assert.equal(L.detailsOf({ details: '{oops' }), null);
    assert.equal(L.detailsOf({ details: null }), null);
    assert.equal(L.detailsOf({ details: [1] }), null);
});

test('installment changes give a before and an after table; undated rows say so', () => {
    const item = {
        action: 'INSTALLMENTS_CHANGED',
        details: {
            cause: 'DISCOUNT_LAPSE_RESPLIT',
            before: [{ amount: 100, amountPaid: 100, dueDate: '2026-10-01' }, { amount: 50, amountPaid: 0, dueDate: null }],
            after: []
        }
    };
    const tables = L.installmentTables(item);
    assert.equal(tables.before.length, 2);
    assert.equal(tables.after.length, 0);
    assert.equal(L.dueLabel(tables.before[0], (iso) => 'D' + iso), 'D2026-10-01');
    assert.equal(L.dueLabel(tables.before[1], () => assert.fail('format must not run for an undated row')), 'بدون سررسید');
    assert.equal(L.installmentTables({ action: 'PAYMENT_RECORDED', details: {} }), null);
    assert.equal(L.installmentTables({ action: 'INSTALLMENTS_CHANGED', details: null }), null);
    assert.deepEqual(L.detailEntries(item, FMT), []);
    assert.equal(L.hasDetails(item, FMT), true);
});

test('other actions list key/value entries, skipping empty values', () => {
    const payment = { action: 'PAYMENT_RECORDED', details: { paymentId: 7, method: 'BANK_TRANSFER', note: null } };
    assert.deepEqual(L.detailEntries(payment, FMT), [
        { label: 'شناسه‌ی پرداخت', value: 'N7' },
        { label: 'روش پرداخت', value: 'انتقال بانکی' }
    ]);
    const discount = { action: 'INVOICE_DISCOUNTED', details: { discountId: 3, reason: 'برادر', deadline: '2026-10-12', status: 'PENDING' } };
    assert.deepEqual(L.detailEntries(discount, FMT).map((e) => e.label), ['شناسه‌ی تخفیف', 'دلیل', 'مهلت تسویه', 'وضعیت']);
    const setting = { action: 'INSTALLMENTS_SETTING_CHANGED', details: { from: true, to: false } };
    assert.deepEqual(L.detailEntries(setting, FMT), [
        { label: 'پیش از تغییر', value: 'روشن' },
        { label: 'پس از تغییر', value: 'خاموش' }
    ]);
    assert.deepEqual(L.detailEntries({ action: 'INVOICE_ISSUED', details: null }, FMT), []);
    assert.equal(L.hasDetails({ action: 'INVOICE_ISSUED', details: null }, FMT), false);
    assert.deepEqual(L.detailEntries({ action: 'EXPENSE_NEW', details: { k: 'v' } }, FMT), [{ label: 'k', value: 'v' }]);
});

test('clock pads the hour and minute and runs them through the digit function', () => {
    const date = new Date(2026, 9, 12, 7, 5);
    assert.equal(L.clock(date, (text) => text), '07:05');
    assert.equal(L.clock(date, (text) => '<' + text + '>'), '<07:05>');
});

test('page count is at least one and rounds up', () => {
    assert.equal(L.pageCount(0, 50), 1);
    assert.equal(L.pageCount(50, 50), 1);
    assert.equal(L.pageCount(51, 50), 2);
    assert.equal(L.pageCount(undefined, 50), 1);
});

test('the initial query pre-fills invoiceId and studentId and ignores junk', () => {
    assert.deepEqual(L.parseInitialQuery('?invoiceId=12&studentId=3', toAscii), { invoiceId: '12', studentId: '3' });
    assert.deepEqual(L.parseInitialQuery('?invoiceId=۱۲', toAscii), { invoiceId: '12', studentId: '' });
    assert.deepEqual(L.parseInitialQuery('?invoiceId=abc&studentId=-1', toAscii), { invoiceId: '', studentId: '' });
    assert.deepEqual(L.parseInitialQuery('?invoiceId=0', toAscii), { invoiceId: '', studentId: '' });
    assert.deepEqual(L.parseInitialQuery('', toAscii), { invoiceId: '', studentId: '' });
});

test('filter validation puts Jalali text on the wire as ISO and ASCII digits', () => {
    const parseDate = (text) => ({ '1405/07/20': '2026-10-12', '1405/07/25': '2026-10-17' }[toAscii(text).trim()] || null);
    const helpers = { toAscii, parseDate, example: '۱۴۰۵/۰۷/۱۳' };

    const ok = L.validateFilters({ action: 'PAYMENT_RECORDED', from: '۱۴۰۵/۰۷/۲۰', to: '1405/07/25', invoiceId: '۱۲' }, helpers);
    assert.equal(ok.ok, true);
    assert.deepEqual(ok.value, { invoiceId: '12', action: 'PAYMENT_RECORDED', from: '2026-10-12', to: '2026-10-17' });

    const empty = L.validateFilters({ action: '', from: '', to: '', invoiceId: '' }, helpers);
    assert.equal(empty.ok, true);
    assert.deepEqual(empty.value, { invoiceId: '', action: '', from: '', to: '' });

    const bad = L.validateFilters({ action: '', from: 'سلام', to: '', invoiceId: 'x1' }, helpers);
    assert.equal(bad.ok, false);
    assert.match(bad.errors.from, /سال\/ماه\/روز/);
    assert.match(bad.errors.from, /۱۴۰۵\/۰۷\/۱۳/);
    assert.ok(bad.errors.invoiceId);

    const reversed = L.validateFilters({ action: '', from: '1405/07/25', to: '1405/07/20', invoiceId: '' }, helpers);
    assert.equal(reversed.ok, false);
    assert.ok(reversed.errors.to);
    assert.equal(reversed.errors.from, '');
});

test('the query string carries only filled filters, with a zero-based page', () => {
    assert.equal(L.buildQuery({ invoiceId: '', studentId: '', action: '', from: '', to: '' }, 0, 50), 'page=0&size=50');
    assert.equal(L.buildQuery({ invoiceId: '12', studentId: '3', action: 'PAYMENT_RECORDED', from: '2026-10-12', to: '2026-10-17' }, 2, 50),
        'invoiceId=12&studentId=3&action=PAYMENT_RECORDED&from=2026-10-12&to=2026-10-17&page=2&size=50');
    assert.equal(L.buildQuery({}, 1, 50), 'page=1&size=50');
});

test('the page script is read-only and sends the school header', () => {
    const source = fs.readFileSync(path.join(__dirname, JS, 'finance-audit.js'), 'utf8');
    assert.ok(source.includes("'X-School-ID'"));
    assert.ok(!/method:\s*'(POST|PUT|PATCH|DELETE)'/.test(source));
    assert.ok(!source.includes('X-XSRF-TOKEN'));
});
