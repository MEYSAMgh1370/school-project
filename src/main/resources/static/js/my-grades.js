document.addEventListener('alpine:init', () => {
    Alpine.data('myGradesPage', () => ({
        schoolId: null,
        grades: [],
        // فیلتر یک فرزند از لینک داشبورد (?student=<id>)؛ null یعنی همه. فقط
        // نمایش است: API همه‌ی فرزندانِ لینک‌شده را می‌دهد و دسترسی سمت سرور
        // می‌ماند. شناسه‌ی نامعتبر یا ناآشنا فقط فهرستِ خالی می‌دهد.
        studentFilter: null,
        loading: false,
        error: '',

        init() {
            const requested = parseInt(new URLSearchParams(window.location.search).get('student') || '', 10);
            this.studentFilter = Number.isInteger(requested) ? requested : null;
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        // بدون X-School-ID: خودِ endpoint هم مثل بقیه‌ی /api/me/** از
        // TenantResolverFilter مستثناست - schoolId از path می‌آید
        // (academic.MyGradesController، همان الگوی my-invoices.js)
        async load() {
            this.loading = true;
            this.error = '';
            try {
                const response = await fetch(`/api/me/schools/${this.schoolId}/grades`);
                if (!response.ok) {
                    try {
                        const body = await response.json();
                        this.error = body.detail || body.title || 'خطایی رخ داد.';
                    } catch (e) {
                        this.error = 'خطایی رخ داد. (' + response.status + ')';
                    }
                    return;
                }
                this.grades = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        visibleGrades() {
            return this.studentFilter === null
                ? this.grades
                : this.grades.filter((grade) => grade.studentId === this.studentFilter);
        },

        termLabel(term) {
            return term === 'FIRST' ? 'نوبت اول' : 'نوبت دوم';
        },

        gradeValue(grade) {
            if (grade.scheme === 'DESCRIPTIVE') {
                return {
                    EXCELLENT: 'خیلی خوب',
                    GOOD: 'خوب',
                    ACCEPTABLE: 'قابل قبول',
                    NEEDS_EFFORT: 'نیازمند تلاش'
                }[grade.descriptiveLevel] || '—';
            }
            return grade.numericScore !== null && grade.numericScore !== undefined
                ? Jalali.toPersianDigits(grade.numericScore) : '—';
        }
    }));
});
