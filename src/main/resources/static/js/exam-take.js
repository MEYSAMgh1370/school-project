document.addEventListener('alpine:init', () => {
    Alpine.data('examTakePage', () => ({
        token: '',
        loading: true,
        fatalError: '',
        exam: null,
        selectedSectionId: null,
        questionsBySection: {},
        loadingQuestions: false,
        savingByQuestion: {},
        pendingSaveCount: 0,
        saveStatus: '',
        saveStatusTimer: null,
        saveErrorBySection: {},
        remainingLabel: '',
        expired: false,
        timerHandle: null,
        // «گزارش مشکل» کاملاً مستقل از وضعیت آزمون است و به selectedChoice/savingByQuestion/
        // expired دست نمی‌زند. null = modal بسته؛ وگرنه { question, opener, errors, error }
        report: null,
        reportForm: { reportType: '', comment: '' },
        submittingReport: false,
        reportNotice: '',
        reportPageError: '',
        reportNoticeTimer: null,
        reportTypes: [
            { value: 'WRONG_ANSWER', label: 'پاسخ نادرست' },
            { value: 'TYPO', label: 'غلط املایی' },
            { value: 'UNCLEAR_TEXT', label: 'متن نامفهوم' },
            { value: 'OTHER', label: 'سایر' }
        ],

        init() {
            const params = new URLSearchParams(window.location.search);
            this.token = params.get('token') || '';
            const expireAtParam = params.get('expireAt');
            if (!this.token) {
                this.fatalError = 'لینک آزمون نامعتبر است — پارامتر token وجود ندارد.';
                this.loading = false;
                return;
            }
            if (expireAtParam) {
                this.startCountdown(new Date(expireAtParam));
            }
            this.load();
        },

        destroy() {
            clearInterval(this.timerHandle);
            clearTimeout(this.saveStatusTimer);
            clearTimeout(this.reportNoticeTimer);
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        // صفحه‌ی آزمون فقط توکن دارد و navbar ندارد؛ مدرسه همان مدرسه‌ی خودِ آزمون است
        // (از پاسخ /api/exam-take/exam)، نه مدرسه‌ی انتخاب‌شده‌ی localStorage که ممکن است کهنه باشد.
        reportSchoolId() {
            return this.exam && Number.isInteger(this.exam.schoolId) ? this.exam.schoolId : null;
        },

        openReport(question, event) {
            this.reportNotice = '';
            this.reportPageError = '';
            this.reportForm = { reportType: '', comment: '' };
            this.report = {
                question,
                opener: event ? event.currentTarget : null,
                errors: { reportType: '', comment: '' },
                error: ''
            };
            this.$nextTick(() => {
                if (window.ModalFocus) {
                    window.ModalFocus.focusFirst(this.$refs.reportModal, document.getElementById('reportType'));
                }
            });
        },

        closeReport() {
            const opener = this.report ? this.report.opener : null;
            this.report = null;
            if (window.ModalFocus) {
                window.ModalFocus.restore(opener);
            }
        },

        validateReport() {
            const errors = { reportType: '', comment: '' };
            if (!this.reportTypes.some(t => t.value === this.reportForm.reportType)) {
                errors.reportType = 'نوع مشکل را انتخاب کنید.';
            }
            if (this.reportForm.comment.length > 1000) {
                errors.comment = 'توضیح نباید بیشتر از ۱۰۰۰ نویسه باشد.';
            }
            this.report.errors = errors;
            this.report.error = (errors.reportType || errors.comment) ? 'گزارش ثبت نشد؛ فیلدهای مشخص‌شده را اصلاح کنید.' : '';
            return !this.report.error;
        },

        async submitReport() {
            if (!this.report || this.submittingReport || !this.validateReport()) {
                return;
            }
            const schoolId = this.reportSchoolId();
            if (!schoolId) {
                this.report.error = 'مدرسه‌ی این آزمون مشخص نیست. صفحه را دوباره بارگذاری کنید.';
                return;
            }
            this.submittingReport = true;
            try {
                const comment = this.reportForm.comment.trim();
                const response = await fetch(
                    `/api/student/schools/${schoolId}/questions/${this.report.question.questionId}/reports`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-School-ID': schoolId,
                            'X-XSRF-TOKEN': this.csrfToken()
                        },
                        body: JSON.stringify({
                            reportType: this.reportForm.reportType,
                            comment: comment || null
                        })
                    });
                if (!response.ok) {
                    const message = await this.parseError(response);
                    if (response.status === 403) {
                        // دسترسی/ماژول: پیام در صفحه؛ پاسخ‌ها و آزمون دست‌نخورده می‌مانند
                        this.closeReport();
                        this.reportPageError = message;
                    } else {
                        this.report.error = message;
                    }
                    return;
                }
                this.closeReport();
                this.reportNotice = 'گزارش ثبت شد.';
                clearTimeout(this.reportNoticeTimer);
                this.reportNoticeTimer = setTimeout(() => {
                    this.reportNotice = '';
                }, 5000);
            } catch (e) {
                if (this.report) {
                    this.report.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                this.submittingReport = false;
            }
        },

        startCountdown(expireAt) {
            const tick = () => {
                const remainingMs = expireAt.getTime() - Date.now();
                if (remainingMs <= 0) {
                    this.remainingLabel = '۰۰:۰۰';
                    this.expired = true;
                    clearInterval(this.timerHandle);
                    return;
                }
                const totalSeconds = Math.floor(remainingMs / 1000);
                const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
                const seconds = String(totalSeconds % 60).padStart(2, '0');
                this.remainingLabel = this.toPersianDigits(`${minutes}:${seconds}`);
            };
            tick();
            this.timerHandle = setInterval(tick, 1000);
        },

        toPersianDigits(value) {
            return String(value).replace(/\d/g, digit => '۰۱۲۳۴۵۶۷۸۹'[Number(digit)]);
        },

        async parseError(response) {
            try {
                const body = await response.json();
                return body.detail || body.title || 'خطایی رخ داد.';
            } catch (e) {
                return 'خطایی رخ داد. (' + response.status + ')';
            }
        },

        async load() {
            this.loading = true;
            this.fatalError = '';
            try {
                const response = await fetch(`/api/exam-take/exam?token=${encodeURIComponent(this.token)}`);
                if (!response.ok) {
                    this.fatalError = await this.parseError(response);
                    return;
                }
                this.exam = await response.json();
                if (this.exam.sections.length > 0) {
                    await this.selectSection(this.exam.sections[0].id);
                }
            } catch (e) {
                this.fatalError = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async selectSection(sectionId) {
            this.selectedSectionId = sectionId;
            if (this.questionsBySection[sectionId]) {
                return;
            }
            this.loadingQuestions = true;
            try {
                const response = await fetch(
                    `/api/exam-take/sections/${sectionId}/questions?token=${encodeURIComponent(this.token)}`
                );
                if (!response.ok) {
                    this.saveErrorBySection[sectionId] = await this.parseError(response);
                    return;
                }
                this.questionsBySection[sectionId] = await response.json();
            } catch (e) {
                this.saveErrorBySection[sectionId] = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loadingQuestions = false;
            }
        },

        currentQuestions() {
            return this.questionsBySection[this.selectedSectionId] || [];
        },

        answeredCount(sectionId) {
            const questions = this.questionsBySection[sectionId];
            if (!questions) {
                return 0;
            }
            return questions.filter(q => q.selectedChoice).length;
        },

        isQuestionSaving(questionId) {
            return this.savingByQuestion[String(questionId)] === true;
        },

        saveStatusMessage() {
            if (this.pendingSaveCount === 1) {
                return 'در حال ذخیره‌ی یک پاسخ…';
            }
            if (this.pendingSaveCount > 1) {
                return `در حال ذخیره‌ی ${this.toPersianDigits(this.pendingSaveCount)} پاسخ…`;
            }
            return this.saveStatus;
        },

        async selectChoice(question, choice) {
            if (this.expired || this.isQuestionSaving(question.questionId)) {
                return;
            }
            const sectionId = this.selectedSectionId;
            const previousChoice = question.selectedChoice;
            this.savingByQuestion[String(question.questionId)] = true;
            this.pendingSaveCount += 1;
            this.saveStatus = '';
            clearTimeout(this.saveStatusTimer);
            delete this.saveErrorBySection[sectionId];
            // Optimistic selection gives immediate feedback; any failed request rolls
            // back to the last server-confirmed answer below.
            question.selectedChoice = choice;
            try {
                const response = await fetch(`/api/exam-take/submit?token=${encodeURIComponent(this.token)}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sectionId, questionId: question.questionId, choice }),
                    keepalive: true
                });
                if (!response.ok) {
                    question.selectedChoice = previousChoice;
                    this.saveStatus = '';
                    this.saveErrorBySection[sectionId] = await this.parseError(response);
                    if (response.status === 400 || response.status === 401 || response.status === 403) {
                        this.expired = true;
                    }
                    return;
                }
                clearTimeout(this.saveStatusTimer);
                this.saveStatus = 'پاسخ ذخیره شد.';
                this.saveStatusTimer = setTimeout(() => {
                    this.saveStatus = '';
                }, 2500);
            } catch (e) {
                question.selectedChoice = previousChoice;
                this.saveStatus = '';
                this.saveErrorBySection[sectionId] = 'پاسخ ذخیره نشد. اتصال خود را بررسی و دوباره تلاش کنید.';
            } finally {
                delete this.savingByQuestion[String(question.questionId)];
                this.pendingSaveCount = Math.max(0, this.pendingSaveCount - 1);
            }
        }
    }));
});
