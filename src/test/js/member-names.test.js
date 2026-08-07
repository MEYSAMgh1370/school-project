const test = require('node:test');
const assert = require('node:assert/strict');

global.window = global;
require('../../main/resources/static/js/member-names.js');

test('large member-name rosters are fetched in bounded URL batches and merged', async () => {
    const requestedBatches = [];
    global.fetch = async (url, options) => {
        const parsed = new URL(url, 'https://school.test');
        const ids = parsed.searchParams.getAll('userIds');
        requestedBatches.push({ ids, options });
        return {
            ok: true,
            async json() {
                return ids.map(id => ({ userId: Number(id), fullName: `نام ${id}` }));
            }
        };
    };

    const ids = Array.from({ length: 350 }, (_, index) => index + 1);
    const names = await window.MemberNames.resolve(42, ids);

    assert.equal(requestedBatches.length, 4);
    assert.deepEqual(requestedBatches.map(batch => batch.ids.length), [100, 100, 100, 50]);
    assert.ok(requestedBatches.every(batch => batch.options.headers['X-School-ID'] === 42));
    assert.equal(names.length, 350);
    assert.deepEqual(names[0], { userId: 1, fullName: 'نام 1', email: '' });
    assert.deepEqual(names[349], { userId: 350, fullName: 'نام 350', email: '' });
});
