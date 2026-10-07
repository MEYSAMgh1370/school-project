document.addEventListener('alpine:init', () => {
    Alpine.data('financePage', () => ({
        schoolId: null,
        tuitionFee: null,
        invoices: [],
        // جست‌وجوی نام دانش‌آموز روی فهرست فاکتورها (DESIGN.md §6)
        filterText: '',
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
        backfilling: false,
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

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                // سالِ مدرسه‌ی قبلی برای مدرسه‌ی تازه معنا ندارد
                this.years = [];
                this.year = '';
                this.currentYear = null;
                this.attributeYear = '';
                this.unattributed = { tuitionFee: false, invoices: 0 };
                this.filterText = '';
                this.load();
            });
            // پنل پرداخت‌های یک فاکتور که جست‌وجو پنهانش کرده باز نمی‌ماند: با هیچ ردیفی قابل بستن نیست
            this.$watch('filterText', () => {
                if (this.expandedInvoiceId !== null
                    && !this.visibleInvoices().some(invoice => invoice.id === this.expandedInvoiceId)) {
                    this.expandedInvoiceId = null;
                }
            });
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
        visibleInvoices() {
            const query = this.filterText.trim();
            if (!query) {
                return this.invoices;
            }
            return this.invoices.filter(invoice => ListSearch.matches(query, [this.studentName(invoice.studentId)]));
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
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                await this.loadYears();
                const feeResponse = await fetch(this.tuitionFeeBase() + this.yearQuery(), { headers: { 'X-School-ID': this.schoolId } });
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
                if (!invoicesResponse.ok) {
                    const classified = await this.classifyError(invoicesResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.invoices = await invoicesResponse.json();
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
                this.students = studentsResponse.ok ? await studentsResponse.json() : [];
                if (membershipsResponse.ok) {
                    this.pupils = (await membershipsResponse.json()).filter(m => m.active && m.roleCode === 'STUDENT');
                } else {
                    this.pupils = await window.MemberNames.resolve(
                        this.schoolId,
                        this.students.map(s => s.userId)
                    );
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
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
            this.expandedInvoiceId = null;
            this.payments = [];
            this.error = '';
            this.notice = '';
            await this.load();
        },

        // اقدامِ صریحِ ادمین: شهریه و فاکتورهای «بدون سال» را به یک سال نسبت می‌دهد
        async attribute() {
            if (!this.attributeYear) {
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
                    body: JSON.stringify({ academicYear: this.attributeYear })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                const result = await response.json();
                this.notice = `اطلاعاتِ مالیِ قبلی به سالِ ${Calendar.digits(this.attributeYear)} نسبت داده شد `
                    + `(${Calendar.digits(String(result.invoicesAttributed))} فاکتور`
                    + `${result.tuitionAttributed ? ' و شهریه' : ''}).`;
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.attributing = false;
            }
        },

        async togglePayments(invoice) {
            if (this.expandedInvoiceId === invoice.id) {
                this.expandedInvoiceId = null;
                this.payments = [];
                return;
            }
            this.expandedInvoiceId = invoice.id;
            this.payments = [];
            this.paymentForm = { amount: '', method: 'CASH', note: '' };
            this.paymentsLoading = true;
            try {
                const response = await fetch(`${this.invoicesBase()}/${invoice.id}/payments`,
                    { headers: { 'X-School-ID': this.schoolId } });
                if (response.ok) {
                    this.payments = await response.json();
                } else {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.paymentsLoading = false;
            }
        },

        async recordPayment(invoice) {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.invoicesBase()}/${invoice.id}/payments`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
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
                this.submitting = false;
            }
        },

        // صدور فاکتور برای دانش‌آموزانی که فاکتوری ندارند (مثلاً قبل از تنظیم
        // شهریه ثبت‌نام شده‌اند) - backfill سمت سرور idempotent است
        async backfill() {
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
                    this.error = classified.message;
                    return;
                }
                const result = await response.json();
                this.notice = result.issuedCount > 0
                    ? `${result.issuedCount} فاکتور جدید صادر شد.`
                    : 'همه‌ی دانش‌آموزان برای این سال از قبل فاکتور دارند - چیزی صادر نشد.';
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
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
