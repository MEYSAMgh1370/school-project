// منطقِ خالصِ بخش «رویدادهای نزدیک» پیشخوان (openspec/changes/event-reminders.md): برچسبِ نسبیِ «امروز /
// فردا / N روز دیگر» از daysLeft و سطرهای نمایشی. ISO فقط داده است؛ نمایش با ارقام فارسی از Calendar/DateInput
// می‌گذرد. جدا از dashboard-upcoming.js است تا بدونِ مرورگر و Alpine تست‌پذیر باشد.
//
// وابستگی: /js/jalali.js، /js/calendar.js و /js/date-input.js پیش از این فایل.
window.UpcomingEventsLogic = (function () {
    // daysLeft: 0 = امروز؛ مقدارِ نامعتبر یا منفی ''
    function relativeLabel(daysLeft) {
        if (!Number.isInteger(daysLeft) || daysLeft < 0) {
            return '';
        }
        if (daysLeft === 0) {
            return 'امروز';
        }
        return daysLeft === 1 ? 'فردا' : Calendar.digits(daysLeft) + ' روز دیگر';
    }

    // پاسخ API ({ events: [...] }) → سطرهای نمایش، به ترتیبِ روز و سپس عنوان؛ ورودیِ ناقص یا بی‌روز نادیده گرفته می‌شود
    function rows(payload) {
        const events = payload && Array.isArray(payload.events) ? payload.events : [];
        return events
            .filter((event) => event && event.title && DateInput.civil(event.day))
            .sort((a, b) => a.day < b.day ? -1 : a.day > b.day ? 1 : a.title.localeCompare(b.title))
            .map((event) => ({
                id: event.id,
                title: event.title,
                date: DateInput.format(event.day),
                relative: relativeLabel(event.daysLeft)
            }));
    }

    return { relativeLabel, rows };
})();
