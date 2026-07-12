// تبدیل تقویم جلالی/میلادی بدون هیچ وابستگی خارجی - هیچ کتابخانه‌ی جلالی
// (jalaali-js، moment-jalaali، ICU4J) در این پروژه وجود نداشت (رجوع به
// بررسی زیرساخت هنگام طراحی ماژول هزینه‌ها)؛ این پیاده‌سازی الگوریتم
// استاندارد جلالیِ کاظمی/بروکس است، همان الگوریتمی که jalaali-js از آن
// استفاده می‌کند - فقط بدون بسته‌ی npm.
//
// فقط برای انتخابگر بازه‌ی گزارش هزینه لازم است: تبدیل «ماه/فصل/سال جلالی»
// به بازه‌ی تاریخ میلادی که سرور (ExpenseController) با آن کار می‌کند.
window.Jalali = (function () {
    const breaks = [
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    ];

    function div(a, b) {
        return ~~(a / b);
    }

    function mod(a, b) {
        return a - ~~(a / b) * b;
    }

    function jalCal(jy) {
        const bl = breaks.length;
        const gy = jy + 621;
        let leapJ = -14, jp = breaks[0];
        let jump = 0;
        for (let i = 1; i < bl; i += 1) {
            const jm = breaks[i];
            jump = jm - jp;
            if (jy < jm) break;
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4);
            jp = jm;
        }
        let n = jy - jp;
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4);
        if (mod(jump, 33) === 4 && jump - n === 4) leapJ += 1;
        const leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150;
        const march = 20 + leapJ - leapG;
        if (jump - n < 6) n = n - jump + div(jump, 33) * 33;
        let leap = mod(mod(n + 1, 33) - 1, 4);
        if (leap === -1) leap = 4;
        return { leap: leap, march: march };
    }

    function g2d(gy, gm, gd) {
        let d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4)
            + div(153 * mod(gm + 9, 12) + 2, 5)
            + gd - 34840408;
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752;
        return d;
    }

    function d2g(jdn) {
        let j = 4 * jdn + 139361631;
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908;
        const i = div(mod(j, 1461), 4) * 5 + 308;
        const gd = div(mod(i, 153), 5) + 1;
        const gm = mod(div(i, 153), 12) + 1;
        const gy = div(j, 1461) - 100100 + div(8 - gm, 6);
        return { gy: gy, gm: gm, gd: gd };
    }

    // تبدیل تاریخ جلالی به Date میلادی (UTC نیمه‌شب) - تنها جهتی که این
    // ماژول واقعاً لازم دارد (انتخابگر بازه فقط ورودی جلالی می‌گیرد)
    function toGregorian(jy, jm, jd) {
        const r = jalCal(jy);
        const gy = jy + 621;
        let days = jd - 1;
        for (let m = 1; m < jm; m += 1) {
            days += m <= 6 ? 31 : (m <= 11 ? 30 : (r.leap === 1 ? 30 : 29));
        }
        const jdn = g2d(gy, 3, r.march) + days;
        const g = d2g(jdn);
        return new Date(Date.UTC(g.gy, g.gm - 1, g.gd));
    }

    // بازه‌ی ماه/فصل/سالِ جلالی → { from, to } تاریخ میلادی (YYYY-MM-DD،
    // برای query param) - این تنها تابعی است که expenses.js صدا می‌زند
    function periodRange(jy, kind, index) {
        let startMonth, monthCount;
        if (kind === 'year') {
            startMonth = 1;
            monthCount = 12;
        } else if (kind === 'quarter') {
            startMonth = (index - 1) * 3 + 1;
            monthCount = 3;
        } else {
            startMonth = index;
            monthCount = 1;
        }
        const from = toGregorian(jy, startMonth, 1);
        const r = jalCal(jy);
        let endMonth = startMonth + monthCount - 1;
        const lastDay = endMonth <= 6 ? 31 : (endMonth <= 11 ? 30 : (r.leap === 1 ? 30 : 29));
        const to = toGregorian(jy, endMonth, lastDay);
        return { from: toIsoDate(from), to: toIsoDate(to) };
    }

    function toIsoDate(date) {
        return date.toISOString().slice(0, 10);
    }

    return { toGregorian: toGregorian, periodRange: periodRange };
})();
