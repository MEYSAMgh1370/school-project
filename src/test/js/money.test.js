const test = require('node:test');
const assert = require('node:assert/strict');

global.window = globalThis;
require('../../main/resources/static/js/digits.js');
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

test('writes the toman amount in Persian words for a rial entry', () => {
    assert.equal(Money.tomanWords(2000000), 'دویست هزار تومان');
    assert.equal(Money.tomanWords('۲۰۰۰۰۰۰'), 'دویست هزار تومان');
    assert.equal(Money.tomanWords(45000000), 'چهار میلیون و پانصد هزار تومان');
    assert.equal(Money.tomanWords(1000000000), 'صد میلیون تومان');
    assert.equal(Money.tomanWords(20000000000), 'دو میلیارد تومان');
    assert.equal(Money.tomanWords(1111110), 'صد و یازده هزار و صد و یازده تومان');
});

test('a scale word alone is not preceded by یک, and ten rials make one toman', () => {
    assert.equal(Money.tomanWords(10000), 'هزار تومان');
    assert.equal(Money.tomanWords(10), 'یک تومان');
    assert.equal(Money.tomanWords(15), 'یک تومان و پنج ریال');
    assert.equal(Money.tomanWords(1000), 'صد تومان');
});

test('zero is spelled out and empty or invalid input gives no words', () => {
    assert.equal(Money.tomanWords(0), 'صفر تومان');
    assert.equal(Money.tomanWords(''), '');
    assert.equal(Money.tomanWords(null), '');
    assert.equal(Money.tomanWords('1.5'), '');
    assert.equal(Money.tomanWords(-10), '');
    assert.equal(Money.tomanWords(1e15), '');
});
