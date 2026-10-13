document.addEventListener('alpine:init', () => {
    const FIELD_LABELS = {
        TITLE: 'عنوان آزمون',
        SUBJECT: 'درس',
        CLASS: 'کلاس',
        TEACHER: 'معلم',
        DATE: 'تاریخ',
        DURATION: 'مدت',
        STUDENT_NAME: 'نام دانش‌آموز',
        SCORE_BOX: 'نمره'
    };
    // فیلدهایی که معلم هر بار برای برگه پر می‌کند؛ نام دانش‌آموز و نمره روی کاغذ خالی می‌مانند و عنوان از خودِ برگه می‌آید
    const VALUE_FIELDS = ['SUBJECT', 'CLASS', 'TEACHER', 'DATE', 'DURATION'];
    const LAYOUTS = [
        { value: 'CLASSIC', label: 'کلاسیک (نام مدرسه وسط، فیلدها در دو ستون)' },
        { value: 'COMPACT', label: 'فشرده (فیلدها در یک ردیف)' },
        { value: 'BOXED', label: 'کادردار (جدول)' }
    ];

    let keySeed = 0;
    const nextKey = () => 'sec-' + (++keySeed);

    const emptyDraft = () => ({
        id: null,
        title: '',
        headerId: '',
        headerValues: {},
        showEssayAnswerSpace: true,
        includeAnswerKey: false,
        sections: [{ key: nextKey(), title: 'بخش اول', instruction: '', items: [] }]
    });

    const emptyHeaderForm = () => ({
        id: null,
        opener: null,
        name: '',
        mode: 'LAYOUT',
        layout: 'CLASSIC',
        showSchoolName: true,
        fields: ['TITLE', 'CLASS', 'DATE'],
        hasImage: false,
        errors: {},
        error: ''
    });

    Alpine.data('questionSheetsPage', () => ({
        schoolId: null,
        view: 'list',
        tab: 'sheets',
        sheets: [],
        headers: [],
        boxQuestions: [],
        // کلیدِ «BOX:12» یا «BANK:7» -> نمای متنی آیتم (برای نمایش در سازنده)
        lookup: {},
        draft: emptyDraft(),
        errors: {},
        loading: false,
        saving: false,
        error: '',
        notice: '',
        blocked: false,
        layouts: LAYOUTS,
        allFields: Object.entries(FIELD_LABELS).map(([value, label]) => ({ value, label })),
        // انتخابگر سؤال: همیشه شیء است تا x-model روی null نیفتد؛ open=false یعنی بسته
        picker: null,
        topicsFlat: [],
        bankQuestions: [],
        headerForm: null,
        savingHeader: false,
        uploading: false,
        removal: null,
        deleting: false,

        init() {
            this.schoolId = QbApi.schoolId();
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.blocked = false;
                this.view = 'list';
                this.topicsFlat = [];
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        fieldLabel(field) {
            return FIELD_LABELS[field] || field;
        },

        layoutLabel(layout) {
            const found = LAYOUTS.find(l => l.value === layout);
            return found ? found.label.split(' (')[0] : '';
        },

        async load() {
            this.loading = true;
            this.error = '';
            try {
                const [sheets, headers, questions] = await Promise.all([
                    QbApi.call(this.schoolId, 'GET', '/sheets'),
                    QbApi.call(this.schoolId, 'GET', '/headers'),
                    QbApi.call(this.schoolId, 'GET', '/questions')
                ]);
                const failed = [sheets, headers, questions].find(r => !r.ok);
                if (failed) {
                    this.error = failed.message;
                    this.blocked = failed.blocking;
                    return;
                }
                this.sheets = sheets.data;
                this.headers = headers.data;
                this.boxQuestions = questions.data;
            } finally {
                this.loading = false;
            }
        },

        // ---- سازنده‌ی برگه ----

        usableHeaders() {
            return this.headers.filter(h => h.complete);
        },

        selectedHeader() {
            return this.headers.find(h => String(h.id) === String(this.draft.headerId)) || null;
        },

        headerValueFields() {
            const header = this.selectedHeader();
            return header && header.mode === 'LAYOUT' ? VALUE_FIELDS.filter(f => header.fields.includes(f)) : [];
        },

        newSheet() {
            this.notice = '';
            this.errors = {};
            this.draft = emptyDraft();
            this.view = 'builder';
        },

        backToList() {
            this.view = 'list';
            this.load();
        },

        async openSheet(id) {
            this.notice = '';
            this.errors = {};
            const [sheet, resolved] = await Promise.all([
                QbApi.call(this.schoolId, 'GET', `/sheets/${id}`),
                QbApi.call(this.schoolId, 'GET', `/sheets/${id}/resolved`)
            ]);
            if (!sheet.ok || !resolved.ok) {
                this.error = (sheet.ok ? resolved : sheet).message;
                return;
            }
            this.lookup = {};
            resolved.data.sections.forEach(section => section.items.forEach(item => {
                this.lookup[item.source + ':' + item.questionId] = item;
            }));
            this.draft = {
                id: sheet.data.id,
                title: sheet.data.title,
                headerId: sheet.data.headerId ? String(sheet.data.headerId) : '',
                headerValues: { ...sheet.data.headerValues },
                showEssayAnswerSpace: sheet.data.showEssayAnswerSpace,
                includeAnswerKey: sheet.data.includeAnswerKey,
                sections: sheet.data.sections.map(s => ({
                    key: nextKey(),
                    title: s.title,
                    instruction: s.instruction || '',
                    items: s.items.map(i => ({ source: i.source, questionId: i.questionId }))
                }))
            };
            this.view = 'builder';
        },

        addSection() {
            this.draft.sections.push({
                key: nextKey(),
                title: 'بخش ' + (this.draft.sections.length + 1),
                instruction: '',
                items: []
            });
        },

        removeSection(index) {
            this.draft.sections.splice(index, 1);
        },

        moveSection(index, delta) {
            const target = index + delta;
            if (target < 0 || target >= this.draft.sections.length) {
                return;
            }
            const [section] = this.draft.sections.splice(index, 1);
            this.draft.sections.splice(target, 0, section);
        },

        moveItem(sectionIndex, itemIndex, delta) {
            const items = this.draft.sections[sectionIndex].items;
            const target = itemIndex + delta;
            if (target < 0 || target >= items.length) {
                return;
            }
            const [item] = items.splice(itemIndex, 1);
            items.splice(target, 0, item);
        },

        removeItem(sectionIndex, itemIndex) {
            this.draft.sections[sectionIndex].items.splice(itemIndex, 1);
        },

        previewOf(item) {
            return this.lookup[item.source + ':' + item.questionId] || null;
        },

        previewText(item) {
            const preview = this.previewOf(item);
            return preview ? (preview.text || '') : '';
        },

        previewFallback(item) {
            const preview = this.previewOf(item);
            if (!preview || preview.available === false) {
                return 'سؤال در دسترس نیست';
            }
            return 'سؤال فقط‌تصویر';
        },

        previewMeta(item) {
            const preview = this.previewOf(item);
            const source = item.source === 'BANK' ? 'بانک سؤال' : 'باکس';
            if (!preview || preview.available === false) {
                return source;
            }
            return source + ' · ' + (preview.type === 'ESSAY' ? 'تشریحی' : 'تستی');
        },

        validateDraft() {
            const errors = {};
            if (!this.draft.title.trim()) {
                errors.title = 'عنوان برگه را بنویسید.';
            }
            if (this.draft.sections.length === 0) {
                errors.sections = 'برگه دست‌کم یک بخش لازم دارد.';
            }
            this.draft.sections.forEach((section, index) => {
                if (!section.title.trim()) {
                    errors['section' + index] = 'عنوان بخش را بنویسید.';
                }
            });
            this.errors = errors;
            if (Object.keys(errors).length) {
                this.error = 'برگه ذخیره نشد؛ فیلدهای مشخص‌شده را اصلاح کنید.';
                return false;
            }
            return true;
        },

        draftPayload() {
            const values = {};
            this.headerValueFields().forEach(field => {
                const value = (this.draft.headerValues[field] || '').trim();
                if (value) {
                    values[field] = value;
                }
            });
            return {
                title: this.draft.title.trim(),
                headerId: this.draft.headerId ? parseInt(this.draft.headerId, 10) : null,
                headerValues: values,
                showEssayAnswerSpace: this.draft.showEssayAnswerSpace,
                includeAnswerKey: this.draft.includeAnswerKey,
                sections: this.draft.sections.map(s => ({
                    title: s.title.trim(),
                    instruction: s.instruction.trim() || null,
                    items: s.items.map(i => ({ source: i.source, questionId: i.questionId }))
                }))
            };
        },

        async saveSheet() {
            this.error = '';
            this.notice = '';
            if (this.saving || !this.validateDraft()) {
                return;
            }
            this.saving = true;
            try {
                const editing = this.draft.id !== null;
                const result = await QbApi.call(this.schoolId, editing ? 'PUT' : 'POST',
                    editing ? `/sheets/${this.draft.id}` : '/sheets', { json: this.draftPayload() });
                if (!result.ok) {
                    this.error = result.message;
                    this.blocked = result.blocking;
                    return;
                }
                this.draft.id = result.data.id;
                this.notice = editing ? 'برگه ذخیره شد.' : 'برگه ایجاد شد.';
            } finally {
                this.saving = false;
            }
        },

        // ---- انتخابگر سؤال ----

        openPicker(sectionIndex, event) {
            this.picker = {
                sectionIndex,
                opener: event ? event.currentTarget : null,
                tab: 'BOX',
                search: '',
                topicId: '',
                loading: false,
                error: ''
            };
            this.bankQuestions = [];
            this.$nextTick(() => {
                if (window.ModalFocus) {
                    window.ModalFocus.focusFirst(this.$refs.pickerModal, document.getElementById('pickerSearch'));
                }
            });
        },

        closePicker() {
            const opener = this.picker ? this.picker.opener : null;
            this.picker = null;
            if (window.ModalFocus) {
                window.ModalFocus.restore(opener);
            }
        },

        async pickTab(tab) {
            this.picker.tab = tab;
            this.picker.error = '';
            this.picker.search = '';
            if (tab === 'BANK' && this.topicsFlat.length === 0) {
                const result = await QbApi.call(this.schoolId, 'GET', `/api/admin/schools/${this.schoolId}/topics/tree`);
                if (!result.ok) {
                    this.picker.error = 'بانک سؤال در دسترس نیست: ' + result.message;
                    return;
                }
                this.topicsFlat = this.flatten(result.data, 0);
            }
        },

        flatten(nodes, depth) {
            let result = [];
            for (const node of nodes) {
                result.push({ id: node.id, name: node.name, depth });
                result = result.concat(this.flatten(node.children, depth + 1));
            }
            return result;
        },

        async loadBankQuestions() {
            if (!this.picker || !this.picker.topicId) {
                this.bankQuestions = [];
                return;
            }
            this.picker.loading = true;
            this.picker.error = '';
            try {
                const result = await QbApi.call(this.schoolId, 'GET',
                    `/api/admin/schools/${this.schoolId}/questions?topicId=${this.picker.topicId}`);
                if (!result.ok) {
                    this.picker.error = 'بانک سؤال در دسترس نیست: ' + result.message;
                    this.bankQuestions = [];
                    return;
                }
                this.bankQuestions = result.data.map(q => ({
                    id: q.id, text: q.text, type: 'MULTIPLE_CHOICE', complete: true, source: 'BANK'
                }));
            } finally {
                this.picker.loading = false;
            }
        },

        pickerRows() {
            if (!this.picker) {
                return [];
            }
            const rows = this.picker.tab === 'BOX'
                ? this.boxQuestions.map(q => ({
                    id: q.id, text: q.text, type: q.type, complete: q.complete, source: 'BOX', raw: q
                }))
                : this.bankQuestions;
            const query = this.picker.search.trim();
            return query ? rows.filter(r => ListSearch.matches(query, [r.text])) : rows;
        },

        isPicked(row) {
            const section = this.picker ? this.draft.sections[this.picker.sectionIndex] : null;
            return !!section && section.items.some(i => i.source === row.source && i.questionId === row.id);
        },

        pick(row) {
            const section = this.draft.sections[this.picker.sectionIndex];
            section.items.push({ source: row.source, questionId: row.id });
            this.lookup[row.source + ':' + row.id] = {
                available: true, text: row.text, type: row.type, source: row.source
            };
        },

        // ---- سربرگ‌ها ----

        openHeaderCreate(event) {
            this.notice = '';
            this.headerForm = emptyHeaderForm();
            this.headerForm.opener = event ? event.currentTarget : null;
            this.focusHeaderForm();
        },

        openHeaderEdit(header, event) {
            this.notice = '';
            this.headerForm = {
                ...emptyHeaderForm(),
                id: header.id,
                opener: event ? event.currentTarget : null,
                name: header.name,
                mode: header.mode,
                layout: header.layout || 'CLASSIC',
                showSchoolName: header.showSchoolName,
                fields: [...header.fields],
                hasImage: header.hasImage
            };
            this.focusHeaderForm();
        },

        focusHeaderForm() {
            this.$nextTick(() => {
                if (window.ModalFocus) {
                    window.ModalFocus.focusFirst(this.$refs.headerModal, document.getElementById('headerName'));
                }
            });
        },

        closeHeaderForm() {
            const opener = this.headerForm ? this.headerForm.opener : null;
            this.headerForm = null;
            if (window.ModalFocus) {
                window.ModalFocus.restore(opener);
            }
        },

        async saveHeader() {
            const f = this.headerForm;
            if (!f || this.savingHeader) {
                return;
            }
            f.errors = {};
            f.error = '';
            if (!f.name.trim()) {
                f.errors.name = 'نام سربرگ را بنویسید.';
                f.error = 'سربرگ ذخیره نشد؛ فیلدهای مشخص‌شده را اصلاح کنید.';
                return;
            }
            this.savingHeader = true;
            try {
                const editing = f.id !== null;
                const result = await QbApi.call(this.schoolId, editing ? 'PUT' : 'POST',
                    editing ? `/headers/${f.id}` : '/headers', {
                        json: {
                            name: f.name.trim(),
                            mode: f.mode,
                            layout: f.mode === 'LAYOUT' ? f.layout : null,
                            showSchoolName: f.showSchoolName,
                            showLogo: false,
                            fields: f.mode === 'LAYOUT' ? f.fields : []
                        }
                    });
                if (!result.ok) {
                    f.error = result.message;
                    return;
                }
                f.id = result.data.id;
                f.hasImage = result.data.hasImage;
                this.notice = editing ? 'سربرگ ذخیره شد.' : 'سربرگ ایجاد شد.';
                await this.load();
                if (f.mode === 'LAYOUT') {
                    this.closeHeaderForm();
                }
            } finally {
                this.savingHeader = false;
            }
        },

        async uploadHeaderImage(event) {
            const file = event.target.files[0];
            event.target.value = '';
            const f = this.headerForm;
            if (!file || !f || !f.id) {
                return;
            }
            this.uploading = true;
            f.error = '';
            try {
                const formData = new FormData();
                formData.append('file', file);
                const result = await QbApi.call(this.schoolId, 'PUT', `/headers/${f.id}/image`, { form: formData });
                if (!result.ok) {
                    f.error = result.message;
                    return;
                }
                QbApi.forgetImage('header', f.id);
                f.hasImage = false;
                this.$nextTick(() => { f.hasImage = true; });
                this.notice = 'تصویر سربرگ بارگذاری شد.';
                await this.load();
            } finally {
                this.uploading = false;
            }
        },

        // ---- حذف برگه / سربرگ ----

        askDeleteSheet(sheet, event) {
            this.openRemoval({ kind: 'sheet', id: sheet.id, label: sheet.title }, event);
        },

        askDeleteHeader(header, event) {
            this.openRemoval({ kind: 'header', id: header.id, label: header.name }, event);
        },

        openRemoval(removal, event) {
            this.notice = '';
            this.removal = { ...removal, opener: event ? event.currentTarget : null };
            this.$nextTick(() => {
                if (window.ModalFocus) {
                    window.ModalFocus.focusFirst(this.$refs.deleteModal, null);
                }
            });
        },

        closeRemoval() {
            const opener = this.removal ? this.removal.opener : null;
            this.removal = null;
            if (window.ModalFocus) {
                window.ModalFocus.restore(opener);
            }
        },

        async confirmRemoval() {
            if (!this.removal || this.deleting) {
                return;
            }
            this.deleting = true;
            try {
                const path = this.removal.kind === 'header' ? `/headers/${this.removal.id}` : `/sheets/${this.removal.id}`;
                const result = await QbApi.call(this.schoolId, 'DELETE', path);
                const kind = this.removal.kind;
                this.closeRemoval();
                if (!result.ok) {
                    this.error = result.message;
                    return;
                }
                this.notice = kind === 'header' ? 'سربرگ حذف شد.' : 'برگه حذف شد.';
                await this.load();
            } finally {
                this.deleting = false;
            }
        }
    }));
});
