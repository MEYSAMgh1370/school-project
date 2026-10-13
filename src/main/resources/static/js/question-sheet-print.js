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
    const LETTERS = ['الف', 'ب', 'ج', 'د'];

    Alpine.data('questionSheetPrintPage', () => ({
        sheet: null,
        schoolName: '',
        error: '',

        async init() {
            const schoolId = QbApi.schoolId();
            if (!schoolId) {
                this.error = 'مدرسه‌ای انتخاب نشده است؛ از برگه‌های سؤال دوباره وارد شوید.';
                return;
            }
            const holder = document.querySelector('[data-school-id="' + schoolId + '"]');
            this.schoolName = holder ? holder.dataset.schoolName : '';
            const result = await QbApi.call(schoolId, 'GET', `/sheets/${this.$el.dataset.sheetId}/resolved`);
            if (!result.ok) {
                this.error = result.message;
                return;
            }
            this.sheet = result.data;
        },

        num(value) {
            return QbApi.digits(value);
        },

        fieldLabel(field) {
            return FIELD_LABELS[field] || field;
        },

        // عنوان از خودِ برگه می‌آید؛ نام دانش‌آموز و نمره روی کاغذ خالی می‌مانند
        fieldValue(field) {
            if (field === 'TITLE') {
                return this.sheet.title;
            }
            return (this.sheet.headerValues && this.sheet.headerValues[field]) || '';
        },

        choiceLetter(index) {
            return LETTERS[index] || '';
        },

        imagesOf(item, role) {
            return item.images.filter(image => image.role === role);
        },

        choiceImageCount(item) {
            return item.images.filter(image => image.role.startsWith('CHOICE_')).length;
        },

        answerKeyItems() {
            if (!this.sheet) {
                return [];
            }
            return this.sheet.sections.flatMap(section => section.items)
                .filter(item => item.available && (item.correctChoice || item.answer || this.imagesOf(item, 'ANSWER').length > 0));
        },

        printSheet() {
            window.print();
        }
    }));
});
