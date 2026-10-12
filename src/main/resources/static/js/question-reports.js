document.addEventListener('alpine:init', () => {
    const PAGE_SIZE = 25;

    Alpine.data('questionReportsPage', () => ({
        schoolId: null,
        reports: [],
        filterText: '',
        statusFilter: '',
        shown: PAGE_SIZE,
        loading: false,
        submitting: false,
        blocked: false,
        error: '',
        notice: '',

        // modal تغییر وضعیت (تنها modal صفحه): null = بسته
        // { report, status, label, opener, error, noteError }
        action: null,
        actionNote: '',

        statuses: [
            { value: 'OPEN', label: 'باز' },
            { value: 'REVIEWED', label: 'بررسی شد' },
            { value: 'FIXED', label: 'رفع شد' },
            { value: 'CLOSED', label: 'بسته' }
        ],
        types: {
            WRONG_ANSWER: 'پاسخ نادرست',
            TYPO: 'غلط املایی',
            UNCLEAR_TEXT: 'متن نامفهوم',
            OTHER: 'سایر'
        },
        // فعل دکمه‌ی هر گذار + پیام موفقیتی که همان فعل را تکرار می‌کند
        transitions: {
            REVIEWED: { label: 'بررسی شد', done: 'گزارش بررسی شد.' },
            FIXED: { label: 'رفع شد', done: 'گزارش رفع شد.' },
            CLOSED: { label: 'بستن', done: 'گزارش بسته شد.' }
        },

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            // جست‌وجو یا فیلتر عوض شود، فهرست به ۲۵ ردیف اول برمی‌گردد
            this.$watch('filterText', () => { this.shown = PAGE_SIZE; });
            this.$watch('statusFilter', () => { this.shown = PAGE_SIZE; });
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.filterText = '';
                this.statusFilter = '';
                this.shown = PAGE_SIZE;
                this.action = null;
                this.reports = [];
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

        base() {
            return `/api/admin/schools/${this.schoolId}/question-reports`;
        },

        typeLabel(code) {
            return this.types[code] || code || '—';
        },

        statusLabel(code) {
            const status = this.statuses.find(s => s.value === code);
            return status ? status.label : (code || '—');
        },

        // فقط معنا با کلمه می‌آید؛ رنگ‌ها همان کلاس‌های موجود tag اند
        statusClass(code) {
            return { OPEN: 'is-warning', REVIEWED: 'is-info', FIXED: 'is-success', CLOSED: 'is-light' }[code] || 'is-light';
        },

        truncate(text, max) {
            const value = String(text == null ? '' : text);
            return value.length > max ? value.slice(0, max) + '…' : value;
        },

        filteredReports() {
            const query = this.filterText.trim();
            return this.reports.filter(report =>
                (!this.statusFilter || report.status === this.statusFilter)
                && ListSearch.matches(query, [report.questionText, report.comment, report.resolutionNote]));
        },

        // صفحه‌بندی سمت کلاینت: هر بار ۲۵ ردیف (DESIGN.md §6)
        visibleReports() {
            return this.filteredReports().slice(0, this.shown);
        },

        remainingCount() {
            return Math.max(0, this.filteredReports().length - this.shown);
        },

        moreLabel() {
            return `نمایش بیشتر (${Calendar.digits(this.remainingCount())} مورد دیگر)`;
        },

        showMore() {
            this.shown += PAGE_SIZE;
        },

        // تشخیص دو نوع 403 لایه‌ی §5 از URI فیلد type در ProblemDetail - همان
        // الگوی finance.js/expenses.js؛ صفحه پشت @RequiresModule("CURRICULUM") است.
        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول سرفصل‌ها و بانک سؤال را نخریده است. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دسترسی به این بخش را ندارد.' };
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
                const response = await fetch(this.base(), { headers: { 'X-School-ID': this.schoolId } });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    this.reports = [];
                    return;
                }
                this.reports = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        openAction(report, status, event) {
            this.notice = '';
            this.error = '';
            this.actionNote = '';
            this.action = {
                report,
                status,
                label: this.transitions[status].label,
                opener: event ? event.currentTarget : null,
                error: '',
                noteError: ''
            };
            this.$nextTick(() => {
                if (window.ModalFocus) {
                    window.ModalFocus.focusFirst(this.$refs.actionModal, document.getElementById('reportActionNote'));
                }
            });
        },

        closeAction() {
            const opener = this.action ? this.action.opener : null;
            this.action = null;
            if (window.ModalFocus) {
                window.ModalFocus.restore(opener);
            }
        },

        async submitAction() {
            if (!this.action || this.submitting) {
                return;
            }
            const note = this.actionNote.trim();
            if (note.length > 1000) {
                this.action.noteError = 'یادداشت نباید بیشتر از ۱۰۰۰ نویسه باشد.';
                this.action.error = 'تغییر وضعیت ثبت نشد؛ فیلد مشخص‌شده را اصلاح کنید.';
                return;
            }
            this.action.noteError = '';
            this.action.error = '';
            this.submitting = true;
            const target = this.action;
            try {
                const response = await fetch(`${this.base()}/${target.report.id}/status`, {
                    method: 'PATCH',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ status: target.status, resolutionNote: note || null })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    if (classified.blocking) {
                        // 403 (ماژول/دسترسی): پیام مشترک بالای صفحه
                        this.closeAction();
                        // (فهرست همچنان قابل مرور می‌ماند؛ فقط همین اقدام مجاز نبود)
                        this.error = classified.message;
                    } else {
                        // مثلاً گذار نامعتبر (۴۰۹) یا گزارش دیگر موجود نیست: داخل modal
                        target.error = classified.message;
                    }
                    return;
                }
                this.closeAction();
                this.notice = this.transitions[target.status].done;
                await this.load();
            } catch (e) {
                if (this.action) {
                    this.action.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                this.submitting = false;
            }
        }
    }));
});
