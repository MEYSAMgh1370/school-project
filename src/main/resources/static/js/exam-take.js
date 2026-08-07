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
