// کامپوننت صفحه‌ی ثبت مدرسه (/schools/new) - جابه‌جاشده از dashboard.js
// (تصمیم ۲۰۲۶-۰۷-۰۶: فرم ساخت مدرسه از داشبورد به صفحه‌ی اختصاصی رفت).
document.addEventListener('alpine:init', () => {

    // ساخت مدرسه‌ی self-service: POST /api/admin/schools - سازنده بلافاصله
    // SCHOOL_ADMIN می‌شود (SchoolController). X-School-ID لازم نیست: این
    // مسیر در TenantResolverFilter مستثناست (مدرسه هنوز وجود ندارد که id
    // داشته باشد). پس از موفقیت به داشبورد می‌رویم تا عضویت تازه در جدول
    // و سوییچر دیده شود.
    Alpine.data('createSchoolForm', () => ({
        name: '',
        timezone: 'Asia/Tehran',
        locale: 'fa-IR',
        // مقطع تحصیلی مدرسه (CLAUDE.md §4 - SchoolFacade.SchoolLevel): هر مدرسه
        // دقیقاً یک مقطع دارد، بازه‌ی پایه‌ی کلاس‌های آن مدرسه را تعیین می‌کند.
        level: 'ELEMENTARY',
        levelOptions: [
            { value: 'ELEMENTARY', label: 'دبستان (پایه‌های ۱ تا ۶)' },
            { value: 'LOWER_SECONDARY', label: 'متوسطه اول (پایه‌های ۷ تا ۹)' },
            { value: 'UPPER_SECONDARY', label: 'متوسطه دوم (پایه‌های ۱۰ تا ۱۲)' }
        ],
        submitting: false,
        errorMessage: '',
        get canSubmit() {
            return this.name.trim().length > 0 && this.timezone.trim().length > 0
                && this.locale.trim().length > 0 && !this.submitting;
        },
        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },
        async submit() {
            this.submitting = true;
            this.errorMessage = '';
            try {
                const response = await fetch('/api/admin/schools', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({
                        name: this.name.trim(),
                        timezone: this.timezone.trim(),
                        locale: this.locale.trim(),
                        level: this.level
                    })
                });
                if (response.ok) {
                    window.location.href = '/dashboard';
                    return;
                }
                let detail = '';
                try { detail = (await response.json()).detail || ''; } catch (e) {}
                this.errorMessage = detail || 'خطایی رخ داد. دوباره تلاش کنید.';
            } catch (e) {
                this.errorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
