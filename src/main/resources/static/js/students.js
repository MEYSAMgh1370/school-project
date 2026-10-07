document.addEventListener('alpine:init', () => {
    Alpine.data('studentsPage', () => ({
        schoolId: null,
        students: [],
        // کلاس‌های سالِ جاری (برای دراپ‌داونِ ثبت‌نام)
        classes: [],
        // کلاس‌های همه‌ی سال‌ها فقط برای نمایش نام (مدیر)؛ برای بقیه همان classes
        allClasses: [],
        pupils: [],
        parents: [],
        selectedStudentId: null,
        // جست‌وجوی آزاد روی نام، ایمیل و کلاس (فهرست ردیفی؛ DESIGN.md §6)
        filterText: '',
        guardiansByStudent: {},
        guardiansLoading: false,
        guardianForm: { guardianUserId: '' },
        guardianSubmitting: false,
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        form: { email: '', fullName: '', classId: '' },

        // میان‌بر دعوت بعد از ثبت‌نام (UX_ROADMAP.md §6.3) - دانش‌آموزِ تازه‌
        // ثبت‌نام‌شده‌ای که هنوز رمز عبور ندارد
        pendingInviteStudent: null,
        pendingInviteResult: null,
        invitingPending: false,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.selectedStudentId = null;
                this.filterText = '';
                this.guardiansByStudent = {};
                this.pendingInviteStudent = null;
                this.pendingInviteResult = null;
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        visibleStudents() {
            const query = this.filterText.trim();
            if (!query) {
                return this.students;
            }
            return this.students.filter(student => ListSearch.matches(query, [
                this.studentName(student.userId),
                this.studentEmail(student.userId),
                this.classLabel(student)
            ]));
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

        guardiansBase(studentId) {
            return `${this.studentsBase()}/${studentId}/guardians`;
        },

        className(classId) {
            const classItem = this.allClasses.find(c => c.id === classId) || this.classes.find(c => c.id === classId);
            return classItem ? classItem.name : 'نامشخص';
        },

        // دانش‌آموزِ بدون کلاس (ارتقایافته و جا‌نشده) وضعیتش را نشان می‌دهد، نه «نامشخص»
        classLabel(student) {
            if (student.classId) {
                return this.className(student.classId);
            }
            return student.status === 'PENDING' ? 'در انتظار جا دادن در کلاس' : '—';
        },

        studentName(userId) {
            const pupil = this.pupils.find(p => p.userId === userId);
            return pupil ? pupil.fullName : 'نامشخص';
        },

        studentEmail(userId) {
            const pupil = this.pupils.find(p => p.userId === userId);
            return pupil ? pupil.email : '';
        },

        // نام/ایمیل والد هم از همان فهرست عضویت‌ها resolve می‌شود، این بار
        // فیلترشده روی نقش PARENT - همان الگوی teacherName/studentName.
        guardianName(userId) {
            const parent = this.parents.find(p => p.userId === userId);
            return parent ? parent.fullName : `کاربر #${userId}`;
        },

        guardianEmail(userId) {
            const parent = this.parents.find(p => p.userId === userId);
            return parent ? parent.email : '—';
        },

        selectedStudentUserId() {
            const student = this.students.find(s => s.studentId === this.selectedStudentId);
            return student ? student.userId : null;
        },

        guardiansForSelected() {
            return this.selectedStudentId ? (this.guardiansByStudent[this.selectedStudentId] || []) : [];
        },

        // والدینی که هنوز به دانش‌آموز انتخاب‌شده لینک نشده‌اند - صرفاً برای
        // این‌که دراپ‌داون کسی را که از قبل لینک شده دوباره پیشنهاد ندهد؛
        // خودِ لینک کردن idempotent است، این فقط UX را تمیزتر می‌کند.
        availableParentsForSelected() {
            const linkedIds = new Set(this.guardiansForSelected());
            return this.parents.filter(p => !linkedIds.has(p.userId));
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
                const [studentsResponse, classesResponse, allClassesResponse, membershipsResponse] = await Promise.all([
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.classesBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    // همه‌ی سال‌ها فقط برای مدیرِ سال؛ برای بقیه 403 است و همان فهرستِ سالِ جاری می‌ماند
                    fetch(this.classesBase() + '?academicYear=all', { headers: { 'X-School-ID': this.schoolId } }),
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
                this.allClasses = allClassesResponse.ok ? await allClassesResponse.json() : this.classes;
                if (membershipsResponse.ok) {
                    const memberships = await membershipsResponse.json();
                    this.pupils = memberships.filter(m => m.active && m.roleCode === 'STUDENT');
                    this.parents = memberships.filter(m => m.active && m.roleCode === 'PARENT');
                } else {
                    // نقش‌هایی که MEMBERSHIP_MANAGE ندارند (مثلاً TEACHER) به فهرست
                    // کامل عضویت‌ها دسترسی ندارند - نام دانش‌آموزهای همین صفحه از
                    // مسیر محدودترِ MEMBER_NAMES_VIEW گرفته می‌شود (UX_ROADMAP.md
                    // §6.4). والدین بدون نام می‌مانند - لینک کردن ولی هم‌چنان فقط
                    // SCHOOL_ADMIN است (GUARDIAN_MANAGE)، پس این پنل برای TEACHER
                    // اصلاً باز نمی‌شود.
                    this.parents = [];
                    this.pupils = await window.MemberNames.resolve(
                        this.schoolId,
                        this.students.map(s => s.userId)
                    );
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async toggleGuardianPanel(studentId) {
            if (this.selectedStudentId === studentId) {
                this.selectedStudentId = null;
                return;
            }
            this.selectedStudentId = studentId;
            this.guardianForm = { guardianUserId: '' };
            if (!this.guardiansByStudent[studentId]) {
                await this.loadGuardiansFor(studentId);
            }
        },

        async loadGuardiansFor(studentId) {
            this.guardiansLoading = true;
            try {
                const response = await fetch(this.guardiansBase(studentId), {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    await this.showError(response);
                    this.guardiansByStudent[studentId] = [];
                    return;
                }
                this.guardiansByStudent[studentId] = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
                this.guardiansByStudent[studentId] = [];
            } finally {
                this.guardiansLoading = false;
            }
        },

        async linkGuardian() {
            if (!this.guardianForm.guardianUserId || !this.selectedStudentId) {
                return;
            }
            this.guardianSubmitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.guardiansBase(this.selectedStudentId), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ guardianUserId: parseInt(this.guardianForm.guardianUserId, 10) })
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                this.notice = 'ولی با موفقیت لینک شد.';
                this.guardianForm = { guardianUserId: '' };
                await this.loadGuardiansFor(this.selectedStudentId);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.guardianSubmitting = false;
            }
        },

        async enrollStudent() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            this.pendingInviteStudent = null;
            this.pendingInviteResult = null;
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
                const enrolled = await response.json();
                this.notice = 'دانش‌آموز با موفقیت ثبت‌نام شد.';
                this.form = { email: '', fullName: '', classId: '' };
                await this.load();

                // عضویتِ متناظرِ دانش‌آموزِ تازه‌ثبت‌نام‌شده را از همان فهرست
                // عضویت‌هایی که load() همین حالا تازه کرد پیدا می‌کنیم - بدون
                // fetch اضافه؛ اگر یافت نشد (مثلاً TEACHER بدون MEMBERSHIP_MANAGE
                // نتوانسته فهرست را بگیرد) میان‌بر به‌سادگی نمایش داده نمی‌شود.
                const pupilMembership = this.pupils.find(p => p.userId === enrolled.userId);
                this.pendingInviteStudent = pupilMembership
                    ? { membershipId: pupilMembership.membershipId, fullName: pupilMembership.fullName }
                    : null;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        },

        async sendInviteForPendingStudent() {
            if (!this.pendingInviteStudent) {
                return;
            }
            this.invitingPending = true;
            this.error = '';
            try {
                const response = await fetch(`${this.membershipsBase()}/${this.pendingInviteStudent.membershipId}/invite-token`, {
                    method: 'POST',
                    headers: {
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    }
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                const body = await response.json();
                if (!body.issued) {
                    this.pendingInviteResult = { alreadyActivated: true };
                } else if (body.emailSent) {
                    this.pendingInviteResult = { emailSent: true };
                } else {
                    this.pendingInviteResult = {
                        emailSent: false,
                        link: `${location.origin}/activate?token=${body.inviteToken}`
                    };
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.invitingPending = false;
            }
        },

        copyPendingInviteLink() {
            const input = this.$refs.pendingInviteLink;
            input.select();
            navigator.clipboard.writeText(input.value).catch(() => document.execCommand('copy'));
        }
    }));
});
