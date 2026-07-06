document.addEventListener('alpine:init', () => {
    Alpine.data('classesPage', () => ({
        schoolId: null,
        classes: [],
        teachers: [],
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        form: { name: '', section: '', teacherId: '' },

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

        classesBase() {
            return `/api/admin/schools/${this.schoolId}/classes`;
        },

        // نام معلم از iam.MembershipController گرفته می‌شود - ماژول academic
        // فقط teacherId را به‌عنوان یک Long مات نگه می‌دارد (طبق مرز ماژولی،
        // بدون FK به جداول iam)، پس نام باید سمت کلاینت resolve شود.
        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        teacherName(teacherId) {
            if (!teacherId) {
                return 'تخصیص‌نشده';
            }
            const teacher = this.teachers.find(t => t.userId === teacherId);
            return teacher ? teacher.fullName : 'تخصیص‌نشده';
        },

        async showError(response) {
            try {
                const body = await response.json();
                this.error = body.detail || body.title || 'خطایی رخ داد.';
            } catch (e) {
                this.error = 'خطایی رخ داد. (' + response.status + ')';
            }
        },

        async load() {
            this.loading = true;
            this.error = '';
            try {
                const [classesResponse, membershipsResponse] = await Promise.all([
                    fetch(this.classesBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                if (!classesResponse.ok) {
                    await this.showError(classesResponse);
                    this.classes = [];
                    return;
                }
                this.classes = await classesResponse.json();
                // فهرست معلم‌ها فقط برای پر کردن دراپ‌داون و نمایش نام است؛
                // اگر خودِ عضویت‌ها در دسترس نبود (مثلاً نقش کاربر اجازه‌ی
                // MEMBERSHIP_MANAGE ندارد)، صفحه‌ی کلاس‌ها را نمی‌شکند - فقط
                // نام معلم‌ها «تخصیص‌نشده» نمایش داده می‌شود.
                this.teachers = membershipsResponse.ok
                    ? (await membershipsResponse.json()).filter(m => m.active && m.roleCode === 'TEACHER')
                    : [];
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async createClass() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.classesBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        name: this.form.name,
                        section: this.form.section || null,
                        // select همیشه رشته برمی‌گرداند؛ تبدیل صریح به عدد چون
                        // teacherId سمت سرور Long است
                        teacherId: this.form.teacherId ? parseInt(this.form.teacherId, 10) : null
                    })
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                this.notice = 'کلاس جدید اضافه شد.';
                this.form = { name: '', section: '', teacherId: '' };
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
