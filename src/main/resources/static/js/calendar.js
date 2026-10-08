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
// روز شروع هفته یک قانون دامنه‌ی هر مدرسه است نه ترجیح نمایشی (W9): سرور
// (SchoolFacade.WeekStart) آن را برای هر مدرسه نگه می‌دارد و navbar در
// data-week-starts می‌گذارد. این فایل آن را فقط می‌خواند (weekStart()) و هیچ
// پیش‌فرضی از زبان نمی‌سازد: مدرسه‌ی ایرانی و فارسی یکی نیستند. فهرست‌های
// weekdayNames/weekdayShort همچنان شنبه‌اول‌اند؛ *For(week) آن‌ها را بر اساس
// هفته‌ی مدرسه می‌چرخاند. بدون navbar (صفحه‌ی بی‌مدرسه) شنبه است.
//
// وابستگی: برای fa باید /js/jalali.js پیش از این فایل بارگذاری شده باشد.
// برای زبان‌های میلادی لازم نیست.
window.Calendar = (function () {
    const DAY_MS = 86400000;

    // ---------------------------------------------------- بخش مشترک

    // ---- هفته‌ی مدرسه (W9): به تقویم وابسته نیست، پس بین هر دو پیاده‌سازی مشترک است

    // کدها همان کدهای ذخیره‌شده‌ی school.week_start_day و TimetableDay اند
    const WEEK_CODES = ['SAT', 'SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI']; // شنبه‌اول
    // Date.getDay() (یکشنبه ۰) روزِ اولِ هر هفته
    const FIRST_GET_DAY = { SAT: 6, MON: 1 };

    // مدرسه‌ی انتخاب‌شده در navbar: همان قرارداد صفحه‌ها - navbar پیش از کامپوننت
    // صفحه init می‌شود و مدرسه را در localStorage می‌گذارد.
    function readSelectedWeekStart() {
        if (typeof document === 'undefined') return 'SAT';
        const holder = document.querySelector('[data-week-starts]');
        if (!holder) return 'SAT';
        let bySchool;
        try {
            bySchool = JSON.parse(holder.getAttribute('data-week-starts'));
        } catch (e) {
            return 'SAT';
        }
        let id = null;
        try {
            id = localStorage.getItem('selectedSchoolId');
        } catch (e) {
            // حالت خصوصی/مسدود: مثل navbar، اولین مدرسه
        }
        const week = Object.prototype.hasOwnProperty.call(bySchool, id) ? bySchool[id] : Object.values(bySchool)[0];
        return Object.prototype.hasOwnProperty.call(FIRST_GET_DAY, week) ? week : 'SAT';
    }

    let weekStartSource = readSelectedWeekStart;

    function resolveWeek(week) {
        const resolved = week || weekStartSource();
        if (!Object.prototype.hasOwnProperty.call(FIRST_GET_DAY, resolved)) {
            throw new Error('calendar.js: روز شروع هفته‌ی ناشناخته: ' + resolved);
        }
        return resolved;
    }

    // جایگاه یک روز در هفته‌ی مدرسه: ۰ = روز شروع هفته. بدون week، مدرسه‌ی انتخاب‌شده.
    function weekdayIndex(date, week) {
        return (date.getDay() - FIRST_GET_DAY[resolveWeek(week)] + 7) % 7;
    }

    // نیمه‌شبِ محلیِ آخرین روز شروع هفته (همان روز یا پیش از آن)
    function weekStartOf(date, week) {
        const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
        start.setDate(start.getDate() - weekdayIndex(start, week));
        return start;
    }

    // لیستی شنبه‌اول را به هفته‌ی مدرسه می‌چرخاند
    function rotate(list, week) {
        const shift = WEEK_CODES.indexOf(resolveWeek(week));
        return list.slice(shift).concat(list.slice(0, shift));
    }

    function addWeekSupport(calendar) {
        calendar.weekStart = () => resolveWeek();
        calendar.weekStartOf = weekStartOf;
        calendar.weekCodes = (week) => rotate(WEEK_CODES, week);
        // روزهای درسی = پنج روز اولِ هفته: شنبه تا چهارشنبه یا دوشنبه تا جمعه
        // (همان SchoolFacade.WeekStart.schoolDays())
        calendar.schoolDayCodes = (week) => rotate(WEEK_CODES, week).slice(0, 5);
        calendar.weekdayNamesFor = (week) => rotate(calendar.weekdayNames, week);
        calendar.weekdayShortFor = (week) => rotate(calendar.weekdayShort, week);
        return calendar;
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

    // بازه‌ی زمانِ محلی (مثلاً «2026-10-20T08:00») → تاریخ و ساعت از خودِ متن.
    // عمداً از Date(string) استفاده نمی‌کنیم: آن برای رشته‌ی بدون منطقه گاهی
    // ساعت را جابه‌جا می‌کند. Date محلی از اجزا ساخته می‌شود، پس روزِ جلالی درست است.
    function parseLocalDateTime(value) {
        const match = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/.exec(String(value));
        if (!match) {
            throw new Error('calendar.js: زمان ناشناخته: ' + value);
        }
        return {
            date: new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])),
            hour: match[4] || '00',
            minute: match[5] || '00'
        };
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
            },

            // بازه‌ی زمان با ساعت ۲۴‌تایی و رقم فارسی. اگر شروع و پایان یک روز باشند تاریخ
            // یک‌بار می‌آید («۲۸ مهر ۱۴۰۵ ۰۸:۰۰ تا ۱۰:۰۰»)؛ در غیر این صورت هر دو تاریخ
            formatDateTimeRange(startValue, endValue) {
                const start = parseLocalDateTime(startValue);
                const end = parseLocalDateTime(endValue);
                const time = (p) => calendar.digits(p.hour) + ':' + calendar.digits(p.minute);
                if (start.date.getTime() === end.date.getTime()) {
                    return calendar.formatDate(start.date) + ' ' + time(start) + ' تا ' + time(end);
                }
                return calendar.formatDate(start.date) + ' ' + time(start) + ' تا ' + calendar.formatDate(end.date) + ' ' + time(end);
            }
        };
        calendar.daysInMonth = (y, m) => daysInMonth(calendar, y, m);
        calendar.periodRange = (y, kind, index) => periodRange(calendar, y, kind, index);
        return addWeekSupport(calendar);
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
            },

            // همان قاعده‌ی fa؛ ساعت ۲۴‌تایی و رقم لاتین (زبان میلادی رقم فارسی ندارد)
            formatDateTimeRange(startValue, endValue) {
                const start = parseLocalDateTime(startValue);
                const end = parseLocalDateTime(endValue);
                const time = (p) => p.hour + ':' + p.minute;
                if (start.date.getTime() === end.date.getTime()) {
                    return calendar.formatDate(start.date) + ' ' + time(start) + ' – ' + time(end);
                }
                return calendar.formatDate(start.date) + ' ' + time(start) + ' – ' + calendar.formatDate(end.date) + ' ' + time(end);
            }
        };
        calendar.daysInMonth = (y, m) => daysInMonth(calendar, y, m);
        calendar.periodRange = (y, kind, index) => periodRange(calendar, y, kind, index);
        return addWeekSupport(calendar);
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
    // برای تست: منبع «هفته‌ی مدرسه‌ی انتخاب‌شده» را جایگزین می‌کند (null = برگشت به خواندن از navbar)
    active.setWeekStartSource = (source) => {
        weekStartSource = source || readSelectedWeekStart;
    };
    return active;
})();
