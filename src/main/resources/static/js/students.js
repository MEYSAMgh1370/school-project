document.addEventListener('alpine:init', () => {
    Alpine.data('studentsPage', () => ({
        schoolId: null,
        students: [],
        classes: [],
        pupils: [],
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        form: { email: '', fullName: '', classId: '' },

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

        studentsBase() {
            return `/api/admin/schools/${this.schoolId}/students`;
        },

        classesBase() {
            return `/api/admin/schools/${this.schoolId}/classes`;
        },

        // نام/ایمیل دانش‌آموز از iam.MembershipController گرفته می‌شود - ماژول
        // academic فقط userId را به‌عنوان یک Long مات نگه می‌دارد (همان الگوی
        // teacherName در classes.js)
        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        className(classId) {
            const classItem = this.classes.find(c => c.id === classId);
            return classItem ? classItem.name : 'نامشخص';
        },

        studentName(userId) {
            const pupil = this.pupils.find(p => p.userId === userId);
            return pupil ? pupil.fullName : 'نامشخص';
        },

        studentEmail(userId) {
            const pupil = this.pupils.find(p => p.userId === userId);
            return pupil ? pupil.email : '';
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
                const [studentsResponse, classesResponse, membershipsResponse] = await Promise.all([
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.classesBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                if (!studentsResponse.ok) {
                    await this.showError(studentsResponse);
                    this.students = [];
                    return;
                }
                this.students = await studentsResponse.json();
                // فهرست کلاس‌ها برای پر کردن دراپ‌داون و نمایش نام کلاس است؛
                // اگر در دسترس نبود (مثلاً نقش کاربر اجازه‌ی CLASS_MANAGE
                // ندارد)، صفحه‌ی دانش‌آموزان را نمی‌شکند
                this.classes = classesResponse.ok ? await classesResponse.json() : [];
                this.pupils = membershipsResponse.ok
                    ? (await membershipsResponse.json()).filter(m => m.active && m.roleCode === 'STUDENT')
                    : [];
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async enrollStudent() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.studentsBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        email: this.form.email,
                        fullName: this.form.fullName,
                        // select همیشه رشته برمی‌گرداند؛ تبدیل صریح به عدد چون
                        // classId سمت سرور Long است
                        classId: this.form.classId ? parseInt(this.form.classId, 10) : null
                    })
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                this.notice = 'دانش‌آموز با موفقیت ثبت‌نام شد.';
                this.form = { email: '', fullName: '', classId: '' };
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
