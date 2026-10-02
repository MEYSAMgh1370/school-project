document.addEventListener('alpine:init', () => {
    Alpine.data('myGradesPage', () => ({
        schoolId: null,
        grades: [],
        // نام فرزندان از سمت سرور (DOM) می‌آید؛ API نمره‌ها نام نمی‌دهد.
        wardRefs: [],
        // دانش‌آموزی که از لینک داشبورد (?student=<id>) خواسته شده؛ null یعنی هیچ.
        // فقط تعیین می‌کند کدام دانش‌آموز از قبل انتخاب باشد: API همه‌ی فرزندانِ
        // لینک‌شده را می‌دهد و دسترسی سمت سرور می‌ماند. شناسه‌ی نامعتبر یا
        // ناآشنا نادیده می‌ماند.
        requestedStudentId: null,
        selectedStudentId: null,
        pickerOpen: false,
        loading: false,
        error: '',

        init() {
            // کل مقدار باید عدد باشد؛ پیشوندِ عددی («100junk») نادیده می‌ماند.
            const raw = new URLSearchParams(window.location.search).get('student') || '';
            const requested = /^\d+$/.test(raw) ? parseInt(raw, 10) : NaN;
            this.requestedStudentId = Number.isInteger(requested) ? requested : null;
            this.wardRefs = Array.from(document.querySelectorAll('#my-grades-wards span')).map((node) => ({
                studentId: parseInt(node.dataset.studentId, 10),
                schoolId: parseInt(node.dataset.schoolId, 10),
                name: node.dataset.name,
                own: node.dataset.own === 'true'
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
                this.resolveSelection();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        // یک گزینه برای هر دانش‌آموزِ شناخته‌شده‌ی این مدرسه از سمت سرور (فرزندان و
        // خودِ کاربر «نمره‌های من»، حتی بدون نمره) + هر دانش‌آموزِ ناشناخته‌ای که در
        // نمره‌ها آمده (محافظ: به‌عنوان «نمره‌های من»). فرزندان بر اساس نام مرتب
        // می‌شوند و گزینه‌ی خودِ کاربر اول می‌آید.
        groups() {
            const byStudent = new Map();
            this.wardRefs
                .filter((ward) => ward.schoolId === this.schoolId)
                .forEach((ward) => byStudent.set(ward.studentId,
                    { studentId: ward.studentId, label: ward.name, own: ward.own, grades: [] }));
            this.grades.forEach((grade) => {
                if (!byStudent.has(grade.studentId)) {
                    byStudent.set(grade.studentId,
                        { studentId: grade.studentId, label: 'نمره‌های من', own: true, grades: [] });
                }
                byStudent.get(grade.studentId).grades.push(grade);
            });
            // ردیف‌های هر دانش‌آموز: سال، نوبت، سپس «نمره‌ی کل» و بعد درس‌ها به ترتیب نام
            byStudent.forEach((group) => group.grades.sort((a, b) =>
                a.academicYear.localeCompare(b.academicYear)
                || a.term.localeCompare(b.term)
                || (a.courseName || '').localeCompare(b.courseName || '', 'fa')));
            return Array.from(byStudent.values()).sort((a, b) =>
                (a.own === b.own) ? a.label.localeCompare(b.label, 'fa') : (a.own ? -1 : 1));
        },

        // دانش‌آموزِ انتخاب‌شده: همان که از لینک خواسته شده اگر در این مدرسه هست؛
        // وگرنه انتخابِ قبلی اگر هنوز وجود دارد؛ وگرنه اولین گزینه‌ی فهرست. با
        // عوض‌شدنِ مدرسه دوباره حساب می‌شود (گزینه‌ی قبلی ممکن است دیگر نباشد).
        resolveSelection() {
            const ids = this.groups().map((group) => group.studentId);
            if (this.selectedStudentId === null || !ids.includes(this.selectedStudentId)) {
                this.selectedStudentId = ids.includes(this.requestedStudentId)
                    ? this.requestedStudentId
                    : (ids.length > 0 ? ids[0] : null);
            }
            this.pickerOpen = false;
        },

        selectedGroup() {
            return this.groups().find((group) => group.studentId === this.selectedStudentId) || null;
        },

        isSelected(studentId) {
            return this.selectedStudentId === studentId;
        },

        select(studentId) {
            this.selectedStudentId = studentId;
            this.closePicker(true);
        },

        // بستنِ فهرست؛ با Escape یا انتخاب، فوکوس به دکمه برمی‌گردد (گزینه‌ی
        // فوکوس‌شده با بسته‌شدن پنهان می‌شود و کاربر کیبورد جای خود را گم می‌کرد).
        closePicker(returnFocus) {
            this.pickerOpen = false;
            if (returnFocus && this.$refs.pickerToggle) {
                this.$refs.pickerToggle.focus();
            }
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
