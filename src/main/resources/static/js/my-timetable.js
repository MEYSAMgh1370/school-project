document.addEventListener('alpine:init', () => {
    Alpine.data('myTimetablePage', () => ({
        schoolId: null,
        academicYear: null,
        periods: [],
        views: [],
        selectedKey: null,
        pickerOpen: false,
        loading: false,
        // فقط بعد از یک بارگذاریِ موفق true است: با شکست، «برنامه‌ای نیست» نشان داده نمی‌شود
        loaded: false,
        error: '',
        // فقط پاسخ آخرین درخواست اثر می‌گذارد (عوض‌کردنِ سریعِ مدرسه پاسخ کهنه را دور می‌ریزد)
        seq: 0,
        days: window.TimetableGrid.DAYS,
        todayCode: window.TimetableGrid.todayCode(),

        init() {
            window.TimetableGrid.watchToday((code) => {
                this.todayCode = code;
            });
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

        // بدون X-School-ID: /api/me/** از TenantResolverFilter مستثناست و schoolId از path می‌آید
        async load() {
            const seq = ++this.seq;
            this.loading = true;
            this.loaded = false;
            this.error = '';
            // برنامه‌ی مدرسه‌ی قبلی نباید زیرِ مدرسه‌ی تازه بماند، حتی اگر این بارگذاری شکست بخورد
            this.academicYear = null;
            this.periods = [];
            this.views = [];
            this.selectedKey = null;
            this.pickerOpen = false;
            try {
                const response = await fetch(`/api/me/schools/${this.schoolId}/timetable`);
                if (!response.ok) {
                    let message;
                    try {
                        const body = await response.json();
                        message = body.detail || body.title || 'خطایی رخ داد.';
                    } catch (e) {
                        message = 'خطایی رخ داد. (' + response.status + ')';
                    }
                    if (seq === this.seq) {
                        this.error = message;
                    }
                    return;
                }
                const data = await response.json();
                if (seq === this.seq) {
                    this.academicYear = data.academicYear;
                    this.periods = data.periods;
                    this.views = data.views.map((view) => ({ ...view, key: this.keyOf(view) }));
                    this.selectedKey = this.views.length > 0 ? this.views[0].key : null;
                    this.loaded = true;
                }
            } catch (e) {
                if (seq === this.seq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.seq) {
                    this.loading = false;
                }
            }
        },

        keyOf(view) {
            return view.kind + ':' + (view.studentId === null ? '' : view.studentId);
        },

        // «خودم» اول، بعد فرزندان به ترتیب نام، و ساعت‌های تدریس آخر
        viewLabel(view) {
            if (view.kind === 'TEACHING') {
                return 'ساعت‌های تدریس من';
            }
            if (view.kind === 'WARD') {
                return view.studentName || 'فرزند';
            }
            return view.className ? 'برنامه‌ی من (' + view.className + ')' : 'برنامه‌ی من';
        },

        selectedView() {
            return this.views.find((view) => view.key === this.selectedKey) || null;
        },

        isTeaching() {
            const view = this.selectedView();
            return !!view && view.kind === 'TEACHING';
        },

        isSelected(key) {
            return this.selectedKey === key;
        },

        select(key) {
            this.selectedKey = key;
            this.closePicker(true);
        },

        closePicker(returnFocus) {
            this.pickerOpen = false;
            if (returnFocus && this.$refs.pickerToggle) {
                this.$refs.pickerToggle.focus();
            }
        },

        entryAt(weekday, periodId) {
            const view = this.selectedView();
            return view ? window.TimetableGrid.entryAt(view.entries, weekday, periodId) : null;
        },

        subjectOf(entry) {
            return window.TimetableGrid.subjectOf(entry);
        },

        // خط دوم: برای معلم نامِ کلاس، برای دانش‌آموز/ولی نامِ معلم
        metaOf(entry) {
            return this.isTeaching() ? (entry.className || '') : (entry.teacherName || '');
        },

        // خانه‌های یک زنگ در یک روز؛ برای معلم ممکن است چند کلاس نباشد (یک معلم در یک خانه فقط یک کلاس)
        hasAnyEntry() {
            const view = this.selectedView();
            return !!view && view.entries.length > 0;
        }
    }));
});
