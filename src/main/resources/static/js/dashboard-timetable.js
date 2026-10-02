document.addEventListener('alpine:init', () => {
    // «برنامه‌ی امروز» روی داشبوردِ دانش‌آموز (openspec/changes/class-timetable.md، تصمیم ۱۰).
    // فقط وقتی نقشِ انتخاب‌شده STUDENT است داده می‌گیرد (برای نقش‌های دیگر هیچ درخواستی
    // نمی‌رود) و فقط «امروز» را از همان endpoint خودسرویسِ /my-timetable نشان می‌دهد.
    Alpine.data('dashboardTimetable', () => ({
        schoolId: null,
        academicYear: null,
        periods: [],
        entries: [],
        className: '',
        loading: false,
        // فقط بعد از یک بارگذاریِ موفق true است تا شکست «امروز کلاس ندارید» نشان ندهد
        loaded: false,
        error: '',
        // پاسخ کهنه (مدرسه یا نقشِ عوض‌شده) دور ریخته می‌شود
        seq: 0,
        todayCode: window.TimetableGrid.todayCode(),

        init() {
            window.TimetableGrid.watchToday((code) => {
                this.todayCode = code;
            });
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.refresh();
            });
            this.$watch('$store.nav.selectedRole', () => this.refresh());
            this.refresh();
        },

        isStudent() {
            return Alpine.store('nav').selectedRole === 'STUDENT';
        },

        clear() {
            this.academicYear = null;
            this.periods = [];
            this.entries = [];
            this.className = '';
            this.loaded = false;
            this.error = '';
        },

        async refresh() {
            const seq = ++this.seq;
            this.clear();
            this.loading = false;
            if (!this.isStudent() || !this.schoolId) {
                return;
            }
            this.loading = true;
            try {
                const response = await fetch(`/api/me/schools/${this.schoolId}/timetable`);
                if (!response.ok) {
                    if (seq === this.seq) {
                        this.error = 'برنامه‌ی امروز بارگذاری نشد.';
                    }
                    return;
                }
                const data = await response.json();
                if (seq === this.seq) {
                    const own = data.views.find((view) => view.kind === 'OWN');
                    this.academicYear = data.academicYear;
                    this.periods = data.periods;
                    this.entries = own ? own.entries : [];
                    this.className = own && own.className ? own.className : '';
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

        dayTitle() {
            return this.todayCode ? window.TimetableGrid.dayLabel(this.todayCode) : '';
        },

        isSchoolDay() {
            return this.todayCode !== null;
        },

        // درس‌های امروز به ترتیبِ زنگ
        todayRows() {
            if (!this.todayCode) {
                return [];
            }
            return this.periods
                .map((period) => ({ period, entry: window.TimetableGrid.entryAt(this.entries, this.todayCode, period.id) }))
                .filter((row) => row.entry !== null);
        },

        subjectOf(entry) {
            return window.TimetableGrid.subjectOf(entry);
        },

        rangeLabel(period) {
            return window.TimetableGrid.rangeLabel(period);
        }
    }));
});
