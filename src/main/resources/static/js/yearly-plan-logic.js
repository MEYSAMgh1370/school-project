// منطقِ خالصِ «برنامه‌ی سالانه» (/yearly-plan، openspec/changes/yearly-plan.md): گروه‌بندیِ رویدادها بر اساس
// ماهِ تقویمِ صفحه (جلالی برای fa) و متن‌های بازه‌ی سالِ تحصیلی. جدا از yearly-plan.js است تا بدونِ مرورگر و
// Alpine تست‌پذیر باشد. ISO فقط داده است؛ نمایش با Persian digits از Calendar/DateInput می‌گذرد.
//
// وابستگی: /js/jalali.js، /js/calendar.js و /js/date-input.js پیش از این فایل.
window.YearlyPlanLogic = (function () {
    // کلیدِ پایدارِ یک ردیف: id ماخذهای مختلف می‌تواند یکی باشد (PLATFORM و SCHOOL)
    function itemKey(item) {
        return item.source + ':' + item.id;
    }

    // رویدادها به ترتیبِ زمانی (روز، سپس عنوان) و گروه‌بندی‌شده به ماهِ تقویمِ صفحه. ترتیبِ زمانی همان ترتیبِ ماه
    // نسبت به شروعِ بازه است: بازه‌ی پیش‌فرض مهر … خرداد را می‌دهد و بازه‌ی گسترش‌یافته (مثلاً از مرداد یا تا
    // شهریور) ماه‌های بیرونِ آن را هم به‌ترتیب می‌آورد؛ کلیدِ گروه سال را هم دارد. ماهِ بدونِ رویداد گروهی ندارد.
    function groupByMonth(items) {
        const sorted = [...items].sort((a, b) =>
            a.day < b.day ? -1 : a.day > b.day ? 1 : a.title.localeCompare(b.title));
        const groups = [];
        for (const item of sorted) {
            const civil = DateInput.civil(item.day);
            if (!civil) {
                continue;
            }
            const key = civil.y + '-' + civil.m;
            let group = groups.length ? groups[groups.length - 1] : null;
            if (!group || group.key !== key) {
                group = { key, title: Calendar.monthNames[civil.m - 1] + ' ' + Calendar.digits(civil.y), items: [] };
                groups.push(group);
            }
            group.items.push(item);
        }
        return groups;
    }

    // بازه‌ی مؤثرِ مدرسه ({ startDate, endDate }): range اگر باشد، وگرنه تاریخ‌های پیش‌فرضِ خودِ سال
    function effectiveRange(year, range) {
        const source = range || year;
        return { startDate: source.startDate, endDate: source.endDate };
    }

    // «سال تحصیلی ۱۴۰۵-۱۴۰۶ (۱ مهر ۱۴۰۵ تا ۳۱ خرداد ۱۴۰۶)» با بازه‌ی مؤثر
    function yearLine(year, range) {
        const eff = effectiveRange(year, range);
        return 'سال تحصیلی ' + Calendar.digits(year.code) + ' (' + DateInput.formatLong(eff.startDate)
            + ' تا ' + DateInput.formatLong(eff.endDate) + ')';
    }

    // برچسبِ «بازه‌ی سفارشی» فقط وقتی مدرسه بازه را گسترش داده است
    const CUSTOM_TAG = 'بازه‌ی سفارشی';
    function isCustom(range) {
        return !!(range && range.custom);
    }

    // متنِ راهنمای فیلدِ تاریخ: بازه‌ی مجازِ مؤثر
    function rangeHelp(year, range) {
        const eff = effectiveRange(year, range);
        return 'تاریخ را به شکل سال/ماه/روز بنویسید (ارقام فارسی هم پذیرفته می‌شود)؛ فقط از '
            + DateInput.formatLong(eff.startDate) + ' تا ' + DateInput.formatLong(eff.endDate) + ' مجاز است.';
    }

    // سقفِ گسترش: حداکثر یک ماهِ تقویمِ صفحه از هر طرف (برای fa: ۱ شهریور تا ۳۱ تیر). سرور مرجعِ نهایی
    // است و همین قاعده را با ماه‌های جلالی می‌سنجد؛ اینجا فقط برای پیامِ زودهنگام است.
    function rangeLimits(year) {
        const s = DateInput.civil(year.startDate);
        const e = DateInput.civil(year.endDate);
        const before = s.m === 1 ? { y: s.y - 1, m: 12 } : { y: s.y, m: s.m - 1 };
        const after = e.m === 12 ? { y: e.y + 1, m: 1 } : { y: e.y, m: e.m + 1 };
        return {
            earliestStart: DateInput.monthRange(before.y, before.m).from,
            latestEnd: DateInput.monthRange(after.y, after.m).to
        };
    }

    // راهنمای modal تنظیم بازه: پیش‌فرض حداقل است و تا یک ماه از هر طرف گسترش‌پذیر
    function defaultRangeHelp(year) {
        const limits = rangeLimits(year);
        return 'بازه‌ی پیش‌فرض (از ' + DateInput.formatLong(year.startDate) + ' تا ' + DateInput.formatLong(year.endDate)
            + ') حداقل بازه است؛ فقط می‌توانید آن را گسترش دهید، حداکثر یک ماه از هر طرف (از '
            + DateInput.formatLong(limits.earliestStart) + ' تا ' + DateInput.formatLong(limits.latestEnd) + ').';
    }

    // بازه‌ی بسته‌ی [startDate, endDate]؛ ISO با ارقام ASCII مقایسه‌پذیر است
    function inRange(iso, range) {
        return iso >= range.startDate && iso <= range.endDate;
    }

    // نمونه‌ی تاریخ داخلِ بازه برای placeholder: امروز اگر داخل بازه باشد، وگرنه اولِ بازه
    function exampleDate(range, todayIso) {
        return DateInput.format(inRange(todayIso, range) ? todayIso : range.startDate);
    }

    // کلیدِ خطای اعتبارسنجیِ بازه → متن (سرور مرجع نهایی است)
    function rangeMessage(key, year) {
        switch (key) {
            case 'required':
                return 'تاریخ را بنویسید.';
            case 'invalid':
                return 'تاریخ را به شکل سال/ماه/روز بنویسید، مثل ' + DateInput.format(year.startDate) + '.';
            case 'startAfterDefault':
                return 'شروع نباید بعد از ' + DateInput.formatLong(year.startDate) + ' باشد؛ بازه را فقط می‌توانید گسترش دهید.';
            case 'endBeforeDefault':
                return 'پایان نباید پیش از ' + DateInput.formatLong(year.endDate) + ' باشد؛ بازه را فقط می‌توانید گسترش دهید.';
            case 'startTooEarly':
                return 'شروع نباید پیش از ' + DateInput.formatLong(rangeLimits(year).earliestStart)
                    + ' باشد؛ بازه را حداکثر یک ماه می‌توانید گسترش دهید.';
            case 'endTooLate':
                return 'پایان نباید بعد از ' + DateInput.formatLong(rangeLimits(year).latestEnd)
                    + ' باشد؛ بازه را حداکثر یک ماه می‌توانید گسترش دهید.';
            case 'startAfterEnd':
                return 'شروع باید پیش از پایان باشد.';
            default:
                return '';
        }
    }

    // اعتبارسنجیِ «فقط گسترش»: متنِ دو ورودی + بازه‌ی پیش‌فرضِ سال → { startDate, endDate, startError, endError }
    // که خطاها کلیدِ پیام‌اند ('' = درست): required | invalid | startAfterDefault | endBeforeDefault | startTooEarly | endTooLate | startAfterEnd
    function validateRange(startText, endText, year) {
        const startDate = DateInput.parse(startText || '');
        const endDate = DateInput.parse(endText || '');
        const limits = rangeLimits(year);
        let startError = '';
        let endError = '';
        if (!(startText || '').trim()) {
            startError = 'required';
        } else if (startDate === null) {
            startError = 'invalid';
        } else if (startDate > year.startDate) {
            startError = 'startAfterDefault';
        } else if (startDate < limits.earliestStart) {
            startError = 'startTooEarly';
        }
        if (!(endText || '').trim()) {
            endError = 'required';
        } else if (endDate === null) {
            endError = 'invalid';
        } else if (endDate < year.endDate) {
            endError = 'endBeforeDefault';
        } else if (endDate > limits.latestEnd) {
            endError = 'endTooLate';
        }
        if (!startError && !endError && startDate > endDate) {
            endError = 'startAfterEnd';
        }
        return { startDate, endDate, startError, endError };
    }

    // سالِ پیش‌فرضِ «تعطیلی‌های سراسری» (/platform/holidays): آخرین سالی که بازه‌اش امروز را دربرمی‌گیرد، وگرنه
    // آخرین سالِ فهرست (فهرست از قدیمی به جدید است)؛ بدونِ سال null
    function pickYear(years, todayIso) {
        for (let i = years.length - 1; i >= 0; i--) {
            if (inRange(todayIso, years[i])) {
                return years[i];
            }
        }
        return years.length ? years[years.length - 1] : null;
    }

    return {
        itemKey, groupByMonth, pickYear, effectiveRange, yearLine, isCustom, CUSTOM_TAG, rangeHelp, defaultRangeHelp,
        inRange, exampleDate, rangeMessage, validateRange, rangeLimits
    };
})();
