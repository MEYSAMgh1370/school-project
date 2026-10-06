// «حضور و غیاب» (/attendance، openspec/changes/attendance.md): معلم/مدیر کلاس و روز را انتخاب می‌کند، برای هر
// دانش‌آموز حاضر/غایب/تأخیر (و «موجه» برای غایب) و یادداشتِ اختیاری می‌گذارد و با یک دکمه ثبت می‌کند. روزِ
// پیش‌فرض امروز است (ورودی جلالی، روی سیم ISO - date-input.js). دانش‌آموزِ بدونِ علامت حاضر است؛ فقط
// غایب‌ها و دیرآمدگان به سرور می‌روند (PUT .../classes/{classId}/days/{day}، «جایگزینیِ idempotent»).
// API: GET .../classes?day=، GET|PUT .../classes/{classId}/days/{day} زیر /api/admin/schools/{id}/attendance.
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول حضور و غیاب را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی ثبت حضور و غیاب را ندارد.'
    };
    const BASE = (schoolId) => `/api/admin/schools/${schoolId}/attendance`;
    const NOTE_MAX = 255;

    Alpine.data('attendancePage', () => ({
        schoolId: null,
        loading: false,
        // false = هنوز فهرستِ کلاس‌ها نرسیده؛ شکستِ شبکه «کلاسی نیست» نشان نمی‌دهد
        loaded: false,
        blocked: false,
        error: '',
        notice: '',
        saving: false,

        // روز: ISO (state و سیم) و متنِ جلالیِ ورودی
        day: '',
        dayText: '',
        dayError: '',

        classes: [],
        classId: '',

        // فهرستِ دانش‌آموزانِ کلاسِ انتخاب‌شده در آن روز
        roll: null,
        rollLoading: false,
        rows: [],
        noteErrors: {},
        statuses: [
            { code: 'PRESENT', label: 'حاضر' },
            { code: 'ABSENT', label: 'غایب' },
            { code: 'LATE', label: 'تأخیر' }
        ],

        // فقط پاسخِ آخرین درخواستِ هر نوع اثر می‌گذارد؛ عوض‌کردنِ مدرسه همه را بالا می‌برد
        classesSeq: 0,
        rollSeq: 0,
        mutateSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.setDay(DateInput.today());
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.reset();
                this.loadClasses(false);
            });
            if (this.schoolId) {
                this.loadClasses(false);
            }
        },

        reset() {
            this.classesSeq++;
            this.rollSeq++;
            this.mutateSeq++;
            this.saving = false;
            this.loading = false;
            this.loaded = false;
            this.blocked = false;
            this.error = '';
            this.notice = '';
            this.classes = [];
            this.classId = '';
            this.clearRoll();
            this.setDay(DateInput.today());
        },

        clearRoll() {
            this.roll = null;
            this.rows = [];
            this.noteErrors = {};
            this.rollLoading = false;
        },

        // ---------------------------------------------------------------- روز

        setDay(iso) {
            this.day = iso;
            this.dayText = DateInput.format(iso);
            this.dayError = '';
        },

        exampleDate() {
            return DateInput.format(DateInput.today());
        },

        dayTitle() {
            return DateInput.weekdayName(this.day) + ' ' + DateInput.formatLong(this.day);
        },

        // متنِ تایپ‌شده را به ISO می‌رساند؛ false اگر تاریخِ واقعی نباشد (خطا کنارِ همان فیلد می‌ماند)
        syncDay() {
            const iso = DateInput.parse(this.dayText);
            if (iso === null) {
                this.dayError = 'تاریخ را به شکل سال/ماه/روز بنویسید، مثل ' + this.exampleDate() + '.';
                return false;
            }
            this.setDay(iso);
            return true;
        },

        onDayInput() {
            const previous = this.day;
            if (!this.syncDay()) {
                return;
            }
            if (this.day !== previous) {
                this.notice = '';
                this.loadClasses(true);
            }
        },

        goToday() {
            this.setDay(DateInput.today());
            this.notice = '';
            this.loadClasses(true);
        },

        futureDay() {
            return this.day > DateInput.today();
        },

        // ---------------------------------------------------------------- خواندن

        async loadClasses(keepSelection) {
            if (!this.schoolId) {
                return;
            }
            const seq = ++this.classesSeq;
            const day = this.day;
            this.loading = true;
            this.error = '';
            try {
                const classes = await SchoolApi.request(this.schoolId, 'GET',
                    `${BASE(this.schoolId)}/classes?day=${day}`, undefined, MESSAGES);
                if (seq !== this.classesSeq) {
                    return;
                }
                this.classes = classes;
                this.loaded = true;
                const keep = keepSelection && classes.some((c) => String(c.classId) === String(this.classId));
                this.classId = keep ? this.classId : (classes.length > 0 ? String(classes[0].classId) : '');
                this.loadRoll();
            } catch (e) {
                if (seq === this.classesSeq) {
                    this.loaded = false;
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.classesSeq) {
                    this.loading = false;
                }
            }
        },

        async loadRoll() {
            const seq = ++this.rollSeq;
            this.clearRoll();
            if (!this.classId) {
                return;
            }
            this.rollLoading = true;
            try {
                const roll = await SchoolApi.request(this.schoolId, 'GET',
                    `${BASE(this.schoolId)}/classes/${this.classId}/days/${this.day}`, undefined, MESSAGES);
                if (seq === this.rollSeq) {
                    this.applyRoll(roll);
                }
            } catch (e) {
                if (seq === this.rollSeq) {
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.rollSeq) {
                    this.rollLoading = false;
                }
            }
        },

        // پاسخِ سرور (خواندن یا ذخیره) → ردیف‌های قابل‌ویرایش؛ status تهی یعنی حاضر
        applyRoll(roll) {
            this.roll = roll;
            this.noteErrors = {};
            this.rows = roll.students.map((s) => ({
                studentId: s.studentId,
                studentName: s.studentName,
                status: s.status || 'PRESENT',
                excused: s.status === 'ABSENT' && s.excused === true,
                note: s.note || ''
            }));
        },

        onClassChange() {
            this.notice = '';
            this.loadRoll();
        },

        classLabel(item) {
            return item.name + (item.section ? ' ' + item.section : '') + (item.taken ? ' (ثبت شده)' : '');
        },

        // ---------------------------------------------------------------- ردیف‌ها

        setStatus(row, code) {
            row.status = code;
            if (code !== 'ABSENT') {
                row.excused = false;
            }
            if (code === 'PRESENT') {
                row.note = '';
                delete this.noteErrors[row.studentId];
            }
        },

        countOf(code) {
            return this.rows.filter((r) => r.status === code).length;
        },

        excusedCount() {
            return this.rows.filter((r) => r.status === 'ABSENT' && r.excused).length;
        },

        rollRecordable() {
            return this.roll !== null && this.roll.recordable;
        },

        canSave() {
            return this.rollRecordable() && this.rows.length > 0;
        },

        // ---------------------------------------------------------------- ذخیره

        validate() {
            const errors = {};
            this.rows.forEach((row) => {
                if (row.status !== 'PRESENT' && row.note.trim().length > NOTE_MAX) {
                    errors[row.studentId] = 'یادداشت نباید بیشتر از ' + Calendar.digits(NOTE_MAX) + ' نویسه باشد.';
                }
            });
            this.noteErrors = errors;
            return Object.keys(errors).length === 0;
        },

        async save() {
            if (this.saving) {
                return;
            }
            this.notice = '';
            // روزِ تایپ‌شده‌ی هنوز اعمال‌نشده، مبنای ذخیره نیست: اول باید معتبر شود
            if (!this.syncDay()) {
                this.error = 'روز را درست بنویسید، بعد حضور و غیاب را ثبت کنید.';
                return;
            }
            if (!this.canSave()) {
                this.error = this.classId
                    ? 'برای این روز نمی‌توان حضور و غیاب ثبت کرد.'
                    : 'اول یک کلاس را انتخاب کنید.';
                return;
            }
            if (!this.validate()) {
                this.error = 'یادداشت‌ها را بررسی کنید و دوباره ثبت کنید.';
                return;
            }
            const classId = this.classId;
            const day = this.day;
            const marks = this.rows
                .filter((row) => row.status !== 'PRESENT')
                .map((row) => ({
                    studentId: row.studentId,
                    status: row.status,
                    excused: row.status === 'ABSENT' && row.excused,
                    note: row.note.trim() === '' ? null : row.note.trim()
                }));

            const schoolId = this.schoolId;
            const seq = ++this.mutateSeq;
            this.saving = true;
            this.error = '';
            try {
                const roll = await SchoolApi.request(schoolId, 'PUT',
                    `${BASE(schoolId)}/classes/${classId}/days/${day}`, { marks }, MESSAGES);
                if (schoolId !== this.schoolId || seq !== this.mutateSeq) {
                    return;
                }
                if (classId === this.classId && day === this.day) {
                    this.applyRoll(roll);
                }
                const item = this.classes.find((c) => String(c.classId) === String(classId));
                if (item) {
                    item.taken = true;
                }
                this.notice = 'حضور و غیاب ثبت شد: '
                    + Calendar.digits(marks.filter((m) => m.status === 'ABSENT').length) + ' غایب، '
                    + Calendar.digits(marks.filter((m) => m.status === 'LATE').length) + ' تأخیر.';
            } catch (e) {
                if (seq === this.mutateSeq && schoolId === this.schoolId) {
                    // blocking (ماژول/مجوز) بدنه را می‌بندد؛ بقیه (مثلاً ویرایشِ روزِ گذشته) پیامِ خودِ سرور را
                    // در همین ناحیه می‌گذارند و صفحه باز می‌ماند
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.mutateSeq) {
                    this.saving = false;
                }
            }
        }
    }));
});
