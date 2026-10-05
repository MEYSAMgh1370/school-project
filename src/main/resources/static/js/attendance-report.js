// «گزارش حضور و غیاب» (/attendance/report، openspec/changes/attendance.md «Report» و «The admin figure»):
// دو بخش روی یک صفحه.
//   «غایبین روز»: رقمِ مدیر (/days/{day}/summary) و کارتِ هر کلاس (/days/{day}/absentees).
//   «گزارش بازه‌ای»: فیلترِ شروع/پایان (جلالی)، کلاسِ اختیاری و نامِ دانش‌آموز (شاملِ) → جدولِ غیبت/تأخیر/حضور
//   به‌ازای هر دانش‌آموز (/report)، با صفحه‌بندیِ سمت‌مرورگر.
// روزها در ورودی جلالی با ارقام فارسی‌اند و روی سیم ISO می‌روند (date-input.js). مجوز: ATTENDANCE_VIEW_ALL.
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول حضور و غیاب را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دیدنِ گزارش حضور و غیاب را ندارد.'
    };
    const BASE = (schoolId) => `/api/admin/schools/${schoolId}/attendance`;
    // سقفِ بازه‌ی گزارش در سرور (AttendanceFacade.MAX_REPORT_DAYS)
    const MAX_REPORT_DAYS = 366;
    const STATUS_LABELS = { ABSENT: 'غایب', LATE: 'تأخیر' };

    Alpine.data('attendanceReportPage', () => ({
        schoolId: null,
        blocked: false,
        error: '',
        tab: 'day',

        // ---- غایبین روز
        day: '',
        dayText: '',
        dayError: '',
        summary: null,
        cards: [],
        dayLoading: false,
        // false = این روز هنوز با موفقیت خوانده نشده؛ شکستِ شبکه «چیزی ثبت نشده» نشان نمی‌دهد
        dayLoaded: false,

        // ---- گزارش بازه‌ای
        from: '',
        fromText: '',
        fromError: '',
        to: '',
        toText: '',
        toError: '',
        classFilter: '',
        name: '',
        classes: [],
        rows: [],
        queried: null,
        rangeLoading: false,
        rangeLoaded: false,
        page: 1,
        pageSize: 25,

        daySeq: 0,
        rangeSeq: 0,
        classesSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.setDefaults();
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.reset();
                this.loadAll();
            });
            if (this.schoolId) {
                this.loadAll();
            }
        },

        // امروز؛ بازه‌ی پیش‌فرض از اولِ ماهِ جاری تا امروز
        setDefaults() {
            const today = DateInput.today();
            this.setDay(today);
            const civil = DateInput.civil(today);
            this.setFrom(DateInput.monthRange(civil.y, civil.m).from);
            this.setTo(today);
        },

        reset() {
            this.daySeq++;
            this.rangeSeq++;
            this.classesSeq++;
            this.blocked = false;
            this.error = '';
            this.summary = null;
            this.cards = [];
            this.dayLoading = false;
            this.dayLoaded = false;
            this.classFilter = '';
            this.name = '';
            this.classes = [];
            this.rows = [];
            this.queried = null;
            this.rangeLoading = false;
            this.rangeLoaded = false;
            this.page = 1;
            this.setDefaults();
        },

        loadAll() {
            this.loadClasses();
            this.run();
        },

        exampleDate() {
            return DateInput.format(DateInput.today());
        },

        dateProblem() {
            return 'تاریخ را به شکل سال/ماه/روز بنویسید، مثل ' + this.exampleDate() + '.';
        },

        // ---------------------------------------------------------------- اقدامِ اصلی: «نمایش گزارش»

        run() {
            if (this.tab === 'day') {
                this.loadDay();
            } else {
                this.loadRange();
            }
        },

        showTab(tab) {
            this.tab = tab;
            // اولین بار که بازه‌ای باز می‌شود، همان پیش‌فرض (ماهِ جاری) خوانده می‌شود
            if (tab === 'range' && !this.rangeLoaded && !this.rangeLoading) {
                this.loadRange();
            }
        },

        // ---------------------------------------------------------------- کلاس‌های فیلتر

        // بهترین تلاش: انتخابگرِ کلاس از همان endpointِ صفحه‌ی ثبت می‌آید (ATTENDANCE_RECORD[_ANY]). نقشی که فقط
        // ATTENDANCE_VIEW_ALL دارد آن را نمی‌گیرد و فقط «همه‌ی کلاس‌ها» می‌ماند؛ این شکست صفحه را نمی‌بندد.
        async loadClasses() {
            if (!this.schoolId) {
                return;
            }
            const seq = ++this.classesSeq;
            try {
                const classes = await SchoolApi.request(this.schoolId, 'GET',
                    `${BASE(this.schoolId)}/classes?day=${DateInput.today()}`, undefined, MESSAGES);
                if (seq === this.classesSeq) {
                    this.classes = classes;
                }
            } catch (e) {
                if (seq === this.classesSeq) {
                    this.classes = [];
                }
            }
        },

        classLabel(item) {
            return item.name + (item.section ? ' ' + item.section : '');
        },

        // ---------------------------------------------------------------- غایبین روز

        setDay(iso) {
            this.day = iso;
            this.dayText = DateInput.format(iso);
            this.dayError = '';
        },

        dayTitle() {
            return DateInput.weekdayName(this.day) + ' ' + DateInput.formatLong(this.day);
        },

        onDayInput() {
            const iso = DateInput.parse(this.dayText);
            if (iso === null) {
                this.dayError = this.dateProblem();
                return;
            }
            const changed = iso !== this.day;
            this.setDay(iso);
            if (changed) {
                this.loadDay();
            }
        },

        goToday() {
            this.setDay(DateInput.today());
            this.loadDay();
        },

        async loadDay() {
            if (!this.schoolId) {
                return;
            }
            const iso = DateInput.parse(this.dayText);
            if (iso === null) {
                this.dayError = this.dateProblem();
                this.error = 'روز را درست بنویسید، بعد گزارش را نمایش دهید.';
                return;
            }
            this.setDay(iso);
            const seq = ++this.daySeq;
            this.dayLoading = true;
            this.error = '';
            try {
                const [summary, cards] = await Promise.all([
                    SchoolApi.request(this.schoolId, 'GET', `${BASE(this.schoolId)}/days/${iso}/summary`, undefined, MESSAGES),
                    SchoolApi.request(this.schoolId, 'GET', `${BASE(this.schoolId)}/days/${iso}/absentees`, undefined, MESSAGES)
                ]);
                if (seq !== this.daySeq) {
                    return;
                }
                this.summary = summary;
                this.cards = cards;
                this.dayLoaded = true;
            } catch (e) {
                if (seq === this.daySeq) {
                    this.summary = null;
                    this.cards = [];
                    this.dayLoaded = false;
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.daySeq) {
                    this.dayLoading = false;
                }
            }
        },

        // وضعیتِ رقمِ بالای صفحه: بسته (روزِ غیردرسی) | هنوز ثبت نشده | رقم
        summaryClosed() {
            return this.dayLoaded && this.summary !== null && !this.summary.schoolDay;
        },

        summaryEmpty() {
            return this.dayLoaded && this.summary !== null && this.summary.schoolDay && this.summary.presentPercent === null;
        },

        summaryFigure() {
            return this.dayLoaded && this.summary !== null && this.summary.schoolDay && this.summary.presentPercent !== null;
        },

        classesWithoutRoll() {
            return this.summary === null ? 0 : Math.max(0, this.summary.classesTotal - this.summary.classesTaken);
        },

        statusLabel(code) {
            return STATUS_LABELS[code] || code;
        },

        // ---------------------------------------------------------------- گزارش بازه‌ای

        setFrom(iso) {
            this.from = iso;
            this.fromText = DateInput.format(iso);
            this.fromError = '';
        },

        setTo(iso) {
            this.to = iso;
            this.toText = DateInput.format(iso);
            this.toError = '';
        },

        // هر دو تاریخ + بازه‌ی منطقی؛ خطا کنارِ همان فیلد می‌ماند
        validateRange() {
            const from = DateInput.parse(this.fromText);
            const to = DateInput.parse(this.toText);
            this.fromError = from === null ? this.dateProblem() : '';
            this.toError = to === null ? this.dateProblem() : '';
            if (from !== null && to !== null) {
                const days = DateInput.daysBetweenInclusive(from, to);
                if (days === 0) {
                    this.toError = 'تاریخ پایان باید همان روزِ شروع یا بعد از آن باشد.';
                } else if (days > MAX_REPORT_DAYS) {
                    this.toError = 'بازه نباید بیشتر از ' + Calendar.digits(MAX_REPORT_DAYS) + ' روز باشد.';
                }
            }
            if (this.fromError || this.toError) {
                return null;
            }
            return { from, to };
        },

        async loadRange() {
            if (!this.schoolId) {
                return;
            }
            const range = this.validateRange();
            if (range === null) {
                this.error = 'بازه را درست بنویسید، بعد گزارش را نمایش دهید.';
                return;
            }
            this.setFrom(range.from);
            this.setTo(range.to);
            const seq = ++this.rangeSeq;
            this.rangeLoading = true;
            this.error = '';
            let query = `from=${range.from}&to=${range.to}`;
            if (this.classFilter) {
                query += `&classId=${encodeURIComponent(this.classFilter)}`;
            }
            const name = this.name.trim();
            if (name) {
                query += `&name=${encodeURIComponent(name)}`;
            }
            try {
                const rows = await SchoolApi.request(this.schoolId, 'GET',
                    `${BASE(this.schoolId)}/report?${query}`, undefined, MESSAGES);
                if (seq !== this.rangeSeq) {
                    return;
                }
                this.rows = rows;
                this.queried = { from: range.from, to: range.to };
                this.page = 1;
                this.rangeLoaded = true;
            } catch (e) {
                if (seq === this.rangeSeq) {
                    this.rows = [];
                    this.queried = null;
                    this.rangeLoaded = false;
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.rangeSeq) {
                    this.rangeLoading = false;
                }
            }
        },

        rangeLabel() {
            return this.queried === null ? ''
                : DateInput.format(this.queried.from) + ' تا ' + DateInput.format(this.queried.to);
        },

        // ---- صفحه‌بندیِ سمت‌مرورگر (بیش از ۲۵ ردیف)
        pageCount() {
            return Math.max(1, Math.ceil(this.rows.length / this.pageSize));
        },

        pagedRows() {
            const start = (this.page - 1) * this.pageSize;
            return this.rows.slice(start, start + this.pageSize);
        },

        paginated() {
            return this.rows.length > this.pageSize;
        },

        previousPage() {
            this.page = Math.max(1, this.page - 1);
        },

        nextPage() {
            this.page = Math.min(this.pageCount(), this.page + 1);
        }
    }));
});
