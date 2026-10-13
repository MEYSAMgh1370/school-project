document.addEventListener('alpine:init', () => {
    Alpine.data('myInvoicesPage', () => ({
        schoolId: null,
        invoices: [],
        // فیلتر یک فرزند از لینک پیشخوان (?student=<id>)؛ null یعنی همه. فقط
        // نمایش است: API همه‌ی فرزندانِ لینک‌شده را می‌دهد و دسترسی سمت سرور
        // می‌ماند. شناسه‌ی نامعتبر یا ناآشنا فقط فهرستِ خالی می‌دهد.
        studentFilter: null,
        loading: false,
        error: '',

        init() {
            // کل مقدار باید عدد باشد؛ پیشوندِ عددی («100junk») نادیده می‌ماند.
            const raw = new URLSearchParams(window.location.search).get('student') || '';
            const requested = /^\d+$/.test(raw) ? parseInt(raw, 10) : NaN;
            this.studentFilter = Number.isInteger(requested) ? requested : null;
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        // بدون X-School-ID: خودِ endpoint هم مثل بقیه‌ی /api/me/** از
        // TenantResolverFilter مستثناست - schoolId از path می‌آید
        // (finance.MyInvoicesController)
        async load() {
            this.loading = true;
            this.error = '';
            try {
                const response = await fetch(`/api/me/schools/${this.schoolId}/invoices`);
                if (!response.ok) {
                    try {
                        const body = await response.json();
                        this.error = body.detail || body.title || 'خطایی رخ داد.';
                    } catch (e) {
                        this.error = 'خطایی رخ داد. (' + response.status + ')';
                    }
                    return;
                }
                this.invoices = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        visibleInvoices() {
            return this.studentFilter === null
                ? this.invoices
                : this.invoices.filter((invoice) => invoice.studentId === this.studentFilter);
        },

        // قابل‌پرداخت = مبلغ - تخفیف؛ مانده = قابل‌پرداخت - پرداخت‌شده (هرگز منفی نمی‌شود)
        discount(invoice) {
            return Number(invoice.discountTotal) || 0;
        },

        payable(invoice) {
            return (Number(invoice.amount) || 0) - this.discount(invoice);
        },

        remaining(invoice) {
            return Math.max(0, this.payable(invoice) - (Number(invoice.amountPaid) || 0));
        },

        // تخفیف مشروط: بخشی از discountTotal که فقط با تسویه‌ی کامل تا پایانِ
        // روزِ مهلت می‌ماند؛ بدون مهلت چیزی برای نمایش نیست.
        hasPendingDiscount(invoice) {
            return (Number(invoice.pendingDiscount) || 0) > 0 && !!invoice.pendingDiscountDeadline;
        },

        installmentsOf(invoice) {
            return Array.isArray(invoice.installments) ? invoice.installments : [];
        },

        // ردیف‌های اقساط به‌صورت پیش‌فرض بسته‌اند (روی موبایل جا نمی‌گیرند)
        expanded: {},

        toggleInstallments(invoice) {
            this.expanded = { ...this.expanded, [invoice.id]: !this.expanded[invoice.id] };
        },

        isExpanded(invoice) {
            return this.expanded[invoice.id] === true;
        },

        // «قسط بعدی»: اولین قسطِ پرداخت‌نشده‌ی کامل (به ترتیب شماره‌ی قسط)
        nextInstallmentId(invoice) {
            const open = this.installmentsOf(invoice)
                .filter((item) => item.status !== 'PAID')
                .sort((a, b) => a.seq - b.seq);
            return open.length > 0 ? open[0].id : null;
        },

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
        }
    }));
});
