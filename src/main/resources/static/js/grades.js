document.addEventListener('alpine:init', () => {
    Alpine.data('gradesPage', () => ({
        schoolId: null,
        classes: [],
        classesSeq: 0,
        students: [],
        pupils: [],
        // درس‌های سراسریِ هم‌پایه‌ی کلاسِ انتخاب‌شده؛ courseId خالی = «نمره‌ی کل»
        courses: [],
        filter: { classId: '', courseId: '', academicYear: '', term: 'FIRST' },
        // سال‌هایی که کاربر می‌تواند انتخاب کند: مدیر (ACADEMIC_YEAR_MANAGE) همه‌ی فهرست، بقیه فقط
        // سالِ جاریِ مدرسه؛ خالی = مدرسه هنوز سالِ جاری ندارد و کاربر مدیر نیست (فیلدِ متنی)
        years: [],
        roster: [],
        filterText: '',
        loading: false,
        // شمارنده‌ی بارگذاری: فقط پاسخ آخرین درخواست روی roster اثر می‌گذارد
        // (تغییرِ سریع درس/کلاس پاسخ کهنه را دور می‌ریزد، وگرنه جدولِ یک درس
        // با برچسب و ثبتِ درسِ دیگر نمایش داده می‌شد).
        loadSeq: 0,
        // همین الگو برای درس‌ها و برای کلاس/دانش‌آموزان: فقط پاسخ آخرین درخواست
        // (و فقط برای همان مدرسه/کلاس) اثر می‌گذارد.
        courseSeq: 0,
        schoolSeq: 0,
        yearSeq: 0,
        loaded: false,
        // انتخاب‌هایی که جدولِ فعلی با آن‌ها ساخته شده؛ تغییرِ انتخاب با نمره‌ی ثبت‌نشده از اینجا برمی‌گردد
        loadedFor: {},
        blocked: false,
        error: '',
        notice: '',
        // ورود سریع (DESIGN.md §5a): نمای فهرست یا «تمرکز» روی یک دانش‌آموز، فیلتر با chip،
        // و حالتِ مرور پس از ثبتِ ناموفق. هیچ‌کدام endpoint تازه نمی‌خواهند: هر نمره همچنان
        // با همان POSTِ تکی ثبت می‌شود.
        view: 'list',
        focusId: null,
        chip: 'all',
        // شناسه‌ی دانش‌آموزانِ «نیاز به اصلاح» پس از آخرین «ثبت همه»؛ ثابت می‌ماند تا ردیف
        // وسطِ اصلاح از فهرست نپرد
        reviewIds: [],
        reviewSummary: '',
        savingAll: false,
        // ارتفاعِ کیبوردِ صفحه‌نمایش؛ نوارِ پایین درست بالای آن می‌نشیند
        keyboardInset: 0,

        init() {
            // نمره‌ی تایپ‌شده‌ی ثبت‌نشده با بستن/تازه‌کردنِ تب از بین می‌رود؛ هشدارِ خودِ
            // مرورگر (نه alert) جلوی از دست رفتنِ بی‌صدا را می‌گیرد. ذخیره‌ی پیش‌نویس
            // (DESIGN.md §13.5) هنوز تصمیم‌گرفته نشده است.
            window.addEventListener('beforeunload', (event) => {
                if (this.dirtyRows().length > 0) {
                    event.preventDefault();
                    event.returnValue = '';
                }
            });
            if (window.visualViewport) {
                const sync = () => {
                    const viewport = window.visualViewport;
                    this.keyboardInset = Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop);
                };
                window.visualViewport.addEventListener('resize', sync);
                window.visualViewport.addEventListener('scroll', sync);
            }
            this.initFilters();
        },

        initFilters() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.loadSeq++;
                this.courseSeq++;
                this.loading = false;
                this.loaded = false;
                this.roster = [];
                this.resetEntry();
                this.courses = [];
                this.filter = { classId: '', courseId: '', academicYear: '', term: 'FIRST' };
                this.years = [];
                this.loadClasses();
                this.loadYears();
            });
            // با عوض‌شدنِ کلاس، فهرست درس‌های همان پایه گرفته می‌شود و انتخابِ
            // درس به «نمره‌ی کل» برمی‌گردد (درسِ پایه‌ی قبلی برای این کلاس معتبر نیست).
            // جدولِ کلاس قبلی هم کنار گذاشته می‌شود (و بارگذاری در جریانش دور ریخته
            // می‌شود): ردیف‌های کلاس قبلی با کلاس جدید قابل ثبت نیستند.
            this.$watch('filter.classId', () => {
                if (this.revertUnsavedSelection('classId')) {
                    return;
                }
                this.loadSeq++;
                this.loading = false;
                this.loaded = false;
                this.roster = [];
                this.filter.courseId = '';
                this.loadCourses();
            });
            // سالِ دیگر یعنی جدولِ دیگر: ردیف‌های سالِ قبلی با سالِ تازه قابل ثبت نیستند (ثبت از filter.academicYear
            // می‌خواند) و فهرستِ درس‌ها هم به بازه‌ی اعتبارِ درس وابسته است
            // (فقط با فهرستِ سال‌ها؛ در فیلدِ متنی load() خودش سال را نرمال می‌کند و این واکنش بارگذاریِ
            // در جریان را باطل می‌کرد)
            this.$watch('filter.academicYear', () => {
                if (this.years.length === 0) {
                    return;
                }
                if (this.revertUnsavedSelection('academicYear')) {
                    return;
                }
                this.loadSeq++;
                this.loading = false;
                this.loaded = false;
                this.roster = [];
                this.loadCourses();
                // کلاس‌ها به سال تعلق دارند (academic-year.md فاز ۳)
                this.reloadClassesForYear();
            });
            // درس و نوبت هم جدول را عوض می‌کنند: با نمره‌ی ثبت‌نشده، همان برگشت را دارند
            this.$watch('filter.courseId', () => {
                if (this.revertUnsavedSelection('courseId')) {
                    return;
                }
                if ((this.loaded || this.loading) && String(this.filter.courseId) !== String(this.loadedFor.courseId)) {
                    this.load();
                }
            });
            this.$watch('filter.term', () => {
                if (this.revertUnsavedSelection('term')) {
                    return;
                }
                // جدولِ نوبتِ اول نباید زیرِ عنوانِ نوبتِ دوم بماند (نمره‌ها به نوبتِ اشتباه ثبت می‌شدند)
                if ((this.loaded || this.loading) && String(this.filter.term) !== String(this.loadedFor.term)) {
                    this.load();
                }
            });
            if (this.schoolId) {
                this.loadClasses();
                this.loadYears();
            }
        },

        // اگر نمره‌ی ثبت‌نشده هست و انتخاب عوض شده، انتخاب به همان چیزی برمی‌گردد که جدول با آن ساخته شده.
        // true یعنی برگشت انجام شد. تغییر انتخاب بدون نمره‌ی ثبت‌نشده آزاد است.
        revertUnsavedSelection(key) {
            if (this.dirtyRows().length === 0 || !(key in this.loadedFor)) {
                return false;
            }
            // back on the loaded selection (the revert itself fires the watcher again): nothing to change
            if (String(this.filter[key]) === String(this.loadedFor[key])) {
                return true;
            }
            this.filter[key] = this.loadedFor[key];
            this.error = 'نمره‌های ثبت‌نشده دارید. اول «ثبت همه» را بزنید یا آن‌ها را پاک کنید، سپس انتخاب را عوض کنید.';
            return true;
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

        // نمره با صفحه‌کلیدِ فارسی هم تایپ می‌شود: رقم‌های فارسی/عربی و جداکننده‌های «٫» «،» «/» «,»
        // به ASCII و نقطه نرمال می‌شوند (DESIGN.md §8). مقدارِ خام در state می‌ماند؛ هر
        // اعتبارسنجی و ارسال از همین تابع می‌گذرد.
        normalizeScore(raw) {
            if (raw === null || raw === undefined) {
                return '';
            }
            return this.normalizeDigits(String(raw)).replace(/[٫،,]/g, '.').trim();
        },

        fa(value) {
            return window.Calendar ? window.Calendar.digits(String(value)) : String(value);
        },

        // عددِ اعشاری برای نمایش: رقم فارسی و ممیزِ «٫» (DESIGN.md §8)؛ داده و ارسال ASCII می‌مانند
        faNumber(value) {
            return this.fa(String(value).replace('.', '٫'));
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
            const seq = ++this.courseSeq;
            this.courses = [];
            const classId = this.filter.classId ? parseInt(this.filter.classId, 10) : null;
            if (!classId) {
                return;
            }
            try {
                // فقط وقتی سال یکی از سال‌های فهرست است (در فیلدِ متنی، نیمه‌تایپ‌شده سالِ نامعتبر می‌شد)
                const yearQuery = this.years.includes(this.filter.academicYear)
                    ? `&academicYear=${encodeURIComponent(this.filter.academicYear)}` : '';
                const response = await fetch(`${this.gradesBase()}/courses?classId=${classId}${yearQuery}`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    // بدون فهرست درس‌ها، «نمره‌ی کل» همچنان قابل ثبت است
                    const message = (await this.classifyError(response)).message;
                    if (seq === this.courseSeq) {
                        this.error = message;
                    }
                    return;
                }
                // بدنه اول خوانده می‌شود و درست قبل از انتساب بررسی می‌شود که هنوز همین
                // درخواست آخرین است (کلاس/مدرسه ممکن است حین خواندنِ بدنه عوض شده باشد)
                const courses = await response.json();
                if (seq === this.courseSeq) {
                    this.courses = courses;
                    // درسِ انتخاب‌شده‌ای که برای این سال ارائه نمی‌شود به «نمره‌ی کل» برمی‌گردد
                    if (this.filter.courseId && !courses.some(c => String(c.id) === String(this.filter.courseId))) {
                        this.filter.courseId = '';
                    }
                }
            } catch (e) {
                if (seq === this.courseSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
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

        // سال‌های قابل‌انتخاب و سالِ جاریِ مدرسه (openspec/changes/academic-year.md): مدیر (ACADEMIC_YEAR_MANAGE)
        // همه‌ی فهرست را از endpoint ادمین می‌گیرد و هر سال را می‌تواند باز کند؛ بقیه فقط سالِ جاری را
        // می‌بینند. سالِ جاری پیش‌فرضِ انتخاب است و فقط وقتی پر می‌شود که خالی باشد و مدرسه عوض
        // نشده باشد؛ بدون هیچ سالی، فیلد متنی می‌ماند و سرور سالِ ناشناخته را رد می‌کند.
        async loadYears() {
            const seq = ++this.yearSeq;
            const schoolId = this.schoolId;
            try {
                let codes = [];
                let current = null;
                const adminResponse = await fetch(`/api/admin/schools/${schoolId}/academic-year`,
                    { headers: { 'X-School-ID': schoolId } });
                if (adminResponse.ok) {
                    const settings = await adminResponse.json();
                    codes = settings.years.map(y => y.code);
                    current = settings.current ? settings.current.code : null;
                } else {
                    const response = await fetch(`/api/me/schools/${schoolId}/academic-year`);
                    if (!response.ok) {
                        return;
                    }
                    const settings = await response.json();
                    current = settings.current ? settings.current.code : null;
                    codes = current ? [current] : [];
                }
                if (seq !== this.yearSeq || schoolId !== this.schoolId) {
                    return;
                }
                this.years = codes;
                if (current && !this.filter.academicYear) {
                    // پس از رندرِ گزینه‌ها، تا x-model روی select مقدار را از دست ندهد
                    this.$nextTick(() => {
                        if (seq === this.yearSeq && !this.filter.academicYear) {
                            this.filter.academicYear = current;
                        }
                    });
                }
            } catch (e) {
                // پیش‌فرض اختیاری است؛ نبودنش مانع کار نیست
            }
        },

        async loadClasses() {
            const seq = ++this.schoolSeq;
            const schoolId = this.schoolId;
            this.error = '';
            this.blocked = false;
            try {
                const [classesResponse, studentsResponse, membershipsResponse] = await Promise.all([
                    fetch(this.classesBase(), { headers: { 'X-School-ID': schoolId } }),
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': schoolId } })
                ]);
                if (!classesResponse.ok) {
                    const classified = await this.classifyError(classesResponse);
                    if (seq === this.schoolSeq) {
                        this.error = classified.message;
                        this.blocked = classified.blocking;
                    }
                    return;
                }
                const classes = await classesResponse.json();
                const students = studentsResponse.ok ? await studentsResponse.json() : [];
                let pupils;
                if (membershipsResponse.ok) {
                    const memberships = await membershipsResponse.json();
                    // نامِ دانش‌آموزِ ترک‌کرده (عضویتِ غیرفعال) هم برای دفترِ نمراتِ سالِ قبل لازم است
                    pupils = memberships.filter(m => m.roleCode === 'STUDENT');
                } else {
                    // TEACHER can manage grades but intentionally cannot read the
                    // complete membership directory. Resolve only the roster names
                    // through MEMBER_NAMES_VIEW so every grade row remains identifiable.
                    pupils = await window.MemberNames.resolve(
                        schoolId,
                        students.map(student => student.userId)
                    );
                }
                // اگر در این فاصله مدرسه عوض شده، پاسخِ مدرسه‌ی قبلی دور ریخته می‌شود
                if (seq !== this.schoolSeq) {
                    return;
                }
                this.classes = classes;
                this.students = students;
                this.pupils = pupils;
            } catch (e) {
                if (seq === this.schoolSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            }
        },

        // کلاس‌های سالِ انتخاب‌شده (برای مدیر که سالِ دیگری را باز می‌کند)؛ اگر کلاسِ انتخاب‌شده در آن سال نبود
        // انتخاب پاک می‌شود. شکست (کاربری که فقط سالِ جاری را می‌بیند) فهرستِ قبلی را نگه می‌دارد.
        async reloadClassesForYear() {
            const year = this.normalizeDigits(this.filter.academicYear || '').trim();
            if (!year || this.years.length === 0) {
                return;
            }
            const seq = ++this.classesSeq;
            const schoolId = this.schoolId;
            try {
                const response = await fetch(`${this.classesBase()}?academicYear=${encodeURIComponent(year)}`,
                    { headers: { 'X-School-ID': schoolId } });
                if (!response.ok) {
                    return;
                }
                const classes = await response.json();
                if (seq !== this.classesSeq || schoolId !== this.schoolId
                    || year !== this.normalizeDigits(this.filter.academicYear || '').trim()) {
                    return;
                }
                this.classes = classes;
                if (this.filter.classId && !classes.some(c => String(c.id) === String(this.filter.classId))) {
                    this.filter.classId = '';
                }
            } catch (e) {
                // فهرستِ قبلی می‌ماند
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

        // دانش‌آموزانِ کلاس: فهرستِ سمتِ سرور (شاملِ کسانی که در سالی در آن بوده‌اند)، وگرنه دانش‌آموزانِ فعلیِ کلاس
        rosterStudents(classStudents) {
            const classId = this.filter.classId ? parseInt(this.filter.classId, 10) : null;
            if (!classId) {
                return [];
            }
            return classStudents || this.students.filter(s => s.classId === classId);
        },

        visibleRoster() {
            const query = this.filterText.trim();
            if (!query) {
                return this.roster;
            }
            return this.roster.filter(row => ListSearch.matches(query, [row.name]));
        },

        async load() {
            if (!this.filter.classId || !this.filter.academicYear) {
                return;
            }
            // «بارگذاری» جدول را از سرور دوباره می‌سازد و نمره‌های تایپ‌شده‌ی ثبت‌نشده را دور می‌ریزد
            if (this.dirtyRows().length > 0) {
                this.error = 'نمره‌های ثبت‌نشده دارید. اول «ثبت همه» را بزنید، سپس دوباره بارگذاری کنید.';
                return;
            }
            this.filter.academicYear = this.normalizeDigits(this.filter.academicYear).trim();
            const seq = ++this.loadSeq;
            const requested = {
                classId: this.filter.classId,
                academicYear: this.filter.academicYear,
                courseId: this.filter.courseId,
                term: this.filter.term
            };
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
                    if (seq !== this.loadSeq) {
                        return;
                    }
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                const existingGrades = await response.json();
                const rosterResponse = await fetch(`${this.studentsBase()}?classId=${classId}`,
                    { headers: { 'X-School-ID': this.schoolId } });
                const classStudents = rosterResponse.ok ? await rosterResponse.json() : null;
                if (seq !== this.loadSeq) {
                    return;
                }
                this.roster = this.rosterStudents(classStudents)
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
                            suggestion: '',
                            // اطلاعات کمکی (فقط نمایش؛ ثبت را هرگز مسدود نمی‌کنند): غیبت/تأخیرِ امروز و نمره‌ی نوبتِ قبل
                            absence: '',
                            absenceExcused: false,
                            previous: '',
                            saving: false
                        };
                    })
                    .sort((a, b) => a.name.localeCompare(b.name, 'fa'));
                this.roster.forEach(row => { row.savedKey = this.rowKey(row); });
                this.resetEntry();
                this.loadedFor = requested;
                this.loaded = true;
                // بعد از نمایش جدول و بدونِ انتظار؛ هر شکستی بی‌صدا نادیده گرفته می‌شود
                this.loadAttendanceHint(seq, classId);
                this.loadPreviousTerm(seq, classId);
            } catch (e) {
                if (seq === this.loadSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.loadSeq) {
                    this.loading = false;
                }
            }
        },

        // ---- اطلاعاتِ کمکیِ ردیف (فقط نمایش) -----------------------------------------------------

        todayIso() {
            const now = new Date();
            const pad = (n) => String(n).padStart(2, '0');
            return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
        },

        // غیبت/تأخیرِ امروزِ کلاس از ماژول حضور و غیاب (رایگان و برای همه‌ی مدرسه‌ها فعال). فقط
        // وقتی حضور و غیابِ امروز ثبت شده باشد برچسب دارد؛ معلمِ غیرِ این کلاس 403 می‌گیرد و
        // چیزی نمایش داده نمی‌شود. نمره‌دادن هرگز بسته نمی‌شود: نمره سطحِ نوبت است نه روز.
        async loadAttendanceHint(seq, classId) {
            try {
                const response = await fetch(
                    `/api/admin/schools/${this.schoolId}/attendance/classes/${classId}/days/${this.todayIso()}`,
                    { headers: { 'X-School-ID': this.schoolId } });
                if (!response.ok) {
                    return;
                }
                const day = await response.json();
                if (seq !== this.loadSeq || !day.taken) {
                    return;
                }
                const marks = new Map(day.students.map(m => [m.studentId, m]));
                this.roster.forEach(row => {
                    const mark = marks.get(row.studentId);
                    row.absence = mark && mark.status ? mark.status : '';
                    row.absenceExcused = !!(mark && mark.excused);
                });
            } catch (e) {
                // اطلاعاتِ کمکی است؛ نبودنش مانع کار نیست
            }
        },

        absenceLabel(row) {
            if (row.absence === 'ABSENT') {
                return row.absenceExcused ? 'غایب امروز (موجه)' : 'غایب امروز';
            }
            return row.absence === 'LATE' ? 'تأخیر امروز' : '';
        },

        levelLabel(code) {
            return window.GradeLabels.descriptive[code] || '';
        },

        // نمره‌ی نوبت اول، فقط هنگام وارد کردنِ نوبت دوم و برای همان کلاس/سال/درس؛ هم‌سنجی بین
        // درس‌ها یا سال‌ها حدس است و نمی‌آید. برای نوبت اول چیزی برای مقایسه نیست.
        async loadPreviousTerm(seq, classId) {
            if (this.filter.term !== 'SECOND') {
                return;
            }
            try {
                const params = new URLSearchParams({
                    classId: String(classId),
                    academicYear: this.filter.academicYear,
                    term: 'FIRST'
                });
                if (this.filter.courseId) {
                    params.set('courseId', this.filter.courseId);
                }
                const response = await fetch(`${this.gradesBase()}?${params}`,
                    { headers: { 'X-School-ID': this.schoolId } });
                if (!response.ok) {
                    return;
                }
                const grades = await response.json();
                if (seq !== this.loadSeq) {
                    return;
                }
                this.roster.forEach(row => {
                    const grade = grades.find(g => g.studentId === row.studentId);
                    if (!grade) {
                        return;
                    }
                    row.previous = grade.numericScore !== null && grade.numericScore !== undefined
                        ? this.faNumber(grade.numericScore) : this.levelLabel(grade.descriptiveLevel);
                });
            } catch (e) {
                // اطلاعاتِ کمکی است؛ نبودنش مانع کار نیست
            }
        },

        // ---- ورود سریع -------------------------------------------------------------------------

        resetEntry() {
            this.view = 'list';
            this.focusId = null;
            this.chip = 'all';
            this.reviewIds = [];
            this.reviewSummary = '';
            this.filterText = '';
        },

        rowKey(row) {
            return [this.normalizeScore(row.numericScore), row.descriptiveLevel || '', row.comment || ''].join('|');
        },

        isFilled(row) {
            return this.selectedScheme() === 'DESCRIPTIVE'
                ? !!row.descriptiveLevel
                : this.normalizeScore(row.numericScore) !== '';
        },

        isDirty(row) {
            return this.rowKey(row) !== row.savedKey;
        },

        // ردیف‌هایی که مقدار دارند و از آخرین ثبت تغییر کرده‌اند — همان‌ها که «ثبت همه» می‌فرستد
        dirtyRows() {
            return this.roster.filter(row => this.isDirty(row) && this.isFilled(row));
        },

        // وضعیتِ هر ردیف همیشه با کلمه می‌آید، نه فقط رنگ (DESIGN.md §4.3)
        rowStatus(row) {
            if (row.validationError) {
                return { label: 'نیاز به اصلاح', cls: 'is-danger' };
            }
            if (this.isDirty(row) && this.isFilled(row)) {
                return { label: 'ثبت‌نشده', cls: 'is-warning' };
            }
            if (row.recordedAt) {
                return { label: 'ثبت‌شده', cls: 'is-success' };
            }
            return { label: 'بدون نمره', cls: 'is-light' };
        },

        // سرگروه‌های فیلتر؛ «نیاز به اصلاح» فقط پس از یک ثبتِ ناموفق پیدا می‌شود
        chips() {
            const chips = [
                { key: 'all', label: 'همه', count: this.roster.length },
                { key: 'empty', label: 'بدون نمره', count: this.roster.filter(r => !this.isFilled(r)).length }
            ];
            if (this.reviewIds.length > 0) {
                chips.push({ key: 'fix', label: 'نیاز به اصلاح', count: this.reviewIds.length });
            }
            return chips;
        },

        showChips() {
            return this.roster.length > 25 || this.reviewIds.length > 0;
        },

        selectChip(key) {
            this.chip = key;
        },

        shownRoster() {
            return this.visibleRoster().filter(row => {
                if (this.chip === 'empty') {
                    return !this.isFilled(row);
                }
                if (this.chip === 'fix') {
                    return this.reviewIds.includes(row.studentId);
                }
                return true;
            });
        },

        // ترتیبِ گام‌برداریِ حالتِ تمرکز: در مرور فقط موارد نیازمند اصلاح، وگرنه فهرستِ فیلترشده
        steppingRows() {
            return this.reviewIds.length > 0
                ? this.roster.filter(row => this.reviewIds.includes(row.studentId))
                : this.shownRoster();
        },

        focusRow() {
            return this.roster.find(row => row.studentId === this.focusId) || null;
        },

        focusPosition() {
            return this.steppingRows().findIndex(row => row.studentId === this.focusId);
        },

        nextRow() {
            const rows = this.steppingRows();
            const index = this.focusPosition();
            return index >= 0 && index < rows.length - 1 ? rows[index + 1] : null;
        },

        prevRow() {
            const rows = this.steppingRows();
            const index = this.focusPosition();
            return index > 0 ? rows[index - 1] : null;
        },

        // دکمه‌ی اصلی نامِ مقصد را می‌گوید («بعدی: نام»)؛ در آخرین مورد کارِ پایانی را
        nextLabel() {
            const next = this.nextRow();
            return next ? 'بعدی: ' + next.name : 'ثبت همه';
        },

        startFocus(row) {
            this.focusId = row.studentId;
            this.view = 'focus';
            this.$nextTick(() => {
                const field = document.getElementById('gradeFocusField');
                if (field) {
                    field.focus();
                }
            });
        },

        exitFocus() {
            this.view = 'list';
            this.focusId = null;
        },

        // «رد کردن»: بدونِ نمره و بدونِ هیچ قاعده‌ی تازه‌ای به بعدی می‌رود؛ ردیف همان «بدون نمره» می‌ماند
        skip() {
            const next = this.nextRow();
            if (next) {
                this.startFocus(next);
            } else {
                this.exitFocus();
            }
        },

        goPrev() {
            const prev = this.prevRow();
            if (prev) {
                this.startFocus(prev);
            }
        },

        // «بعدی» مقدار را همین‌جا می‌سنجد (خطا کنارِ فیلد می‌ماند) و فقط جابه‌جا می‌شود؛ ثبت با
        // «ثبت همه» یا دکمه‌ی «ثبت» همان ردیف انجام می‌شود
        async goNext() {
            const row = this.focusRow();
            if (!row) {
                return;
            }
            if (this.isFilled(row) && !this.validateRow(row)) {
                return;
            }
            const next = this.nextRow();
            if (next) {
                this.startFocus(next);
                return;
            }
            this.exitFocus();
            await this.saveAll();
        },

        // ---- اعتبارسنجی و ثبت ------------------------------------------------------------------

        validateRow(row) {
            const scheme = this.selectedScheme();
            row.validationError = '';
            row.suggestion = '';
            if (scheme === 'DESCRIPTIVE') {
                if (!row.descriptiveLevel) {
                    row.validationError = 'سطح ارزشیابی را انتخاب کنید.';
                }
            } else if (scheme === 'NUMERIC') {
                const text = this.normalizeScore(row.numericScore);
                const value = Number(text);
                if (text === '') {
                    row.validationError = 'نمره را وارد کنید.';
                } else if (!Number.isFinite(value)) {
                    row.validationError = 'نمره را به‌صورت عدد وارد کنید.';
                } else if (value < 0 || value > 20) {
                    row.validationError = 'نمره باید بین ۰ تا ۲۰ باشد.';
                    // سه‌رقمی بدونِ ممیز (۱۷۵) یعنی تقریباً حتماً ۱۷٫۵؛ دیگر مقدارهای بیرون از بازه
                    // مبهم‌اند و حدس نمی‌خورند
                    if (value >= 100 && value <= 200 && /^\d{3}$/.test(text)) {
                        row.suggestion = String(value / 10);
                    }
                }
            }
            return !row.validationError;
        },

        acceptSuggestion(row) {
            if (row.suggestion) {
                row.numericScore = row.suggestion;
                row.suggestion = '';
                row.validationError = '';
            }
        },

        // ثبتِ یک ردیف با همان POSTِ همیشگی؛ پیامِ خطا را برمی‌گرداند و state را خودش نمی‌نویسد
        // هدفِ ثبت (کلاس، سال، نوبت، درس) در لحظه‌ی شروعِ ثبت گرفته می‌شود: تغییرِ انتخاب‌ها
        // وسط یک «ثبت همه» نباید ردیف‌های بعدی را به نوبت یا درسِ دیگری ببرد
        saveTarget() {
            return {
                classId: parseInt(this.filter.classId, 10),
                academicYear: this.filter.academicYear,
                term: this.filter.term,
                courseId: this.filter.courseId ? parseInt(this.filter.courseId, 10) : null,
                scheme: this.selectedScheme()
            };
        },

        async postGrade(row, target = this.saveTarget()) {
            const scheme = target.scheme;
            const sentKey = this.rowKey(row);
            const response = await fetch(this.gradesBase(), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-School-ID': this.schoolId,
                    'X-XSRF-TOKEN': this.csrfToken()
                },
                body: JSON.stringify({
                    studentId: row.studentId,
                    classId: target.classId,
                    academicYear: target.academicYear,
                    term: target.term,
                    descriptiveLevel: scheme === 'DESCRIPTIVE' ? row.descriptiveLevel : null,
                    numericScore: scheme === 'NUMERIC' ? this.normalizeScore(row.numericScore) : null,
                    comment: row.comment || null,
                    courseId: target.courseId
                })
            });
            if (!response.ok) {
                const classified = await this.classifyError(response);
                return { ok: false, message: classified.message };
            }
            const saved = await response.json();
            row.recordedAt = saved.recordedAt;
            row.savedKey = sentKey;
            return { ok: true };
        },

        async saveGrade(row) {
            if (!this.validateRow(row)) {
                this.error = 'برخی نمرات نامعتبرند - قبل از ثبت آن‌ها را اصلاح کنید.';
                return;
            }

            row.saving = true;
            this.error = '';
            this.notice = '';
            // درس (و برچسبش) همان لحظه‌ی ارسال ثابت می‌شود: اگر کاربر وسط درخواست
            // درس را عوض کند، پیام موفقیت باید نام درسی را بگوید که واقعاً ثبت شد.
            const submittedCourseLabel = this.courseLabel();
            try {
                const result = await this.postGrade(row);
                if (!result.ok) {
                    this.error = result.message;
                    return;
                }
                this.notice = `نمره‌ی ${row.name} (${submittedCourseLabel}) با موفقیت ثبت شد.`;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                row.saving = false;
            }
        },

        // «ثبت همه»: همه‌ی ردیف‌های تغییرکرده را یکی‌یکی با همان POSTِ تکی می‌فرستد. دکمه هرگز برای
        // داده‌ی نامعتبر غیرفعال نمی‌شود؛ فشرده می‌شود و به حالتِ مرور می‌رود (DESIGN.md §6).
        async saveAll() {
            if (this.savingAll) {
                return;
            }
            this.error = '';
            this.notice = '';
            this.reviewSummary = '';
            const pending = this.dirtyRows();
            // ردیفِ نیمه‌پُرنشده (مقدارِ پاک‌شده) ثبت نمی‌شود و خطا هم نیست
            if (pending.length === 0) {
                this.notice = 'نمره‌ی تازه‌ای برای ثبت نیست.';
                return;
            }
            this.savingAll = true;
            const target = this.saveTarget();
            const courseLabel = this.courseLabel();
            let saved = 0;
            const failedIds = [];
            try {
                for (const row of pending) {
                    if (!this.validateRow(row)) {
                        failedIds.push(row.studentId);
                        continue;
                    }
                    row.saving = true;
                    try {
                        const result = await this.postGrade(row, target);
                        if (result.ok) {
                            saved++;
                        } else {
                            row.validationError = result.message;
                            failedIds.push(row.studentId);
                        }
                    } catch (e) {
                        row.validationError = 'ارتباط با سرور برقرار نشد.';
                        failedIds.push(row.studentId);
                    } finally {
                        row.saving = false;
                    }
                }
            } finally {
                this.savingAll = false;
            }
            this.reviewIds = failedIds;
            if (failedIds.length === 0) {
                this.chip = 'all';
                this.notice = `نمره‌ی ${this.fa(saved)} دانش‌آموز (${courseLabel}) با موفقیت ثبت شد.`;
                return;
            }
            this.chip = 'fix';
            this.reviewSummary = `${this.fa(saved)} نمره ثبت شد و ${this.fa(failedIds.length)} مورد نیاز به اصلاح دارد. `
                + 'هر مورد را اصلاح کنید و دوباره «ثبت همه» را بزنید.';
        },

        // دکمه‌ی اصلی در مرور: «اصلاح موارد (۱ از n)»؛ وقتی همه اصلاح شد، همان «ثبت همه»
        reviewLabel() {
            const open = this.roster.filter(r => this.reviewIds.includes(r.studentId) && r.validationError);
            if (open.length === 0) {
                return `ثبت همه (${this.fa(this.dirtyRows().length)})`;
            }
            return `اصلاح موارد (${this.fa(this.reviewIds.length - open.length + 1)} از ${this.fa(this.reviewIds.length)})`;
        },

        async primaryAction() {
            if (this.reviewIds.length > 0) {
                const open = this.roster.find(r => this.reviewIds.includes(r.studentId) && r.validationError);
                if (open) {
                    this.startFocus(open);
                    return;
                }
            }
            await this.saveAll();
        }
    }));
});
