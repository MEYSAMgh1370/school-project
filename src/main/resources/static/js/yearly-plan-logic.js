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

    // رویدادها به ترتیبِ زمانی (روز، سپس عنوان) و گروه‌بندی‌شده به ماهِ تقویمِ صفحه؛ چون سال از مهر تا خرداد است،
    // ترتیبِ زمانی همان ترتیبِ مهر … خرداد است. ماهِ بدونِ رویداد گروهی ندارد.
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

    // «سال تحصیلی ۱۴۰۵-۱۴۰۶ (۱ مهر ۱۴۰۵ تا ۳۱ خرداد ۱۴۰۶)»
    function yearLine(year) {
        return 'سال تحصیلی ' + Calendar.digits(year.code) + ' (' + DateInput.formatLong(year.startDate)
            + ' تا ' + DateInput.formatLong(year.endDate) + ')';
    }

    // متنِ راهنمای فیلدِ تاریخ: بازه‌ی مجازِ سال
    function rangeHelp(year) {
        return 'تاریخ را به شکل سال/ماه/روز بنویسید (ارقام فارسی هم پذیرفته می‌شود)؛ فقط از '
            + DateInput.formatLong(year.startDate) + ' تا ' + DateInput.formatLong(year.endDate) + ' مجاز است.';
    }

    // بازه‌ی بسته‌ی [startDate, endDate]؛ ISO با ارقام ASCII مقایسه‌پذیر است
    function inRange(iso, year) {
        return iso >= year.startDate && iso <= year.endDate;
    }

    // نمونه‌ی تاریخ داخلِ سال برای placeholder: امروز اگر داخل سال باشد، وگرنه اولِ سال
    function exampleDate(year, todayIso) {
        return DateInput.format(inRange(todayIso, year) ? todayIso : year.startDate);
    }

    return { itemKey, groupByMonth, yearLine, rangeHelp, inRange, exampleDate };
})();
