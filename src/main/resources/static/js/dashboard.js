document.addEventListener('alpine:init', () => {
    // فیلد شماره تلفن روی پیشخوان - قدم اول برای اتصال ربات بله و ورود با
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

        normalizeDigits(value) {
            return value
                .replace(/[۰-۹]/g, digit => String(digit.charCodeAt(0) - 0x06F0))
                .replace(/[٠-٩]/g, digit => String(digit.charCodeAt(0) - 0x0660))
                .trim();
        },

        async save() {
            this.submitting = true;
            this.errorMessage = '';
            this.successMessage = '';
            try {
                const canonicalPhoneNumber = this.normalizeDigits(this.phoneNumber);
                const response = await fetch('/api/me/phone', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ phoneNumber: canonicalPhoneNumber })
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
                this.phoneNumber = canonicalPhoneNumber;
                this.successMessage = 'شماره تلفن ذخیره شد.';
            } catch (e) {
                this.errorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));

    // نشانی ورودِ اختصاصیِ مدرسه (/s/{slug}) برای مدیرِ مدرسه - تا امروز
    // slug فقط هنگام ثبت مدرسه نوشته می‌شد و هیچ‌جا دیده نمی‌شد
    // (UX_ROADMAP.md §6.4). داده از رندر سمت سرور می‌آید؛ اینجا فقط نشانیِ
    // مطلق ساخته و کپی می‌شود - هیچ fetch ای در کار نیست.
    Alpine.data('schoolEntryLinks', () => ({
        errorMessage: '',
        successMessage: '',

        // نشانیِ مطلق سمت کلاینت ساخته می‌شود تا سرور به یک تنظیمِ
        // «نشانیِ عمومیِ پلتفرم» نیاز پیدا نکند؛ همان چیزی که نمایش داده
        // می‌شود دقیقاً همان چیزی است که کپی می‌شود.
        entryUrl(slug) {
            return window.location.origin + '/s/' + slug;
        },

        async copy(slug) {
            this.errorMessage = '';
            this.successMessage = '';
            const url = this.entryUrl(slug);
            // clipboard فقط در context امن (https یا localhost) در دسترس است -
            // در غیر این صورت به‌جای شکستِ خاموش، به کاربر می‌گوییم چه کند.
            if (!navigator.clipboard) {
                this.errorMessage = 'کپی خودکار در این مرورگر ممکن نیست. نشانی را دستی انتخاب و کپی کنید.';
                return;
            }
            try {
                await navigator.clipboard.writeText(url);
                this.successMessage = 'نشانی کپی شد.';
            } catch (e) {
                this.errorMessage = 'کپی خودکار انجام نشد. نشانی را دستی انتخاب و کپی کنید.';
            }
        }
    }));
});
