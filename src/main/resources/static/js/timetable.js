document.addEventListener('alpine:init', () => {
    const ORDINALS = ['اول', 'دوم', 'سوم', 'چهارم', 'پنجم', 'ششم', 'هفتم', 'هشتم', 'نهم', 'دهم'];
    const BASE = (schoolId) => `/api/admin/schools/${schoolId}/timetable`;

    Alpine.data('timetablePage', () => ({
        schoolId: null,
        tab: 'grid',
        loading: false,
        blocked: false,
        error: '',
        notice: '',
        saving: false,

        // سال، کلاس و داده‌های هر تب
        years: [],
        classesSeq: 0,
        year: '',
        currentYear: null,
        classes: [],
        classId: '',
        teachers: [],
        activities: [],
        periods: [],
        entries: [],
        courses: [],
        // فقط بعد از بارگذاریِ موفقِ پایه true می‌شود تا شکستِ شبکه «بدون زنگ/کلاس» نشان ندهد
        loaded: false,
        // تا زنگ‌ها و خانه‌های سالِ تازه نرسیده‌اند، جدولِ سالِ قبل نمایش و قابل‌ویرایش نیست
        periodsLoading: false,
        entriesLoading: false,
        // false = آخرین خواندن شکست خورده (وضعیتِ سرور نامعلوم است): نه «زنگی نیست» و نه جدولِ قابل‌ویرایش
        periodsOk: true,
        entriesOk: true,

        // فرم‌ها
        periodForm: { id: null, label: '', start: '', end: '' },
        periodErrors: {},
        activityForm: { id: null, name: '' },
        activityError: '',
        cell: null,
        cellError: '',
        confirm: null,

        // شمارنده‌ها: فقط پاسخِ آخرین درخواستِ هر نوع اثر می‌گذارد. عوض‌کردنِ مدرسه همه را
        // بالا می‌برد، پس پاسخ‌های مدرسه‌ی قبلی (حتی نوشتن‌های در جریان) دور ریخته می‌شوند.
        baseSeq: 0,
        periodSeq: 0,
        entrySeq: 0,
        courseSeq: 0,
        mutateSeq: 0,

        days: window.TimetableGrid.days(),

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                // روزهای درسیِ مدرسه‌ی تازه (W9)
                this.days = window.TimetableGrid.days();
                this.reset();
                this.loadBase();
            });
            if (this.schoolId) {
                this.loadBase();
            }
        },

        reset() {
            this.baseSeq++;
            this.periodSeq++;
            this.entrySeq++;
            this.courseSeq++;
            this.mutateSeq++;
            this.saving = false;
            this.loading = false;
            this.loaded = false;
            this.periodsLoading = false;
            this.entriesLoading = false;
            this.periodsOk = true;
            this.entriesOk = true;
            this.blocked = false;
            this.error = '';
            this.notice = '';
            this.years = [];
            this.year = '';
            this.currentYear = null;
            this.classes = [];
            this.classId = '';
            this.teachers = [];
            this.activities = [];
            this.periods = [];
            this.entries = [];
            this.courses = [];
            this.periodForm = { id: null, label: '', start: '', end: '' };
            this.periodErrors = {};
            this.activityForm = { id: null, name: '' };
            this.activityError = '';
            this.cell = null;
            this.cellError = '';
            this.confirm = null;
        },

        // خطای خواندن: ماژول/مجوزِ پس‌گرفته‌شده (blocking) مثل loadBase و mutate صفحه را می‌بندد و
        // modalهای باز را هم می‌بندد؛ بقیه فقط پیامِ خطا می‌گذارند
        failLoad(e) {
            this.error = e.message;
            if (e.blocking) {
                this.blocked = true;
                this.cell = null;
                this.confirm = null;
            }
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        // همان تفکیک دولایه‌ی finance.js/grades.js: ماژول پس‌گرفته‌شده یا مجوزِ ندارد → صفحه بسته می‌شود
        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول آموزشی را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی مدیریت برنامه‌ی کلاسی را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        async getJson(path) {
            const response = await fetch(`${BASE(this.schoolId)}${path}`, {
                headers: { 'X-School-ID': this.schoolId }
            });
            if (!response.ok) {
                const classified = await this.classifyError(response);
                const error = new Error(classified.message);
                error.blocking = classified.blocking;
                throw error;
            }
            return response.json();
        },

        async send(method, path, body) {
            const response = await fetch(`${BASE(this.schoolId)}${path}`, {
                method,
                headers: {
                    'Content-Type': 'application/json',
                    'X-School-ID': this.schoolId,
                    'X-XSRF-TOKEN': this.csrfToken()
                },
                body: body === undefined ? undefined : JSON.stringify(body)
            });
            if (!response.ok) {
                const classified = await this.classifyError(response);
                const error = new Error(classified.message);
                error.blocking = classified.blocking;
                throw error;
            }
            return response.status === 204 ? null : response.json();
        },

        // ---------------------------------------------------------------- بارگذاری

        async loadBase() {
            const seq = ++this.baseSeq;
            this.loading = true;
            this.error = '';
            try {
                const [years, classes, teachers, activities] = await Promise.all([
                    this.getJson('/years'), this.getJson('/classes'),
                    this.getJson('/teachers'), this.getJson('/activities')
                ]);
                if (seq !== this.baseSeq) {
                    return;
                }
                this.years = years.years;
                this.currentYear = years.currentYear;
                this.year = years.currentYear || (years.years.length > 0 ? years.years[years.years.length - 1] : '');
                this.classes = classes;
                this.classId = classes.length > 0 ? String(classes[0].id) : '';
                this.teachers = teachers;
                this.activities = activities;
                this.loaded = true;
                this.resetPeriodForm();
                this.loadYear();
                this.loadCourses();
            } catch (e) {
                if (seq === this.baseSeq) {
                    this.blocked = !!e.blocking;
                    this.error = e.blocking !== undefined ? e.message : 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.baseSeq) {
                    this.loading = false;
                }
            }
        },

        // با عوض‌شدنِ سال: زنگ‌ها و خانه‌های همان سال
        loadYear() {
            // دادهٔ سالِ قبل همین حالا کنار گذاشته می‌شود: با پاسخِ کند، کلیک روی خانه‌ی قدیمی
            // شناسه‌ی زنگِ سالِ قبل را می‌فرستاد و خانه در سالِ قبل ذخیره می‌شد
            this.periods = [];
            this.entries = [];
            this.loadPeriods();
            this.loadEntries();
        },

        // quiet: بازخوانیِ بعد از یک نوشتن، جدول را پنهان نمی‌کند (فقط عوض‌شدنِ سال/کلاس پنهانش می‌کند)
        async loadPeriods(quiet) {
            const seq = ++this.periodSeq;
            this.periodsLoading = false;
            if (!this.year) {
                this.periods = [];
                this.periodsOk = true;
                return;
            }
            this.periodsLoading = !quiet;
            if (!quiet) {
                this.periodsOk = false;
            }
            try {
                const periods = await this.getJson(`/periods?academicYear=${encodeURIComponent(this.year)}`);
                if (seq === this.periodSeq) {
                    this.periods = periods;
                    this.periodsOk = true;
                    if (this.periodForm.id === null) {
                        this.resetPeriodForm();
                    }
                }
            } catch (e) {
                if (seq === this.periodSeq) {
                    this.periods = [];
                    this.periodsOk = false;
                    this.failLoad(e);
                }
            } finally {
                if (seq === this.periodSeq) {
                    this.periodsLoading = false;
                }
            }
        },

        async loadEntries(quiet) {
            const seq = ++this.entrySeq;
            this.entriesLoading = false;
            if (!this.year || !this.classId) {
                this.entries = [];
                this.entriesOk = true;
                return;
            }
            this.entriesLoading = !quiet;
            if (!quiet) {
                this.entriesOk = false;
            }
            try {
                const entries = await this.getJson(
                    `/classes/${this.classId}?academicYear=${encodeURIComponent(this.year)}`);
                if (seq === this.entrySeq) {
                    this.entries = entries;
                    this.entriesOk = true;
                }
            } catch (e) {
                if (seq === this.entrySeq) {
                    this.entries = [];
                    this.entriesOk = false;
                    this.failLoad(e);
                }
            } finally {
                if (seq === this.entrySeq) {
                    this.entriesLoading = false;
                }
            }
        },

        // تا زنگ‌ها و خانه‌های سال/کلاسِ انتخاب‌شده نرسیده‌اند، جدول نمایش و قابل‌ویرایش نیست
        busy() {
            return this.periodsLoading || this.entriesLoading;
        },

        // جدول فقط وقتی نشان داده/ویرایش می‌شود که خواندنِ زنگ‌ها و خانه‌ها موفق بوده باشد:
        // با شکستِ خواندنِ خانه‌ها، خانه‌ی «خالی» دروغ است و کلیک روی آن خانه‌ی موجود را بازنویسی می‌کرد
        gridReady() {
            return this.periods.length > 0 && !this.busy() && this.periodsOk && this.entriesOk;
        },

        async loadCourses() {
            const seq = ++this.courseSeq;
            if (!this.classId) {
                this.courses = [];
                return;
            }
            try {
                // فقط درس‌هایی که برای سالِ انتخاب‌شده ارائه می‌شوند (بازه‌ی اعتبارِ درس)
                const yearQuery = this.year ? `?academicYear=${encodeURIComponent(this.year)}` : '';
                const courses = await this.getJson(`/classes/${this.classId}/courses${yearQuery}`);
                if (seq === this.courseSeq) {
                    this.courses = courses;
                }
            } catch (e) {
                if (seq === this.courseSeq) {
                    this.courses = [];
                    this.failLoad(e);
                }
            }
        },

        async loadActivities() {
            const schoolId = this.schoolId;
            try {
                const activities = await this.getJson('/activities');
                if (schoolId === this.schoolId) {
                    this.activities = activities;
                }
            } catch (e) {
                if (schoolId === this.schoolId) {
                    this.failLoad(e);
                }
            }
        },

        onYearChange() {
            // خانه‌ی در حال ویرایش و ویرایشِ زنگ مال سالِ قبلی‌اند
            this.cell = null;
            this.resetPeriodForm();
            this.loadYear();
            // فهرست درس‌ها به بازه‌ی اعتبارِ درس وابسته است و برای سالِ دیگر فرق می‌کند
            this.loadCourses();
            // کلاس‌ها هم به سال تعلق دارند (academic-year.md فاز ۳)
            this.reloadClassesForYear();
        },

        // کلاس‌های سالِ انتخاب‌شده؛ اگر کلاسِ انتخاب‌شده در آن سال نبود به اولین کلاس می‌رود. شکست (مثلاً کاربری که
        // فقط سالِ جاری را می‌بیند) فهرست قبلی را نگه می‌دارد.
        async reloadClassesForYear() {
            if (!this.year) {
                return;
            }
            const seq = ++this.classesSeq;
            const year = this.year;
            let classes;
            try {
                classes = await this.getJson(`/classes?academicYear=${encodeURIComponent(year)}`);
            } catch (e) {
                return;
            }
            if (seq !== this.classesSeq || year !== this.year) {
                return;
            }
            this.classes = classes;
            if (!classes.some((c) => String(c.id) === String(this.classId))) {
                this.classId = classes.length > 0 ? String(classes[0].id) : '';
                this.onClassChange();
            }
        },

        onClassChange() {
            this.cell = null;
            this.entries = [];
            this.courses = [];
            this.loadEntries();
            this.loadCourses();
        },

        isCurrentYear() {
            return this.year === this.currentYear;
        },

        classLabel(classItem) {
            return classItem.name;
        },

        // ---------------------------------------------------------------- زنگ‌ها

        nextPeriodLabel() {
            const n = this.periods.length;
            return 'زنگ ' + (n < ORDINALS.length ? ORDINALS[n] : Calendar.digits(n + 1));
        },

        resetPeriodForm() {
            this.periodForm = { id: null, label: this.nextPeriodLabel(), start: '', end: '' };
            this.periodErrors = {};
        },

        editPeriod(period) {
            this.periodForm = {
                id: period.id, label: period.label,
                start: period.startTime.slice(0, 5), end: period.endTime.slice(0, 5)
            };
            this.periodErrors = {};
        },

        validatePeriod() {
            const errors = {};
            const form = this.periodForm;
            if (!form.label.trim()) {
                errors.label = 'برچسب زنگ را بنویسید.';
            }
            if (!form.start) {
                errors.start = 'ساعت شروع را انتخاب کنید.';
            }
            if (!form.end) {
                errors.end = 'ساعت پایان را انتخاب کنید.';
            }
            if (form.start && form.end && form.end <= form.start) {
                errors.end = 'ساعت پایان باید بعد از شروع باشد.';
            }
            this.periodErrors = errors;
            return Object.keys(errors).length === 0;
        },

        async savePeriod() {
            if (!this.validatePeriod()) {
                this.error = 'فرم زنگ را کامل و درست پر کنید.';
                return;
            }
            const editing = this.periodForm.id !== null;
            const body = {
                academicYear: this.year, label: this.periodForm.label.trim(),
                startTime: this.periodForm.start, endTime: this.periodForm.end
            };
            await this.mutate(async () => {
                if (editing) {
                    await this.send('PUT', `/periods/${this.periodForm.id}`, body);
                } else {
                    await this.send('POST', '/periods', body);
                }
            }, editing ? 'زنگ ذخیره شد.' : 'زنگ ایجاد شد.', () => {
                this.resetPeriodForm();
                this.loadPeriods(true);
            });
        },

        // ---------------------------------------------------------------- فعالیت‌ها

        ownActivities() {
            return this.activities.filter((a) => !a.platformDefault);
        },

        defaultActivities() {
            return this.activities.filter((a) => a.platformDefault);
        },

        editActivity(activity) {
            this.activityForm = { id: activity.id, name: activity.name };
            this.activityError = '';
        },

        cancelActivityEdit() {
            this.activityForm = { id: null, name: '' };
            this.activityError = '';
        },

        async saveActivity() {
            const name = this.activityForm.name.trim();
            if (!name) {
                this.activityError = 'نام فعالیت را بنویسید.';
                this.error = 'نام فعالیت را بنویسید.';
                return;
            }
            this.activityError = '';
            const editing = this.activityForm.id !== null;
            await this.mutate(async () => {
                if (editing) {
                    await this.send('PUT', `/activities/${this.activityForm.id}`, { name });
                } else {
                    await this.send('POST', '/activities', { name });
                }
            }, editing ? 'فعالیت ذخیره شد.' : 'فعالیت ایجاد شد.', () => {
                this.cancelActivityEdit();
                this.loadActivities();
                // نامِ فعالیت در خانه‌ها هم می‌آید
                this.loadEntries(true);
            });
        },

        // ---------------------------------------------------------------- حذف (با تأیید در modal)

        askDelete(kind, item) {
            this.confirm = {
                kind, id: item.id,
                name: kind === 'period' ? item.label : item.name,
                error: ''
            };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
        },

        async runDelete() {
            const target = this.confirm;
            if (!target) {
                return;
            }
            const path = target.kind === 'period' ? `/periods/${target.id}` : `/activities/${target.id}`;
            await this.mutate(async () => {
                await this.send('DELETE', path);
            }, target.kind === 'period' ? 'زنگ حذف شد.' : 'فعالیت حذف شد.', () => {
                this.confirm = null;
                if (target.kind === 'period') {
                    if (this.periodForm.id === target.id) {
                        this.resetPeriodForm();
                    }
                    this.loadPeriods(true);
                } else {
                    this.loadActivities();
                }
            }, (message) => {
                // خطا کنار همان modal می‌ماند (مثلاً «زنگ خانه دارد»)
                if (this.confirm) {
                    this.confirm.error = message;
                } else {
                    this.error = message;
                }
            });
        },

        // ---------------------------------------------------------------- خانه‌ها

        entryAt(weekday, periodId) {
            return window.TimetableGrid.entryAt(this.entries, weekday, periodId);
        },

        subjectOf(entry) {
            return window.TimetableGrid.subjectOf(entry);
        },

        cellLabel(weekday, period) {
            const entry = this.entryAt(weekday, period.id);
            const where = window.TimetableGrid.dayLabel(weekday) + '، ' + period.label;
            return entry ? where + ': ' + this.subjectOf(entry) : where + ': خالی';
        },

        selectedClassName() {
            const found = this.classes.find((c) => String(c.id) === String(this.classId));
            return found ? found.name : '';
        },

        openCell(weekday, period) {
            const entry = this.entryAt(weekday, period.id);
            this.cell = {
                weekday, periodId: period.id, periodLabel: period.label,
                kind: entry && entry.activityId ? 'activity' : 'course',
                courseId: entry && entry.courseId ? String(entry.courseId) : '',
                activityId: entry && entry.activityId ? String(entry.activityId) : '',
                teacherId: entry && entry.teacherUserId ? String(entry.teacherUserId) : '',
                hasEntry: !!entry,
                // خانه‌ای که این صفحه‌ی بارگذاری‌شده می‌بیند؛ هنگام ذخیره با همین کلاس/سال ارسال می‌شود
                classId: this.classId,
                year: this.year
            };
            this.cellError = '';
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.cellModal));
        },

        closeCell() {
            this.cell = null;
            this.cellError = '';
        },

        cellDayLabel() {
            return this.cell ? window.TimetableGrid.dayLabel(this.cell.weekday) : '';
        },

        canSaveCell() {
            if (!this.cell) {
                return false;
            }
            return this.cell.kind === 'course' ? this.cell.courseId !== '' : this.cell.activityId !== '';
        },

        async saveCell() {
            const cell = this.cell;
            if (!cell) {
                return;
            }
            if (!this.canSaveCell()) {
                this.cellError = cell.kind === 'course' ? 'یک درس انتخاب کنید.' : 'یک فعالیت انتخاب کنید.';
                return;
            }
            const body = {
                periodId: cell.periodId, weekday: cell.weekday,
                courseId: cell.kind === 'course' ? parseInt(cell.courseId, 10) : null,
                activityId: cell.kind === 'activity' ? parseInt(cell.activityId, 10) : null,
                teacherUserId: cell.teacherId ? parseInt(cell.teacherId, 10) : null
            };
            await this.mutate(async () => {
                await this.send('PUT', `/classes/${cell.classId}/entries`, body);
            }, 'خانه‌ی برنامه ذخیره شد.', () => {
                this.cell = null;
                this.cellError = '';
                this.loadEntries(true);
            }, (message) => {
                this.cellError = message;
            });
        },

        async clearCell() {
            const cell = this.cell;
            if (!cell) {
                return;
            }
            await this.mutate(async () => {
                await this.send('DELETE',
                    `/classes/${cell.classId}/entries?periodId=${cell.periodId}&weekday=${cell.weekday}`);
            }, 'خانه‌ی برنامه خالی شد.', () => {
                this.cell = null;
                this.cellError = '';
                this.loadEntries(true);
            }, (message) => {
                this.cellError = message;
            });
        },

        // یک نوشتن: فقط آخرین نوشتنِ همین مدرسه پیام و بستنِ modal را اعمال می‌کند (mutateSeq)؛
        // پس از موفقیت، فهرست‌ها دوباره از سرور خوانده می‌شوند (loaderها خودشان با شمارنده
        // محافظت می‌شوند)، پس حتی اگر پاسخ دور ریخته شود حالتِ نمایش به سرور همگرا می‌ماند.
        async mutate(run, successMessage, onSuccess, onFailure) {
            // is-loading (CSS) + این محافظ، نه disabled: دکمه‌ی disabled فوکوس را از modal می‌گیرد
            // و Escape دیگر modal را نمی‌بندد
            if (this.saving) {
                return;
            }
            const schoolId = this.schoolId;
            const seq = ++this.mutateSeq;
            this.saving = true;
            this.error = '';
            this.notice = '';
            try {
                await run();
                if (schoolId !== this.schoolId) {
                    return;
                }
                if (seq === this.mutateSeq) {
                    this.notice = successMessage;
                    onSuccess();
                } else {
                    // نوشتنِ جدیدتری شروع شده؛ فقط داده‌ها را با سرور هم‌گام کن
                    this.loadYear();
                    this.loadActivities();
                }
            } catch (e) {
                if (seq === this.mutateSeq) {
                    const message = e.blocking !== undefined ? e.message : 'ارتباط با سرور برقرار نشد.';
                    if (e.blocking) {
                        // ماژول/مجوز پس گرفته شده: بدنه‌ی صفحه بسته می‌شود، پس دلیل باید در ناحیه‌ی خطای
                        // صفحه بماند (modalها هم بسته می‌شوند تا بعد از بستنشان صفحه‌ی خالی نماند)
                        this.blocked = true;
                        this.cell = null;
                        this.confirm = null;
                        this.error = message;
                    } else if (onFailure) {
                        // خطا کنار همان modal می‌ماند، نه در ناحیه‌ی خطای صفحه‌ی پشتِ آن
                        onFailure(message);
                    } else {
                        this.error = message;
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
