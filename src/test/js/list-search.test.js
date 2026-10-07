const test = require('node:test');
const assert = require('node:assert/strict');

global.window = global;
require('../../main/resources/static/js/list-search.js');

test('an empty query matches every row', () => {
    assert.equal(ListSearch.matches('', ['x']), true);
    assert.equal(ListSearch.matches('   ', ['x']), true);
});

test('Arabic ي and ك match the Persian letters stored in names', () => {
    assert.equal(ListSearch.matches('كريمي', ['پویا کریمی']), true);
    assert.equal(ListSearch.matches('علي', ['علی رضایی']), true);
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
