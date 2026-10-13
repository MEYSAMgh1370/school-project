document.addEventListener('alpine:init', () => {
    const EMPTY_FORM = () => ({
        id: null,
        opener: null,
        type: 'MULTIPLE_CHOICE',
        contentMode: 'TEXT',
        text: '',
        choices: ['', '', '', ''],
        correctChoice: '1',
        answer: '',
        suggestedScore: '',
        images: [],
        errors: {},
        error: '',
        imageError: ''
    });

    const ROLE_LABELS = {
        STEM: 'صورت سؤال',
        CHOICE_1: 'گزینه ۱',
        CHOICE_2: 'گزینه ۲',
        CHOICE_3: 'گزینه ۳',
        CHOICE_4: 'گزینه ۴',
        ANSWER: 'پاسخ یا راه‌حل'
    };

    Alpine.data('questionBoxPage', () => ({
        schoolId: null,
        questions: [],
        filterText: '',
        typeFilter: '',
        pageSize: 25,
        shown: 25,
        loading: false,
        error: '',
        notice: '',
        blocked: false,
        // modal افزودن/ویرایش: null = بسته
        form: null,
        imageRole: 'STEM',
        saving: false,
        uploading: false,
        // modal تأیید حذف: null = بسته
        removal: null,
        deleting: false,

        init() {
            this.schoolId = QbApi.schoolId();
            this.$watch('filterText', () => { this.shown = this.pageSize; });
            this.$watch('typeFilter', () => { this.shown = this.pageSize; });
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.blocked = false;
                this.questions = [];
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        typeLabel(question) {
            return question.type === 'ESSAY' ? 'تشریحی' : 'تستی';
        },

        roleLabel(role) {
            return ROLE_LABELS[role] || role;
        },

        availableRoles() {
            const roles = ['STEM', 'ANSWER'];
            if (this.form && this.form.type === 'MULTIPLE_CHOICE') {
                roles.splice(1, 0, 'CHOICE_1', 'CHOICE_2', 'CHOICE_3', 'CHOICE_4');
            }
            return roles.map(value => ({ value, label: ROLE_LABELS[value] }));
        },

        filteredQuestions() {
            const query = this.filterText.trim();
            return this.questions.filter(question => {
                if (this.typeFilter && question.type !== this.typeFilter) {
                    return false;
                }
                return !query || ListSearch.matches(query, [question.text, ...question.choices, question.answer]);
            });
        },

        visibleQuestions() {
            return this.filteredQuestions().slice(0, this.shown);
        },

        remainingLabel() {
            return (this.filteredQuestions().length - this.shown).toLocaleString('fa-IR-u-nu-arabext');
        },

        showMore() {
            this.shown += this.pageSize;
        },

        async load() {
            this.loading = true;
            this.error = '';
            try {
                const result = await QbApi.call(this.schoolId, 'GET', '/questions');
                if (!result.ok) {
                    this.error = result.message;
                    this.blocked = result.blocking;
                    this.questions = [];
                    return;
                }
                this.questions = result.data;
            } finally {
                this.loading = false;
            }
        },

        // ---- modal سؤال ----

        openCreate(event) {
            this.notice = '';
            this.form = EMPTY_FORM();
            this.form.opener = event ? event.currentTarget : null;
            this.imageRole = 'STEM';
            this.focusForm();
        },

        openEdit(question, event) {
            this.notice = '';
            this.form = {
                ...EMPTY_FORM(),
                id: question.id,
                opener: event ? event.currentTarget : null,
                type: question.type,
                contentMode: question.contentMode,
                text: question.text || '',
                choices: question.choices.length === 4 ? [...question.choices] : ['', '', '', ''],
                correctChoice: question.correctChoice ? String(question.correctChoice) : '1',
                answer: question.answer || '',
                suggestedScore: question.suggestedScore ? String(question.suggestedScore) : '',
                images: [...question.images]
            };
            this.imageRole = 'STEM';
            this.focusForm();
        },

        focusForm() {
            this.$nextTick(() => {
                if (window.ModalFocus) {
                    window.ModalFocus.focusFirst(this.$refs.formModal, document.getElementById('qbFormType'));
                }
            });
        },

        closeForm() {
            const opener = this.form ? this.form.opener : null;
            this.form = null;
            if (window.ModalFocus) {
                window.ModalFocus.restore(opener);
            }
        },

        validate() {
            const f = this.form;
            const errors = {};
            const score = Digits.toAscii(f.suggestedScore || '').trim();
            if (f.contentMode === 'TEXT' && !f.text.trim()) {
                errors.text = 'متن سؤال را بنویسید.';
            }
            if (f.type === 'MULTIPLE_CHOICE' && f.contentMode === 'TEXT'
                && f.choices.some(choice => !String(choice || '').trim())) {
                errors.choices = 'هر چهار گزینه را بنویسید.';
            }
            if (score && !/^[0-9]+$/.test(score)) {
                errors.suggestedScore = 'نمره را با عدد صحیح بنویسید.';
            }
            f.errors = errors;
            f.error = Object.keys(errors).length ? 'سؤال ذخیره نشد؛ فیلدهای مشخص‌شده را اصلاح کنید.' : '';
            return !f.error;
        },

        payload() {
            const f = this.form;
            const score = Digits.toAscii(f.suggestedScore || '').trim();
            const isMcq = f.type === 'MULTIPLE_CHOICE';
            return {
                type: f.type,
                contentMode: f.contentMode,
                text: f.contentMode === 'TEXT' ? f.text.trim() : null,
                choices: isMcq && f.contentMode === 'TEXT' ? f.choices.map(c => String(c).trim()) : null,
                correctChoice: isMcq ? parseInt(f.correctChoice, 10) : null,
                answer: f.answer.trim() || null,
                suggestedScore: score ? parseInt(score, 10) : null
            };
        },

        async save() {
            if (!this.form || this.saving || !this.validate()) {
                return;
            }
            this.saving = true;
            try {
                const editing = this.form.id !== null;
                const result = await QbApi.call(this.schoolId, editing ? 'PUT' : 'POST',
                    editing ? `/questions/${this.form.id}` : '/questions', { json: this.payload() });
                if (!result.ok) {
                    if (result.blocking) {
                        this.closeForm();
                        this.error = result.message;
                        this.blocked = true;
                    } else {
                        this.form.error = result.message;
                    }
                    return;
                }
                this.form.id = result.data.id;
                this.form.images = result.data.images;
                this.notice = editing ? 'سؤال ذخیره شد.' : 'سؤال ایجاد شد.';
                await this.load();
                // فقط‌تصویر تا تصویرش نیامده ناقص است؛ پنجره برای افزودن تصویر باز می‌ماند
                if (this.form && this.form.contentMode === 'TEXT') {
                    this.closeForm();
                }
            } finally {
                this.saving = false;
            }
        },

        async uploadImage(event) {
            const file = event.target.files[0];
            event.target.value = '';
            if (!file || !this.form || !this.form.id) {
                return;
            }
            this.uploading = true;
            this.form.imageError = '';
            try {
                const formData = new FormData();
                formData.append('file', file);
                formData.append('role', this.imageRole);
                const result = await QbApi.call(this.schoolId, 'POST', `/questions/${this.form.id}/images`, { form: formData });
                if (!result.ok) {
                    this.form.imageError = result.message;
                    return;
                }
                this.form.images = [...this.form.images, result.data];
                this.notice = 'تصویر افزوده شد.';
                await this.load();
            } finally {
                this.uploading = false;
            }
        },

        async removeImage(image) {
            if (!this.form || !this.form.id) {
                return;
            }
            this.form.imageError = '';
            const result = await QbApi.call(this.schoolId, 'DELETE', `/questions/${this.form.id}/images/${image.id}`);
            if (!result.ok) {
                this.form.imageError = result.message;
                return;
            }
            this.form.images = this.form.images.filter(i => i.id !== image.id);
            this.notice = 'تصویر حذف شد.';
            await this.load();
        },

        // ---- حذف ----

        askDelete(question, event) {
            this.notice = '';
            this.removal = { question, opener: event ? event.currentTarget : null };
            this.$nextTick(() => {
                if (window.ModalFocus) {
                    window.ModalFocus.focusFirst(this.$refs.deleteModal, null);
                }
            });
        },

        closeDelete() {
            const opener = this.removal ? this.removal.opener : null;
            this.removal = null;
            if (window.ModalFocus) {
                window.ModalFocus.restore(opener);
            }
        },

        async confirmDelete() {
            if (!this.removal || this.deleting) {
                return;
            }
            this.deleting = true;
            try {
                const result = await QbApi.call(this.schoolId, 'DELETE', `/questions/${this.removal.question.id}`);
                this.closeDelete();
                if (!result.ok) {
                    this.error = result.message;
                    return;
                }
                this.notice = 'سؤال حذف شد.';
                await this.load();
            } finally {
                this.deleting = false;
            }
        }
    }));
});
