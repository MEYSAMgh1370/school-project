// منطقِ خالصِ صفحه‌ی «فاکتورهای من» (مانده، تخفیف، قسط بعدی) بدون مرورگر و Alpine.
const test = require('node:test');
const assert = require('node:assert/strict');

let factory = null;
global.document = { addEventListener: (name, cb) => { if (name === 'alpine:init') cb(); } };
global.Alpine = { data: (name, fn) => { if (name === 'myInvoicesPage') factory = fn; } };
require('../../main/resources/static/js/my-invoices.js');

const page = () => factory();

test('payable and remaining account for the discount', () => {
    const p = page();
    const invoice = { amount: 1000, discountTotal: 200, amountPaid: 300 };
    assert.equal(p.payable(invoice), 800);
    assert.equal(p.remaining(invoice), 500);
});

test('missing discount behaves like a plain invoice; remaining never negative', () => {
    const p = page();
    assert.equal(p.discount({ amount: 1000, amountPaid: 0 }), 0);
    assert.equal(p.remaining({ amount: 1000, amountPaid: 0 }), 1000);
    assert.equal(p.remaining({ amount: 1000, discountTotal: 100, amountPaid: 1200 }), 0);
});

test('installmentsOf tolerates a missing list', () => {
    const p = page();
    assert.deepEqual(p.installmentsOf({}), []);
    assert.deepEqual(p.installmentsOf({ installments: null }), []);
});

test('next installment is the first non-paid one by seq', () => {
    const p = page();
    const invoice = {
        installments: [
            { id: 12, seq: 2, status: 'UNPAID' },
            { id: 11, seq: 1, status: 'PAID' },
            { id: 13, seq: 3, status: 'UNPAID' }
        ]
    };
    assert.equal(p.nextInstallmentId(invoice), 12);
    assert.equal(p.nextInstallmentId({ installments: [{ id: 1, seq: 1, status: 'PAID' }] }), null);
    assert.equal(p.nextInstallmentId({}), null);
});

test('installment rows start collapsed and toggle per invoice', () => {
    const p = page();
    const invoice = { id: 7 };
    assert.equal(p.isExpanded(invoice), false);
    p.toggleInstallments(invoice);
    assert.equal(p.isExpanded(invoice), true);
    assert.equal(p.isExpanded({ id: 8 }), false);
    p.toggleInstallments(invoice);
    assert.equal(p.isExpanded(invoice), false);
});

test('hasPendingDiscount needs a positive amount and a deadline', () => {
    const p = page();
    assert.equal(p.hasPendingDiscount({ pendingDiscount: 500, pendingDiscountDeadline: '2026-11-01' }), true);
    assert.equal(p.hasPendingDiscount({ pendingDiscount: 0, pendingDiscountDeadline: '2026-11-01' }), false);
    assert.equal(p.hasPendingDiscount({ pendingDiscount: 500, pendingDiscountDeadline: null }), false);
    assert.equal(p.hasPendingDiscount({}), false);
});

test('OVERDUE has a label and a class', () => {
    const p = page();
    assert.equal(p.statusLabel('OVERDUE'), 'دیرکرد');
    assert.equal(p.statusClass('OVERDUE'), 'is-danger');
});
