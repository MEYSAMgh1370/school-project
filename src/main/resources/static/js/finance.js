document.addEventListener('alpine:init', () => {
    // اندازه‌ی صفحه‌ی فهرست دانش‌آموزان (سمت سرور؛ سقفِ سرور ۱۰۰ است) و تأخیر جست‌وجو
    const PAGE_SIZE = 50;
    const MAX_SIZE = 100;
    const SEARCH_DELAY = 300;

    Alpine.data('financePage', () => ({
        schoolId: null,
        tuitionFee: null,
        // فهرست دانش‌آموزان (GET /finance/students): ردیف‌ها، شمارنده‌ی دامنه‌ها (زیر همان فیلتر نام/بدهی) و کل نتیجه
        students: [],
        total: 0,
        counts: { all: 0, current: 0, previous: 0 },
        scope: 'all',
        debt: 'any',
        firstName: '',
        lastName: '',
        q: '',
        debtChips: [
            { key: 'any', label: 'همه‌ی وضعیت‌ها' },
            { key: 'owing', label: 'بدهکار' },
            { key: 'overdue', label: 'دیرکرد' },
            { key: 'settled', label: 'تسویه‌شده' }
        ],
        listLoading: false,
        listLoaded: false,
        loadingMore: false,
        searchTimer: null,
        // فاکتورهای دانش‌آموزِ بازشده (همه‌ی سال‌ها، تازه‌ترین سال اول)؛ یک دانش‌آموز در هر لحظه
        expandedStudentId: null,
        studentInvoices: [],
        invoicesLoading: false,
        invoicesError: '',
        blocked: false,
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        form: { amount: '' },
        // پرداخت‌های فاکتورِ باز‌شده (یک فاکتور در هر لحظه، مثل روستر classes.js)
        expandedInvoiceId: null,
        payments: [],
        paymentsLoading: false,
        paymentForm: { amount: '', method: 'CASH', note: '' },
        // یک ثبتِ پرداخت در حال انجام: فرمِ دوباره‌فرستاده‌شده (Enter دوبار) همان مبلغ را دوباره ثبت می‌کرد
        recordingPayment: false,
        backfilling: false,
        // تأییدِ صدورِ گروهیِ فاکتور (همیشه برای سالِ جاریِ مدرسه): null یا { error }
        confirm: null,
        // شمارنده‌ی بارگذاری: فقط پاسخِ آخرین درخواست روی صفحه می‌نشیند (پاسخ دیرِ مدرسه یا فیلترِ قبلی، فهرستِ
        // تازه را بازنویسی نمی‌کند)
        loadSeq: 0,
        listSeq: 0,
        invoicesSeq: 0,
        paymentsSeq: 0,
        discountsSeq: 0,
        installmentsSeq: 0,
        // کلیدِ «اقساط شهریه» (مدرسه‌ای، در /settings تغییر می‌کند): این صفحه فقط می‌خواندش تا فرم‌های اقساط را باز یا بسته کند
        installmentsEnabled: false,
        // تخفیف‌ها و اقساطِ فاکتورِ باز‌شده؛ خطا و اعلانِ پنل داخل خودِ پنل نشان داده می‌شود (بالای صفحه دور از چشم است)
        discounts: [],
        discountsLoading: false,
        discountForm: { amount: '', reason: '', conditional: false, deadline: '' },
        discounting: false,
        installments: [],
        installmentsLoading: false,
        planForm: { count: '', firstDue: '', interval: '1', error: '' },
        planSaving: false,
        // حالتِ «ویرایش مبالغ و تاریخ‌ها»: ردیف‌های اقساطِ ویرایش‌پذیر
        editing: false,
        editRows: [],
        panelError: '',
        panelNotice: '',

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.confirm = null;
                // داده‌های مدرسه‌ی قبلی تا پاسخِ مدرسه‌ی تازه نمایش داده نمی‌شوند
                this.cancelSearchTimer();
                this.students = [];
                this.total = 0;
                this.counts = { all: 0, current: 0, previous: 0 };
                this.listLoaded = false;
                this.payments = [];
                this.collapseStudent();
                this.installmentsEnabled = false;
                this.tuitionFee = null;
                this.scope = 'all';
                this.debt = 'any';
                this.firstName = '';
                this.lastName = '';
                this.q = '';
                this.load();
            });
            // جست‌وجوی نام سمت سرور است: با ۳۰۰ میلی‌ثانیه تأخیر (هر حرف یک درخواست نباشد) از صفحه‌ی اول
            const onSearchChange = () => {
                this.cancelSearchTimer();
                this.searchTimer = setTimeout(() => {
                    this.searchTimer = null;
                    this.loadStudents();
                }, SEARCH_DELAY);
            };
            this.$watch('firstName', onSearchChange);
            this.$watch('lastName', onSearchChange);
            this.$watch('q', onSearchChange);
            if (this.schoolId) {
                this.load();
            }
        },

        cancelSearchTimer() {
            if (this.searchTimer !== null) {
                clearTimeout(this.searchTimer);
                this.searchTimer = null;
            }
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        tuitionFeeBase() {
            return `/api/admin/schools/${this.schoolId}/tuition-fee`;
        },

        invoicesBase() {
            return `/api/admin/schools/${this.schoolId}/invoices`;
        },

        financeBase() {
            return `/api/admin/schools/${this.schoolId}/finance`;
        },

        // ------------------------------------------------------------ فهرست دانش‌آموزان

        fa(value) {
            return window.Calendar ? window.Calendar.digits(String(value)) : String(value);
        },

        hasFilters() {
            return this.scope !== 'all' || this.debt !== 'any'
                || [this.firstName, this.lastName, this.q].some(text => text.trim() !== '');
        },

        selectScope(key) {
            if (this.scope !== key) {
                this.scope = key;
                this.cancelSearchTimer();
                this.loadStudents();
            }
        },

        selectDebt(key) {
            if (this.debt !== key) {
                this.debt = key;
                this.cancelSearchTimer();
                this.loadStudents();
            }
        },

        clearFilters() {
            this.cancelSearchTimer();
            this.scope = 'all';
            this.debt = 'any';
            this.firstName = '';
            this.lastName = '';
            this.q = '';
            this.$nextTick(() => {
                this.cancelSearchTimer();
                this.loadStudents();
            });
        },

        studentsQuery(page, size) {
            return FinanceLogic.buildStudentsQuery({
                scope: this.scope, firstName: this.firstName, lastName: this.lastName, q: this.q, debt: this.debt, page, size
            });
        },

        // صفحه‌ی اول با فیلترهای فعلی؛ ردیف بازشده بسته می‌شود (ممکن است دیگر در فهرست نباشد)
        async loadStudents() {
            this.collapseStudent();
            await this.fetchStudents(0, PAGE_SIZE, false);
        },

        // بعد از پرداخت/تخفیف/اقساط: همان تعداد ردیفِ بارگذاری‌شده (تا سقف سرور) تازه می‌شود، بدون بستن ردیف باز
        async refreshStudents() {
            const loaded = Math.max(this.students.length, PAGE_SIZE);
            await this.fetchStudents(0, Math.min(MAX_SIZE, Math.ceil(loaded / PAGE_SIZE) * PAGE_SIZE), false);
        },

        hasMoreStudents() {
            return this.students.length < this.total;
        },

        moreStudentsLabel() {
            return this.fa(this.total - this.students.length);
        },

        async loadMoreStudents() {
            if (this.loadingMore || this.listLoading) {
                return;
            }
            // ردیف‌ها همیشه مضربی از اندازه‌ی صفحه‌اند، پس شماره‌ی صفحه‌ی بعد همین است
            await this.fetchStudents(Math.floor(this.students.length / PAGE_SIZE), PAGE_SIZE, true);
        },

        async fetchStudents(page, size, append) {
            const seq = ++this.listSeq;
            const schoolId = this.schoolId;
            if (append) {
                this.loadingMore = true;
            } else {
                this.listLoading = true;
                this.loadingMore = false;
            }
            try {
                const response = await fetch(`${this.financeBase()}/students?${this.studentsQuery(page, size)}`,
                    { headers: { 'X-School-ID': schoolId } });
                if (seq !== this.listSeq || schoolId !== this.schoolId) {
                    return;
                }
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                const body = await response.json();
                if (seq !== this.listSeq || schoolId !== this.schoolId) {
                    return;
                }
                this.error = '';
                this.students = append ? this.students.concat(body.items) : body.items;
                this.total = body.total;
                this.counts = body.counts;
                this.listLoaded = true;
            } catch (e) {
                if (seq === this.listSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.listSeq) {
                    this.listLoading = false;
                    this.loadingMore = false;
                }
            }
        },

        // برچسبِ وضعیتِ خودِ دانش‌آموز؛ فعال برچسب ندارد (فقط وضعیتِ غیرمعمول دیده شود)
        studentStatusLabel(student) {
            return {
                PENDING: 'در انتظار جا دادن در کلاس',
                GRADUATED: 'فارغ‌التحصیل',
                LEFT: 'ترک تحصیل'
            }[student.studentStatus] || '';
        },

        // ------------------------------------------------------------ فاکتورهای یک دانش‌آموز

        collapseStudent() {
            this.invoicesSeq++;
            this.paymentsSeq++;
            this.discountsSeq++;
            this.installmentsSeq++;
            this.expandedStudentId = null;
            this.studentInvoices = [];
            this.invoicesLoading = false;
            this.invoicesError = '';
            this.expandedInvoiceId = null;
            this.payments = [];
            this.paymentsLoading = false;
            this.resetPanelExtras();
        },

        async toggleStudent(student) {
            if (this.expandedStudentId === student.studentId) {
                this.collapseStudent();
                return;
            }
            this.collapseStudent();
            this.expandedStudentId = student.studentId;
            await this.loadStudentInvoices(student.studentId);
            // دانش‌آموزِ دارای یک فاکتور: پنلِ همان فاکتور مستقیم باز می‌شود
            if (this.expandedStudentId === student.studentId && this.studentInvoices.length === 1) {
                await this.togglePayments(this.studentInvoices[0]);
            }
        },

        async loadStudentInvoices(studentId) {
            const seq = ++this.invoicesSeq;
            const schoolId = this.schoolId;
            this.invoicesLoading = true;
            this.invoicesError = '';
            try {
                const response = await fetch(`${this.financeBase()}/students/${studentId}/invoices`,
                    { headers: { 'X-School-ID': schoolId } });
                if (seq !== this.invoicesSeq || schoolId !== this.schoolId) {
                    return;
                }
                if (response.ok) {
                    this.studentInvoices = await response.json();
                } else {
                    this.invoicesError = (await this.classifyError(response)).message;
                }
            } catch (e) {
                if (seq === this.invoicesSeq) {
                    this.invoicesError = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.invoicesSeq) {
                    this.invoicesLoading = false;
                }
            }
        },

        // وضعیت مشتق‌شده‌ی فاکتور (InvoiceStatus سمت سرور - هرگز persist نمی‌شود)
        statusLabel(status) {
            return {
                PAID: 'تسویه‌شده',
                PARTIAL: 'پرداخت جزئی',
                UNPAID: 'پرداخت‌نشده',
                OVERDUE: 'دیرکرد'
            }[status] || status;
        },

        statusClass(status) {
            return {
                PAID: 'is-success',
                PARTIAL: 'is-info',
                UNPAID: 'is-warning',
                OVERDUE: 'is-danger'
            }[status] || '';
        },

        methodLabel(method) {
            return {
                CASH: 'نقدی',
                CARD: 'کارت',
                BANK_TRANSFER: 'انتقال بانکی',
                CHEQUE: 'چک',
                OTHER: 'سایر'
            }[method] || method;
        },

        // مانده نسبت به مبلغِ قابل‌پرداخت (مبلغ - تخفیف) است، نه خودِ مبلغ
        remaining(invoice) {
            return FinanceLogic.remaining(invoice);
        },

        payable(invoice) {
            return FinanceLogic.payable(invoice);
        },

        // ------------------------------------------------------------ تنظیم اقساط، تخفیف و اقساط

        async jsonRequest(url, method, payload) {
            return fetch(url, {
                method,
                headers: {
                    'Content-Type': 'application/json',
                    'X-School-ID': this.schoolId,
                    'X-XSRF-TOKEN': this.csrfToken()
                },
                body: JSON.stringify(payload)
            });
        },

        // کلیدِ «اقساط شهریه» (FINANCE_VIEW می‌خواند؛ تغییرش در /settings است). اگر خواندن نشد اقساط خاموش فرض می‌شود
        async loadSettings() {
            const schoolId = this.schoolId;
            try {
                const response = await fetch(`${this.financeBase()}/settings`, { headers: { 'X-School-ID': schoolId } });
                if (schoolId !== this.schoolId) {
                    return;
                }
                if (response.ok) {
                    this.installmentsEnabled = !!(await response.json()).installmentsEnabled;
                }
            } catch (e) {
                // اختیاری است؛ بدون آن فرم‌های اقساط بسته می‌مانند
            }
        },

        invoiceUrl(invoice, suffix) {
            return `${this.invoicesBase()}/${invoice.id}/${suffix}`;
        },

        // پنلِ فاکتور: تخفیف‌ها و اقساط با شمارنده‌ی خودشان بارگذاری می‌شوند (مثل paymentsSeq)
        resetPanelExtras() {
            this.discounts = [];
            this.installments = [];
            this.discountsLoading = false;
            this.installmentsLoading = false;
            this.discountForm = this.emptyDiscountForm();
            this.planForm = this.emptyPlanForm();
            this.editing = false;
            this.editRows = [];
            this.panelError = '';
            this.panelNotice = '';
        },

        emptyDiscountForm() {
            return { amount: '', reason: '', conditional: false, deadline: '' };
        },

        // تخفیف مشروط فقط با تاریخِ معتبر قابل ثبت است
        discountDeadlineValid() {
            return !this.discountForm.conditional || DateInput.parse(this.discountForm.deadline) !== null;
        },

        pendingDiscounts() {
            return FinanceLogic.pendingDiscounts(this.discounts);
        },

        discountStatusLabel(discount) {
            return FinanceLogic.discountStatusLabel(discount,
                discount.deadline ? Calendar.formatDate(discount.deadline) : '');
        },

        discountStatusClass(discount) {
            return FinanceLogic.discountStatusClass(discount);
        },

        emptyPlanForm() {
            return { count: '', firstDue: '', interval: '1', error: '' };
        },

        async loadDiscounts(invoice) {
            const seq = ++this.discountsSeq;
            this.discountsLoading = true;
            try {
                const response = await fetch(this.invoiceUrl(invoice, 'discounts'), { headers: { 'X-School-ID': this.schoolId } });
                if (seq !== this.discountsSeq || this.expandedInvoiceId !== invoice.id) {
                    return;
                }
                if (response.ok) {
                    this.discounts = await response.json();
                } else {
                    this.panelError = (await this.classifyError(response)).message;
                }
            } catch (e) {
                if (seq === this.discountsSeq) {
                    this.panelError = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.discountsSeq) {
                    this.discountsLoading = false;
                }
            }
        },

        async loadInstallments(invoice) {
            const seq = ++this.installmentsSeq;
            this.installmentsLoading = true;
            try {
                const response = await fetch(this.invoiceUrl(invoice, 'installments'), { headers: { 'X-School-ID': this.schoolId } });
                if (seq !== this.installmentsSeq || this.expandedInvoiceId !== invoice.id) {
                    return;
                }
                if (response.ok) {
                    this.installments = await response.json();
                } else {
                    this.panelError = (await this.classifyError(response)).message;
                }
            } catch (e) {
                if (seq === this.installmentsSeq) {
                    this.panelError = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.installmentsSeq) {
                    this.installmentsLoading = false;
                }
            }
        },

        // بعد از پرداخت/تخفیف/اقساط: فاکتورهای دانش‌آموزِ بازشده (مبلغ قابل‌پرداخت، وضعیت و سررسید عوض می‌شود) و
        // مجموع‌های ردیف‌های فهرست تازه می‌شوند؛ پنل باز می‌ماند
        async refreshInvoices() {
            const schoolId = this.schoolId;
            const studentId = this.expandedStudentId;
            try {
                if (studentId !== null) {
                    const response = await fetch(`${this.financeBase()}/students/${studentId}/invoices`,
                        { headers: { 'X-School-ID': schoolId } });
                    if (response.ok && schoolId === this.schoolId && studentId === this.expandedStudentId) {
                        this.studentInvoices = await response.json();
                    }
                }
            } catch (e) {
                // پنل در بازشدنِ بعدی به‌روز می‌شود
            }
            await this.refreshStudents();
        },

        async applyDiscount(invoice) {
            if (this.discounting) {
                return;
            }
            const amount = Money.normalizeAmount(this.discountForm.amount);
            this.panelError = '';
            this.panelNotice = '';
            if (FinanceLogic.cents(amount) === null || FinanceLogic.cents(amount) <= 0) {
                this.panelError = 'مبلغ تخفیف را به‌صورت عددِ مثبت بنویسید.';
                return;
            }
            const body = { amount, reason: this.discountForm.reason.trim() || null };
            if (this.discountForm.conditional) {
                const deadline = DateInput.parse(this.discountForm.deadline);
                if (deadline === null) {
                    this.panelError = 'تاریخ تسویه را به شکل سال/ماه/روز بنویسید، مثل ' + this.exampleDate() + '.';
                    return;
                }
                body.deadline = deadline;
            }
            const schoolId = this.schoolId;
            this.discounting = true;
            try {
                const response = await this.jsonRequest(this.invoiceUrl(invoice, 'discounts'), 'POST', body);
                if (!response.ok) {
                    this.panelError = (await this.classifyError(response)).message;
                    return;
                }
                if (schoolId !== this.schoolId || this.expandedInvoiceId !== invoice.id) {
                    return;
                }
                this.discountForm = this.emptyDiscountForm();
                this.panelNotice = 'تخفیف ثبت شد.';
                // تخفیف اقساطِ پرداخت‌نشده را دوباره تقسیم می‌کند
                await Promise.all([this.refreshInvoices(), this.loadDiscounts(invoice), this.loadInstallments(invoice)]);
            } catch (e) {
                this.panelError = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.discounting = false;
            }
        },

        // تعریفِ اقساط و «تغییر تعداد» یک فرم و یک endpoint اند؛ سرور فقط اقساطِ پرداخت‌نشده را جایگزین می‌کند
        async generateInstallments(invoice) {
            if (this.planSaving) {
                return;
            }
            const form = this.planForm;
            form.error = '';
            this.panelError = '';
            this.panelNotice = '';
            const count = parseInt(Digits.toAscii(form.count), 10);
            const interval = parseInt(Digits.toAscii(form.interval), 10);
            const firstDue = DateInput.parse(form.firstDue);
            // شمارش ۰ فقط وقتی مجاز است که مانده‌ای برای تقسیم نباشد (سرور تصمیم می‌گیرد)
            if (!Number.isInteger(count) || count < 0) {
                form.error = 'تعداد اقساط را به‌صورت عدد بنویسید.';
                return;
            }
            if (!Number.isInteger(interval) || interval < 1 || interval > 12) {
                form.error = 'فاصله‌ی اقساط باید بین ۱ تا ۱۲ ماه باشد.';
                return;
            }
            if (count > 0 && firstDue === null) {
                form.error = 'تاریخ سررسید قسط اول را به شکل سال/ماه/روز بنویسید، مثل ' + this.exampleDate() + '.';
                return;
            }
            const schoolId = this.schoolId;
            this.planSaving = true;
            try {
                const response = await this.jsonRequest(this.invoiceUrl(invoice, 'installments/generate'), 'POST',
                    { count, firstDueDate: firstDue, intervalMonths: interval });
                if (!response.ok) {
                    this.panelError = (await this.classifyError(response)).message;
                    return;
                }
                if (schoolId !== this.schoolId || this.expandedInvoiceId !== invoice.id) {
                    return;
                }
                this.planForm = this.emptyPlanForm();
                this.editing = false;
                this.panelNotice = 'اقساط ثبت شد.';
                await Promise.all([this.refreshInvoices(), this.loadInstallments(invoice)]);
            } catch (e) {
                this.panelError = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.planSaving = false;
            }
        },

        // تعدادِ اقساط: فقط رقمِ ASCII (ارقام فارسی/عربی تبدیل می‌شوند)
        digitsOnly(event) {
            const value = Digits.toAscii(event.target.value).replace(/\D/g, '');
            event.target.value = value;
            return value;
        },

        editRow(item) {
            return this.editRows.find(row => row.id === item.id);
        },

        exampleDate() {
            return DateInput.format(DateInput.today());
        },

        // ردیف‌های ویرایش‌پذیر (اقساطِ کاملاً پرداخت‌شده هرگز)
        startEditing() {
            this.panelError = '';
            this.panelNotice = '';
            this.editRows = this.installments.filter(i => FinanceLogic.isEditable(i)).map(i => ({
                id: i.id,
                seq: i.seq,
                amount: String(i.amount),
                // قسطِ بدون سررسید ورودیِ خالی دارد و خالی ماندنش مجاز است
                undated: FinanceLogic.isUndated(i),
                dueDate: FinanceLogic.editDueText(i, DateInput.format),
                dateError: ''
            }));
            this.editing = true;
        },

        cancelEditing() {
            this.editing = false;
            this.editRows = [];
            this.panelError = '';
        },

        editedSum() {
            return FinanceLogic.editedSum(this.editRows);
        },

        expectedSum(invoice) {
            return FinanceLogic.expectedSum(invoice, this.installments);
        },

        canSaveEdit(invoice) {
            return FinanceLogic.sumMatches(this.editRows, this.expectedSum(invoice))
                && this.editRows.every(row => FinanceLogic.resolveDueDate(row, DateInput.parse).ok);
        },

        async saveEditing(invoice) {
            if (this.planSaving) {
                return;
            }
            this.panelError = '';
            this.panelNotice = '';
            let invalid = false;
            const items = this.editRows.map(row => {
                const resolved = FinanceLogic.resolveDueDate(row, DateInput.parse);
                row.dateError = resolved.ok ? '' : 'تاریخ را به شکل سال/ماه/روز بنویسید.';
                invalid = invalid || !resolved.ok;
                return { id: row.id, amount: Money.normalizeAmount(row.amount), dueDate: resolved.value };
            });
            if (invalid || !this.canSaveEdit(invoice)) {
                this.panelError = 'مبلغ‌ها و تاریخ‌ها را کامل و درست پر کنید.';
                return;
            }
            const schoolId = this.schoolId;
            this.planSaving = true;
            try {
                const response = await this.jsonRequest(this.invoiceUrl(invoice, 'installments'), 'PUT', { items });
                if (!response.ok) {
                    this.panelError = (await this.classifyError(response)).message;
                    return;
                }
                if (schoolId !== this.schoolId || this.expandedInvoiceId !== invoice.id) {
                    return;
                }
                this.editing = false;
                this.editRows = [];
                this.panelNotice = 'اقساط ذخیره شد.';
                await Promise.all([this.refreshInvoices(), this.loadInstallments(invoice)]);
            } catch (e) {
                this.panelError = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.planSaving = false;
            }
        },

        // نوع خطای دولایه‌ی §5 را از URI فیلد type در ProblemDetail تشخیص
        // می‌دهد تا «ماژول فعال نیست» را از «نقش شما اجازه ندارد» جدا کند
        // (UX_ROADMAP.md §3، اولین صفحه‌ای که این تفکیک لازم است).
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

        // شهریه‌ی سالِ جاری (بدون آرگومانِ سال: سرور سالِ جاریِ مدرسه را برمی‌دارد)، کلید اقساط و صفحه‌ی اولِ فهرست
        async load() {
            const seq = ++this.loadSeq;
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                const feeResponse = await fetch(this.tuitionFeeBase(), { headers: { 'X-School-ID': this.schoolId } });
                if (seq !== this.loadSeq) {
                    return;
                }
                if (feeResponse.status === 404) {
                    this.tuitionFee = null;
                } else if (!feeResponse.ok) {
                    const classified = await this.classifyError(feeResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                } else {
                    this.tuitionFee = (await feeResponse.json()).amount;
                }
                this.loadSettings();
                await this.loadStudents();
            } catch (e) {
                if (seq === this.loadSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.loadSeq) {
                    this.loading = false;
                }
            }
        },

        async togglePayments(invoice) {
            const seq = ++this.paymentsSeq;
            // پاسخ‌های دیرِ تخفیف/اقساطِ پنلِ قبلی هم دور ریخته شوند
            this.discountsSeq++;
            this.installmentsSeq++;
            this.resetPanelExtras();
            if (this.expandedInvoiceId === invoice.id) {
                this.expandedInvoiceId = null;
                this.payments = [];
                this.paymentsLoading = false;
                return;
            }
            this.expandedInvoiceId = invoice.id;
            this.payments = [];
            this.paymentForm = { amount: '', method: 'CASH', note: '' };
            // تخفیف‌ها و اقساط تنبل و موازی با پرداخت‌ها بارگذاری می‌شوند
            this.loadDiscounts(invoice);
            this.loadInstallments(invoice);
            await this.loadPayments(invoice, seq);
        },

        // پرداخت‌های فاکتورِ باز؛ خطا داخل خودِ پنل نشان داده می‌شود
        async loadPayments(invoice, seq = ++this.paymentsSeq) {
            this.paymentsLoading = true;
            try {
                const response = await fetch(`${this.invoicesBase()}/${invoice.id}/payments`,
                    { headers: { 'X-School-ID': this.schoolId } });
                // پاسخِ پنلِ فاکتورِ دیگری که کاربر بعداً باز کرده، یا پنلِ بسته‌شده، دور ریخته می‌شود
                if (seq !== this.paymentsSeq || this.expandedInvoiceId !== invoice.id) {
                    return;
                }
                if (response.ok) {
                    this.payments = await response.json();
                } else {
                    this.panelError = (await this.classifyError(response)).message;
                }
            } catch (e) {
                if (seq === this.paymentsSeq) {
                    this.panelError = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.paymentsSeq) {
                    this.paymentsLoading = false;
                }
            }
        },

        async recordPayment(invoice) {
            if (this.recordingPayment) {
                return;
            }
            // مدرسه همان لحظه‌ی شروع ثابت می‌شود: تغییر مدرسه وسط درخواست، پرداخت را به مدرسه‌ی دیگر نمی‌برد
            const schoolId = this.schoolId;
            this.recordingPayment = true;
            this.panelError = '';
            this.panelNotice = '';
            try {
                const response = await fetch(`/api/admin/schools/${schoolId}/invoices/${invoice.id}/payments`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        amount: Money.normalizeAmount(this.paymentForm.amount),
                        method: this.paymentForm.method,
                        note: this.paymentForm.note || null
                    })
                });
                if (!response.ok) {
                    this.panelError = (await this.classifyError(response)).message;
                    return;
                }
                if (schoolId !== this.schoolId || this.expandedInvoiceId !== invoice.id) {
                    // مدرسه یا پنل عوض شده: پرداخت ثبت شده، ولی صفحه‌ی تازه را نباید با آن بازسازی کرد
                    return;
                }
                this.paymentForm = { amount: '', method: 'CASH', note: '' };
                this.panelNotice = 'پرداخت با موفقیت ثبت شد.';
                await Promise.all([this.refreshInvoices(), this.loadPayments(invoice)]);
            } catch (e) {
                this.panelError = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.recordingPayment = false;
            }
        },

        // صدور فاکتور برای دانش‌آموزانی که فاکتوری ندارند (مثلاً قبل از تنظیم
        // شهریه ثبت‌نام شده‌اند) - backfill سمت سرور idempotent است
        askBackfill() {
            if (this.backfilling) {
                return;
            }
            this.error = '';
            this.notice = '';
            this.confirm = { error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
        },

        async backfill() {
            const target = this.confirm;
            if (!target || this.backfilling) {
                return;
            }
            this.backfilling = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.invoicesBase()}/backfill`, {
                    method: 'POST',
                    headers: {
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    // خطا داخل modal نشان داده می‌شود (پشتِ لایه‌ی modal دیده نمی‌شد)
                    if (this.confirm === target) {
                        target.error = classified.message;
                    } else {
                        this.error = classified.message;
                    }
                    return;
                }
                const result = await response.json();
                this.confirm = null;
                this.notice = result.issuedCount > 0
                    ? `${result.issuedCount} فاکتور جدید صادر شد.`
                    : 'همه‌ی دانش‌آموزان برای این سال از قبل فاکتور دارند - چیزی صادر نشد.';
                await this.load();
            } catch (e) {
                if (this.confirm === target) {
                    target.error = 'ارتباط با سرور برقرار نشد.';
                } else {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                this.backfilling = false;
            }
        },

        async setTuitionFee() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.tuitionFeeBase(), {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ amount: Money.normalizeAmount(this.form.amount) })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                this.notice = 'شهریه با موفقیت ثبت شد.';
                this.form.amount = '';
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
