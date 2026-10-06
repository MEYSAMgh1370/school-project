// «غیبت‌های من» (/my-attendance، openspec/changes/attendance.md «Excuses»): دانش‌آموز غیبت و تأخیرِ خودش را
// می‌بیند و والد غیبت و تأخیرِ فرزندانش را، و برای هر روزِ غایب یادداشتِ عذر می‌فرستد.
// API: GET /api/me/schools/{id}/attendance?from=&to= و POST .../attendance/excuses (بدون مجوز؛ فقط والدِ همان
// دانش‌آموز). schoolId از path می‌آید (مثل my-timetable)، ولی SchoolApi برای تفکیکِ دولایه‌ی 403 و X-XSRF-TOKEN
// استفاده می‌شود. روزها در ورودی جلالی با ارقام فارسی‌اند و روی سیم ISO می‌روند (date-input.js).
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول حضور و غیاب را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دیدنِ غیبت‌ها را ندارد.'
    };
    // سقفِ بازه در سرور (MyAttendanceController) و سقفِ یادداشتِ عذر
    const MAX_DAYS = 366;
    const MAX_NOTE = 500;
    const STATUS_LABELS = { ABSENT: 'غایب', LATE: 'تأخیر' };
    const EXCUSE_LABELS = { PENDING: 'در انتظار بررسی', APPROVED: 'تأیید شد', REJECTED: 'رد شد' };
    const EXCUSE_TAGS = { PENDING: 'is-warning is-light', APPROVED: 'is-success is-light', REJECTED: 'is-light' };

    Alpine.data('myAttendancePage', () => ({
        schoolId: null,
        blocked: false,
        error: '',
        notice: '',
        loading: false,
        // false = هنوز خواندنِ موفقی نبوده؛ شکستِ شبکه «چیزی ثبت نشده» نشان نمی‌دهد
        loaded: false,
        students: [],

        from: '',
        fromText: '',
        fromError: '',
        to: '',
        toText: '',
        toError: '',

        // modal عذر: null = بسته
        excuse: null,
        saving: false,
        maxNote: MAX_NOTE,

        // فقط پاسخِ آخرین درخواست اثر می‌گذارد
        seq: 0,
        mutateSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.setDefaults();
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.reset();
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        // بازه‌ی پیش‌فرض: از اولِ ماهِ جاری تا امروز
        setDefaults() {
            const today = DateInput.today();
            const civil = DateInput.civil(today);
            this.setFrom(DateInput.monthRange(civil.y, civil.m).from);
            this.setTo(today);
        },

        reset() {
            this.seq++;
            this.mutateSeq++;
            this.blocked = false;
            this.error = '';
            this.notice = '';
            this.loading = false;
            this.loaded = false;
            this.saving = false;
            this.students = [];
            this.excuse = null;
            this.setDefaults();
        },

        exampleDate() {
            return DateInput.format(DateInput.today());
        },

        dateProblem() {
            return 'تاریخ را به شکل سال/ماه/روز بنویسید، مثل ' + this.exampleDate() + '.';
        },

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
                } else if (days > MAX_DAYS) {
                    this.toError = 'بازه نباید بیشتر از ' + Calendar.digits(MAX_DAYS) + ' روز باشد.';
                }
            }
            if (this.fromError || this.toError) {
                return null;
            }
            return { from, to };
        },

        // ---------------------------------------------------------------- خواندن («نمایش»)

        async load() {
            if (!this.schoolId) {
                return;
            }
            const range = this.validateRange();
            if (range === null) {
                this.error = 'بازه را درست بنویسید، بعد «نمایش» را بزنید.';
                return;
            }
            this.setFrom(range.from);
            this.setTo(range.to);
            const seq = ++this.seq;
            this.loading = true;
            this.error = '';
            try {
                const data = await SchoolApi.request(this.schoolId, 'GET',
                    `/api/me/schools/${this.schoolId}/attendance?from=${range.from}&to=${range.to}`,
                    undefined, MESSAGES);
                if (seq !== this.seq) {
                    return;
                }
                this.students = data.students;
                this.loaded = true;
            } catch (e) {
                if (seq === this.seq) {
                    this.students = [];
                    this.loaded = false;
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.seq) {
                    this.loading = false;
                }
            }
        },

        hasAnyDay() {
            return this.students.some((student) => student.days.length > 0);
        },

        // ---------------------------------------------------------------- نمایش

        dayLabel(iso) {
            return DateInput.weekdayName(iso) + ' ' + DateInput.format(iso);
        },

        statusLabel(code) {
            return STATUS_LABELS[code] || code;
        },

        excuseLabel(code) {
            return EXCUSE_LABELS[code] || code;
        },

        excuseTag(code) {
            return EXCUSE_TAGS[code] || 'is-light';
        },

        // «ارسال/ویرایش عذر» فقط برای والد (ردیفِ فرزند، نه ردیفِ خودِ دانش‌آموز) و فقط روزِ غایبی که عذرش
        // هنوز تصمیم نگرفته‌اند؛ این «وضعیت» است، نه مجوز (عذرِ تصمیم‌گرفته‌شده اقدامی ندارد)
        canExcuse(student, day) {
            return !student.own && day.status === 'ABSENT'
                && (day.excuse === null || day.excuse.status === 'PENDING');
        },

        excuseActionLabel(day) {
            return day.excuse === null ? 'ارسال عذر' : 'ویرایش عذر';
        },

        // ---------------------------------------------------------------- ارسال عذر

        openExcuse(student, day) {
            this.notice = '';
            this.excuse = {
                studentId: student.studentId,
                studentName: student.studentName,
                day: day.day,
                note: day.excuse === null ? '' : day.excuse.note,
                noteError: '',
                error: ''
            };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.excuseModal));
        },

        closeExcuse() {
            this.excuse = null;
        },

        noteLength() {
            return this.excuse ? this.excuse.note.length : 0;
        },

        validateExcuse() {
            const form = this.excuse;
            form.noteError = '';
            form.error = '';
            const note = form.note.trim();
            if (!note) {
                form.noteError = 'دلیلِ غیبت را بنویسید.';
            } else if (note.length > MAX_NOTE) {
                form.noteError = 'یادداشت نباید بیشتر از ' + Calendar.digits(MAX_NOTE) + ' نویسه باشد.';
            }
            if (form.noteError) {
                form.error = 'یادداشتِ عذر را کامل و درست بنویسید.';
                return null;
            }
            return { studentId: form.studentId, day: form.day, note };
        },

        // یک نوشتن: is-loading (CSS) + این محافظ، نه disabled (دکمه‌ی disabled فوکوس را از modal می‌گیرد)
        async submitExcuse() {
            if (!this.excuse || this.saving) {
                return;
            }
            const body = this.validateExcuse();
            if (body === null) {
                return;
            }
            const schoolId = this.schoolId;
            const seq = ++this.mutateSeq;
            this.saving = true;
            this.error = '';
            try {
                await SchoolApi.request(schoolId, 'POST', `/api/me/schools/${schoolId}/attendance/excuses`,
                    body, MESSAGES);
                if (schoolId !== this.schoolId || seq !== this.mutateSeq) {
                    return;
                }
                this.notice = 'عذر ارسال شد.';
                this.excuse = null;
                this.load();
            } catch (e) {
                if (seq === this.mutateSeq && schoolId === this.schoolId) {
                    const message = SchoolApi.messageOf(e);
                    if (e.blocking) {
                        // ماژول/مجوز پس گرفته شده: صفحه بسته می‌شود و دلیل در ناحیه‌ی خطای صفحه می‌ماند
                        this.blocked = true;
                        this.excuse = null;
                        this.error = message;
                    } else if (this.excuse) {
                        this.excuse.error = message;
                    }
                }
            } finally {
                if (seq === this.mutateSeq) {
                    this.saving = false;
                }
            }
        }
    }));
});
