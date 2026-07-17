document.addEventListener('alpine:init', () => {
    Alpine.data('examTakePage', () => ({
        token: '',
        loading: true,
        fatalError: '',
        exam: null,
        selectedSectionId: null,
        questionsBySection: {},
        loadingQuestions: false,
        savingQuestionId: null,
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
                this.remainingLabel = `${minutes}:${seconds}`;
            };
            tick();
            this.timerHandle = setInterval(tick, 1000);
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

        async selectChoice(question, choice) {
            this.savingQuestionId = question.questionId;
            delete this.saveErrorBySection[this.selectedSectionId];
            try {
                const response = await fetch(`/api/exam-take/submit?token=${encodeURIComponent(this.token)}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sectionId: this.selectedSectionId, questionId: question.questionId, choice })
                });
                if (!response.ok) {
                    this.saveErrorBySection[this.selectedSectionId] = await this.parseError(response);
                    if (response.status === 400 || response.status === 401 || response.status === 403) {
                        this.expired = true;
                    }
                    return;
                }
                question.selectedChoice = choice;
            } catch (e) {
                this.saveErrorBySection[this.selectedSectionId] = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.savingQuestionId = null;
            }
        }
    }));
});
