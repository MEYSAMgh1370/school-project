document.addEventListener('alpine:init', () => {
    // این کامپوننت قبلاً Alpine داخلِ login.jte بود؛ طبق قاعده‌ی جای‌گذاری کد
    // (CLAUDE.md §۲) - login.jte از قبل از این قاعده بود و اجازه داشت
    // inline بماند، اما افزودن تبِ ورود با کد پیامکی یک ویرایشِ معنادار است،
    // پس طبق همان قاعده به فایل استاتیک منتقل شد.
    Alpine.data('loginPage', () => ({
        postAuthenticationDestination: document.querySelector('[data-post-authentication-destination]')
            ?.dataset.postAuthenticationDestination || '/dashboard',
        activeTab: 'password',

        // تب رمز عبور (رفتار قبلی، بدون تغییر)
        email: '',
        password: '',
        submittingPassword: false,
        passwordErrorMessage: '',

        // تب کد پیامکی: دو قدم - phone (وارد کردن شماره) و code (وارد کردن کد)
        otpPhone: '',
        otpCode: '',
        otpStep: 'phone',
        submittingOtp: false,
        otpErrorMessage: '',
        otpInfoMessage: '',

        get canSubmitPassword() {
            return this.email.length > 0 && this.password.length > 0 && !this.submittingPassword;
        },

        get canRequestOtp() {
            return this.otpPhone.length > 0 && !this.submittingOtp;
        },

        get canVerifyOtp() {
            return this.otpCode.length > 0 && !this.submittingOtp;
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        // شماره تلفن/کد ممکن است با رقم فارسی یا عربی تایپ شود - قبل از
        // ارسال به ASCII نرمال می‌شود (DESIGN.md §۸)
        normalizeDigits(value) {
            return value
                .replace(/[۰-۹]/g, d => String(d.charCodeAt(0) - 0x06F0))
                .replace(/[٠-٩]/g, d => String(d.charCodeAt(0) - 0x0660));
        },

        async submitPassword() {
            this.submittingPassword = true;
            this.passwordErrorMessage = '';
            try {
                const response = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({ email: this.email, password: this.password })
                });
                if (response.ok) {
                    window.location.href = this.postAuthenticationDestination;
                    return;
                }
                this.passwordErrorMessage = response.status === 401 || response.status === 403
                    ? 'ایمیل یا رمز عبور نادرست است.'
                    : 'خطایی رخ داد. دوباره تلاش کنید.';
            } catch (e) {
                this.passwordErrorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingPassword = false;
            }
        },

        async requestOtp() {
            this.submittingOtp = true;
            this.otpErrorMessage = '';
            this.otpInfoMessage = '';
            try {
                const phone = this.normalizeDigits(this.otpPhone);
                const response = await fetch('/api/auth/otp/request', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({ phoneNumber: phone })
                });
                if (!response.ok) {
                    this.otpErrorMessage = response.status === 404
                        ? 'شماره تلفن ثبت نشده است. ابتدا با ایمیل و رمز عبور وارد شوید و شماره خود را در پیشخوان ثبت کنید.'
                        : 'خطایی رخ داد. دوباره تلاش کنید.';
                    return;
                }
                this.otpStep = 'code';
                this.otpInfoMessage = 'کد تأیید ارسال شد.';
            } catch (e) {
                this.otpErrorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingOtp = false;
            }
        },

        async verifyOtp() {
            this.submittingOtp = true;
            this.otpErrorMessage = '';
            try {
                const phone = this.normalizeDigits(this.otpPhone);
                const code = this.normalizeDigits(this.otpCode);
                const response = await fetch('/api/auth/otp/verify', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({ phoneNumber: phone, code: code })
                });
                if (response.ok) {
                    window.location.href = this.postAuthenticationDestination;
                    return;
                }
                if (response.status === 410) {
                    this.otpErrorMessage = 'کد تأیید منقضی شده است. یک کد جدید درخواست کنید.';
                } else if (response.status === 400) {
                    this.otpErrorMessage = 'کد وارد‌شده صحیح نیست.';
                } else {
                    this.otpErrorMessage = 'خطایی رخ داد. دوباره تلاش کنید.';
                }
            } catch (e) {
                this.otpErrorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingOtp = false;
            }
        },

        backToPhoneStep() {
            this.otpStep = 'phone';
            this.otpCode = '';
            this.otpErrorMessage = '';
            this.otpInfoMessage = '';
        }
    }));
});
