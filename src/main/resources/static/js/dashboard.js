document.addEventListener('alpine:init', () => {
    // فیلد شماره تلفن روی داشبورد - قدم اول برای اتصال ربات بله و ورود با
    // کد پیامکی (UX_ROADMAP.md §6.6 مورد ۳). مقدار اولیه از رندر سمت سرور
    // می‌آید (profile.phoneNumber از IamFacade.findUser، از طریق data-phone-number
    // + x-init - نه یک آرگومان JS، چون setPhoneNumber نویسه‌ها را اعتبارسنجی
    // نمی‌کند) - بدون fetch اولیه؛ فقط ذخیره‌کردن یک mutation است.
    Alpine.data('phoneNumberField', () => ({
        phoneNumber: '',
        submitting: false,
        errorMessage: '',
        successMessage: '',

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        async save() {
            this.submitting = true;
            this.errorMessage = '';
            this.successMessage = '';
            try {
                const response = await fetch('/api/me/phone', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ phoneNumber: this.phoneNumber })
                });
                if (!response.ok) {
                    try {
                        const body = await response.json();
                        this.errorMessage = body.detail || body.title || 'خطایی رخ داد.';
                    } catch (e) {
                        this.errorMessage = 'خطایی رخ داد. (' + response.status + ')';
                    }
                    return;
                }
                this.successMessage = 'شماره تلفن ذخیره شد.';
            } catch (e) {
                this.errorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
