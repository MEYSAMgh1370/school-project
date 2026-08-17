document.addEventListener('alpine:init', () => {
    Alpine.data('expensesPage', () => ({
        schoolId: null,
        categories: [],
        vendors: [],
        expenses: [],
        blocked: false,
        loading: false,
        submitting: false,
        seeding: false,
        error: '',
        notice: '',

        categoryForm: { name: '' },
        vendorForm: { name: '', phone: '', nationalId: '', economicCode: '', address: '' },
        expenseForm: {
            expenseDate: '', amount: '', categoryId: '', vendorId: '', paymentMethod: 'CASH',
            description: '', receiptNumber: '', invoiceNumber: '', documentNumber: ''
        },
        filters: { categoryId: '', vendorId: '', status: '', paymentMethod: '' },

        expandedExpenseId: null,
        attachments: [],
        attachmentsLoading: false,

        // انتخابگر بازه‌ی گزارش - جلالی، تبدیل با jalali.js (بدون هیچ کتابخانه‌ی خارجی)
        summaryYear: 1405,
        summaryPeriodKind: 'month',
        summaryPeriodIndex: 1,
        summaryGroupBy: 'category',
        summaryRows: [],
        financeSummaryResult: null,
        summaryLoading: false,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
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

        categoriesBase() {
            return `/api/admin/schools/${this.schoolId}/expense-categories`;
        },

        vendorsBase() {
            return `/api/admin/schools/${this.schoolId}/vendors`;
        },

        expensesBase() {
            return `/api/admin/schools/${this.schoolId}/expenses`;
        },

        categoryName(categoryId) {
            const category = this.categories.find(c => c.id === categoryId);
            return category ? category.name : 'دسته‌ی #' + categoryId;
        },

        vendorName(vendorId) {
            if (!vendorId) return '—';
            const vendor = this.vendors.find(v => v.id === vendorId);
            return vendor ? vendor.name : 'طرف حساب #' + vendorId;
        },

        statusLabel(status) {
            return { DRAFT: 'پیش‌نویس', APPROVED: 'تأییدشده', PAID: 'پرداخت‌شده', CANCELLED: 'لغوشده' }[status] || status;
        },

        statusClass(status) {
            return { DRAFT: 'is-light', APPROVED: 'is-info', PAID: 'is-success', CANCELLED: 'is-danger' }[status] || '';
        },

        methodLabel(method) {
            return {
                CASH: 'نقدی', CARD: 'کارت', BANK_TRANSFER: 'انتقال بانکی', CHEQUE: 'چک', OTHER: 'سایر'
            }[method] || method;
        },

        // تشخیص دو نوع 403 لایه‌ی §5 از URI فیلد type در ProblemDetail - همان
        // الگوی finance.js (اولین صفحه‌ای که این تفکیک لازم شد)
        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول مالی را نخریده است. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دسترسی به این بخش را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        expenseQuery() {
            const params = new URLSearchParams();
            if (this.filters.categoryId) params.set('categoryId', this.filters.categoryId);
            if (this.filters.vendorId) params.set('vendorId', this.filters.vendorId);
            if (this.filters.status) params.set('status', this.filters.status);
            if (this.filters.paymentMethod) params.set('paymentMethod', this.filters.paymentMethod);
            const query = params.toString();
            return query ? `?${query}` : '';
        },

        async load() {
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                const [categoriesResponse, vendorsResponse, expensesResponse] = await Promise.all([
                    fetch(this.categoriesBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.vendorsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.expensesBase() + this.expenseQuery(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                for (const response of [categoriesResponse, vendorsResponse, expensesResponse]) {
                    if (!response.ok) {
                        const classified = await this.classifyError(response);
                        this.error = classified.message;
                        this.blocked = classified.blocking;
                        return;
                    }
                }
                this.categories = await categoriesResponse.json();
                this.vendors = await vendorsResponse.json();
                this.expenses = await expensesResponse.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async seedDefaults() {
            this.seeding = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.categoriesBase()}/seed-defaults`, {
                    method: 'POST',
                    headers: { 'X-School-ID': this.schoolId, 'X-XSRF-TOKEN': this.csrfToken() }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                const result = await response.json();
                this.notice = result.createdCount > 0
                    ? `${result.createdCount} دسته‌ی پیش‌فرض اضافه شد.`
                    : 'همه‌ی دسته‌های پیش‌فرض از قبل وجود دارند.';
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.seeding = false;
            }
        },

        async createCategory() {
            await this.submitJson(this.categoriesBase(), { name: this.categoryForm.name }, () => {
                this.categoryForm = { name: '' };
                this.notice = 'دسته با موفقیت ثبت شد.';
            });
        },

        async toggleCategoryActive(category) {
            await this.submitJson(`${this.categoriesBase()}/${category.id}`,
                { active: !category.active }, () => {
                    this.notice = 'وضعیت دسته به‌روزرسانی شد.';
                }, 'PUT');
        },

        async createVendor() {
            await this.submitJson(this.vendorsBase(), this.vendorForm, () => {
                this.vendorForm = { name: '', phone: '', nationalId: '', economicCode: '', address: '' };
                this.notice = 'طرف حساب با موفقیت ثبت شد.';
            });
        },

        async toggleVendorActive(vendor) {
            await this.submitJson(`${this.vendorsBase()}/${vendor.id}`, { active: !vendor.active },
                () => { this.notice = 'وضعیت طرف حساب به‌روزرسانی شد.'; }, 'PUT');
        },

        async createExpense() {
            const body = { ...this.expenseForm };
            body.amount = Money.normalizeAmount(this.expenseForm.amount);
            body.categoryId = this.expenseForm.categoryId ? Number(this.expenseForm.categoryId) : null;
            body.vendorId = this.expenseForm.vendorId ? Number(this.expenseForm.vendorId) : null;
            await this.submitJson(this.expensesBase(), body, () => {
                this.expenseForm = {
                    expenseDate: '', amount: '', categoryId: '', vendorId: '', paymentMethod: 'CASH',
                    description: '', receiptNumber: '', invoiceNumber: '', documentNumber: ''
                };
                this.notice = 'هزینه با موفقیت ثبت شد.';
            });
        },

        async approveExpense(expense) {
            await this.submitJson(`${this.expensesBase()}/${expense.id}/approve`, null,
                () => { this.notice = 'هزینه تأیید شد.'; }, 'POST');
        },

        async payExpense(expense) {
            await this.submitJson(`${this.expensesBase()}/${expense.id}/pay`, null,
                () => { this.notice = 'پرداخت هزینه ثبت شد.'; }, 'POST');
        },

        async cancelExpense(expense) {
            await this.submitJson(`${this.expensesBase()}/${expense.id}/cancel`, null,
                () => { this.notice = 'هزینه لغو شد.'; }, 'POST');
        },

        // کمک‌تابع مشترک POST/PUT با بدنه‌ی JSON - همه‌ی فرم‌های این صفحه از
        // همین یک مسیر رد می‌شوند تا CSRF/خطا/بارگذاری‌مجدد یک‌جا مدیریت شود
        async submitJson(url, body, onSuccess, method) {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(url, {
                    method: method || 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: body ? JSON.stringify(body) : undefined
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                onSuccess();
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        },

        async toggleAttachments(expense) {
            if (this.expandedExpenseId === expense.id) {
                this.expandedExpenseId = null;
                this.attachments = [];
                return;
            }
            this.expandedExpenseId = expense.id;
            this.attachments = [];
            this.attachmentsLoading = true;
            try {
                const response = await fetch(`${this.expensesBase()}/${expense.id}/attachments`,
                    { headers: { 'X-School-ID': this.schoolId } });
                if (response.ok) {
                    this.attachments = await response.json();
                } else {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.attachmentsLoading = false;
            }
        },

        async uploadAttachment(expense, event) {
            const file = event.target.files[0];
            event.target.value = '';
            if (!file) return;

            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const formData = new FormData();
                formData.append('file', file);
                // Content-Type عمداً دستی ست نمی‌شود - مرورگر خودش boundary
                // multipart را اضافه می‌کند
                const response = await fetch(`${this.expensesBase()}/${expense.id}/attachments`, {
                    method: 'POST',
                    headers: { 'X-School-ID': this.schoolId, 'X-XSRF-TOKEN': this.csrfToken() },
                    body: formData
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                this.notice = 'پیوست با موفقیت آپلود شد.';
                this.expandedExpenseId = null;
                await this.toggleAttachments(expense);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        },

        // دانلود با هدر X-School-ID نیاز به fetch دارد - یک <a href> ساده
        // این هدر سفارشی را در ناوبری مرورگر ارسال نمی‌کند
        async downloadAttachment(expense, attachment) {
            try {
                const response = await fetch(
                    `${this.expensesBase()}/${expense.id}/attachments/${attachment.id}/content`,
                    { headers: { 'X-School-ID': this.schoolId } });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = attachment.fileName;
                document.body.appendChild(link);
                link.click();
                link.remove();
                URL.revokeObjectURL(url);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        async deleteAttachment(expense, attachment) {
            this.submitting = true;
            try {
                const response = await fetch(
                    `${this.expensesBase()}/${expense.id}/attachments/${attachment.id}`,
                    { method: 'DELETE', headers: { 'X-School-ID': this.schoolId, 'X-XSRF-TOKEN': this.csrfToken() } });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                this.expandedExpenseId = null;
                await this.toggleAttachments(expense);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        },

        async loadSummary() {
            this.summaryLoading = true;
            this.error = '';
            try {
                const range = window.Jalali.periodRange(
                    Number(this.summaryYear), this.summaryPeriodKind, Number(this.summaryPeriodIndex));

                const [summaryResponse, financeResponse] = await Promise.all([
                    fetch(`${this.expensesBase()}/summary?from=${range.from}&to=${range.to}&groupBy=${this.summaryGroupBy}`,
                        { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(`/api/admin/schools/${this.schoolId}/finance-summary?from=${range.from}&to=${range.to}`,
                        { headers: { 'X-School-ID': this.schoolId } })
                ]);
                if (!summaryResponse.ok) {
                    const classified = await this.classifyError(summaryResponse);
                    this.error = classified.message;
                    return;
                }
                this.summaryRows = await summaryResponse.json();
                this.financeSummaryResult = financeResponse.ok ? await financeResponse.json() : null;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.summaryLoading = false;
            }
        },

        summaryRowLabel(row) {
            if (this.summaryGroupBy === 'category') return row.categoryName;
            if (this.summaryGroupBy === 'vendor') return row.vendorName;
            return this.methodLabel(row.method);
        }
    }));
});
