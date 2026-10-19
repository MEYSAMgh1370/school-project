// قفلِ «اسکریپتِ جاافتاده». صفحه‌ها اسکریپت‌هایشان را در لیست scripts قالب می‌دهند و
// مرورگر یک calendar.js جاافتاده را فقط با ReferenceError در لحظه‌ی اجرای یک عبارت
// Alpine نشان می‌دهد - جایی که هیچ تست سمت سرور نمی‌بیند. این تست همان ناسازگاری
// را ایستا و بدون مرورگر می‌گیرد: هر صفحه‌ای که (در قالب یا در اسکریپت‌های خودش)
// از Calendar یا Jalali استفاده می‌کند باید همان فایل را در لیست داشته باشد، و
// calendar.js باید بعد از jalali.js بیاید (هر دو defer هستند و به ترتیب سند اجرا
// می‌شوند؛ calendar.js برای fa هنگام بارگذاری window.Jalali را می‌خواهد).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..', 'main');
const JTE_DIR = path.join(ROOT, 'jte');
const STATIC_DIR = path.join(ROOT, 'resources', 'static');

function walk(dir) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = path.join(dir, entry.name);
        return entry.isDirectory() ? walk(full) : [full];
    });
}

// فقط کدِ واقعی: کامنت‌ها (که پر از اسم Jalali اند) نباید وابستگی ساختگی بسازند
function stripComments(source, kind) {
    let text = source.replace(/<%--[\s\S]*?--%>/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
    if (kind === 'js') text = text.split('\n').filter((line) => !line.trim().startsWith('//')).join('\n');
    return text;
}

function scriptsOf(templateSource) {
    const match = templateSource.match(/scripts\s*=\s*java\.util\.List\.of\(([^)]*)\)/);
    return match ? [...match[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]) : [];
}

const pages = walk(JTE_DIR)
    .filter((file) => file.endsWith('.jte') && !file.includes(`${path.sep}layout${path.sep}`))
    .map((file) => {
        const source = fs.readFileSync(file, 'utf8');
        return { name: path.relative(JTE_DIR, file), source, scripts: scriptsOf(source) };
    })
    .filter((page) => page.scripts.length > 0);

// الگوی کدی که واقعاً به یک global ارجاع می‌دهد، نه نامِ داخل متن
const USES = {
    calendar: /(?<![\w.])(?:window\.)?Calendar\b(?!\s*:)/,
    jalali: /(?<![\w.])(?:window\.)?Jalali\b(?!\s*:)/
};

function codeOf(page) {
    const own = stripComments(page.source, 'jte');
    const scripts = page.scripts
        .filter((src) => src.startsWith('/js/'))
        // خودِ calendar.js و jalali.js پیاده‌سازی اند، نه مصرف‌کننده
        .filter((src) => src !== '/js/calendar.js' && src !== '/js/jalali.js')
        .map((src) => stripComments(fs.readFileSync(path.join(STATIC_DIR, src), 'utf8'), 'js'));
    return [own, ...scripts].join('\n');
}

test('every page lists only scripts that exist', () => {
    for (const page of pages) {
        for (const src of page.scripts.filter((s) => s.startsWith('/js/'))) {
            assert.ok(fs.existsSync(path.join(STATIC_DIR, src)), `${page.name} lists ${src}, which does not exist`);
        }
    }
    assert.ok(pages.length >= 20, `expected to find the page templates, found ${pages.length}`);
});

test('a page that uses Calendar loads calendar.js, after jalali.js', () => {
    const consumers = pages.filter((page) => USES.calendar.test(codeOf(page)));
    assert.ok(consumers.length >= 10, `expected many Calendar consumers, found ${consumers.length}`);
    for (const page of consumers) {
        const calendarAt = page.scripts.indexOf('/js/calendar.js');
        const jalaliAt = page.scripts.indexOf('/js/jalali.js');
        assert.ok(calendarAt >= 0, `${page.name} uses Calendar but does not load /js/calendar.js`);
        assert.ok(jalaliAt >= 0, `${page.name} loads calendar.js without /js/jalali.js, which fa needs at load time`);
        assert.ok(jalaliAt < calendarAt, `${page.name} must load jalali.js before calendar.js`);
    }
});

test('a page that uses Jalali directly loads jalali.js', () => {
    for (const page of pages.filter((p) => USES.jalali.test(codeOf(p)))) {
        assert.ok(page.scripts.includes('/js/jalali.js'), `${page.name} uses Jalali but does not load /js/jalali.js`);
    }
});

test('no page calls a Jalali member directly any more; calendar.js is the only caller', () => {
    // W2 + W2b: the seam is the one place that talks to window.Jalali
    for (const page of pages) {
        const code = codeOf(page);
        assert.ok(!/(?<![\w.])Jalali\./.test(code), `${page.name} still calls Jalali.* directly`);
    }
});

// هلپرهای مشترک (digits.js، list-search.js، money.js، grade-labels.js): هر صفحه‌ای که یکی از آن‌ها را
// صدا می‌زند، یا اسکریپتی را لود می‌کند که آن را صدا می‌زند، باید همان فایل را در لیست داشته باشد. هلپرِ
// جاافتاده فقط در لحظه‌ی اجرا ReferenceError می‌دهد، و کامپایل یا تست سرور آن را نمی‌بیند.
const SHARED_HELPERS = [
    { global: 'Digits', file: '/js/digits.js', uses: /(?<![\w.])(?:window\.)?Digits\b/ },
    { global: 'ListSearch', file: '/js/list-search.js', uses: /(?<![\w.])(?:window\.)?ListSearch\b/ },
    { global: 'Money', file: '/js/money.js', uses: /(?<![\w.])(?:window\.)?Money\b/ },
    { global: 'GradeLabels', file: '/js/grade-labels.js', uses: /(?<![\w.])(?:window\.)?GradeLabels\b/ }
];

test('a page that uses a shared helper loads the file that defines it', () => {
    for (const helper of SHARED_HELPERS) {
        const consumers = pages.filter((page) => helper.uses.test(codeOf(page)));
        for (const page of consumers) {
            // خودِ تعریف‌کننده را هم وقتی کسی آن را لود می‌کند، باید داشته باشد
            assert.ok(page.scripts.includes(helper.file),
                `${page.name} uses ${helper.global} but does not load ${helper.file}`);
        }
    }
});

// برش‌دادنِ انتخابگرهای سال (school-settings): /classes و /grades با سالِ جاریِ مدرسه کار می‌کنند و سال را
// کاربر انتخاب نمی‌کند. متنِ اسکریپت بررسی می‌شود تا برگشتِ بی‌صدای انتخابگر یا state مرده دیده شود.
const staticSource = (name) => stripComments(fs.readFileSync(path.join(STATIC_DIR, 'js', name), 'utf8'), 'js');

test('classes.js has no year state and creates a class without sending academicYear', () => {
    const code = staticSource('classes.js');
    assert.ok(!/academicYear\s*:/.test(code), 'classes.js must not send or keep an academicYear field');
    assert.ok(!/\byears\b|yearFilter|currentYear/.test(code), 'classes.js still has year-selector state');
    assert.ok(!/\/academic-year/.test(code), 'classes.js must not fetch the year list any more');
    assert.ok(/yearLabel\(/.test(code), 'rows keep the year label');
});

test('grades.js has no year list, no year selector watcher and no per-year class reload', () => {
    const code = staticSource('grades.js');
    assert.ok(!/\byears\b|reloadClassesForYear|\$watch\('filter\.academicYear'/.test(code), 'grades.js still has year-selector logic');
    assert.ok(!/\/api\/admin\/schools\/\$\{schoolId\}\/academic-year/.test(code), 'grades.js must read only the current year');
});

test('the removed year pages and scripts are gone and nothing links to the old page', () => {
    assert.ok(!fs.existsSync(path.join(STATIC_DIR, 'js', 'academic-year.js')));
    assert.ok(!fs.existsSync(path.join(JTE_DIR, 'academic-year.jte')));
    for (const file of walk(JTE_DIR).filter((f) => f.endsWith('.jte'))) {
        const source = stripComments(fs.readFileSync(file, 'utf8'), 'jte');
        assert.ok(!/href="\/academic-year"/.test(source), `${path.relative(JTE_DIR, file)} links to /academic-year`);
        assert.ok(!/academic-year\.js/.test(source), `${path.relative(JTE_DIR, file)} loads academic-year.js`);
    }
});
