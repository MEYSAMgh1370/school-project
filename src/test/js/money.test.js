const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
require('../../main/resources/static/js/money.js');

test('formats raw amounts neutrally with Persian digits and grouping', () => {
    assert.equal(Money.formatAmount(1234567), '۱٬۲۳۴٬۵۶۷');
    assert.equal(Money.formatAmount('1234.5'), '۱٬۲۳۴٫۵');
    assert.equal(Money.formatAmount(null), '—');
    assert.equal(Money.formatAmount('invalid'), '—');
});

test('normalizes Persian and Arabic-Indic amount input to ASCII', () => {
    assert.equal(Money.normalizeAmount('۱۲۳'), '123');
    assert.equal(Money.normalizeAmount('١٢٣'), '123');
    assert.equal(Money.normalizeAmount('۱٬۲۳۴٫۵۰'), '1234.50');
});

test('normalizes the live input value before Alpine validation and binding', () => {
    const event = { target: { value: '۱۲۳' } };

    assert.equal(Money.normalizeAmountInput(event), '123');
    assert.equal(event.target.value, '123');
});
