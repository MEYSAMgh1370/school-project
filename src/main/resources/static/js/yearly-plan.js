// «برنامه‌ی سالانه» (/yearly-plan، openspec/changes/yearly-plan.md): رویدادهای مدرسه و تعطیلی‌های سراسریِ یک
// سال تحصیلی را به‌صورت گروه‌بندی‌شده بر اساس ماه نشان می‌دهد؛ مدیر (YEARLY_PLAN_MANAGE) رویداد می‌افزاید و
// (با تأیید در modal) حذف می‌کند. هر عضو می‌خواند؛ کنترل‌ها بر اساس Permission پنهان نمی‌شوند و کاربرِ بدونِ
// مجوز پیامِ «اجازه ندارید» می‌گیرد (UX_ROADMAP §3). روز در ورودی جلالی با ارقام فارسی است و روی سیم ISO
// می‌رود. API: GET /api/me/schools/{id}/yearly-plan، POST/DELETE /api/admin/schools/{id}/yearly-plan.
// منطقِ خالص (گروه‌بندی، متنِ بازه) در yearly-plan-logic.js است.
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول مورد نیاز را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی مدیریت برنامه‌ی سالانه را ندارد.'
    };
    const ME = (schoolId) => `/api/me/schools/${schoolId}`;
    const ADMIN = (schoolId) => `/api/admin/schools/${schoolId}`;
    const Logic = window.YearlyPlanLogic;

    Alpine.data('yearlyPlanPage', () => ({
        schoolId: null,
        loading: false,
        // false = هنوز خواندنِ موفقی نبوده؛ شکستِ شبکه «برنامه‌ی خالی» نشان نمی‌دهد
        loaded: false,
        error: '',
        notice: '',
        saving: false,

        year: null,
        items: [],
        groups: [],
        // سال‌های قابل‌انتخاب ({ id, code, startDate, endDate })؛ انتخاب‌گر فقط با بیش از یک سال دیده می‌شود
        years: [],
        selectedYearId: null,

        // modal افزودن / حذف: null = بسته
        add: null,
        confirm: null,

        // فقط پاسخِ آخرین درخواست اثر می‌گذارد؛ عوض‌کردنِ مدرسه همه را بالا می‌برد
        listSeq: 0,
        yearSeq: 0,
        mutateSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.reset();
                this.start();
            });
            this.start();
        },

        start() {
            if (this.schoolId) {
                this.load();
                this.loadYears();
            }
        },

        reset() {
            this.listSeq++;
            this.yearSeq++;
            this.mutateSeq++;
            this.saving = false;
            this.loading = false;
            this.loaded = false;
            this.error = '';
            this.notice = '';
            this.year = null;
            this.items = [];
            this.groups = [];
            this.years = [];
            this.selectedYearId = null;
            this.add = null;
            this.confirm = null;
        },

        // ---------------------------------------------------------------- خواندن

        async load() {
            if (!this.schoolId) {
                return;
            }
            const seq = ++this.listSeq;
            const query = this.selectedYearId ? `?academicYearId=${this.selectedYearId}` : '';
            this.loading = true;
            this.error = '';
            try {
                const plan = await SchoolApi.request(this.schoolId, 'GET',
                    `${ME(this.schoolId)}/yearly-plan${query}`, undefined, MESSAGES);
                if (seq !== this.listSeq) {
                    return;
                }
                this.year = plan.year;
                this.items = plan.items;
                this.groups = Logic.groupByMonth(plan.items);
                if (plan.year && !this.selectedYearId) {
                    this.selectedYearId = plan.year.id;
                }
                this.loaded = true;
            } catch (e) {
                if (seq === this.listSeq) {
                    this.items = [];
                    this.groups = [];
                    this.loaded = false;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.listSeq) {
                    this.loading = false;
                }
            }
        },

        // مدیر همه‌ی سال‌ها را از endpoint ادمین می‌گیرد؛ بقیه (403) فقط سالِ جاری را (مثل grades.js)
        async loadYears() {
            const seq = ++this.yearSeq;
            const schoolId = this.schoolId;
            let years = [];
            try {
                const settings = await SchoolApi.request(schoolId, 'GET', `${ADMIN(schoolId)}/academic-year`,
                    undefined, MESSAGES);
                years = settings.years || [];
            } catch (e) {
                try {
                    const settings = await SchoolApi.request(schoolId, 'GET', `${ME(schoolId)}/academic-year`,
                        undefined, MESSAGES);
                    years = settings.current ? [settings.current] : [];
                } catch (ignored) {
                    years = [];
                }
            }
            if (seq === this.yearSeq && schoolId === this.schoolId) {
                this.years = years;
            }
        },

        onYearChange() {
            this.selectedYearId = Number(this.selectedYearId);
            this.notice = '';
            this.load();
        },

        yearOptionLabel(year) {
            return Calendar.digits(year.code);
        },

        // ---------------------------------------------------------------- نمایش

        yearLine() {
            return this.year ? Logic.yearLine(this.year) : '';
        },

        itemKey(item) {
            return Logic.itemKey(item);
        },

        dayName(iso) {
            return DateInput.weekdayName(iso);
        },

        dayShort(iso) {
            return DateInput.format(iso);
        },

        // ---------------------------------------------------------------- افزودن

        exampleDate() {
            return this.year ? Logic.exampleDate(this.year, DateInput.today()) : DateInput.format(DateInput.today());
        },

        rangeHelp() {
            return this.year ? Logic.rangeHelp(this.year) : '';
        },

        openAdd() {
            this.notice = '';
            if (!this.year) {
                this.error = 'برای این مدرسه هنوز سال تحصیلی جاری انتخاب نشده است.';
                return;
            }
            this.error = '';
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
            } else if (!Logic.inRange(iso, this.year)) {
                form.dateError = 'تاریخ باید داخل سال تحصیلی باشد: ' + DateInput.formatLong(this.year.startDate)
                    + ' تا ' + DateInput.formatLong(this.year.endDate) + '.';
            }
            const title = form.title.trim();
            if (!title) {
                form.titleError = 'عنوان را بنویسید (مثلاً «جلسه‌ی اولیا»).';
            } else if (title.length > 128) {
                form.titleError = 'عنوان نباید بیشتر از ۱۲۸ نویسه باشد.';
            }
            if (form.dateError || form.titleError) {
                form.error = 'فرم را کامل و درست پر کنید.';
                return null;
            }
            return { academicYearId: this.year.id, day: iso, title };
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
                await SchoolApi.request(this.schoolId, 'POST', `${ADMIN(this.schoolId)}/yearly-plan`, body, MESSAGES);
            }, 'رویداد افزوده شد.', () => {
                this.add = null;
                this.load();
            }, (message) => {
                if (this.add) {
                    this.add.error = message;
                }
            });
        },

        // ---------------------------------------------------------------- حذف (با تأیید در modal)

        askRemove(item) {
            this.notice = '';
            this.confirm = { id: item.id, title: item.title, day: item.day, error: '' };
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
                await SchoolApi.request(this.schoolId, 'DELETE', `${ADMIN(this.schoolId)}/yearly-plan/${target.id}`,
                    undefined, MESSAGES);
            }, 'رویداد حذف شد.', () => {
                this.confirm = null;
                this.load();
            }, (message) => {
                if (this.confirm) {
                    this.confirm.error = message;
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
                        // مجوز نداشتن: صفحه خواندنی می‌ماند؛ modal بسته و دلیل در ناحیه‌ی خطای مشترک نشان داده می‌شود
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
