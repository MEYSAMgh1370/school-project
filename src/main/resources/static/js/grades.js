document.addEventListener('alpine:init', () => {
    Alpine.data('gradesPage', () => ({
        schoolId: null,
        classes: [],
        students: [],
        pupils: [],
        // درس‌های سراسریِ هم‌پایه‌ی کلاسِ انتخاب‌شده؛ courseId خالی = «نمره‌ی کل»
        courses: [],
        filter: { classId: '', courseId: '', academicYear: '', term: 'FIRST' },
        roster: [],
        filterText: '',
        loading: false,
        loaded: false,
        blocked: false,
        error: '',
        notice: '',

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.loaded = false;
                this.roster = [];
                this.courses = [];
                this.filter = { classId: '', courseId: '', academicYear: '', term: 'FIRST' };
                this.loadClasses();
            });
            // با عوض‌شدنِ کلاس، فهرست درس‌های همان پایه گرفته می‌شود و انتخابِ
            // درس به «نمره‌ی کل» برمی‌گردد (درسِ پایه‌ی قبلی برای این کلاس معتبر نیست).
            this.$watch('filter.classId', () => {
                this.filter.courseId = '';
                this.loadCourses();
            });
            if (this.schoolId) {
                this.loadClasses();
            }
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        // سال تحصیلی رشته‌ی آزاد است (مثل "۱۴۰۴-۱۴۰۵") - ورودیِ رقم فارسی/عربی
        // باید قبل از ارسال به سرور به رقم انگلیسی نرمال شود (DESIGN.md §8)
        normalizeDigits(value) {
            if (!value) {
                return value;
            }
            const persian = '۰۱۲۳۴۵۶۷۸۹';
            const arabic = '٠١٢٣٤٥٦٧٨٩';
            return String(value).replace(/[۰-۹٠-٩]/g, (ch) => {
                let index = persian.indexOf(ch);
                if (index === -1) {
                    index = arabic.indexOf(ch);
                }
                return index === -1 ? ch : String(index);
            });
        },

        classesBase() {
            return `/api/admin/schools/${this.schoolId}/classes`;
        },

        studentsBase() {
            return `/api/admin/schools/${this.schoolId}/students`;
        },

        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        gradesBase() {
            return `/api/admin/schools/${this.schoolId}/grades`;
        },

        async loadCourses() {
            this.courses = [];
            const classId = this.filter.classId ? parseInt(this.filter.classId, 10) : null;
            if (!classId) {
                return;
            }
            try {
                const response = await fetch(`${this.gradesBase()}/courses?classId=${classId}`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    // بدون فهرست درس‌ها، «نمره‌ی کل» همچنان قابل ثبت است
                    this.error = (await this.classifyError(response)).message;
                    return;
                }
                // اگر کاربر در این فاصله کلاس را عوض کرده، پاسخِ کلاس قبلی را دور بریز
                if (parseInt(this.filter.classId, 10) === classId) {
                    this.courses = await response.json();
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        courseLabel() {
            const id = this.filter.courseId ? parseInt(this.filter.courseId, 10) : null;
            const course = this.courses.find(c => c.id === id);
            return course ? course.name : 'نمره‌ی کل';
        },

        // همان تفکیک دولایه‌ی §5 که finance.js/expenses.js دارند - ACADEMIC
        // ماژول رایگان است (CLAUDE.md §11 Q10) ولی هنوز می‌تواند از طریق
        // facade پس گرفته شود، پس این تفکیک همچنان واقعی و لازم است
        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول آموزشی را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی مدیریت نمرات را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        async loadClasses() {
            this.error = '';
            this.blocked = false;
            try {
                const [classesResponse, studentsResponse, membershipsResponse] = await Promise.all([
                    fetch(this.classesBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                if (!classesResponse.ok) {
                    const classified = await this.classifyError(classesResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.classes = await classesResponse.json();
                this.students = studentsResponse.ok ? await studentsResponse.json() : [];
                if (membershipsResponse.ok) {
                    const memberships = await membershipsResponse.json();
                    this.pupils = memberships.filter(m => m.active && m.roleCode === 'STUDENT');
                } else {
                    // TEACHER can manage grades but intentionally cannot read the
                    // complete membership directory. Resolve only the roster names
                    // through MEMBER_NAMES_VIEW so every grade row remains identifiable.
                    this.pupils = await window.MemberNames.resolve(
                        this.schoolId,
                        this.students.map(student => student.userId)
                    );
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        selectedClassInfo() {
            const id = this.filter.classId ? parseInt(this.filter.classId, 10) : null;
            return this.classes.find(c => c.id === id) || null;
        },

        selectedScheme() {
            const classInfo = this.selectedClassInfo();
            return classInfo ? classInfo.gradingScheme : null;
        },

        studentName(userId) {
            const pupil = this.pupils.find(p => p.userId === userId);
            return pupil ? pupil.fullName : 'نامشخص';
        },

        rosterStudents() {
            const classId = this.filter.classId ? parseInt(this.filter.classId, 10) : null;
            if (!classId) {
                return [];
            }
            return this.students.filter(s => s.classId === classId);
        },

        visibleRoster() {
            const query = this.filterText.trim();
            if (!query) {
                return this.roster;
            }
            return this.roster.filter(row => row.name.includes(query));
        },

        async load() {
            if (!this.filter.classId || !this.filter.academicYear) {
                return;
            }
            this.filter.academicYear = this.normalizeDigits(this.filter.academicYear).trim();
            this.loading = true;
            this.error = '';
            this.notice = '';
            this.blocked = false;
            this.loaded = false;
            try {
                const classId = parseInt(this.filter.classId, 10);
                const params = new URLSearchParams({
                    classId: String(classId),
                    academicYear: this.filter.academicYear,
                    term: this.filter.term
                });
                if (this.filter.courseId) {
                    params.set('courseId', this.filter.courseId);
                }
                const response = await fetch(`${this.gradesBase()}?${params}`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                const existingGrades = await response.json();
                this.roster = this.rosterStudents()
                    .map(student => {
                        const existing = existingGrades.find(g => g.studentId === student.studentId);
                        return {
                            studentId: student.studentId,
                            name: this.studentName(student.userId),
                            descriptiveLevel: existing ? existing.descriptiveLevel : '',
                            numericScore: existing && existing.numericScore !== null && existing.numericScore !== undefined
                                ? existing.numericScore : '',
                            comment: existing ? (existing.comment || '') : '',
                            recordedAt: existing ? existing.recordedAt : null,
                            validationError: '',
                            saving: false
                        };
                    })
                    .sort((a, b) => a.name.localeCompare(b.name, 'fa'));
                this.loaded = true;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async saveGrade(row) {
            const scheme = this.selectedScheme();
            row.validationError = '';
            if (scheme === 'DESCRIPTIVE' && !row.descriptiveLevel) {
                row.validationError = 'سطح ارزشیابی را انتخاب کنید.';
            } else if (scheme === 'NUMERIC' && (row.numericScore === '' || row.numericScore === null)) {
                row.validationError = 'نمره را وارد کنید.';
            } else if (scheme === 'NUMERIC' && (Number(row.numericScore) < 0 || Number(row.numericScore) > 20)) {
                row.validationError = 'نمره باید بین ۰ تا ۲۰ باشد.';
            }
            if (row.validationError) {
                this.error = 'برخی نمرات نامعتبرند - قبل از ثبت آن‌ها را اصلاح کنید.';
                return;
            }

            row.saving = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.gradesBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        studentId: row.studentId,
                        classId: parseInt(this.filter.classId, 10),
                        academicYear: this.filter.academicYear,
                        term: this.filter.term,
                        descriptiveLevel: scheme === 'DESCRIPTIVE' ? row.descriptiveLevel : null,
                        numericScore: scheme === 'NUMERIC' ? row.numericScore : null,
                        comment: row.comment || null,
                        courseId: this.filter.courseId ? parseInt(this.filter.courseId, 10) : null
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                const saved = await response.json();
                row.recordedAt = saved.recordedAt;
                this.notice = `نمره‌ی ${row.name} (${this.courseLabel()}) با موفقیت ثبت شد.`;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                row.saving = false;
            }
        }
    }));
});
