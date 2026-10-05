// ورودی/نمایشِ تاریخ برای صفحه‌هایی که کاربر روز را تایپ می‌کند (تقویم مدرسه، حضور و غیاب).
// DESIGN.md §8: نمایش جلالی با ارقام فارسی، روی سیم و در state فقط ISO-8601 میلادی با ارقام ASCII؛
// ورودی ارقام فارسی/عربی را پیش از اعتبارسنجی به ASCII برمی‌گرداند. همه‌ی حساب‌ها از Calendar
// (calendar.js) می‌گذرد، پس برای زبان‌های غیرفارسی همین کد با تقویم میلادی کار می‌کند.
//
// وابستگی: /js/jalali.js (برای fa) و /js/calendar.js پیش از این فایل.
window.DateInput = (function () {
    const ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
    const DAY_MS = 86400000;

    function pad(n) {
        return String(n).padStart(2, '0');
    }

    // ۰-۹ فارسی (U+06F0) و ٠-٩ عربی (U+0660) → ASCII
    function toAscii(value) {
        return String(value == null ? '' : value).replace(/[۰-۹٠-٩]/g, (ch) => {
            const code = ch.charCodeAt(0);
            return String(code >= 0x06F0 ? code - 0x06F0 : code - 0x0660);
        });
    }

    // YYYY-MM-DD از اجزای «محلی» (نه toISOString که UTC است و در منطقه‌های منفی یک روز جابه‌جا می‌شود)
    function isoOf(date) {
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }

    // نیمه‌شبِ محلیِ یک ISO؛ null اگر ISO معتبر نباشد
    function localDate(iso) {
        const match = ISO.exec(iso || '');
        return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : null;
    }

    function today() {
        return isoOf(new Date());
    }

    // متنِ تایپ‌شده («۱۴۰۵/۰۷/۱۳»، «1405-7-13»، …) → ISO میلادی؛ null اگر تاریخِ واقعی نباشد
    function parse(text) {
        const cleaned = toAscii(text).replace(/[\s‌‎‏]/g, '');
        const parts = cleaned.split(/[\/\-.]/);
        if (parts.length !== 3 || parts[0].length !== 4 || parts.some((p) => !/^\d{1,4}$/.test(p))) {
            return null;
        }
        const y = Number(parts[0]);
        const m = Number(parts[1]);
        const d = Number(parts[2]);
        if (m < 1 || m > 12 || d < 1 || d > 31) {
            return null;
        }
        const utc = Calendar.toDate(y, m, d);
        // رفت‌وبرگشت: «۳۱ شهریور» یا «۳۰ اسفند در سالِ غیرکبیسه» به روزِ دیگری سرریز نمی‌شود
        const back = Calendar.fromDate(new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate()));
        if (back.y !== y || back.m !== m || back.d !== d) {
            return null;
        }
        return utc.toISOString().slice(0, 10);
    }

    // ISO → «۱۴۰۵/۰۷/۱۳» (ارقام فارسی برای fa)
    function format(iso) {
        const date = localDate(iso);
        if (!date) {
            return '';
        }
        const c = Calendar.fromDate(date);
        return Calendar.digits(c.y + '/' + pad(c.m) + '/' + pad(c.d));
    }

    // ISO → «۱۳ مهر ۱۴۰۵»
    function formatLong(iso) {
        const date = localDate(iso);
        if (!date) {
            return '';
        }
        const c = Calendar.fromDate(date);
        return Calendar.digits(c.d) + ' ' + Calendar.monthNames[c.m - 1] + ' ' + Calendar.digits(c.y);
    }

    // نامِ روزِ هفته («پنجشنبه»)؛ weekdayNames شنبه‌محور است
    function weekdayName(iso) {
        const date = localDate(iso);
        return date ? Calendar.weekdayNames[Calendar.weekdayIndex(date, 'SAT')] : '';
    }

    function addDays(iso, days) {
        const date = localDate(iso);
        if (!date) {
            return iso;
        }
        date.setDate(date.getDate() + days);
        return isoOf(date);
    }

    // تعدادِ روزهای بازه‌ی بسته [from, to]؛ 0 اگر نامعتبر یا برعکس باشد
    function daysBetweenInclusive(fromIso, toIso) {
        const from = localDate(fromIso);
        const to = localDate(toIso);
        if (!from || !to || to < from) {
            return 0;
        }
        const utc = (d) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
        return Math.round((utc(to) - utc(from)) / DAY_MS) + 1;
    }

    // { y, m, d } جلالی (یا میلادی) یک ISO
    function civil(iso) {
        const date = localDate(iso);
        return date ? Calendar.fromDate(date) : null;
    }

    // بازه‌ی ماهِ (y, m) تقویمِ صفحه → { from, to } ISO میلادی
    function monthRange(y, m) {
        return Calendar.periodRange(y, 'month', m);
    }

    return {
        toAscii, today, parse, format, formatLong, weekdayName, addDays, daysBetweenInclusive,
        civil, monthRange, isoOf
    };
})();
