document.addEventListener('alpine:init', () => {
    Alpine.data('myGradesPage', () => ({
        schoolId: null,
        grades: [],
        // نام فرزندان از سمت سرور (DOM) می‌آید؛ API نمره‌ها نام نمی‌دهد.
        wardRefs: [],
        // دانش‌آموزی که از لینک داشبورد (?student=<id>) خواسته شده؛ null یعنی هیچ.
        // فقط تعیین می‌کند کدام بخش باز شود: API همه‌ی فرزندانِ لینک‌شده را
        // می‌دهد و دسترسی سمت سرور می‌ماند. شناسه‌ی نامعتبر/ناآشنا نادیده می‌ماند.
        requestedStudentId: null,
        openStudentId: null,
        loading: false,
        error: '',

        init() {
            const requested = parseInt(new URLSearchParams(window.location.search).get('student') || '', 10);
            this.requestedStudentId = Number.isInteger(requested) ? requested : null;
            this.wardRefs = Array.from(document.querySelectorAll('#my-grades-wards span')).map((node) => ({
                studentId: parseInt(node.dataset.studentId, 10),
                schoolId: parseInt(node.dataset.schoolId, 10),
                name: node.dataset.name
            }));
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
                this.resolveOpenSection();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        // یک بخش برای هر فرزندِ این مدرسه (حتی بدون نمره) + هر دانش‌آموزی که
        // در نمره‌ها هست و فرزندِ لینک‌شده نیست (یعنی خودِ کاربر: «نمره‌های من»).
        // فرزندان بر اساس نام مرتب می‌شوند.
        groups() {
            const byStudent = new Map();
            this.wardRefs
                .filter((ward) => ward.schoolId === this.schoolId)
                .forEach((ward) => byStudent.set(ward.studentId,
                    { studentId: ward.studentId, label: ward.name, own: false, grades: [] }));
            this.grades.forEach((grade) => {
                if (!byStudent.has(grade.studentId)) {
                    byStudent.set(grade.studentId,
                        { studentId: grade.studentId, label: 'نمره‌های من', own: true, grades: [] });
                }
                byStudent.get(grade.studentId).grades.push(grade);
            });
            return Array.from(byStudent.values()).sort((a, b) =>
                (a.own === b.own) ? a.label.localeCompare(b.label, 'fa') : (a.own ? -1 : 1));
        },

        // بخشِ بازِ اولیه: دانش‌آموزِ خواسته‌شده از لینک اگر در این مدرسه هست، وگرنه
        // اگر فقط یک بخش هست همان؛ وگرنه همه بسته‌اند. با عوض‌شدنِ مدرسه دوباره
        // حساب می‌شود (بخشِ بازِ قبلی ممکن است دیگر وجود نداشته باشد).
        resolveOpenSection() {
            const ids = this.groups().map((group) => group.studentId);
            if (this.openStudentId !== null && !ids.includes(this.openStudentId)) {
                this.openStudentId = null;
            }
            if (this.openStudentId === null && ids.includes(this.requestedStudentId)) {
                this.openStudentId = this.requestedStudentId;
            } else if (this.openStudentId === null && ids.length === 1) {
                this.openStudentId = ids[0];
            }
        },

        isOpen(studentId) {
            return this.openStudentId === studentId;
        },

        // آکاردئون: باز کردنِ یک بخش بقیه را می‌بندد؛ کلیک روی بخشِ باز آن را می‌بندد.
        toggle(studentId) {
            this.openStudentId = this.isOpen(studentId) ? null : studentId;
        },

        countLabel(count) {
            return count === 0 ? 'بدون نمره' : Jalali.toPersianDigits(count) + ' نمره';
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
