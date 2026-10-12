// انتخابگر پایه و درس برای صفحه‌های سرفصل، آزمون و برنامه‌ی مطالعه.
// فهرست درس‌ها از /courses می‌آید و درختِ هر درس فقط از /topics/{topicId}/subtree،
// پس هیچ صفحه‌ای کلِ درختِ سرفصل (۵٬۶۵۶ گره) را نمی‌خواند.
//
// استفاده: هر Alpine component این دو بخش را پخش می‌کند:
//   ...CoursePicker.state(), ...CoursePicker.methods
// و خودش دو چیز دارد: schoolId و topicsBase() (همان فرم بقیه‌ی صفحه).
// با انتخاب درس، flatTopics صفحه پر می‌شود و هوک onCoursePicked() اگر باشد صدا زده می‌شود.
window.CoursePicker = {
    state() {
        return {
            pickerGrade: '',
            pickerCourses: [],
            pickerCourseId: '',
            pickerCoursesLoading: false,
            pickerTopicsLoading: false,
            pickerError: ''
        };
    },

    methods: {
        // پایه‌ی انتخاب‌شده را می‌گیرد و فهرست درس‌های همان پایه را می‌خواند. درسِ بی‌سرفصل
        // (topicId تهی) نشان داده نمی‌شود، چون درختی برای بارگذاری ندارد.
        async pickerLoadCourses() {
            this.pickerCourseId = '';
            this.pickerCourses = [];
            this.flatTopics = [];
            this.pickerError = '';
            if (!this.pickerGrade || !this.schoolId) {
                return;
            }
            this.pickerCoursesLoading = true;
            try {
                const response = await fetch(
                    `/api/admin/schools/${this.schoolId}/courses?gradeLevel=${encodeURIComponent(this.pickerGrade)}`,
                    { headers: { 'X-School-ID': this.schoolId } }
                );
                if (!response.ok) {
                    this.pickerError = 'فهرست درس‌ها بارگذاری نشد.';
                    return;
                }
                const courses = await response.json();
                this.pickerCourses = courses.filter(course => course.topicId !== null);
            } catch (e) {
                this.pickerError = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.pickerCoursesLoading = false;
            }
        },

        // درختِ زیرِ سرفصل درس را به فهرست تخت تبدیل می‌کند، با همان فرمت flattenTree صفحه‌ها.
        async pickerLoadTopics() {
            this.flatTopics = [];
            this.pickerError = '';
            if (!this.pickerCourseId) {
                return;
            }
            const course = this.pickerCourses.find(c => String(c.id) === String(this.pickerCourseId));
            if (!course) {
                return;
            }
            this.pickerTopicsLoading = true;
            try {
                const response = await fetch(`${this.topicsBase()}/${course.topicId}/subtree`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    this.pickerError = 'سرفصل‌های این درس بارگذاری نشد.';
                    return;
                }
                const subtree = await response.json();
                this.flatTopics = this.flattenTree([subtree], 0);
                if (typeof this.onCoursePicked === 'function') {
                    this.onCoursePicked();
                }
            } catch (e) {
                this.pickerError = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.pickerTopicsLoading = false;
            }
        }
    }
};
