document.addEventListener('alpine:init', () => {
    Alpine.data('financePage', () => ({
        schoolId: null,
        tuitionFee: null,
        invoices: [],
        students: [],
        pupils: [],
        blocked: false,
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        form: { amount: '' },

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
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

        tuitionFeeBase() {
            return `/api/admin/schools/${this.schoolId}/tuition-fee`;
        },

        invoicesBase() {
            return `/api/admin/schools/${this.schoolId}/invoices`;
        },

        studentsBase() {
            return `/api/admin/schools/${this.schoolId}/students`;
        },

        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        // فاکتور فقط studentId (شناسه‌ی academic) دارد، نه نام - همان دلیل
        // teacherName در classes.js: باید از academic.StudentController (id
        // به userId) و iam.MembershipController (userId به نام) ترکیب شود.
        // اگر نقش کاربر (مثلاً FINANCE_ADMIN) اجازه‌ی STUDENT_ENROLL نداشته
        // باشد، این دو فراخوانی 403 می‌شوند - صفحه نمی‌شکند، فقط شناسه‌ی خام نشان داده می‌شود.
        studentName(studentId) {
            const student = this.students.find(s => s.studentId === studentId);
            if (!student) {
                return 'دانش‌آموز #' + studentId;
            }
            const pupil = this.pupils.find(p => p.userId === student.userId);
            return pupil ? pupil.fullName : 'دانش‌آموز #' + studentId;
        },

        // نوع خطای دولایه‌ی §5 را از URI فیلد type در ProblemDetail تشخیص
        // می‌دهد تا «ماژول فعال نیست» را از «نقش شما اجازه ندارد» جدا کند
        // (UX_ROADMAP.md §3، اولین صفحه‌ای که این تفکیک لازم است).
        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول مالی را نخریده است. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دسترسی به بخش مالی را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        async load() {
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                const feeResponse = await fetch(this.tuitionFeeBase(), { headers: { 'X-School-ID': this.schoolId } });
                if (feeResponse.status === 404) {
                    this.tuitionFee = null;
                } else if (!feeResponse.ok) {
                    const classified = await this.classifyError(feeResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                } else {
                    this.tuitionFee = (await feeResponse.json()).amount;
                }

                const invoicesResponse = await fetch(this.invoicesBase(), { headers: { 'X-School-ID': this.schoolId } });
                if (!invoicesResponse.ok) {
                    const classified = await this.classifyError(invoicesResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.invoices = await invoicesResponse.json();

                // بهترین تلاش برای نام دانش‌آموز - اگر نقش فعلی اجازه‌ی
                // STUDENT_ENROLL نداشته باشد، فقط شناسه‌ی خام نمایش داده می‌شود
                const [studentsResponse, membershipsResponse] = await Promise.all([
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                this.students = studentsResponse.ok ? await studentsResponse.json() : [];
                this.pupils = membershipsResponse.ok
                    ? (await membershipsResponse.json()).filter(m => m.active && m.roleCode === 'STUDENT')
                    : [];
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async setTuitionFee() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.tuitionFeeBase(), {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ amount: this.form.amount })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                this.notice = 'شهریه با موفقیت ثبت شد.';
                this.form.amount = '';
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
