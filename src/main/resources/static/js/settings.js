// صفحه‌ی «تنظیمات مدرسه»: چهار بخشِ مستقل (سال تحصیلی، مالی، یادآوری‌ها، بات بله) که هر کدام فقط وقتی نشان داده
// می‌شوند که GET /settings آن را پر کرده باشد، و هر کدام ذخیره و خطا و پیامِ موفقیتِ خودش را دارد.
// منطقِ خالص در settings-logic.js است. وابستگی: /js/calendar.js، /js/modal-focus.js و /js/settings-logic.js پیش از این فایل.
document.addEventListener('alpine:init', () => {
    const Logic = window.SettingsLogic;
    const NO_SERVER = 'ارتباط با سرور برقرار نشد.';
    const ENTITLED = 'این مدرسه ماژول مورد نیاز را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.';
    const DENIED = 'نقش انتخاب‌شده‌ی شما اجازه‌ی تغییر این تنظیم را ندارد.';
    const NONE_DENIED = 'نقش انتخاب‌شده‌ی شما به هیچ‌یک از تنظیمات مدرسه دسترسی ندارد.';

    Alpine.data('settingsPage', () => ({
        schoolId: null,
        loading: false,
        // فقط بعد از یک بارگذاریِ موفقِ GET /settings true است
        loaded: false,
        blocked: false,
        error: '',
        // فقط پاسخ آخرین بارگذاری اثر می‌گذارد (عوض‌کردنِ سریعِ مدرسه پاسخ کهنه را دور می‌ریزد)
        seq: 0,
        // خطا، پیام و وضعیتِ ذخیره‌ی هر بخش جدا؛ seq هر بخش پاسخِ کهنه‌ی همان بخش را دور می‌اندازد
        sec: Logic.emptySections(),

        hasYear: false,
        hasFinance: false,
        hasReminders: false,
        hasBot: false,

        // سال تحصیلی
        current: null,
        years: [],
        selectedId: '',
        autoPromotion: false,
        autoSaving: false,
        unattributed: { classes: 0, students: 0 },
        attributeYear: '',
        attributing: false,
        // تأیید «نسبت دادن»: null یا { year, classes, students, error }
        confirm: null,
        // تأیید «ارتقای خودکار»: null یا { enabled, error }
        autoConfirm: null,
        financeUnattributed: { tuitionFee: false, invoices: 0 },
        financeAttributeYear: '',
        financeAttributing: false,
        // تأیید «نسبت دادنِ مالی»: null یا { year, invoices, tuitionFee, error }
        financeConfirm: null,

        // مالی
        installmentsEnabled: false,

        // نقش‌ها (فهرستِ مشترکِ یادآوری‌ها و بات؛ منبعش GET reminder-roles است)
        roles: [],
        rolesLoading: false,
        reminderRoles: [],
        remindersFieldError: '',
        botEnabled: false,
        botRoles: [],

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        // خطای سرور: Error با server=true، و blocking (مسدودکردنِ کلِ صفحه) یا reload (وضعیتِ سرور عوض شده)
        async fail(response, deniedMessage) {
            const error = new Error('خطایی رخ داد. (' + response.status + ')');
            error.server = true;
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    error.message = ENTITLED;
                } else if (type.endsWith('permission-denied')) {
                    error.message = deniedMessage;
                    error.blocking = true;
                } else if (type.endsWith('academic-year-changed')) {
                    // سالِ جاری در فاصله‌ی دیدن تا ذخیره عوض شده (درخواستِ کهنه یا مدیرِ دیگر)
                    error.message = 'سال تحصیلی جاری در این فاصله عوض شده است؛ اطلاعات تازه بارگذاری شد، دوباره انتخاب کنید.';
                    error.reload = true;
                } else {
                    error.message = body.detail || body.title || 'خطایی رخ داد.';
                }
            } catch (e) {
                // بدنه‌ی غیر JSON: همان پیامِ کدِ وضعیت
            }
            return error;
        },

        async api(schoolId, method, path, body, deniedMessage = DENIED) {
            const headers = { 'X-School-ID': schoolId };
            if (method !== 'GET') {
                headers['X-XSRF-TOKEN'] = this.csrfToken();
            }
            if (body !== undefined) {
                headers['Content-Type'] = 'application/json';
            }
            const response = await fetch(`/api/admin/schools/${schoolId}${path}`, {
                method,
                headers,
                body: body === undefined ? undefined : JSON.stringify(body)
            });
            if (!response.ok) {
                throw await this.fail(response, deniedMessage);
            }
            return response.status === 204 ? null : response.json();
        },

        // پیامِ خطای سرور همان است؛ هر چیز دیگر (fetch که پرتاب کرد، پاسخِ ناخوانا) خطای ارتباط است
        errorText(error) {
            return error && error.server ? error.message : NO_SERVER;
        },

        // ------------------------------------------------------------------ ذخیره‌ی هر بخش

        startSave(key) {
            const section = this.sec[key];
            section.error = '';
            section.notice = '';
            section.saving = true;
            return { key, n: ++section.seq, schoolId: this.schoolId };
        },

        // پاسخِ ذخیره‌ی مدرسه‌ی قبلی یا ذخیره‌ی کهنه‌ترِ همین بخش دور ریخته می‌شود
        isLatest(token) {
            return Logic.isLatest(this.sec, token) && token.schoolId === this.schoolId;
        },

        endSave(token) {
            if (this.isLatest(token)) {
                this.sec[token.key].saving = false;
            }
        },

        // ------------------------------------------------------------------ بارگذاری

        resetState() {
            this.error = '';
            this.blocked = false;
            this.loaded = false;
            this.sec = Logic.emptySections(this.sec);
            Object.assign(this, {
                hasYear: false, hasFinance: false, hasReminders: false, hasBot: false,
                current: null, years: [], selectedId: '', autoPromotion: false, autoSaving: false,
                unattributed: { classes: 0, students: 0 }, attributeYear: '', attributing: false,
                confirm: null, autoConfirm: null,
                financeUnattributed: { tuitionFee: false, invoices: 0 }, financeAttributeYear: '',
                financeAttributing: false, financeConfirm: null,
                installmentsEnabled: false, roles: [], rolesLoading: false, reminderRoles: [], remindersFieldError: '',
                botEnabled: false, botRoles: []
            });
        },

        async load() {
            const seq = ++this.seq;
            const schoolId = this.schoolId;
            // وضعیتِ مدرسه‌ی قبلی کنار گذاشته می‌شود: اگر این بارگذاری شکست بخورد، صفحه نباید مقدارهای
            // مدرسه‌ی قبلی را زیرِ مدرسه‌ی تازه نشان دهد
            this.resetState();
            this.loading = true;
            try {
                const settings = await this.api(schoolId, 'GET', '/settings', undefined, NONE_DENIED);
                if (seq !== this.seq) {
                    return;
                }
                const state = Logic.fromResponse(settings);
                Object.assign(this, state);
                this.selectedId = state.current ? state.current.id : '';
                this.loaded = true;
            } catch (e) {
                if (seq === this.seq) {
                    this.error = this.errorText(e);
                    this.blocked = !!e.blocking;
                }
                return;
            } finally {
                if (seq === this.seq) {
                    this.loading = false;
                }
            }
            // فهرست‌ها و شمارنده‌های جانبی: هر کدام بهترین تلاش است و خطایش به بخشِ خودش می‌رود
            const tasks = [];
            if (this.hasYear) {
                tasks.push(this.loadYears(seq), this.loadUnattributed(seq), this.loadFinanceUnattributed(seq));
            }
            if (this.hasReminders || this.hasBot) {
                tasks.push(this.loadRoles(seq));
            }
            await Promise.all(tasks);
        },

        // فهرستِ همه‌ی سال‌ها فقط برای مدیر است (ACADEMIC_YEAR_MANAGE)
        async loadYears(seq = this.seq) {
            const schoolId = this.schoolId;
            try {
                const body = await this.api(schoolId, 'GET', '/academic-year');
                if (seq === this.seq && schoolId === this.schoolId) {
                    this.years = body.years;
                    // x-model روی select بعد از رندرِ گزینه‌ها مقدار را می‌گیرد؛ بدون این، انتخابِ سالِ جاری گم می‌شد
                    const chosen = this.selectedId;
                    this.selectedId = '';
                    await this.$nextTick();
                    this.selectedId = chosen;
                }
            } catch (e) {
                if (seq === this.seq && schoolId === this.schoolId) {
                    this.sec.year.error = this.errorText(e);
                }
            }
        },

        async loadRoles(seq = this.seq) {
            const schoolId = this.schoolId;
            this.rolesLoading = true;
            try {
                const body = await this.api(schoolId, 'GET', '/yearly-plan/reminder-roles');
                if (seq === this.seq && schoolId === this.schoolId) {
                    this.roles = body.roles;
                    this.reminderRoles = Logic.knownSelected(this.reminderRoles, this.roles);
                    this.botRoles = Logic.knownSelected(this.botRoles, this.roles);
                }
            } catch (e) {
                if (seq === this.seq && schoolId === this.schoolId) {
                    // بدون فهرست، انتخابِ ذخیره‌شده دست‌نخورده می‌ماند و ذخیره آن را پاک نمی‌کند
                    const message = 'فهرست نقش‌ها بارگذاری نشد: ' + this.errorText(e);
                    if (this.hasReminders) {
                        this.sec.reminders.error = message;
                    }
                    if (this.hasBot) {
                        this.sec.bot.error = message;
                    }
                }
            } finally {
                if (seq === this.seq) {
                    this.rolesLoading = false;
                }
            }
        },

        // ------------------------------------------------------------------ سال تحصیلی

        apply(settings) {
            this.current = settings.current;
            this.years = settings.years;
            this.selectedId = settings.current ? settings.current.id : '';
            this.autoPromotion = !!settings.autoPromotion;
        },

        get attributableYears() {
            return Logic.attributableYears(this.years, this.current);
        },

        canSave() {
            return this.selectedId !== '' && !this.sec.year.saving
                && !(this.current && String(this.current.id) === String(this.selectedId));
        },

        async saveYear() {
            const section = this.sec.year;
            if (section.saving) {
                return;
            }
            if (this.selectedId === '') {
                section.notice = '';
                section.error = 'یک سال تحصیلی را انتخاب کنید.';
                return;
            }
            if (!this.canSave()) {
                section.notice = '';
                section.error = 'این سال هم‌اکنون سال تحصیلی جاری است؛ برای تغییر، سال دیگری را انتخاب کنید.';
                return;
            }
            const token = this.startSave('year');
            try {
                const settings = await this.api(token.schoolId, 'PUT', '/academic-year',
                    Logic.yearBody(this.selectedId, this.current));
                if (this.isLatest(token)) {
                    this.apply(settings);
                    section.notice = 'سال تحصیلی جاری ثبت شد.';
                    this.loadUnattributed();
                    this.loadFinanceUnattributed();
                }
            } catch (e) {
                if (this.isLatest(token)) {
                    if (e.reload) {
                        // وضعیتِ تازه را بگیر، بعد پیام را نشان بده
                        await this.reloadYear();
                    }
                    if (this.isLatest(token)) {
                        section.error = this.errorText(e);
                    }
                }
            } finally {
                this.endSave(token);
            }
        },

        // وضعیتِ تازه‌ی سال (بعد از academic-year-changed) بدون دست‌زدن به بخش‌های دیگر
        async reloadYear() {
            const schoolId = this.schoolId;
            try {
                const settings = await this.api(schoolId, 'GET', '/academic-year');
                if (schoolId === this.schoolId) {
                    this.apply(settings);
                    this.loadUnattributed();
                    this.loadFinanceUnattributed();
                }
            } catch (e) {
                // خطای ذخیره همچنان نشان داده می‌شود
            }
        },

        hasUnattributed() {
            return !!this.current && (this.unattributed.classes > 0 || this.unattributed.students > 0);
        },

        hasFinanceUnattributed() {
            return !!this.current && (this.financeUnattributed.tuitionFee || this.financeUnattributed.invoices > 0);
        },

        // بهترین تلاش: مدرسه‌ای که ماژول آموزشی را ندارد 403 می‌گیرد و فقط بنر نمی‌آید
        async loadUnattributed(seq = this.seq) {
            const schoolId = this.schoolId;
            this.unattributed = { classes: 0, students: 0 };
            if (!this.current) {
                return;
            }
            try {
                const body = await this.api(schoolId, 'GET', '/academic-year/unattributed');
                if (seq === this.seq && schoolId === this.schoolId) {
                    this.unattributed = body;
                }
            } catch (e) {
                // بنر اختیاری است
            }
        },

        // فقط برای دارنده‌ی FINANCE_MANAGE؛ بقیه 403 می‌گیرند و بنر را نمی‌بینند
        async loadFinanceUnattributed(seq = this.seq) {
            const schoolId = this.schoolId;
            this.financeUnattributed = { tuitionFee: false, invoices: 0 };
            if (!this.current) {
                return;
            }
            try {
                const body = await this.api(schoolId, 'GET', '/finance/unattributed');
                if (seq === this.seq && schoolId === this.schoolId) {
                    this.financeUnattributed = body;
                }
            } catch (e) {
                // بنر اختیاری است
            }
        },

        // نسبت دادنِ داده‌ی قبلی به یک سال برگشت‌پذیر نیست: اول تأیید (modal) گرفته می‌شود
        askAttribute() {
            if (this.attributing) {
                return;
            }
            const section = this.sec.year;
            section.error = '';
            section.notice = '';
            if (!this.attributeYear) {
                section.error = 'یک سال تحصیلی را انتخاب کنید.';
                return;
            }
            this.confirm = {
                year: this.attributeYear,
                classes: this.unattributed.classes,
                students: this.unattributed.students,
                error: ''
            };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
        },

        async attribute() {
            const target = this.confirm;
            if (!target || this.attributing) {
                return;
            }
            const schoolId = this.schoolId;
            const section = this.sec.year;
            this.attributing = true;
            section.error = '';
            section.notice = '';
            try {
                const result = await this.api(schoolId, 'POST', '/academic-year/attribution', { academicYear: target.year });
                if (schoolId !== this.schoolId) {
                    return;
                }
                this.confirm = null;
                section.notice = `کلاس‌ها و دانش‌آموزان قبلی به سال ${Calendar.digits(result.academicYear)} نسبت داده شد `
                    + `(${Calendar.digits(String(result.classes))} کلاس، ${Calendar.digits(String(result.students))} دانش‌آموز).`;
                this.attributeYear = '';
                await this.loadUnattributed();
            } catch (e) {
                if (schoolId === this.schoolId) {
                    // خطا داخل modal نشان داده می‌شود (پشتِ لایه‌ی modal دیده نمی‌شد)
                    if (this.confirm === target) {
                        target.error = this.errorText(e);
                    } else {
                        section.error = this.errorText(e);
                    }
                }
            } finally {
                this.attributing = false;
            }
        },

        askFinanceAttribute() {
            if (this.financeAttributing) {
                return;
            }
            const section = this.sec.year;
            section.error = '';
            section.notice = '';
            if (!this.financeAttributeYear) {
                section.error = 'یک سال تحصیلی را انتخاب کنید.';
                return;
            }
            this.financeConfirm = {
                year: this.financeAttributeYear,
                invoices: this.financeUnattributed.invoices,
                tuitionFee: this.financeUnattributed.tuitionFee,
                error: ''
            };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.financeConfirmModal));
        },

        closeFinanceConfirm() {
            this.financeConfirm = null;
        },

        // اقدامِ صریحِ ادمین: شهریه و فاکتورهای «بدون سال» را به یک سال نسبت می‌دهد
        async attributeFinance() {
            const target = this.financeConfirm;
            if (!target || this.financeAttributing) {
                return;
            }
            const schoolId = this.schoolId;
            const section = this.sec.year;
            this.financeAttributing = true;
            section.error = '';
            section.notice = '';
            try {
                const result = await this.api(schoolId, 'POST', '/finance/attribute-year', { academicYear: target.year });
                if (schoolId !== this.schoolId) {
                    return;
                }
                this.financeConfirm = null;
                section.notice = `اطلاعاتِ مالیِ قبلی به سالِ ${Calendar.digits(target.year)} نسبت داده شد `
                    + `(${Calendar.digits(String(result.invoicesAttributed))} فاکتور`
                    + `${result.tuitionAttributed ? ' و شهریه' : ''}).`;
                this.financeAttributeYear = '';
                await this.loadFinanceUnattributed();
            } catch (e) {
                if (schoolId === this.schoolId) {
                    if (this.financeConfirm === target) {
                        target.error = this.errorText(e);
                    } else {
                        section.error = this.errorText(e);
                    }
                }
            } finally {
                this.financeAttributing = false;
            }
        },

        // روشن/خاموش‌کردنِ ارتقای خودکار روی همه‌ی دانش‌آموزانِ تعویضِ بعدیِ سال اثر می‌گذارد: اول تأیید (modal)
        // گرفته می‌شود. چک‌باکس با کلیک می‌چرخد ولی وضعیتِ واقعی فقط بعد از تأیید عوض می‌شود، پس همان لحظه
        // به وضعیتِ واقعی برمی‌گردد (انصراف دیگر کاری لازم ندارد)
        askAutoPromotion(event) {
            const box = event.target;
            const enabled = box.checked;
            box.checked = this.autoPromotion;
            if (this.autoSaving) {
                return;
            }
            const section = this.sec.year;
            section.error = '';
            section.notice = '';
            this.autoConfirm = { enabled, error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.autoConfirmModal));
        },

        closeAutoConfirm() {
            this.autoConfirm = null;
        },

        async setAutoPromotion() {
            const target = this.autoConfirm;
            if (!target || this.autoSaving) {
                return;
            }
            const schoolId = this.schoolId;
            const section = this.sec.year;
            this.autoSaving = true;
            section.error = '';
            section.notice = '';
            try {
                const settings = await this.api(schoolId, 'PUT', '/academic-year/auto-promotion', { enabled: target.enabled });
                if (schoolId !== this.schoolId) {
                    return;
                }
                this.autoConfirm = null;
                this.autoPromotion = !!settings.autoPromotion;
                section.notice = this.autoPromotion
                    ? 'ارتقای خودکار روشن شد؛ از تعویض بعدیِ سال اثر می‌کند.'
                    : 'ارتقای خودکار خاموش شد.';
            } catch (e) {
                if (schoolId === this.schoolId) {
                    // سرور نپذیرفت؛ وضعیت همان قبلی می‌ماند و خطا داخل modal نشان داده می‌شود
                    if (this.autoConfirm === target) {
                        target.error = this.errorText(e);
                    } else {
                        section.error = this.errorText(e);
                    }
                }
            } finally {
                this.autoSaving = false;
            }
        },

        // ------------------------------------------------------------------ مالی

        async saveFinance() {
            if (this.sec.finance.saving) {
                return;
            }
            const token = this.startSave('finance');
            try {
                const body = await this.api(token.schoolId, 'PUT', '/finance/settings/installments',
                    { enabled: this.installmentsEnabled });
                if (this.isLatest(token)) {
                    this.installmentsEnabled = !!body.installmentsEnabled;
                    this.sec.finance.notice = this.installmentsEnabled ? 'اقساط شهریه فعال شد.' : 'اقساط شهریه غیرفعال شد.';
                }
            } catch (e) {
                if (this.isLatest(token)) {
                    this.sec.finance.error = this.errorText(e);
                }
            } finally {
                this.endSave(token);
            }
        },

        // ------------------------------------------------------------------ یادآوری‌ها

        remindersAll() {
            return Logic.isAllRoles(this.reminderRoles);
        },

        async saveReminders() {
            if (this.sec.reminders.saving) {
                return;
            }
            this.remindersFieldError = Logic.rolesError(this.reminderRoles);
            if (this.remindersFieldError) {
                this.sec.reminders.notice = '';
                this.sec.reminders.error = 'فرم را کامل و درست پر کنید.';
                return;
            }
            const token = this.startSave('reminders');
            try {
                const body = await this.api(token.schoolId, 'PUT', '/yearly-plan/reminder-roles',
                    Logic.reminderBody(this.reminderRoles, this.roles));
                if (this.isLatest(token)) {
                    this.roles = body.roles;
                    this.reminderRoles = Logic.knownSelected(body.selected || [], this.roles);
                    this.sec.reminders.notice = 'گیرندگان یادآوری ذخیره شد.';
                }
            } catch (e) {
                if (this.isLatest(token)) {
                    this.sec.reminders.error = this.errorText(e);
                }
            } finally {
                this.endSave(token);
            }
        },

        // ------------------------------------------------------------------ بات بله

        botAll() {
            return Logic.isAllRoles(this.botRoles);
        },

        async saveBot() {
            if (this.sec.bot.saving) {
                return;
            }
            const token = this.startSave('bot');
            try {
                const body = await this.api(token.schoolId, 'PUT', '/settings/bot',
                    Logic.botBody(this.botEnabled, this.botRoles, this.roles));
                if (this.isLatest(token)) {
                    this.botEnabled = !!body.enabled;
                    this.botRoles = Logic.knownSelected(body.roles || [], this.roles);
                    this.sec.bot.notice = 'تنظیمات بات بله ذخیره شد.';
                }
            } catch (e) {
                if (this.isLatest(token)) {
                    this.sec.bot.error = this.errorText(e);
                }
            } finally {
                this.endSave(token);
            }
        }
    }));
});
