// «تقویمِ نمایشی» - تنها نقطه‌ای که صفحه‌ها برای نمایش و حساب‌کردن تاریخ باید
// با آن حرف بزنند، نه مستقیم با window.Jalali (openspec/changes/
// eu-market-readiness.md، W2).
//
// زبان از صفت lang روی <html> خوانده می‌شود: سرور (LocaleResolverFilter) آن را
// از Accept-Language حل می‌کند و main.jte روی صفحه می‌گذارد (W3)، پس کلاینت
// خودش چیزی حدس نمی‌زند. fa تقویم جلالی می‌دهد و هر زبان دیگری میلادی.
// نبودِ lang هم مثل سرور فارسی حساب می‌شود.
//
// این فایل فقط «نمایش» را عوض می‌کند. تاریخ‌ها در API و ذخیره میلادی/ISO-8601
// می‌مانند. استثنا: «سال تحصیلی» - سرور کدش را با قاعده‌ی جلالی می‌سازد و ذخیره
// می‌کند (school.internal.AcademicYearProvisioner، مثلاً 1405-1406)؛ آن یک قاعده‌ی
// دامنه است نه نمایش، پس اینجا دست‌نخورده می‌ماند (eu-market-readiness.md، W8).
// روز شروع هفته هم عمداً اینجا نیست: هفته‌ی درسی شنبه‌محور یک قانون دامنه‌ی
// سرور است (studyplan و exam، requireSaturday)، نه ترجیح نمایشی - تغییرش
// تصمیم محصول است. به همین دلیل weekdayNames در هر دو تقویم از شنبه شروع می‌شود.
//
// وابستگی: برای fa باید /js/jalali.js پیش از این فایل بارگذاری شده باشد.
// برای زبان‌های میلادی لازم نیست.
window.Calendar = (function () {
    const DAY_MS = 86400000;

    // ---------------------------------------------------- بخش مشترک

    // index صفر شنبه است (نه یکشنبه‌ی getDay) - همان قانون هفته‌ی شنبه‌محور
    // سرور؛ به تقویم وابسته نیست، پس بین هر دو پیاده‌سازی مشترک است.
    function weekdayIndex(date) {
        return (date.getDay() + 1) % 7;
    }

    function toIsoDate(date) {
        return date.toISOString().slice(0, 10);
    }

    // طول ماه از تفاوت دو toDate - برای هر تقویمی درست است و جدول جدایی لازم ندارد.
    function daysInMonth(calendar, y, m) {
        const nextY = m === 12 ? y + 1 : y;
        const nextM = m === 12 ? 1 : m + 1;
        return Math.round((calendar.toDate(nextY, nextM, 1) - calendar.toDate(y, m, 1)) / DAY_MS);
    }

    // بازه‌ی ماه/فصل/سال → { from, to } به‌صورت YYYY-MM-DD میلادی (برای query
    // param). هر دو تقویم ۱۲ ماه دارند و فصل سه ماه است، پس منطق یکی است.
    function periodRange(calendar, y, kind, index) {
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
        const endMonth = startMonth + monthCount - 1;
        const from = calendar.toDate(y, startMonth, 1);
        const to = calendar.toDate(y, endMonth, calendar.daysInMonth(y, endMonth));
        return { from: toIsoDate(from), to: toIsoDate(to) };
    }

    // ---------------------------------------------------- تقویم جلالی (fa)

    function createJalali() {
        const J = window.Jalali;
        if (!J) {
            throw new Error('calendar.js: برای زبان fa باید /js/jalali.js پیش از calendar.js بارگذاری شود.');
        }
        const calendar = {
            kind: 'jalali',
            monthNames: J.monthNames,
            weekdayNames: J.weekdayNames,
            weekdayShort: J.weekdayShort,
            weekdayIndex: weekdayIndex,
            digits: J.toPersianDigits,

            // از اجزای محلیِ Date می‌خواند (همان کاری که فراخوان‌ها تا امروز
            // دستی می‌کردند)، و { y, m, d } جلالی برمی‌گرداند.
            fromDate(date) {
                const j = J.toJalali(date.getFullYear(), date.getMonth() + 1, date.getDate());
                return { y: j.jy, m: j.jm, d: j.jd };
            },

            // نیمه‌شبِ UTC همان روز (قرارداد قدیمیِ Jalali.toGregorian)
            toDate(y, m, d) {
                return J.toGregorian(y, m, d);
            },

            // دقیقاً همان toLocaleDateString('fa-IR') که قالب‌ها قبلاً مستقیم صدا می‌زدند
            formatDate(value) {
                return new Date(value).toLocaleDateString('fa-IR');
            },

            // برچسب بازه‌ی یک هفته. عیناً منطق قدیمیِ weekRangeLabel در
            // my-study-plan.js؛ «تا» متن فارسی است و تا W1 (پیام‌های ترجمه‌پذیر)
            // همین‌جا می‌ماند.
            formatRange(startDate, endDate) {
                const start = calendar.fromDate(startDate);
                const end = calendar.fromDate(endDate);
                const names = calendar.monthNames;
                const fa = calendar.digits;
                const startLabel = fa(start.d) + ' ' + names[start.m - 1];
                if (start.y === end.y && start.m === end.m) {
                    return fa(start.d) + ' تا ' + fa(end.d) + ' ' + names[start.m - 1] + ' ' + fa(start.y);
                }
                if (start.y === end.y) {
                    return startLabel + ' تا ' + fa(end.d) + ' ' + names[end.m - 1] + ' ' + fa(start.y);
                }
                return startLabel + ' ' + fa(start.y) + ' تا ' + fa(end.d) + ' ' + names[end.m - 1] + ' ' + fa(end.y);
            }
        };
        calendar.daysInMonth = (y, m) => daysInMonth(calendar, y, m);
        calendar.periodRange = (y, kind, index) => periodRange(calendar, y, kind, index);
        return calendar;
    }

    // ---------------------------------------------------- تقویم میلادی

    function createGregorian(lang) {
        // نام‌ها از Intl می‌آیند، نه جدول دست‌نویس: متنی اختراع نمی‌شود و
        // زبان‌های بعدی (de، nl، fr) بدون کد تازه کار می‌کنند.
        const monthFormat = new Intl.DateTimeFormat(lang, { month: 'long', timeZone: 'UTC' });
        const weekdayLong = new Intl.DateTimeFormat(lang, { weekday: 'long', timeZone: 'UTC' });
        // 'short' نه 'narrow': narrow تکراری می‌دهد (S,S,T,T) و x-for با :key یکتا را می‌شکند
        const weekdayShortFormat = new Intl.DateTimeFormat(lang, { weekday: 'short', timeZone: 'UTC' });
        const dateFormat = new Intl.DateTimeFormat(lang, { year: 'numeric', month: 'short', day: 'numeric' });

        // ۳ اکتبر ۲۰۲۶ شنبه است؛ هفت روز از آن، فهرست شنبه‌محور را می‌سازد
        const SATURDAY = Date.UTC(2026, 9, 3);
        const weekday = (format, i) => format.format(new Date(SATURDAY + i * DAY_MS));

        const calendar = {
            kind: 'gregorian',
            monthNames: Array.from({ length: 12 }, (_, i) => monthFormat.format(new Date(Date.UTC(2026, i, 1)))),
            weekdayNames: Array.from({ length: 7 }, (_, i) => weekday(weekdayLong, i)),
            weekdayShort: Array.from({ length: 7 }, (_, i) => weekday(weekdayShortFormat, i)),
            weekdayIndex: weekdayIndex,
            digits: (value) => String(value),

            fromDate(date) {
                return { y: date.getFullYear(), m: date.getMonth() + 1, d: date.getDate() };
            },

            // نیمه‌شبِ UTC همان روز، مثل نسخه‌ی جلالی. setUTCFullYear، چون
            // Date.UTC سال‌های ۰ تا ۹۹ را ۱۹۰۰ به بعد می‌گیرد.
            toDate(y, m, d) {
                const date = new Date(0);
                date.setUTCFullYear(y, m - 1, d);
                return date;
            },

            formatDate(value) {
                return dateFormat.format(new Date(value));
            },

            formatRange(startDate, endDate) {
                return dateFormat.formatRange(startDate, endDate);
            }
        };
        calendar.daysInMonth = (y, m) => daysInMonth(calendar, y, m);
        calendar.periodRange = (y, kind, index) => periodRange(calendar, y, kind, index);
        return calendar;
    }

    // ---------------------------------------------------- انتخاب بر اساس زبان

    function forLanguage(lang) {
        const base = String(lang || 'fa').toLowerCase().split('-')[0];
        return base === 'fa' ? createJalali() : createGregorian(base);
    }

    const documentLang = typeof document !== 'undefined' && document.documentElement
        ? document.documentElement.getAttribute('lang')
        : null;
    const active = forLanguage(documentLang);
    // برای تست و هر جایی که باید صریحاً یک زبان را بسازد
    active.forLanguage = forLanguage;
    return active;
})();
