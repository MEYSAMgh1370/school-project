// منطقِ خالصِ صفحه‌ی مالی: قابل‌پرداخت، مانده و بررسیِ جمع اقساط (بدون مرورگر و Alpine).
const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
const JS = '../../main/resources/static/js/';
require(JS + 'digits.js');
require(JS + 'money.js');
require(JS + 'finance-logic.js');

test('payable subtracts the discount and remaining subtracts what is paid', () => {
    const invoice = { amount: 10000000, discountTotal: 1500000, amountPaid: 2000000 };
    assert.equal(FinanceLogic.payable(invoice), 8500000);
    assert.equal(FinanceLogic.remaining(invoice), 6500000);
});

test('an invoice without a discountTotal behaves as before', () => {
    const invoice = { amount: 5000000, amountPaid: 1000000 };
    assert.equal(FinanceLogic.payable(invoice), 5000000);
    assert.equal(FinanceLogic.remaining(invoice), 4000000);
});

test('pendingDiscounts keeps only the open conditional discounts', () => {
    const list = [{ id: 1, status: 'APPLIED' }, { id: 2, status: 'PENDING' }, { id: 3, status: 'LAPSED' }, { id: 4, status: 'EARNED' }];
    assert.deepEqual(FinanceLogic.pendingDiscounts(list).map((d) => d.id), [2]);
    assert.deepEqual(FinanceLogic.pendingDiscounts(null), []);
});

test('discountStatusLabel and class per status', () => {
    assert.equal(FinanceLogic.discountStatusLabel({ status: 'APPLIED' }), 'دائمی');
    assert.equal(FinanceLogic.discountStatusLabel({ status: 'PENDING' }, '۱۴۰۵/۰۷/۳۰'), 'مشروط تا ۱۴۰۵/۰۷/۳۰');
    assert.equal(FinanceLogic.discountStatusLabel({ status: 'EARNED' }), 'حاصل شد');
    assert.equal(FinanceLogic.discountStatusLabel({ status: 'LAPSED' }), 'ساقط شد');
    assert.equal(FinanceLogic.discountStatusClass({ status: 'PENDING' }), 'is-warning');
    assert.equal(FinanceLogic.discountStatusClass({ status: 'EARNED' }), 'is-success');
    assert.equal(FinanceLogic.discountStatusClass({ status: 'LAPSED' }), 'is-light');
});

test('a fully paid discounted invoice has no remaining', () => {
    assert.equal(FinanceLogic.remaining({ amount: 100, discountTotal: 40, amountPaid: 60 }), 0);
});

test('expected sum is the payable minus the fully paid installments', () => {
    const invoice = { amount: 9000000, discountTotal: 0, amountPaid: 3000000 };
    const plan = [
        { id: 1, amount: 3000000, status: 'PAID' },
        { id: 2, amount: 3000000, status: 'UNPAID' },
        { id: 3, amount: 3000000, status: 'OVERDUE' }
    ];
    assert.equal(FinanceLogic.expectedSum(invoice, plan), 6000000);
    assert.equal(FinanceLogic.isEditable(plan[0]), false);
    assert.equal(FinanceLogic.isEditable(plan[1]), true);
    assert.equal(FinanceLogic.isEditable({ status: 'PARTIAL' }), true);
});

test('edited sum accepts Persian digits and counts blanks as zero', () => {
    assert.equal(FinanceLogic.editedSum([{ amount: '۱۰۰۰' }, { amount: '٢٠٠٠' }, { amount: '' }]), 3000);
});

test('sum matches only when every row is valid and the total is exact', () => {
    assert.equal(FinanceLogic.sumMatches([{ amount: '4000' }, { amount: '۲۰۰۰' }], 6000), true);
    assert.equal(FinanceLogic.sumMatches([{ amount: '4000' }, { amount: '1999' }], 6000), false);
    assert.equal(FinanceLogic.sumMatches([{ amount: '6000' }, { amount: '' }], 6000), false);
    assert.equal(FinanceLogic.sumMatches([{ amount: '7000' }, { amount: '-1000' }], 6000), false);
    assert.equal(FinanceLogic.sumMatches([{ amount: '0' }, { amount: '6000' }], 6000), false);
    assert.equal(FinanceLogic.sumMatches([], 0), false);
});

test('undated installments: label, edit text and null-tolerant sums', () => {
    const fmt = (iso) => 'F(' + iso + ')';
    const undated = { id: 1, amount: 500, status: 'UNPAID', dueDate: null };
    const dated = { id: 2, amount: 500, status: 'UNPAID', dueDate: '2026-11-01' };
    assert.equal(FinanceLogic.isUndated(undated), true);
    assert.equal(FinanceLogic.isUndated({ id: 3 }), true);
    assert.equal(FinanceLogic.isUndated(dated), false);
    assert.equal(FinanceLogic.dueDateLabel(undated, () => { throw new Error('format must not run'); }), 'بدون سررسید');
    assert.equal(FinanceLogic.dueDateLabel(dated, fmt), 'F(2026-11-01)');
    assert.equal(FinanceLogic.editDueText(undated, () => { throw new Error('format must not run'); }), '');
    assert.equal(FinanceLogic.editDueText(dated, fmt), 'F(2026-11-01)');
    assert.equal(FinanceLogic.expectedSum({ amount: 1000, discountTotal: 0 }, [undated, dated]), 1000);
});

test('resolveDueDate: empty is valid only for a row that started undated', () => {
    const parse = (text) => (text === '1405/08/20' ? '2026-11-11' : null);
    assert.deepEqual(FinanceLogic.resolveDueDate({ undated: true, dueDate: '' }, parse), { ok: true, value: null });
    assert.deepEqual(FinanceLogic.resolveDueDate({ undated: true, dueDate: '   ' }, parse), { ok: true, value: null });
    assert.deepEqual(FinanceLogic.resolveDueDate({ undated: true, dueDate: '1405/08/20' }, parse), { ok: true, value: '2026-11-11' });
    assert.equal(FinanceLogic.resolveDueDate({ undated: true, dueDate: 'abc' }, parse).ok, false);
    assert.equal(FinanceLogic.resolveDueDate({ undated: false, dueDate: '' }, parse).ok, false);
    assert.equal(FinanceLogic.resolveDueDate({ dueDate: '' }, parse).ok, false);
    assert.deepEqual(FinanceLogic.resolveDueDate({ undated: false, dueDate: '1405/08/20' }, parse), { ok: true, value: '2026-11-11' });
});

test('decimal rials do not break the exact comparison', () => {
    assert.equal(FinanceLogic.sumMatches([{ amount: '0.1' }, { amount: '0.2' }], 0.3), true);
});

test('buildStudentsQuery omits empty fields and defaults page 0, size 50', () => {
    assert.equal(FinanceLogic.buildStudentsQuery({}), 'page=0&size=50');
    assert.equal(FinanceLogic.buildStudentsQuery({ scope: 'all', firstName: '  ', lastName: '', q: null, debt: undefined }),
        'scope=all&page=0&size=50');
});

test('buildStudentsQuery encodes Persian text and keeps the field order', () => {
    const query = FinanceLogic.buildStudentsQuery({ scope: 'previous', firstName: 'علی', lastName: 'رضایی', q: 'م ن', debt: 'owing', page: 2, size: 25 });
    assert.equal(query, 'scope=previous&firstName=' + encodeURIComponent('علی') + '&lastName=' + encodeURIComponent('رضایی')
        + '&q=' + encodeURIComponent('م ن') + '&debt=owing&page=2&size=25');
    assert.ok(!query.includes('?'));
    assert.ok(!/[^\x00-\x7f]/.test(query));
});

test('failedListState clears rows, total and counts but keeps the list marked as loaded', () => {
    const state = FinanceLogic.failedListState();
    assert.deepEqual(state, { students: [], total: 0, counts: { all: 0, current: 0, previous: 0 }, listLoaded: true });
    assert.notEqual(FinanceLogic.failedListState().counts, state.counts);
});
