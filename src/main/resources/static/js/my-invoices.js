document.addEventListener('alpine:init', () => {
    Alpine.data('myInvoicesPage', () => ({
        schoolId: null,
        invoices: [],
        loading: false,
        error: '',

        init() {
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
