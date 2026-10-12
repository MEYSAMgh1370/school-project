document.addEventListener('alpine:init', () => {
    Alpine.data('curriculumPage', () => ({
        ...CoursePicker.state(),
        ...CoursePicker.methods,
        schoolId: null,
        flatTopics: [],
        questions: [],
        // جست‌وجوی متن سؤال روی فهرست ردیفی (DESIGN.md §6)
        filterText: '',
        pageSize: 25,
        shown: 25,
        selectedTopicId: null,
        difficultyFilter: [],
        loading: false,
        submittingTopic: false,
        submittingQuestion: false,
        error: '',
        notice: '',
        blocked: false,
        // modal «گزارش مشکل» (تنها modal صفحه): null = بسته
        // { question, opener, errors: { reportType, comment }, error }
        report: null,
        reportForm: { reportType: '', comment: '' },
        submittingReport: false,
        reportTypes: [
            { value: 'WRONG_ANSWER', label: 'پاسخ نادرست' },
            { value: 'TYPO', label: 'غلط املایی' },
            { value: 'UNCLEAR_TEXT', label: 'متن نامفهوم' },
            { value: 'OTHER', label: 'سایر' }
        ],
        topicForm: { name: '', parentId: '' },
        questionForm: { topicId: '', text: '', choice1: '', choice2: '', choice3: '', choice4: '', correctChoice: '1', difficulty: '', answer: '' },
        difficultyLevels: [
            { value: 'EDUCATIONAL', label: 'آموزشی' },
            { value: 'EASY', label: 'آسان' },
            { value: 'MEDIUM', label: 'متوسط' },
            { value: 'HARD', label: 'سخت' },
            { value: 'VERY_HARD', label: 'خیلی سخت' }
        ],

        get topics() {
            return this.flatTopics;
        },

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.$watch('filterText', () => { this.shown = this.pageSize; });
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.blocked = false;
                this.selectedTopicId = null;
                this.filterText = '';
                this.shown = this.pageSize;
                this.difficultyFilter = [];
                this.pickerGrade = '';
                this.pickerCourses = [];
                this.pickerCourseId = '';
                this.flatTopics = [];
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

        topicsBase() {
            return `/api/admin/schools/${this.schoolId}/topics`;
        },

        questionsBase() {
            return `/api/admin/schools/${this.schoolId}/questions`;
        },

        // درخت تودرتوی /topics/tree را به یک فهرست تخت با فیلد depth تبدیل
        // می‌کند - رندر تورفتگی سمت کلاینت ساده‌تر از پیمایش بازگشتی در JTE
        // است و برای دراپ‌داون انتخاب سرفصل هم همین فهرست تخت لازم است.
        flattenTree(nodes, depth) {
            let result = [];
            for (const node of nodes) {
                result.push({ id: node.id, name: node.name, depth });
                result = result.concat(this.flattenTree(node.children, depth + 1));
            }
            return result;
        },

        selectedTopicName() {
            const topic = this.flatTopics.find(t => t.id === this.selectedTopicId);
            return topic ? topic.name : '';
        },

        filteredQuestions() {
            const query = this.filterText.trim();
            if (!query) {
                return this.questions;
            }
            return this.questions.filter(question => ListSearch.matches(query, [question.text]));
        },

        // فقط یک صفحه‌ی ۲۵تایی از فهرست پالایش‌شده رندر می‌شود (DESIGN.md §6).
        visibleQuestions() {
            return this.filteredQuestions().slice(0, this.shown);
        },

        remainingLabel() {
            return (this.filteredQuestions().length - this.shown).toLocaleString('fa-IR-u-nu-arabext');
        },

        showMore() {
            this.shown += this.pageSize;
        },

        difficultyLabel(code) {
            const level = this.difficultyLevels.find(l => l.value === code);
            return level ? level.label : '—';
        },

        selectTopic(topicId) {
            this.selectedTopicId = topicId;
            this.filterText = '';
            this.shown = this.pageSize;
            this.loadQuestions();
        },

        toggleDifficulty(value) {
            if (this.difficultyFilter.includes(value)) {
                this.difficultyFilter = this.difficultyFilter.filter(v => v !== value);
            } else {
                this.difficultyFilter = [...this.difficultyFilter, value];
            }
            this.shown = this.pageSize;
            this.loadQuestions();
        },

        // نوع خطای دولایه‌ی §5 را از URI فیلد type در ProblemDetail تشخیص
        // می‌دهد - همان الگوی finance.js/expenses.js، چون این صفحه هم پشت
        // @RequiresModule("CURRICULUM") است.
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

        async loadQuestions() {
            try {
                const params = new URLSearchParams();
                if (this.selectedTopicId) {
                    params.set('topicId', this.selectedTopicId);
                }
                for (const level of this.difficultyFilter) {
                    params.append('difficulty', level);
                }
                const response = await fetch(`${this.questionsBase()}?${params.toString()}`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    this.questions = [];
                    return;
                }
                this.questions = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        // سرفصل‌ها از انتخابگر پایه و درس می‌آیند (course-picker.js)؛ کلِ درخت دیگر خوانده نمی‌شود.
        async load() {
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                await this.loadQuestions();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async createTopic() {
            this.submittingTopic = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.topicsBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        parentId: this.topicForm.parentId ? parseInt(this.topicForm.parentId, 10) : null,
                        name: this.topicForm.name,
                        sortOrder: null
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'سرفصل اضافه شد.';
                this.topicForm = { name: '', parentId: '' };
                await this.pickerLoadTopics();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingTopic = false;
            }
        },

        openReport(question, event) {
            this.notice = '';
            this.error = '';
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

        // اعتبارسنجی سمت کلاینت: خطا کنار فیلد و خلاصه‌اش بالای modal (ناحیه‌ی پیام modal)
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
            this.submittingReport = true;
            try {
                const comment = this.reportForm.comment.trim();
                const response = await fetch(`${this.questionsBase()}/${this.report.question.id}/reports`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        reportType: this.reportForm.reportType,
                        comment: comment || null
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    if (classified.blocking) {
                        // 403 (ماژول/دسترسی): پیام مشترک بالای صفحه؛ فهرست سؤال‌ها همچنان قابل مرور می‌ماند
                        this.closeReport();
                        this.error = classified.message;
                    } else {
                        this.report.error = classified.message;
                    }
                    return;
                }
                this.closeReport();
                this.notice = 'گزارش ثبت شد.';
            } catch (e) {
                if (this.report) {
                    this.report.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                this.submittingReport = false;
            }
        },

        async createQuestion() {
            this.submittingQuestion = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.questionsBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        topicId: parseInt(this.questionForm.topicId, 10),
                        text: this.questionForm.text,
                        choice1: this.questionForm.choice1,
                        choice2: this.questionForm.choice2,
                        choice3: this.questionForm.choice3,
                        choice4: this.questionForm.choice4,
                        correctChoice: parseInt(this.questionForm.correctChoice, 10),
                        answer: this.questionForm.answer || null,
                        difficulty: this.questionForm.difficulty || null
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'سؤال اضافه شد.';
                const topicId = this.questionForm.topicId;
                this.questionForm = { topicId, text: '', choice1: '', choice2: '', choice3: '', choice4: '', correctChoice: '1', difficulty: '', answer: '' };
                await this.loadQuestions();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingQuestion = false;
            }
        }
    }));
});
