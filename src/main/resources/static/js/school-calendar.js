// «تقویم مدرسه» (/calendar، openspec/changes/attendance.md فاز ۰): روزهای تعطیلِ یک ماه را فهرست می‌کند،
// روزِ تعطیل می‌افزاید و (با تأیید در modal) حذف می‌کند. روز در ورودی جلالی با ارقام فارسی است و روی سیم
// ISO میلادی می‌رود (date-input.js). API: GET/POST/DELETE /api/admin/schools/{id}/calendar (CALENDAR_MANAGE).
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول مورد نیاز را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی مدیریت تقویم مدرسه را ندارد.'
    };
    const BASE = (schoolId) => `/api/admin/schools/${schoolId}/calendar`;

    Alpine.data('schoolCalendarPage', () => ({
        schoolId: null,
        loading: false,
        // false = هنوز خواندنِ موفقی نبوده؛ شکستِ شبکه «ماه بدونِ تعطیلی» نشان نمی‌دهد
        loaded: false,
        blocked: false,
        error: '',
        notice: '',
        saving: false,

        // ماهِ نمایش‌داده‌شده (تقویمِ صفحه: جلالی برای fa)
        year: 0,
        month: 0,
        yearText: '',
        monthNames: Calendar.monthNames,
        entries: [],

        // modal افزودن: null = بسته
        add: null,
        // modal حذف: null = بسته
        confirm: null,

        // فقط پاسخِ آخرین درخواست اثر می‌گذارد؛ عوض‌کردنِ مدرسه همه را بالا می‌برد
        listSeq: 0,
        mutateSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.showMonthOf(DateInput.today());
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.reset();
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        reset() {
            this.listSeq++;
            this.mutateSeq++;
            this.saving = false;
            this.loading = false;
            this.loaded = false;
            this.blocked = false;
            this.error = '';
            this.notice = '';
            this.entries = [];
            this.add = null;
            this.confirm = null;
            this.showMonthOf(DateInput.today());
        },

        // ---------------------------------------------------------------- ماه

        showMonthOf(iso) {
            const civil = DateInput.civil(iso);
            this.year = civil.y;
            this.month = civil.m;
            this.yearText = Calendar.digits(civil.y);
        },

        monthTitle() {
            return this.monthNames[this.month - 1] + ' ' + Calendar.digits(this.year);
        },

        previousMonth() {
            this.step(-1);
        },

        nextMonth() {
            this.step(1);
        },

        step(delta) {
            let month = this.month + delta;
            let year = this.year;
            if (month < 1) {
                month = 12;
                year -= 1;
            } else if (month > 12) {
                month = 1;
                year += 1;
            }
            this.year = year;
            this.month = month;
            this.yearText = Calendar.digits(year);
            this.load();
        },

        onMonthChange() {
            this.month = Number(this.month);
            this.load();
        },

        // سالِ تایپ‌شده (ارقام فارسی/عربی هم) نرمال و اعتبارسنجی می‌شود؛ نامعتبر به سالِ قبلی برمی‌گردد
        onYearChange() {
            const text = DateInput.toAscii(this.yearText).trim();
            if (/^\d{4}$/.test(text)) {
                this.year = Number(text);
                this.load();
            }
            this.yearText = Calendar.digits(this.year);
        },

        // ---------------------------------------------------------------- خواندن

        async load() {
            if (!this.schoolId) {
                return;
            }
            const seq = ++this.listSeq;
            const range = DateInput.monthRange(this.year, this.month);
            this.loading = true;
            this.error = '';
            try {
                const entries = await SchoolApi.request(this.schoolId, 'GET',
                    `${BASE(this.schoolId)}?from=${range.from}&to=${range.to}`, undefined, MESSAGES);
                if (seq !== this.listSeq) {
                    return;
                }
                this.entries = entries.filter((entry) => entry.kind === 'CLOSED');
                this.loaded = true;
            } catch (e) {
                if (seq === this.listSeq) {
                    this.entries = [];
                    this.loaded = false;
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.listSeq) {
                    this.loading = false;
                }
            }
        },

        dayName(iso) {
            return DateInput.weekdayName(iso);
        },

        dayShort(iso) {
            return DateInput.format(iso);
        },

        // ---------------------------------------------------------------- افزودن

        exampleDate() {
            return DateInput.format(DateInput.today());
        },

        openAdd() {
            this.notice = '';
            this.add = { date: '', title: '', dateError: '', titleError: '', error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.addModal));
        },

        closeAdd() {
            this.add = null;
        },

        validateAdd() {
            const form = this.add;
            form.dateError = '';
            form.titleError = '';
            form.error = '';
            const iso = DateInput.parse(form.date);
            if (iso === null) {
                form.dateError = 'تاریخ را به شکل سال/ماه/روز بنویسید، مثل ' + this.exampleDate() + '.';
            }
            const title = form.title.trim();
            if (!title) {
                form.titleError = 'عنوان را بنویسید (مثلاً «تعطیلی رسمی»).';
            } else if (title.length > 128) {
                form.titleError = 'عنوان نباید بیشتر از ۱۲۸ نویسه باشد.';
            }
            if (form.dateError || form.titleError) {
                form.error = 'فرم را کامل و درست پر کنید.';
                return null;
            }
            return { day: iso, kind: 'CLOSED', title };
        },

        async submitAdd() {
            if (!this.add || this.saving) {
                return;
            }
            const body = this.validateAdd();
            if (body === null) {
                return;
            }
            await this.mutate(async () => {
                await SchoolApi.request(this.schoolId, 'POST', BASE(this.schoolId), body, MESSAGES);
            }, 'روز تعطیل افزوده شد.', () => {
                this.add = null;
                // ماهی که روزِ تازه در آن است نشان داده می‌شود تا کاربر نتیجه را ببیند
                this.showMonthOf(body.day);
                this.load();
            }, (message) => {
                if (this.add) {
                    this.add.error = message;
                }
            });
        },

        // ---------------------------------------------------------------- حذف (با تأیید در modal)

        askRemove(entry) {
            this.notice = '';
            this.confirm = { id: entry.id, title: entry.title, day: entry.day, error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
        },

        async runRemove() {
            const target = this.confirm;
            if (!target || this.saving) {
                return;
            }
            await this.mutate(async () => {
                await SchoolApi.request(this.schoolId, 'DELETE', `${BASE(this.schoolId)}/${target.id}`,
                    undefined, MESSAGES);
            }, 'روز تعطیل حذف شد.', () => {
                this.confirm = null;
                this.load();
            }, (message) => {
                if (this.confirm) {
                    this.confirm.error = message;
                } else {
                    this.error = message;
                }
            });
        },

        // یک نوشتن: is-loading (CSS) + این محافظ، نه disabled (دکمه‌ی disabled فوکوس را از modal می‌گیرد).
        // فقط آخرین نوشتنِ همین مدرسه پیام و بستنِ modal را اعمال می‌کند.
        async mutate(run, successMessage, onSuccess, onFailure) {
            if (this.saving) {
                return;
            }
            const schoolId = this.schoolId;
            const seq = ++this.mutateSeq;
            this.saving = true;
            this.error = '';
            try {
                await run();
                if (schoolId !== this.schoolId || seq !== this.mutateSeq) {
                    return;
                }
                this.notice = successMessage;
                onSuccess();
            } catch (e) {
                if (seq === this.mutateSeq && schoolId === this.schoolId) {
                    const message = SchoolApi.messageOf(e);
                    if (e.blocking) {
                        // مجوز/ماژول پس گرفته شده: صفحه بسته می‌شود و دلیل در ناحیه‌ی خطای صفحه می‌ماند
                        this.blocked = true;
                        this.add = null;
                        this.confirm = null;
                        this.error = message;
                    } else {
                        onFailure(message);
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
