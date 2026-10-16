// «گزارش حضور و غیاب» (/attendance/report، openspec/changes/attendance.md «Report» و «The admin figure»):
// سه بخش روی یک صفحه (سومی «عذرخواهی‌ها»: فهرستِ عذرها با فیلترِ وضعیت و بازه، و تأیید/ردِ عذرِ در انتظار).
//   «غایبین روز»: رقمِ مدیر (/days/{day}/summary) و کارتِ هر کلاس (/days/{day}/absentees).
//   «گزارش بازه‌ای»: فیلترِ شروع/پایان (جلالی)، کلاسِ اختیاری و نامِ دانش‌آموز (شاملِ) → جدولِ غیبت/تأخیر/حضور
//   به‌ازای هر دانش‌آموز (/report)، با صفحه‌بندیِ سمت‌مرورگر.
// روزها در ورودی جلالی با ارقام فارسی‌اند و روی سیم ISO می‌روند (date-input.js). مجوز: ATTENDANCE_VIEW_ALL.
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول حضور و غیاب را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دیدنِ گزارش حضور و غیاب را ندارد.'
    };
    const Report = window.AttendanceReportLogic;
    const BASE = (schoolId) => `/api/admin/schools/${schoolId}/attendance`;
    // سقفِ بازه‌ی گزارش در سرور (AttendanceFacade.MAX_REPORT_DAYS)
    const MAX_REPORT_DAYS = 366;
    const STATUS_LABELS = { ABSENT: 'غایب', LATE: 'تأخیر' };
    const EXCUSE_LABELS = { PENDING: 'در انتظار بررسی', APPROVED: 'تأیید شد', REJECTED: 'رد شد' };
    const EXCUSE_EMPTY = {
        PENDING: 'عذر در انتظاری وجود ندارد.',
        APPROVED: 'عذر تأییدشده‌ای در این بازه وجود ندارد.',
        REJECTED: 'عذرِ ردشده‌ای در این بازه وجود ندارد.'
    };
    const DECIDED = { APPROVED: 'عذر تأیید شد.', REJECTED: 'عذر رد شد.' };
    // تأیید/ردّ مجوزِ جدا (ATTENDANCE_RECORD_ANY) می‌خواهد؛ 403 آن فقط در ناحیه‌ی پیام می‌آید و صفحه بسته نمی‌شود
    const DECIDE_MESSAGES = {
        entitled: MESSAGES.entitled,
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی تأیید یا ردِ عذر را ندارد.'
    };

    Alpine.data('attendanceReportPage', () => ({
        schoolId: null,
        blocked: false,
        error: '',
        notice: '',
        tab: 'day',

        // ---- عذرخواهی‌ها
        excStatus: 'PENDING',
        excFromText: '',
        excFromError: '',
        excToText: '',
        excToError: '',
        excuses: [],
        // وضعیتِ فیلترِ آخرین خواندنِ موفق (متنِ حالتِ خالی از همین می‌آید، نه از انتخابگرِ در حال تغییر)
        excQueriedStatus: 'PENDING',
        excusesLoading: false,
        excusesLoaded: false,
        excPage: 1,
        // «شناسه:تصمیم» ردیفی که در حال ارسال است؛ '' = هیچ
        deciding: '',
        excusesSeq: 0,
        decideSeq: 0,

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

        // ---- کلاس‌ها (همان بازه و فیلتر کلاسِ گزارش بازه‌ای)
        classRows: [],
        classReportQueried: null,
        classReportLoading: false,
        classReportLoaded: false,

        // ---- معلم‌ها (همان بازه)؛ expanded = { [teacherUserId]: true } برای بخشِ بازشده‌ی «کلاس‌ها»
        teacherRows: [],
        teacherQueried: null,
        teacherLoading: false,
        teacherLoaded: false,
        expanded: {},

        daySeq: 0,
        rangeSeq: 0,
        classesSeq: 0,
        classReportSeq: 0,
        teacherSeq: 0,

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
            this.classReportSeq++;
            this.teacherSeq++;
            this.classRows = [];
            this.classReportQueried = null;
            this.classReportLoading = false;
            this.classReportLoaded = false;
            this.teacherRows = [];
            this.teacherQueried = null;
            this.teacherLoading = false;
            this.teacherLoaded = false;
            this.expanded = {};
            this.excusesSeq++;
            this.decideSeq++;
            this.notice = '';
            this.excStatus = 'PENDING';
            this.excFromText = '';
            this.excFromError = '';
            this.excToText = '';
            this.excToError = '';
            this.excuses = [];
            this.excusesLoading = false;
            this.excusesLoaded = false;
            this.excPage = 1;
            this.deciding = '';
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
            this.notice = '';
            if (this.tab === 'day') {
                this.loadDay();
            } else if (this.tab === 'range') {
                this.invalidateRangeTabs();
                this.loadRange();
            } else if (this.tab === 'classes') {
                this.invalidateRangeTabs();
                this.loadClassReport();
            } else if (this.tab === 'teachers') {
                this.invalidateRangeTabs();
                this.loadTeacherReport();
            } else {
                this.loadExcuses();
            }
        },

        // بازه/فیلتر بین سه بخشِ بازه‌ای مشترک است؛ بخش‌های دیگر با ورودیِ تازه، هنگامِ باز شدن دوباره خوانده می‌شوند
        invalidateRangeTabs() {
            this.rangeLoaded = false;
            this.classReportLoaded = false;
            this.teacherLoaded = false;
        },

        rangeTab() {
            return this.tab === 'range' || this.tab === 'classes' || this.tab === 'teachers';
        },

        showTab(tab) {
            this.tab = tab;
            if (tab === 'classes' && !this.classReportLoaded && !this.classReportLoading) {
                this.loadClassReport();
            }
            if (tab === 'teachers' && !this.teacherLoaded && !this.teacherLoading) {
                this.loadTeacherReport();
            }
            // اولین بار که بازه‌ای باز می‌شود، همان پیش‌فرض (ماهِ جاری) خوانده می‌شود
            if (tab === 'range' && !this.rangeLoaded && !this.rangeLoading) {
                this.loadRange();
            }
            // عذرخواهی‌ها: اولین بار «در انتظار» خوانده می‌شود
            if (tab === 'excuses' && !this.excusesLoaded && !this.excusesLoading) {
                this.loadExcuses();
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

        // ---------------------------------------------------------------- درصد، کلاس‌ها، معلم‌ها

        pct(percent) {
            return Report.percentLabel(percent, Calendar.digits);
        },

        bar(percent) {
            return Report.barValue(percent);
        },

        hasPct(percent) {
            return Report.hasPercent(percent);
        },

        // پیوندِ برگه‌ی چاپیِ دانش‌آموز: بازه‌ی همان گزارشی که روی صفحه است
        sheetHref(row) {
            return this.queried === null ? '#' : Report.sheetHref(row.studentId, this.queried);
        },

        // بخشِ «دانش‌آموزان» در ردیفِ یک کلاس: فیلترِ کلاسِ گزارش بازه‌ای را می‌گذارد و به آن بخش می‌رود
        showClassStudents(item) {
            this.classFilter = String(item.classId);
            this.tab = 'range';
            this.invalidateRangeTabs();
            this.loadRange();
        },

        // خواندنِ مشترکِ دو گزارشِ تازه: بازه را اعتبارسنجی می‌کند و (seq، گرفتن، خطا) را به صدا زننده می‌سپارد
        async loadRangeReport(kind, seqKey, begin, apply, fail, end) {
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
            const seq = ++this[seqKey];
            begin();
            this.error = '';
            const classId = kind === 'classes' ? this.classFilter : '';
            try {
                const rows = await SchoolApi.request(this.schoolId, 'GET',
                    Report.reportPath(this.schoolId, kind, range, classId), undefined, MESSAGES);
                if (seq === this[seqKey]) {
                    apply(rows, range);
                }
            } catch (e) {
                if (seq === this[seqKey]) {
                    fail();
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this[seqKey]) {
                    end();
                }
            }
        },

        loadClassReport() {
            return this.loadRangeReport('classes', 'classReportSeq',
                () => { this.classReportLoading = true; },
                (rows, range) => {
                    this.classRows = rows;
                    this.classReportQueried = { from: range.from, to: range.to };
                    this.classReportLoaded = true;
                },
                () => {
                    this.classRows = [];
                    this.classReportQueried = null;
                    this.classReportLoaded = false;
                },
                () => { this.classReportLoading = false; });
        },

        loadTeacherReport() {
            return this.loadRangeReport('teachers', 'teacherSeq',
                () => { this.teacherLoading = true; },
                (rows, range) => {
                    this.teacherRows = Report.sortedTeachers(rows);
                    this.teacherQueried = { from: range.from, to: range.to };
                    this.expanded = {};
                    this.teacherLoaded = true;
                },
                () => {
                    this.teacherRows = [];
                    this.teacherQueried = null;
                    this.teacherLoaded = false;
                },
                () => { this.teacherLoading = false; });
        },

        classReportLabel() {
            return this.classReportQueried === null ? ''
                : DateInput.format(this.classReportQueried.from) + ' تا ' + DateInput.format(this.classReportQueried.to);
        },

        teacherRangeLabel() {
            return this.teacherQueried === null ? ''
                : DateInput.format(this.teacherQueried.from) + ' تا ' + DateInput.format(this.teacherQueried.to);
        },

        teacherLabel(item) {
            return Report.teacherLabel(item, Calendar.digits);
        },

        toggleTeacher(item) {
            this.expanded = { ...this.expanded, [item.teacherUserId]: !this.expanded[item.teacherUserId] };
        },

        isExpanded(item) {
            return this.expanded[item.teacherUserId] === true;
        },

        // ---------------------------------------------------------------- عذرخواهی‌ها

        // بازه اختیاری است: خالی = بی‌قید؛ پرشده باید تاریخِ واقعی باشد و شروع بعد از پایان نباشد
        validateExcuseRange() {
            const fromText = this.excFromText.trim();
            const toText = this.excToText.trim();
            const from = fromText === '' ? '' : DateInput.parse(fromText);
            const to = toText === '' ? '' : DateInput.parse(toText);
            this.excFromError = from === null ? this.dateProblem() : '';
            this.excToError = to === null ? this.dateProblem() : '';
            if (from && to && DateInput.daysBetweenInclusive(from, to) === 0) {
                this.excToError = 'تاریخ پایان باید همان روزِ شروع یا بعد از آن باشد.';
            }
            if (this.excFromError || this.excToError) {
                return null;
            }
            return { from, to };
        },

        async loadExcuses() {
            if (!this.schoolId) {
                return;
            }
            const range = this.validateExcuseRange();
            if (range === null) {
                this.error = 'بازه را درست بنویسید، بعد گزارش را نمایش دهید.';
                return;
            }
            if (range.from) {
                this.excFromText = DateInput.format(range.from);
            }
            if (range.to) {
                this.excToText = DateInput.format(range.to);
            }
            const seq = ++this.excusesSeq;
            const status = this.excStatus;
            this.excusesLoading = true;
            this.error = '';
            let query = `status=${encodeURIComponent(status)}`;
            if (range.from) {
                query += `&from=${range.from}`;
            }
            if (range.to) {
                query += `&to=${range.to}`;
            }
            try {
                const rows = await SchoolApi.request(this.schoolId, 'GET',
                    `${BASE(this.schoolId)}/excuses?${query}`, undefined, MESSAGES);
                if (seq !== this.excusesSeq) {
                    return;
                }
                this.excuses = rows;
                this.excQueriedStatus = status;
                this.excPage = 1;
                this.excusesLoaded = true;
            } catch (e) {
                if (seq === this.excusesSeq) {
                    this.excuses = [];
                    this.excusesLoaded = false;
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.excusesSeq) {
                    this.excusesLoading = false;
                }
            }
        },

        excuseLabel(code) {
            return EXCUSE_LABELS[code] || code;
        },

        excusesEmptyText() {
            return EXCUSE_EMPTY[this.excQueriedStatus] || EXCUSE_EMPTY.PENDING;
        },

        dayShort(iso) {
            return DateInput.format(iso);
        },

        isDeciding(row, decision) {
            return this.deciding === row.id + ':' + decision;
        },

        // «تأیید»/«رد» مستقیم و بدونِ modal (ردّ مخرب نیست)؛ is-loading روی همان دکمه. یک تصمیم در هر لحظه.
        async decide(row, decision) {
            if (this.deciding !== '') {
                return;
            }
            const schoolId = this.schoolId;
            const seq = ++this.decideSeq;
            this.deciding = row.id + ':' + decision;
            this.error = '';
            this.notice = '';
            try {
                await SchoolApi.request(schoolId, 'POST', `${BASE(schoolId)}/excuses/${row.id}/decision`,
                    { decision }, DECIDE_MESSAGES);
                if (schoolId !== this.schoolId || seq !== this.decideSeq) {
                    return;
                }
                this.notice = DECIDED[decision];
                this.loadExcuses();
            } catch (e) {
                if (schoolId === this.schoolId && seq === this.decideSeq) {
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.decideSeq) {
                    this.deciding = '';
                }
            }
        },

        excPageCount() {
            return Math.max(1, Math.ceil(this.excuses.length / this.pageSize));
        },

        pagedExcuses() {
            const start = (this.excPage - 1) * this.pageSize;
            return this.excuses.slice(start, start + this.pageSize);
        },

        excPaginated() {
            return this.excuses.length > this.pageSize;
        },

        previousExcPage() {
            this.excPage = Math.max(1, this.excPage - 1);
        },

        nextExcPage() {
            this.excPage = Math.min(this.excPageCount(), this.excPage + 1);
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
