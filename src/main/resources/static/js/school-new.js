// کامپوننت صفحه‌ی ثبت مدرسه (/schools/new) - جابه‌جاشده از dashboard.js
// (تصمیم ۲۰۲۶-۰۷-۰۶: فرم ساخت مدرسه از پیشخوان به صفحه‌ی اختصاصی رفت).
document.addEventListener('alpine:init', () => {

    // ساخت مدرسه‌ی self-service: POST /api/admin/schools - سازنده بلافاصله
    // SCHOOL_ADMIN می‌شود (SchoolController). X-School-ID لازم نیست: این
    // مسیر در TenantResolverFilter مستثناست (مدرسه هنوز وجود ندارد که id
    // داشته باشد). پس از موفقیت به پیشخوان می‌رویم تا عضویت تازه در جدول
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
        // روز شروع هفته‌ی درسی (W9 - SchoolFacade.WeekStart): شنبه‌محور = شنبه تا
        // چهارشنبه (هفته‌ی ایرانی، پیش‌فرض)، دوشنبه‌محور = دوشنبه تا جمعه. مقدار
        // همان نام enum سرور است؛ بعد از ساخت قابل تغییر نیست.
        weekStart: 'SATURDAY',
        weekStartOptions: [
            { value: 'SATURDAY', label: 'شنبه (شنبه تا چهارشنبه)' },
            { value: 'MONDAY', label: 'دوشنبه (دوشنبه تا جمعه)' }
        ],
        // نشانی اختصاصی (اختیاری): درِ ورودی برنددار مدرسه در /s/{slug}.
        // قالب همان قالب DNS label سرور است (SchoolFacadeImpl) - چک
        // client-side فقط UX است، اعتبارسنجی واقعی همیشه سمت سرور.
        slug: '',
        submitting: false,
        errorMessage: '',
        get slugValid() {
            const s = this.slug.trim().toLowerCase();
            return s.length >= 3 && s.length <= 63 && /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(s);
        },
        get slugPreview() {
            const s = this.slug.trim().toLowerCase();
            return window.location.origin + '/s/' + (s || 'tizhooshan');
        },
        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },
        async submit() {
            if (this.submitting) {
                return;
            }
            this.errorMessage = '';
            if (!this.name.trim()) {
                this.errorMessage = 'نام مدرسه را وارد کنید.';
                return;
            }
            if (!this.timezone.trim() || !this.locale.trim()) {
                this.errorMessage = 'منطقه‌ی زمانی و زبان/منطقه را وارد کنید.';
                return;
            }
            if (this.slug.trim() && !this.slugValid) {
                this.errorMessage = 'نشانی اختصاصی معتبر نیست؛ فقط حروف کوچک انگلیسی، رقم و خط تیره (۳ تا ۶۳ نویسه) مجاز است.';
                return;
            }
            this.submitting = true;
            try {
                const response = await fetch('/api/admin/schools', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({
                        name: this.name.trim(),
                        timezone: this.timezone.trim(),
                        locale: this.locale.trim(),
                        level: this.level,
                        weekStart: this.weekStart,
                        slug: this.slug.trim().toLowerCase() || null
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
