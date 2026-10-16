// منطقِ خالصِ «گزارش حضور و غیاب» (/attendance/report): متن و مقدارِ نوارِ درصد، نامِ معلم و ترتیبِ معلم‌ها،
// مسیرِ API و پیوندِ برگه‌ی دانش‌آموز (بدون مرورگر و Alpine؛ تست: src/test/js/attendance-report-logic.test.js).
// ارقامِ فارسی از بیرون می‌آید (digits = Calendar.digits) تا این فایل به فایلِ دیگری وابسته نباشد؛
// روی سیم همیشه ISO و ارقامِ لاتین است.
window.AttendanceReportLogic = (function () {
    const DASH = '—';

    // null (پایه‌ی صفر) = «—»؛ وگرنه عدد با رقمِ فارسی و «٪»
    function percentLabel(percent, digits) {
        return percent === null || percent === undefined ? DASH : digits(percent) + '٪';
    }

    // مقدارِ نوارِ <progress>: بیرون از 0..100 نمی‌رود
    function barValue(percent) {
        if (percent === null || percent === undefined || Number.isNaN(Number(percent))) {
            return 0;
        }
        return Math.min(100, Math.max(0, Number(percent)));
    }

    function hasPercent(percent) {
        return percent !== null && percent !== undefined;
    }

    // نامِ معلم؛ کاربرِ ناشناس با شناسه نشان داده می‌شود
    function teacherLabel(teacher, digits) {
        const name = teacher && typeof teacher.teacherName === 'string' ? teacher.teacherName.trim() : '';
        return name !== '' ? name : 'نامشخص (شناسه ' + digits(teacher ? teacher.teacherUserId : '') + ')';
    }

    // معلم‌های نام‌دار به ترتیبِ الفبای فارسی، ناشناس‌ها آخر (به ترتیبِ شناسه)؛ ورودی تغییر نمی‌کند
    function sortedTeachers(teachers) {
        const named = (item) => typeof item.teacherName === 'string' && item.teacherName.trim() !== '';
        return (teachers || []).slice().sort((a, b) => {
            if (named(a) !== named(b)) {
                return named(a) ? -1 : 1;
            }
            if (named(a)) {
                return a.teacherName.trim().localeCompare(b.teacherName.trim(), 'fa');
            }
            return Number(a.teacherUserId) - Number(b.teacherUserId);
        });
    }

    // /api/admin/schools/{id}/attendance/report[/classes|/teachers]?from&to[&classId]
    function reportPath(schoolId, kind, range, classId) {
        const suffix = kind ? '/' + kind : '';
        let path = `/api/admin/schools/${schoolId}/attendance/report${suffix}?from=${range.from}&to=${range.to}`;
        if (classId !== undefined && classId !== null && String(classId) !== '') {
            path += `&classId=${encodeURIComponent(classId)}`;
        }
        return path;
    }

    function sheetHref(studentId, range) {
        return `/attendance/report/student/${encodeURIComponent(studentId)}?from=${range.from}&to=${range.to}`;
    }

    return { percentLabel, barValue, hasPercent, teacherLabel, sortedTeachers, reportPath, sheetHref };
})();
