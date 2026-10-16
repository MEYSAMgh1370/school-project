// صفحه‌ی «سابقه‌ی تغییرات مالی»: فقط‌خواندنی. فهرستِ صفحه‌بندی‌شده‌ی سمتِ سرور از
// GET /api/admin/schools/{id}/finance/audit (ماژول FINANCE + مجوز FINANCE_MANAGE)؛ فیلترها روی سیم ISO و ASCII اند.
// منطقِ خالص (برچسب‌ها، خلاصه، اعتبارسنجی، query) در finance-audit-logic.js است.
//
// وابستگی: digits.js، jalali.js، calendar.js، money.js، date-input.js و finance-audit-logic.js پیش از این فایل.
document.addEventListener('alpine:init', () => {
    // اندازه‌ی صفحه (قرارداد API: پیش‌فرض ۵۰، بیشینه ۱۰۰)
    const PAGE_SIZE = 50;

    const emptyForm = () => ({ action: '', from: '', to: '', invoiceId: '' });
    const noErrors = () => ({ invoiceId: '', from: '', to: '' });

    Alpine.data('financeAuditPage', () => ({
        schoolId: null,
        blocked: false,
        loading: false,
        error: '',
        items: [],
        totalItems: 0,
        // صفحه‌ی جاری بر مبنای صفر (مثل API)؛ نمایش یک‌مبناست
        page: 0,
        // شمارنده‌ی بارگذاری: فقط پاسخِ آخرین درخواست روی صفحه می‌نشیند
        loadSeq: 0,
        // مقدارِ فیلدهای فرم (متنِ تایپ‌شده) و فیلترِ اعمال‌شده‌ی روی سیم (که صفحه‌بندی از آن می‌خواند)
        form: emptyForm(),
        studentId: '',
        errors: noErrors(),
        applied: {},
        // ردیف‌های بازشده‌ی جزئیات (شناسه‌ی ردیف)
        expanded: [],

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            // لینکِ «سابقه‌ی این فاکتور»: ?invoiceId=… (و ?studentId=…) فیلتر را پیش‌پر می‌کند
            const initial = FinanceAuditLogic.parseInitialQuery(window.location.search, Digits.toAscii);
            this.form.invoiceId = initial.invoiceId;
            this.studentId = initial.studentId;
            this.applied = this.currentFilters(initial.invoiceId, initial.studentId);
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                // شماره‌ی فاکتور و دانش‌آموز مالِ مدرسه‌ی قبلی بود
                this.resetFilters();
                this.items = [];
                this.totalItems = 0;
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        fa(value) {
            return window.Calendar ? window.Calendar.digits(String(value)) : String(value);
        },

        auditBase() {
            return `/api/admin/schools/${this.schoolId}/finance/audit`;
        },

        exampleDate() {
            return DateInput.format(DateInput.today());
        },

        currentFilters(invoiceId, studentId) {
            return { invoiceId, studentId, action: '', from: '', to: '' };
        },

        // فرمتِ نمایشی که منطقِ خالص می‌گیرد: ارقام فارسی، مبلغ و تاریخِ جلالی
        fmt() {
            return {
                amount: (value) => Money.formatAmount(value),
                date: (iso) => Calendar.formatDate(iso),
                number: (value) => this.fa(value)
            };
        },

        // نوعِ تغییر برای گزینه‌های فیلتر
        actionOptions() {
            return FinanceAuditLogic.actionOptions();
        },

        actionLabel(item) {
            return FinanceAuditLogic.actionLabel(item.action);
        },

        actor(item) {
            return FinanceAuditLogic.actorLabel(item);
        },

        summary(item) {
            return FinanceAuditLogic.summarize(item, this.fmt());
        },

        detailEntries(item) {
            return FinanceAuditLogic.detailEntries(item, this.fmt());
        },

        hasDetails(item) {
            return FinanceAuditLogic.hasDetails(item, this.fmt());
        },

        tables(item) {
            return FinanceAuditLogic.installmentTables(item);
        },

        dueLabel(row) {
            return FinanceAuditLogic.dueLabel(row, (iso) => Calendar.formatDate(iso));
        },

        // لحظه‌ی رخداد به وقتِ محلیِ مرورگر؛ ISO instant روی سیم، جلالی و ارقام فارسی در نمایش
        occurred(item) {
            const date = new Date(item.occurredAt);
            return Number.isNaN(date.getTime()) ? null : date;
        },

        whenDate(item) {
            const date = this.occurred(item);
            return date ? Calendar.formatDate(date) : '—';
        },

        whenTime(item) {
            const date = this.occurred(item);
            return date ? FinanceAuditLogic.clock(date, (text) => Calendar.digits(text)) : '';
        },

        amountText(item) {
            return item.amount == null ? '—' : Money.formatAmount(item.amount);
        },

        pageCount() {
            return FinanceAuditLogic.pageCount(this.totalItems, PAGE_SIZE);
        },

        // «۱ تا ۵۰ از ۱۲۰ مورد»
        rangeText() {
            if (this.totalItems === 0) {
                return '';
            }
            const first = this.page * PAGE_SIZE + 1;
            const last = Math.min(this.totalItems, first + this.items.length - 1);
            return this.fa(first) + ' تا ' + this.fa(last) + ' از ' + this.fa(this.totalItems) + ' مورد';
        },

        isExpanded(item) {
            return this.expanded.includes(item.id);
        },

        toggle(item) {
            this.expanded = this.isExpanded(item)
                ? this.expanded.filter(id => id !== item.id)
                : [...this.expanded, item.id];
        },

        resetFilters() {
            this.form = emptyForm();
            this.studentId = '';
            this.errors = noErrors();
            this.applied = this.currentFilters('', '');
            this.page = 0;
            this.expanded = [];
        },

        // «اعمال فیلتر»: فیلدها اعتبارسنجی می‌شوند و خطا کنارِ همان فیلد (و در ناحیه‌ی پیام) می‌آید
        apply() {
            const result = FinanceAuditLogic.validateFilters(this.form, {
                toAscii: Digits.toAscii,
                parseDate: DateInput.parse,
                example: this.exampleDate()
            });
            this.errors = result.errors;
            if (!result.ok) {
                this.error = 'فیلترها را اصلاح کنید: ' + [result.errors.invoiceId, result.errors.from, result.errors.to]
                    .filter(Boolean).join(' ');
                return;
            }
            this.error = '';
            this.applied = { ...result.value, studentId: this.studentId };
            this.page = 0;
            return this.load();
        },

        // «پاک کردن فیلترها»: به فهرستِ کامل برمی‌گردد و query string صفحه را هم کنار می‌گذارد
        clear() {
            this.resetFilters();
            this.error = '';
            window.history.replaceState(null, '', window.location.pathname);
            return this.load();
        },

        clearStudent() {
            this.studentId = '';
            this.applied = { ...this.applied, studentId: '' };
            this.page = 0;
            return this.load();
        },

        goTo(page) {
            if (page < 0 || page >= this.pageCount() || this.loading) {
                return;
            }
            this.page = page;
            return this.load();
        },

        // نوعِ خطای دولایه‌ی §5 را از URI فیلد type در ProblemDetail تشخیص
        // می‌دهد تا «ماژول فعال نیست» را از «نقش شما اجازه ندارد» جدا کند
        // (UX_ROADMAP.md §3، همان الگوی finance.js).
        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول مالی را نخریده است. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دسترسی به بخش مالی را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        async load() {
            if (!this.schoolId) {
                return;
            }
            const seq = ++this.loadSeq;
            const schoolId = this.schoolId;
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                const query = FinanceAuditLogic.buildQuery(this.applied, this.page, PAGE_SIZE);
                const response = await fetch(`${this.auditBase()}?${query}`, { headers: { 'X-School-ID': schoolId } });
                if (seq !== this.loadSeq || schoolId !== this.schoolId) {
                    return;
                }
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    if (seq !== this.loadSeq) {
                        return;
                    }
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    this.items = [];
                    this.totalItems = 0;
                    return;
                }
                const body = await response.json();
                if (seq !== this.loadSeq || schoolId !== this.schoolId) {
                    return;
                }
                this.items = body.items || [];
                this.totalItems = body.totalItems || 0;
                this.expanded = [];
            } catch (e) {
                if (seq === this.loadSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.loadSeq) {
                    this.loading = false;
                }
            }
        }
    }));
});
