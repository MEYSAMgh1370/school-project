// منطق خالص صفحه‌ی «بات بله‌ی مدرسه»: انتخاب حالت و متن خطا بر اساس type ProblemDetail.
const test = require('node:test');
const assert = require('node:assert/strict');
const BaleBotLogic = require('../../main/resources/static/js/bale-bot-logic.js');

test('viewState: no bot, active and disabled', () => {
    assert.equal(BaleBotLogic.viewState(null), 'none');
    assert.equal(BaleBotLogic.viewState({ status: 'ACTIVE' }), 'active');
    assert.equal(BaleBotLogic.viewState({ status: 'DISABLED' }), 'disabled');
});

test('errorMessage separates unreachable Bale from the missing encryption key', () => {
    const missingKey = BaleBotLogic.errorMessage({ status: 503, type: 'https://x/bale-bot-encryption-unavailable' });
    assert.equal(missingKey, 'امکان ثبت بات موقتاً فعال نیست؛ با پشتیبانی تماس بگیرید.');
    const down = BaleBotLogic.errorMessage({ status: 503, type: 'https://x/bale-unavailable' });
    assert.match(down, /بله در دسترس نیست/);
    assert.notEqual(down, missingKey);
});

test('errorMessage maps 400, 409 and 429 and falls back to the server detail', () => {
    assert.match(BaleBotLogic.errorMessage({ status: 400, type: 'x/bale-bot-token-invalid' }), /توکن/);
    assert.match(BaleBotLogic.errorMessage({ status: 409, type: 'x/bale-bot-already-registered' }), /مدرسه‌ی دیگری/);
    assert.match(BaleBotLogic.errorMessage({ status: 429, type: 'x/bale-bot-too-many-attempts' }), /۱۰ بار/);
    assert.equal(BaleBotLogic.errorMessage({ status: 500, type: 'x/other', detail: 'جزئیات' }), 'جزئیات');
    assert.equal(BaleBotLogic.errorMessage({ status: 500 }), 'خطایی رخ داد. (500)');
});

test('validateToken rejects blank tokens and trims', () => {
    assert.equal(BaleBotLogic.validateToken('   '), 'توکن بات را وارد کنید.');
    assert.equal(BaleBotLogic.validateToken(' 123:abc '), '');
});
