const test = require('node:test');
const assert = require('node:assert/strict');

global.window = global;
require('../../main/resources/static/js/digits.js');
require('../../main/resources/static/js/list-search.js');

test('an empty query matches every row', () => {
    assert.equal(ListSearch.matches('', ['x']), true);
    assert.equal(ListSearch.matches('   ', ['x']), true);
});

test('Arabic ي and ك match the Persian letters stored in names', () => {
    assert.equal(ListSearch.matches('كريمي', ['پویا کریمی']), true);
    assert.equal(ListSearch.matches('علي', ['علی رضایی']), true);
});

test('other Arabic-keyboard letters match their Persian forms', () => {
    assert.equal(ListSearch.matches('مدرسه', ['مدرسة']), true);
    assert.equal(ListSearch.matches('مدرسة', ['مدرسه']), true);
    assert.equal(ListSearch.matches('مصطفى', ['مصطفی']), true);
    assert.equal(ListSearch.matches('أحمد', ['احمد']), true);
    assert.equal(ListSearch.matches('إسماعيل', ['اسماعیل']), true);
    assert.equal(ListSearch.matches('مؤمن', ['مومن']), true);
    assert.equal(ListSearch.matches('رئيس', ['رئیس']), true);
});

test('tatweel (kashida) is ignored on both sides', () => {
    assert.equal(ListSearch.matches('مـدرسه', ['مدرسه']), true);
    assert.equal(ListSearch.matches('مدرسه', ['مــدرســه']), true);
});

test('Persian and Arabic-Indic digits match ASCII digits', () => {
    assert.equal(ListSearch.matches('۱۴۰۵', ['1405-1406']), true);
    assert.equal(ListSearch.matches('١٤٠٥', ['۱۴۰۵']), true);
});

test('ZWNJ and diacritics are ignored on both sides', () => {
    assert.equal(ListSearch.matches('میدهد', ['می‌دهد']), true);
    assert.equal(ListSearch.matches('می‌دهد', ['میدهد']), true);
    assert.equal(ListSearch.matches('علی', ['عَلی']), true);
});

test('extra spaces collapse and any text may match', () => {
    assert.equal(ListSearch.matches('  پویا   کریمی ', ['پویا کریمی']), true);
    assert.equal(ListSearch.matches('بهار', ['آرش', 'بهار رضایی']), true);
    assert.equal(ListSearch.matches('zzz', ['آرش', 'بهار']), false);
});

test('null and missing texts never throw', () => {
    assert.equal(ListSearch.matches('آ', [null, undefined]), false);
    assert.equal(ListSearch.matches('', [null]), true);
});
