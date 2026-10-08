// «تعطیلی‌های سراسری» (/platform/holidays، openspec/changes/yearly-plan.md فاز C): مدیر پلتفرم تعطیلی‌های هر سال
// تحصیلی را می‌بیند، می‌افزاید و (با تأیید در modal) حذف می‌کند. این‌ها در برنامه‌ی سالانه‌ی همه‌ی مدرسه‌ها می‌آیند و
// روز را در همه‌ی مدرسه‌ها تعطیل می‌کنند. مسیرهای پلتفرم‌اند: هدرِ مدرسه نمی‌فرستند (مدیر پلتفرم شاید هیچ مدرسه‌ای
// ندارد) و fetchِ مشترکِ مدرسه‌ای را هم نمی‌خواهند؛ ولی CSRF دقیقاً مثل آن روی نوشتن می‌رود. کنترل‌ها بر اساس Permission
// پنهان نمی‌شوند (UX_ROADMAP §3): کاربرِ دیگر 403 می‌گیرد و پیامِ مشترک را می‌بیند. روز در ورودی جلالی با ارقام
// فارسی است و روی سیم ISO می‌رود. API: GET /api/platform/academic-years، GET|POST
// /api/platform/academic-years/{yearId}/holidays، DELETE /api/platform/holidays/{id}.
// منطقِ خالص (گروه‌بندی، متنِ بازه، سالِ پیش‌فرض) در yearly-plan-logic.js است.
document.addEventListener('alpine:init', () => {
    const DENIED = 'فقط مدیر پلتفرم می‌تواند تعطیلی‌های سراسری را مدیریت کند.';
    const NETWORK = 'ارتباط با سرور برقرار نشد.';
    const BASE = '/api/platform';
    const Logic = window.YearlyPlanLogic;

    function csrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    // ProblemDetail → Error؛ نبودِ blocking یعنی خطای شبکه (fetch خودش پرتاب کرد)
    async function problemOf(response) {
        try {
            const body = await response.json();
            if ((body.type || '').endsWith('permission-denied')) {
                return { blocking: true, message: DENIED };
            }
            return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
        } catch (e) {
            return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
        }
    }

    async function request(method, url, body) {
        const headers = {};
        if (method !== 'GET') {
            headers['X-XSRF-TOKEN'] = csrfToken();
        }
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (!response.ok) {
            const problem = await problemOf(response);
            const error = new Error(problem.message);
            error.blocking = problem.blocking;
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    function messageOf(error) {
        return error.blocking !== undefined ? error.message : NETWORK;
    }

    Alpine.data('platformHolidaysPage', () => ({
        // years: سال‌های قابل‌انتخاب ({ id, code, startDate, endDate })؛ yearsLoaded: خواندنِ سال‌ها موفق بود
        years: [],
        yearsLoaded: false,
        selectedYearId: null,
        year: null,
        items: [],
        groups: [],
        loading: false,
        // false = هنوز خواندنِ موفقی نبوده؛ شکست «فهرست خالی» نشان نمی‌دهد
        loaded: false,
        error: '',
        notice: '',
        saving: false,

        // modal افزودن / حذف: null = بسته
        add: null,
        confirm: null,

        // فقط پاسخِ آخرین درخواست اثر می‌گذارد
        yearSeq: 0,
        listSeq: 0,
        mutateSeq: 0,

        init() {
            this.loadYears();
        },

        // ---------------------------------------------------------------- خواندن

        async loadYears() {
            const seq = ++this.yearSeq;
            this.loading = true;
            this.error = '';
            try {
                const years = await request('GET', `${BASE}/academic-years`);
                if (seq !== this.yearSeq) {
                    return;
                }
                this.years = years;
                this.yearsLoaded = true;
                const picked = Logic.pickYear(years, DateInput.today());
                this.year = picked;
                this.selectedYearId = picked ? picked.id : null;
                if (picked) {
                    await this.load();
                } else {
                    this.loading = false;
                }
            } catch (e) {
                if (seq === this.yearSeq) {
                    this.loading = false;
                    this.error = messageOf(e);
                }
            }
        },

        async load() {
            if (!this.year) {
                return;
            }
            const seq = ++this.listSeq;
            const yearId = this.year.id;
            this.loading = true;
            this.error = '';
            try {
                const items = await request('GET', `${BASE}/academic-years/${yearId}/holidays`);
                if (seq !== this.listSeq) {
                    return;
                }
                this.items = items;
                this.groups = Logic.groupByMonth(items);
                this.loaded = true;
            } catch (e) {
                if (seq === this.listSeq) {
                    this.items = [];
                    this.groups = [];
                    this.loaded = false;
                    this.error = messageOf(e);
                }
            } finally {
                if (seq === this.listSeq) {
                    this.loading = false;
                }
            }
        },

        onYearChange() {
            this.selectedYearId = Number(this.selectedYearId);
            this.year = this.years.find((y) => y.id === this.selectedYearId) || null;
            this.notice = '';
            this.loaded = false;
            this.items = [];
            this.groups = [];
            this.load();
        },

        // ---------------------------------------------------------------- نمایش

        yearOptionLabel(year) {
            return Calendar.digits(year.code);
        },

        yearLine() {
            return this.year ? Logic.yearLine(this.year) : '';
        },

        dayName(iso) {
            return DateInput.weekdayName(iso);
        },

        dayShort(iso) {
            return DateInput.format(iso);
        },

        // ---------------------------------------------------------------- افزودن

        exampleDate() {
            return this.year
                ? Logic.exampleDate(this.year, DateInput.today())
                : DateInput.format(DateInput.today());
        },

        rangeHelp() {
            return this.year ? Logic.rangeHelp(this.year) : '';
        },

        openAdd() {
            this.notice = '';
            if (!this.year) {
                this.error = this.error || 'سال تحصیلی‌ای برای افزودنِ تعطیلی در دسترس نیست.';
                return;
            }
            this.error = '';
            this.add = { date: '', title: '', dateError: '', titleError: '', error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.addModal));
        },

        closeAdd() {
            this.add = null;
        },

        // سرور مرجع نهایی است؛ این بررسی فقط پیامِ زودتر و دقیق‌تر می‌دهد
        validateAdd() {
            const form = this.add;
            form.dateError = '';
            form.titleError = '';
            form.error = '';
            const iso = DateInput.parse(form.date);
            if (iso === null) {
                form.dateError = 'تاریخ را به شکل سال/ماه/روز بنویسید، مثل ' + this.exampleDate() + '.';
            } else if (!Logic.inRange(iso, this.year)) {
                form.dateError = 'تاریخ باید داخل بازه‌ی سال تحصیلی باشد: ' + DateInput.formatLong(this.year.startDate)
                    + ' تا ' + DateInput.formatLong(this.year.endDate) + '.';
            }
            const title = form.title.trim();
            if (!title) {
                form.titleError = 'عنوان را بنویسید (مثلاً «نوروز»).';
            } else if (title.length > 128) {
                form.titleError = 'عنوان نباید بیشتر از ۱۲۸ نویسه باشد.';
            }
            if (form.dateError || form.titleError) {
                form.error = 'فرم را کامل و درست پر کنید.';
                return null;
            }
            return { day: iso, title };
        },

        async submitAdd() {
            if (!this.add || this.saving) {
                return;
            }
            const body = this.validateAdd();
            if (body === null) {
                return;
            }
            const yearId = this.year.id;
            await this.mutate(async () => {
                await request('POST', `${BASE}/academic-years/${yearId}/holidays`, body);
            }, 'تعطیلی افزوده شد.', () => {
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
                await request('DELETE', `${BASE}/holidays/${target.id}`);
            }, 'تعطیلی حذف شد.', () => {
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

        // یک نوشتن: is-loading (CSS) + این محافظ، نه disabled (دکمه‌ی disabled فوکوس را از modal می‌گیرد)
        async mutate(run, successMessage, onSuccess, onFailure) {
            if (this.saving) {
                return;
            }
            const seq = ++this.mutateSeq;
            this.saving = true;
            this.error = '';
            try {
                await run();
                if (seq !== this.mutateSeq) {
                    return;
                }
                this.notice = successMessage;
                onSuccess();
            } catch (e) {
                if (seq === this.mutateSeq) {
                    const message = messageOf(e);
                    if (e.blocking) {
                        // مجوز نداشتن: modal بسته و دلیل در ناحیه‌ی خطای مشترک نشان داده می‌شود؛ بقیه‌ی صفحه می‌ماند
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
