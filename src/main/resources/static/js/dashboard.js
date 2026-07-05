// کامپوننت‌های Alpine صفحه‌ی داشبورد - اولین فایل استاتیک طبق قانون
// جای‌گذاری کد (CLAUDE.md §2): JSای که fetch دارد در template نمی‌ماند.
// این فایل باید *قبل از* اسکریپت Alpine لود شود (layout/main.jte این را
// با pageScript تضمین می‌کند) تا Alpine.data پیش از alpine:init ثبت شود.
document.addEventListener('alpine:init', () => {

    // ساخت مدرسه‌ی self-service: POST /api/admin/schools - سازنده بلافاصله
    // SCHOOL_ADMIN می‌شود (SchoolController)، پس reload کافی است تا عضویت
    // تازه در جدول و سوییچر ظاهر شود. X-School-ID لازم نیست: این مسیر در
    // TenantResolverFilter مستثناست (مدرسه هنوز وجود ندارد که id داشته باشد).
    Alpine.data('createSchoolForm', () => ({
        open: false,
        name: '',
        timezone: 'Asia/Tehran',
        locale: 'fa-IR',
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
                        locale: this.locale.trim()
                    })
                });
                if (response.ok) {
                    window.location.reload();
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
