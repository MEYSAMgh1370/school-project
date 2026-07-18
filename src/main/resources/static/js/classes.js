// بازه‌ی پایه‌ی هر مقطع تحصیلی - نسخه‌ی سمت کلاینتِ school.SchoolFacade.SchoolLevel
// (CLAUDE.md §4)؛ این دو را با هم هماهنگ نگه دارید.
const SCHOOL_LEVEL_GRADE_RANGES = {
    ELEMENTARY: { minGrade: 1, maxGrade: 6 },
    LOWER_SECONDARY: { minGrade: 7, maxGrade: 9 },
    UPPER_SECONDARY: { minGrade: 10, maxGrade: 12 }
};

document.addEventListener('alpine:init', () => {
    Alpine.data('classesPage', () => ({
        schoolId: null,
        school: null,
        classes: [],
        teachers: [],
        students: [],
        pupils: [],
        selectedClassId: null,
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        form: { name: '', section: '', teacherId: '', gradeLevel: '' },

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.selectedClassId = null;
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

        schoolBase() {
            return `/api/admin/schools/${this.schoolId}`;
        },

        gradeRangeHelp() {
            if (!this.school) {
                return '';
            }
            return `پایه‌ی مجاز برای این مدرسه: ${this.school.minGrade} تا ${this.school.maxGrade}`;
        },

        // نام معلم از iam.MembershipController گرفته می‌شود - ماژول academic
        // فقط teacherId را به‌عنوان یک Long مات نگه می‌دارد (طبق مرز ماژولی،
        // بدون FK به جداول iam)، پس نام باید سمت کلاینت resolve شود.
        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        // فهرست دانش‌آموزان کلاس هم از همان الگوی composition استفاده می‌کند:
        // academic فقط studentId/userId/classId می‌دهد (بدون نام)، نام/ایمیل
        // از همان فهرست عضویت‌های STUDENT که برای students.js هم استفاده
        // می‌شود resolve می‌شود - بدون فراخوانی جدا و بدون تغییر بک‌اند.
        studentsBase() {
            return `/api/admin/schools/${this.schoolId}/students`;
        },

        teacherName(teacherId) {
            if (!teacherId) {
                return 'تخصیص‌نشده';
            }
            const teacher = this.teachers.find(t => t.userId === teacherId);
            return teacher ? teacher.fullName : 'تخصیص‌نشده';
        },

        rosterFor(classId) {
            // اگر عضویت‌ها در دسترس نبود (نقش کاربر MEMBERSHIP_MANAGE ندارد -
            // مثلاً TEACHER)، ردیف حذف نمی‌شود؛ همان الگوی fallback که
            // finance.js برای نام دانش‌آموز در فاکتورها استفاده می‌کند - وگرنه
            // تعداد دانش‌آموزان کلاس برای TEACHER همیشه صفر نشان داده می‌شد.
            return this.students
                .filter(s => s.classId === classId)
                .map(s => {
                    const pupil = this.pupils.find(p => p.userId === s.userId);
                    return {
                        userId: s.userId,
                        fullName: pupil ? pupil.fullName : `دانش‌آموز #${s.userId}`,
                        email: pupil ? pupil.email : '—'
                    };
                });
        },

        rosterForSelected() {
            return this.selectedClassId ? this.rosterFor(this.selectedClassId) : [];
        },

        selectedClassName() {
            const classItem = this.classes.find(c => c.id === this.selectedClassId);
            return classItem ? classItem.name : '';
        },

        toggleRoster(classId) {
            this.selectedClassId = this.selectedClassId === classId ? null : classId;
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
                const [classesResponse, membershipsResponse, studentsResponse, schoolResponse] = await Promise.all([
                    fetch(this.classesBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.schoolBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                if (!classesResponse.ok) {
                    await this.showError(classesResponse);
                    this.classes = [];
                    return;
                }
                this.classes = await classesResponse.json();
                // فقط برای نمایش بازه‌ی مجاز پایه در فرم - best-effort، اگر
                // مدرسه به هر دلیلی در دسترس نبود صفحه نمی‌شکند (اعتبارسنجی
                // واقعی همیشه سمت سرور انجام می‌شود).
                if (schoolResponse.ok) {
                    const schoolInfo = await schoolResponse.json();
                    const range = SCHOOL_LEVEL_GRADE_RANGES[schoolInfo.level];
                    this.school = range ? { ...schoolInfo, ...range } : null;
                } else {
                    this.school = null;
                }
                // فهرست معلم‌ها/دانش‌آموزها فقط برای پر کردن دراپ‌داون و نمایش
                // نام است؛ اگر خودِ عضویت‌ها در دسترس نبود (مثلاً نقش کاربر
                // اجازه‌ی MEMBERSHIP_MANAGE ندارد)، صفحه‌ی کلاس‌ها را نمی‌شکند -
                // فقط نام معلم‌ها «تخصیص‌نشده» و فهرست دانش‌آموزان کلاس خالی
                // نمایش داده می‌شود.
                const memberships = membershipsResponse.ok ? await membershipsResponse.json() : [];
                this.teachers = memberships.filter(m => m.active && m.roleCode === 'TEACHER');
                this.pupils = memberships.filter(m => m.active && m.roleCode === 'STUDENT');
                // فهرست دانش‌آموزان کلاس هم به همین شکل best-effort است؛ اگر
                // نقش کاربر STUDENT_ENROLL نداشت، فهرست خالی می‌ماند و دکمه‌ی
                // «مشاهده دانش‌آموزان» فقط چیزی نشان نمی‌دهد - صفحه نمی‌شکند.
                this.students = studentsResponse.ok ? await studentsResponse.json() : [];
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
                        teacherId: this.form.teacherId ? parseInt(this.form.teacherId, 10) : null,
                        gradeLevel: parseInt(this.form.gradeLevel, 10)
                    })
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                this.notice = 'کلاس جدید اضافه شد.';
                this.form = { name: '', section: '', teacherId: '', gradeLevel: '' };
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
