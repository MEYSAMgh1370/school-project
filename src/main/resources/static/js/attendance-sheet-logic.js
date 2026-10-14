// منطقِ خالصِ «برگه‌ی حضور و غیابِ دانش‌آموز» (/attendance/report/student/{id}): خواندنِ بازه از آدرس، مسیرِ API،
// ترتیب و برچسبِ روزها و متنِ درصد (بدون مرورگر و Alpine؛ تست: src/test/js/attendance-sheet-logic.test.js).
// قالب‌بندیِ تاریخ و ارقام از بیرون می‌آید (fmt = { date, long, weekday, digits }، همان DateInput/Calendar)،
// تا این فایل به هیچ فایلِ دیگری وابسته نباشد. روی سیم همیشه ISO میلادیِ ASCII است.
window.AttendanceSheetLogic = (function () {
    const ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
    const STATUS_LABELS = { ABSENT: 'غایب', LATE: 'تأخیر' };

    // ISO واقعی: «۲۰۲۶-۰۲-۳۰» رد می‌شود (سرریز به ماهِ بعد)
    function isRealIso(value) {
        const match = ISO.exec(value || '');
        if (!match) {
            return false;
        }
        const y = Number(match[1]);
        const m = Number(match[2]);
        const d = Number(match[3]);
        const date = new Date(Date.UTC(y, m - 1, d));
        return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
    }

    // ?from=&to= → { from, to } یا null اگر نبود، ISO واقعی نبود یا پایان قبل از شروع بود
    function rangeFromSearch(search) {
        const params = new URLSearchParams(search || '');
        const from = params.get('from');
        const to = params.get('to');
        if (!isRealIso(from) || !isRealIso(to) || to < from) {
            return null;
        }
        return { from, to };
    }

    function apiPath(schoolId, studentId, range) {
        return `/api/admin/schools/${schoolId}/attendance/report/students/${studentId}?from=${range.from}&to=${range.to}`;
    }

    function statusLabel(code) {
        return STATUS_LABELS[code] || code;
    }

    // روزِ قدیمی‌تر اول؛ در یک روز به ترتیبِ نامِ کلاس
    function sortedDays(days) {
        return (days || []).slice().sort((a, b) => {
            if (a.day !== b.day) {
                return a.day < b.day ? -1 : 1;
            }
            return String(a.className || '').localeCompare(String(b.className || ''), 'fa');
        });
    }

    // یک ردیفِ آمادهٔ نمایش برای هر روزِ غیبت/تأخیر
    function dayLines(days, fmt) {
        return sortedDays(days).map((item) => ({
            key: item.day + '-' + item.classId + '-' + item.status,
            date: fmt.date(item.day),
            weekday: fmt.weekday(item.day),
            className: item.className,
            status: item.status,
            statusLabel: statusLabel(item.status),
            excused: item.status === 'ABSENT' && item.excused === true,
            note: item.note || ''
        }));
    }

    function percentLabel(percent, fmt) {
        return percent === null || percent === undefined ? '—' : fmt.digits(percent) + '٪';
    }

    // مقدارِ نوارِ <progress>: بیرون از 0..100 نمی‌رود
    function barValue(percent) {
        if (percent === null || percent === undefined) {
            return 0;
        }
        return Math.min(100, Math.max(0, percent));
    }

    function totals(rows) {
        return (rows || []).reduce((sum, row) => ({
            absentDays: sum.absentDays + row.absentDays,
            lateDays: sum.lateDays + row.lateDays,
            presentDays: sum.presentDays + row.presentDays
        }), { absentDays: 0, lateDays: 0, presentDays: 0 });
    }

    function rangeText(range, fmt) {
        return range ? fmt.long(range.from) + ' تا ' + fmt.long(range.to) : '';
    }

    return { isRealIso, rangeFromSearch, apiPath, statusLabel, sortedDays, dayLines, percentLabel, barValue, totals, rangeText };
})();
