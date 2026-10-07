document.addEventListener('alpine:init', () => {
    // «رویدادهای نزدیک» روی پیشخوان (openspec/changes/event-reminders.md): رویدادهای برنامه‌ی سالانه‌ی مدرسه‌ی
    // انتخاب‌شده که پنجره‌ی یادآوری‌شان شروع شده است. هر عضوِ گیرنده می‌خواند (endpoint خودسرویس)؛ برای غیرگیرنده
    // لیست خالی می‌آید. هر حالتِ بدونِ داده (بارگذاریِ اول، لیستِ خالی، مدرسه‌ی انتخاب‌نشده، خطا) بخش را پنهان
    // می‌گذارد و پیشخوان را نمی‌شکند. منطقِ خالص در upcoming-events-logic.js است.
    const Logic = window.UpcomingEventsLogic;

    Alpine.data('dashboardUpcoming', () => ({
        schoolId: null,
        rows: [],
        // پاسخ کهنه (مدرسه‌ی عوض‌شده) دور ریخته می‌شود
        seq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.refresh();
            });
            this.refresh();
        },

        async refresh() {
            const seq = ++this.seq;
            this.rows = [];
            if (!this.schoolId) {
                return;
            }
            const schoolId = this.schoolId;
            try {
                const payload = await SchoolApi.request(schoolId, 'GET',
                    `/api/me/schools/${schoolId}/upcoming-events`, undefined, { entitled: '', denied: '' });
                if (seq === this.seq) {
                    this.rows = Logic.rows(payload);
                }
            } catch (e) {
                if (seq === this.seq) {
                    this.rows = [];
                }
            }
        }
    }));
});
