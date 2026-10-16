// «برگه‌ی حضور و غیابِ دانش‌آموز» (/attendance/report/student/{id}?from=&to=، openspec/changes/attendance-reports.md):
// صفحه‌ی چاپ بدون ناوبار؛ مرورگر آن را PDF می‌کند. بازه از آدرس می‌آید (ISO)، داده از
// GET .../attendance/report/students/{id} (ATTENDANCE_VIEW_ALL) با هدرِ X-School-ID (school-api.js).
// نمایش: تاریخِ جلالی و ارقام فارسی؛ منطقِ خالص در attendance-sheet-logic.js.
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول حضور و غیاب را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دیدنِ گزارش حضور و غیاب را ندارد.'
    };
    const L = window.AttendanceSheetLogic;
    const FMT = {
        date: (iso) => DateInput.format(iso),
        long: (iso) => DateInput.formatLong(iso),
        weekday: (iso) => DateInput.weekdayName(iso),
        digits: (value) => Calendar.digits(value)
    };

    Alpine.data('attendanceSheetPage', () => ({
        schoolId: null,
        schoolName: '',
        studentId: null,
        range: null,
        sheet: null,
        classRows: [],
        dayRows: [],
        loading: false,
        blocked: false,
        error: '',

        async init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.studentId = this.$el.dataset.studentId;
            if (!this.schoolId) {
                this.error = 'مدرسه‌ای انتخاب نشده است؛ از صفحه‌ی گزارش حضور و غیاب دوباره وارد شوید.';
                return;
            }
            const holder = document.querySelector('[data-school-id="' + this.schoolId + '"]');
            this.schoolName = holder ? holder.dataset.schoolName : '';
            this.range = L.rangeFromSearch(window.location.search);
            if (this.range === null) {
                this.error = 'بازه‌ی گزارش معتبر نیست؛ از صفحه‌ی گزارش حضور و غیاب، «برگه‌ی حضور» را دوباره بزنید.';
                return;
            }
            await this.load();
        },

        async load() {
            this.loading = true;
            this.error = '';
            try {
                const sheet = await SchoolApi.request(this.schoolId, 'GET',
                    L.apiPath(this.schoolId, this.studentId, this.range), undefined, MESSAGES);
                this.classRows = sheet.rows || [];
                this.dayRows = L.dayLines(sheet.days, FMT);
                this.sheet = sheet;
            } catch (e) {
                this.sheet = null;
                this.blocked = !!e.blocking;
                this.error = SchoolApi.messageOf(e);
            } finally {
                this.loading = false;
            }
        },

        num(value) {
            return FMT.digits(value);
        },

        rangeText() {
            return L.rangeText(this.range, FMT);
        },

        percentText(percent) {
            return L.percentLabel(percent, FMT);
        },

        barValue(percent) {
            return L.barValue(percent);
        },

        absentTotal() {
            return L.totals(this.classRows).absentDays;
        },

        lateTotal() {
            return L.totals(this.classRows).lateDays;
        },

        printDate() {
            const today = DateInput.today();
            return FMT.weekday(today) + ' ' + FMT.long(today);
        },

        printSheet() {
            window.print();
        }
    }));
});
