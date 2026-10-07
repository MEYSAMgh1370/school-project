document.addEventListener('alpine:init', () => {
    Alpine.data('curriculumPage', () => ({
        schoolId: null,
        flatTopics: [],
        questions: [],
        // جست‌وجوی متن سؤال روی فهرست ردیفی (DESIGN.md §6)
        filterText: '',
        selectedTopicId: null,
        difficultyFilter: [],
        loading: false,
        submittingTopic: false,
        submittingQuestion: false,
        error: '',
        notice: '',
        blocked: false,
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
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.blocked = false;
                this.selectedTopicId = null;
                this.filterText = '';
                this.difficultyFilter = [];
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

        visibleQuestions() {
            const query = this.filterText.trim();
            if (!query) {
                return this.questions;
            }
            return this.questions.filter(question => String(question.text || '').includes(query));
        },

        difficultyLabel(code) {
            const level = this.difficultyLevels.find(l => l.value === code);
            return level ? level.label : '—';
        },

        selectTopic(topicId) {
            this.selectedTopicId = topicId;
            this.filterText = '';
            this.loadQuestions();
        },

        toggleDifficulty(value) {
            if (this.difficultyFilter.includes(value)) {
                this.difficultyFilter = this.difficultyFilter.filter(v => v !== value);
            } else {
                this.difficultyFilter = [...this.difficultyFilter, value];
            }
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

        async load() {
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                const treeResponse = await fetch(`${this.topicsBase()}/tree`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!treeResponse.ok) {
                    const classified = await this.classifyError(treeResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    this.flatTopics = [];
                    return;
                }
                const tree = await treeResponse.json();
                this.flatTopics = this.flattenTree(tree, 0);
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
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingTopic = false;
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
