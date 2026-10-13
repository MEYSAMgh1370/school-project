// «پنهان کردن» چک‌لیستِ راه‌اندازیِ مدرسه روی پیشخوان (school-admin-onboarding.md، بسته‌ی ۲). انتخاب فقط در
// localStorageِ همین مرورگر و به‌ازای هر مدرسه نگه داشته می‌شود؛ بدونِ localStorage (حالتِ خصوصی/مسدود)
// کارت همیشه نمایش داده می‌شود.
document.addEventListener('alpine:init', () => {
    Alpine.data('setupChecklist', () => ({
        hidden: false,
        schoolId: '',

        // $el داخلِ متدهایی که از رویدادِ دکمه صدا زده می‌شوند خودِ دکمه است، نه کارت؛ پس شناسه‌ی مدرسه
        // همین‌جا (روی ریشه‌ی کامپوننت) یک‌بار خوانده می‌شود.
        init() {
            this.schoolId = this.$el.dataset.schoolId;
            this.hidden = this.read() === '1';
        },

        key() {
            return 'setupChecklistHidden:' + this.schoolId;
        },

        read() {
            try {
                return window.localStorage.getItem(this.key());
            } catch (e) {
                return null;
            }
        },

        hide() {
            this.hidden = true;
            try {
                window.localStorage.setItem(this.key(), '1');
            } catch (e) {
                // بدونِ ذخیره‌سازی فقط تا بازخوانیِ صفحه پنهان می‌ماند.
            }
        }
    }));
});
