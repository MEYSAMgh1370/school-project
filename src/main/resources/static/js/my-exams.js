document.addEventListener('alpine:init', () => {
    Alpine.data('myExamsPage', () => ({
        schoolId: null,
        exams: [],
        loading: false,
        error: '',
        starting: null,
        // تأیید «شروع آزمون»: null یا { exam, error }
        confirm: null,
        results: {},

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.confirm = null;
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

        // بدون X-School-ID: مثل my-invoices.js، endpoint زیرمجموعه‌ی /api/me
        // است و schoolId از path می‌آید، نه هدر (exam.MyExamsController)
        async load() {
            this.loading = true;
            this.error = '';
            try {
                const response = await fetch(`/api/me/schools/${this.schoolId}/exams`);
                if (!response.ok) {
                    try {
                        const body = await response.json();
                        this.error = body.detail || body.title || 'خطایی رخ داد.';
                    } catch (e) {
                        this.error = 'خطایی رخ داد. (' + response.status + ')';
                    }
                    return;
                }
                this.exams = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        examLabel(exam) {
            if (exam.type === 'WEEKLY') {
                return 'آزمون هفتگی — هفته‌ی ' + exam.weekStart;
            }
            return exam.title;
        },

        hasStarted(exam) {
            return new Date(exam.start) <= new Date();
        },

        hasEnded(exam) {
            return new Date(exam.end) <= new Date();
        },

        // با باز شدنِ تلاش، زمان‌سنجِ آزمون شروع می‌شود: اول تأیید (modal) گرفته می‌شود
        askStart(exam) {
            if (this.starting !== null) {
                return;
            }
            this.error = '';
            this.confirm = { exam, error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
        },

        async startAttempt(exam) {
            // خطا داخل modal نشان داده می‌شود (پشتِ لایه‌ی modal دیده نمی‌شد)
            const target = this.confirm || this;
            if (this.starting !== null) {
                return;
            }
            this.starting = exam.id;
            this.error = '';
            target.error = '';
            try {
                const response = await fetch(`/api/me/schools/${this.schoolId}/exams/${exam.id}/attempts`, {
                    method: 'POST',
                    headers: { 'X-XSRF-TOKEN': this.csrfToken() }
                });
                if (!response.ok) {
                    try {
                        const body = await response.json();
                        target.error = body.detail || body.title || 'خطایی رخ داد.';
                    } catch (e) {
                        target.error = 'خطایی رخ داد. (' + response.status + ')';
                    }
                    return;
                }
                const attempt = await response.json();
                window.location.href = `/exam-take?token=${encodeURIComponent(attempt.token)}&expireAt=${encodeURIComponent(attempt.expireAt)}`;
            } catch (e) {
                target.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.starting = null;
            }
        },

        async viewResult(exam) {
            this.error = '';
            if (this.results[exam.id]) {
                delete this.results[exam.id];
                return;
            }
            try {
                const response = await fetch(`/api/me/schools/${this.schoolId}/exams/${exam.id}/result`);
                if (!response.ok) {
                    try {
                        const body = await response.json();
                        this.error = body.detail || body.title || 'خطایی رخ داد.';
                    } catch (e) {
                        this.error = 'خطایی رخ داد. (' + response.status + ')';
                    }
                    return;
                }
                this.results[exam.id] = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        }
    }));
});
