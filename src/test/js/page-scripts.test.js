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
