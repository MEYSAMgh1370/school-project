document.addEventListener('alpine:init', () => {
    // اندازه‌ی صفحه‌ی فهرست فاکتورها (DESIGN.md §6)
    const PAGE_SIZE = 25;

    Alpine.data('financePage', () => ({
        schoolId: null,
        tuitionFee: null,
        invoices: [],
        // جست‌وجوی نام دانش‌آموز روی فهرست فاکتورها (DESIGN.md §6)
        filterText: '',
        // chip وضعیت پرداخت ('all' یا یکی از وضعیت‌های فاکتور) و تعداد ردیف‌های نمایش‌داده‌شده
        chip: 'all',
        shown: PAGE_SIZE,
        students: [],
        pupils: [],
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
        // تأییدِ صدورِ گروهیِ فاکتور: null یا { year, error }
        confirm: null,
        // تأییدِ «نسبت دادن به سال»: null یا { year, invoices, tuitionFee, error }
        attributeConfirm: null,
        // شهریه و فاکتور به‌تفکیک سال (academic-year.md فاز ۲): سال‌های قابل‌انتخاب و سالِ انتخاب‌شده؛
        // مدرسه‌ای که سالِ جاری ندارد فهرست خالی دارد و مثل پیش از فاز ۲ بدون سال کار می‌کند
        years: [],
        year: '',
        currentYear: null,
        // شهریه/فاکتورهایی که هنوز به سالی نسبت داده نشده‌اند (فقط ادمینِ دارای FINANCE_MANAGE می‌بیند)
        unattributed: { tuitionFee: false, invoices: 0 },
        attributeYear: '',
        attributing: false,
        yearSeq: 0,
        // شمارنده‌ی بارگذاری و پنل پرداخت: فقط پاسخِ آخرین درخواست روی صفحه می‌نشیند (پاسخ دیرِ سالِ قبل یا فاکتورِ
        // قبلی، فهرست یا پنل تازه را بازنویسی نمی‌کند)
        loadSeq: 0,
        paymentsSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.confirm = null;
                this.attributeConfirm = null;
                // داده‌های مدرسه‌ی قبلی تا پاسخِ مدرسه‌ی تازه نمایش داده نمی‌شوند
                this.invoices = [];
                this.students = [];
                this.pupils = [];
                this.payments = [];
                this.expandedInvoiceId = null;
                this.tuitionFee = null;
                // سالِ مدرسه‌ی قبلی برای مدرسه‌ی تازه معنا ندارد
                this.years = [];
                this.year = '';
                this.currentYear = null;
                this.attributeYear = '';
                this.unattributed = { tuitionFee: false, invoices: 0 };
                this.filterText = '';
                this.chip = 'all';
                this.shown = PAGE_SIZE;
                this.load();
            });
            // پنل پرداخت‌های یک فاکتور که جست‌وجو پنهانش کرده باز نمی‌ماند: با هیچ ردیفی قابل بستن نیست
            // با تغییر جست‌وجو یا chip، صفحه‌بندی هم به ۲۵ ردیفِ اول برمی‌گردد
            const onFilterChange = () => {
                this.shown = PAGE_SIZE;
                if (this.expandedInvoiceId !== null
                    && !this.visibleInvoices().some(invoice => invoice.id === this.expandedInvoiceId)) {
                    this.expandedInvoiceId = null;
                }
            };
            this.$watch('filterText', onFilterChange);
            this.$watch('chip', onFilterChange);
            if (this.schoolId) {
                this.load();
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

        // «?academicYear=…» برای سالِ انتخاب‌شده؛ بدون فهرست سال (مدرسه‌ای که سالِ جاری ندارد) خالی
        yearQuery() {
            return this.year ? `?academicYear=${encodeURIComponent(this.year)}` : '';
        },

        studentsBase() {
            return `/api/admin/schools/${this.schoolId}/students`;
        },

        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        // فاکتور فقط studentId (شناسه‌ی academic) دارد، نه نام - همان دلیل
        // teacherName در classes.js: باید از academic.StudentController (id
        // به userId) و iam.MembershipController/memberships/names (userId به
        // نام) ترکیب شود. FINANCE_ADMIN حالا STUDENT_VIEW و MEMBER_NAMES_VIEW
        // را دارد (UX_ROADMAP.md §6.4) پس این resolve معمولاً موفق می‌شود؛ اگر
        // به هر دلیلی موفق نشد، صفحه نمی‌شکند، فقط شناسه‌ی خام نشان داده می‌شود.
        // فهرستِ فیلترشده (جست‌وجو + chip وضعیت) بدون برش؛ شمارنده‌ی «نمایش بیشتر» از همین می‌آید
        filteredInvoices() {
            const query = this.filterText.trim();
            const chip = this.activeChip();
            return this.invoices.filter(invoice => (chip === 'all' || invoice.status === chip)
                && (!query || ListSearch.matches(query, [this.studentName(invoice.studentId)])));
        },

        // ردیف‌های نمایش‌داده‌شده: ۲۵ تا و با هر «نمایش بیشتر» ۲۵ تای دیگر (DESIGN.md §6)
        visibleInvoices() {
            return this.filteredInvoices().slice(0, this.shown);
        },

        hasMoreInvoices() {
            return this.filteredInvoices().length > this.shown;
        },

        // رقم فارسی بدون وابستگی به calendar.js
        moreInvoicesLabel() {
            return (this.filteredInvoices().length - this.shown).toLocaleString('fa-IR-u-nu-arabext');
        },

        showMoreInvoices() {
            this.shown += PAGE_SIZE;
        },

        fa(value) {
            return window.Calendar ? window.Calendar.digits(String(value)) : String(value);
        },

        // chip ها فقط برای وضعیت‌هایی‌اند که در فاکتورهای همین فهرست هست؛ برچسب‌ها همان statusLabel است
        chips() {
            const chips = [{ key: 'all', label: 'همه', count: this.invoices.length }];
            ['PAID', 'PARTIAL', 'UNPAID', 'OVERDUE'].forEach(status => {
                const count = this.invoices.filter(invoice => invoice.status === status).length;
                if (count > 0) {
                    chips.push({ key: status, label: this.statusLabel(status), count });
                }
            });
            return chips;
        },

        // chip ی که با بارگذاری تازه دیگر وضعیتش در فهرست نیست، فهرست را خالی نمی‌کند
        activeChip() {
            return this.invoices.some(invoice => invoice.status === this.chip) ? this.chip : 'all';
        },

        selectChip(key) {
            this.chip = key;
        },

        studentName(studentId) {
            const student = this.students.find(s => s.studentId === studentId);
            if (!student) {
                return 'دانش‌آموز #' + studentId;
            }
            const pupil = this.pupils.find(p => p.userId === student.userId);
            return pupil ? pupil.fullName : 'دانش‌آموز #' + studentId;
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

        remaining(invoice) {
            return invoice.amount - invoice.amountPaid;
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

        async load() {
            const seq = ++this.loadSeq;
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                await this.loadYears();
                if (seq !== this.loadSeq) {
                    return;
                }
                const feeResponse = await fetch(this.tuitionFeeBase() + this.yearQuery(), { headers: { 'X-School-ID': this.schoolId } });
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

                const invoicesResponse = await fetch(this.invoicesBase() + this.yearQuery(), { headers: { 'X-School-ID': this.schoolId } });
                if (seq !== this.loadSeq) {
                    return;
                }
                if (!invoicesResponse.ok) {
                    const classified = await this.classifyError(invoicesResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                const invoices = await invoicesResponse.json();
                if (seq !== this.loadSeq) {
                    return;
                }
                this.invoices = invoices;
                this.loadUnattributed();

                // بهترین تلاش برای نام دانش‌آموز - studentsBase به STUDENT_VIEW
                // نیاز دارد (نه دیگر STUDENT_ENROLL - UX_ROADMAP.md §6.4)، پس
                // FINANCE_ADMIN هم آن را دارد؛ membershipsBase هم‌چنان
                // MEMBERSHIP_MANAGE می‌خواهد که FINANCE_ADMIN ندارد - در آن حالت
                // به مسیر محدودترِ resolveMemberNames (MEMBER_NAMES_VIEW) برمی‌گردیم.
                const [studentsResponse, membershipsResponse] = await Promise.all([
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                const students = studentsResponse.ok ? await studentsResponse.json() : [];
                const pupils = membershipsResponse.ok
                    ? (await membershipsResponse.json()).filter(m => m.active && m.roleCode === 'STUDENT')
                    : await window.MemberNames.resolve(this.schoolId, students.map(s => s.userId));
                if (seq !== this.loadSeq) {
                    return;
                }
                this.students = students;
                this.pupils = pupils;
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

        // سال‌های قابل‌انتخاب (FINANCE_VIEW؛ مدیر مالی ACADEMIC_YEAR_MANAGE ندارد) و سالِ جاری. سالِ جاری پیش‌فرض است
        // و فقط وقتی پر می‌شود که سالی انتخاب نشده باشد؛ بدون فهرست، فیلد سال نمایش داده نمی‌شود.
        async loadYears() {
            const seq = ++this.yearSeq;
            const schoolId = this.schoolId;
            try {
                const response = await fetch(`${this.financeBase()}/years`, { headers: { 'X-School-ID': schoolId } });
                if (!response.ok || seq !== this.yearSeq || schoolId !== this.schoolId) {
                    return;
                }
                const body = await response.json();
                this.currentYear = body.currentYear;
                // مدرسه‌ای که سالِ جاری ندارد بدون سال کار می‌کند: فهرست خالی می‌ماند
                this.years = body.currentYear ? body.years : [];
                if (!this.years.includes(this.year)) {
                    // x-model روی select بعد از رندرِ گزینه‌ها مقدار را می‌گیرد
                    this.year = '';
                    await this.$nextTick();
                    this.year = body.currentYear || '';
                }
            } catch (e) {
                // بدون فهرستِ سال، صفحه مثل پیش از فاز ۲ کار می‌کند
            }
        },

        // شهریه/فاکتورِ بدون سال - فقط برای ادمینِ دارای FINANCE_MANAGE (بقیه 403 می‌گیرند و هشدار نمی‌بینند)
        async loadUnattributed() {
            if (!this.currentYear) {
                this.unattributed = { tuitionFee: false, invoices: 0 };
                return;
            }
            const schoolId = this.schoolId;
            try {
                const response = await fetch(`${this.financeBase()}/unattributed`, { headers: { 'X-School-ID': schoolId } });
                if (response.ok && schoolId === this.schoolId) {
                    this.unattributed = await response.json();
                    // سالِ نسبت‌دادن عمداً پیش‌فرض ندارد: مدیر باید خودش انتخاب کند (پیش‌فرضِ اشتباه، کلِ
                    // اطلاعاتِ قبلی را با یک کلیک به سالِ غلط می‌برد)
                }
            } catch (e) {
                // هشدار اختیاری است
            }
        },

        hasUnattributed() {
            return !!this.currentYear && (this.unattributed.tuitionFee || this.unattributed.invoices > 0);
        },

        async onYearChange() {
            this.chip = 'all';
            this.shown = PAGE_SIZE;
            this.expandedInvoiceId = null;
            this.payments = [];
            this.error = '';
            this.notice = '';
            await this.load();
        },

        // نسبت دادنِ اطلاعاتِ مالیِ قبلی به یک سال برگشت‌پذیر نیست: اول تأیید (modal) گرفته می‌شود
        askAttribute() {
            if (this.attributing) {
                return;
            }
            this.error = '';
            this.notice = '';
            if (!this.attributeYear) {
                this.error = 'یک سال تحصیلی را انتخاب کنید.';
                return;
            }
            this.attributeConfirm = {
                year: this.attributeYear,
                invoices: this.unattributed.invoices,
                tuitionFee: this.unattributed.tuitionFee,
                error: ''
            };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.attributeModal));
        },

        closeAttributeConfirm() {
            this.attributeConfirm = null;
        },

        // اقدامِ صریحِ ادمین: شهریه و فاکتورهای «بدون سال» را به یک سال نسبت می‌دهد
        async attribute() {
            const target = this.attributeConfirm;
            if (!target || this.attributing) {
                return;
            }
            this.attributing = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.financeBase()}/attribute-year`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ academicYear: target.year })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    // خطا داخل modal نشان داده می‌شود (پشتِ لایه‌ی modal دیده نمی‌شد)
                    target.error = classified.message;
                    return;
                }
                const result = await response.json();
                this.attributeConfirm = null;
                this.notice = `اطلاعاتِ مالیِ قبلی به سالِ ${Calendar.digits(target.year)} نسبت داده شد `
                    + `(${Calendar.digits(String(result.invoicesAttributed))} فاکتور`
                    + `${result.tuitionAttributed ? ' و شهریه' : ''}).`;
                await this.load();
            } catch (e) {
                target.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.attributing = false;
            }
        },

        async togglePayments(invoice) {
            const seq = ++this.paymentsSeq;
            if (this.expandedInvoiceId === invoice.id) {
                this.expandedInvoiceId = null;
                this.payments = [];
                this.paymentsLoading = false;
                return;
            }
            this.expandedInvoiceId = invoice.id;
            this.payments = [];
            this.paymentForm = { amount: '', method: 'CASH', note: '' };
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
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                }
            } catch (e) {
                if (seq === this.paymentsSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
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
            this.error = '';
            this.notice = '';
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
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                if (schoolId !== this.schoolId) {
                    // مدرسه عوض شده: پرداخت ثبت شده، ولی فهرستِ مدرسه‌ی تازه را نباید با آن بازسازی کرد
                    return;
                }
                this.notice = 'پرداخت با موفقیت ثبت شد.';
                await this.load();
                // بعد از load، پنل باز را با پرداخت‌های تازه به‌روزرسانی کن
                const refreshed = this.invoices.find(i => i.id === invoice.id);
                this.expandedInvoiceId = null;
                if (refreshed) {
                    await this.togglePayments(refreshed);
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
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
            this.confirm = { year: this.year, error: '' };
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
                const response = await fetch(`${this.invoicesBase()}/backfill${this.yearQuery()}`, {
                    method: 'POST',
                    headers: {
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    // خطا داخل modal نشان داده می‌شود (پشتِ لایه‌ی modal دیده نمی‌شد)
                    target.error = classified.message;
                    return;
                }
                const result = await response.json();
                this.confirm = null;
                this.notice = result.issuedCount > 0
                    ? `${result.issuedCount} فاکتور جدید صادر شد.`
                    : 'همه‌ی دانش‌آموزان برای این سال از قبل فاکتور دارند - چیزی صادر نشد.';
                await this.load();
            } catch (e) {
                target.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.backfilling = false;
            }
        },

        async setTuitionFee() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.tuitionFeeBase() + this.yearQuery(), {
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
