document.addEventListener('alpine:init', () => {
    Alpine.data('activatePage', () => ({
        token: '',
        manualToken: false,
        showPassword: false,
        done: false,
        newPassword: '',
        confirmPassword: '',
        submitting: false,
        resultMessage: '',
        resultType: '',

        init() {
            // The invitation token is untrusted URL input. Read it as inert DOM data;
            // never interpolate it into an Alpine/JavaScript expression.
            this.token = this.$root.dataset.token || '';
            // بدونِ لینک (کاربر خودش کد را می‌زند) فیلدِ کد دعوت نشان داده می‌شود؛ با لینک، نه.
            this.manualToken = this.$root.dataset.manualToken === 'true';
        },

        get passwordTooShort() {
            return this.newPassword.length > 0 && this.newPassword.length < 8;
        },

        get passwordsMismatch() {
            return this.confirmPassword.length > 0 && this.newPassword !== this.confirmPassword;
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        async signIn(email, password) {
            try {
                const response = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({ email: email, password: password })
                });
                return response.ok;
            } catch (e) {
                return false;
            }
        },

        async submit() {
            if (this.submitting) {
                return;
            }
            this.resultMessage = '';
            this.resultType = '';
            if (this.newPassword !== this.confirmPassword) {
                this.resultType = 'danger';
                this.resultMessage = 'رمزهای عبور یکسان نیستند.';
                return;
            }
            this.submitting = true;
            try {
                const response = await fetch('/api/auth/activate', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ token: this.token, newPassword: this.newPassword })
                });
                let body = {};
                try {
                    body = await response.json();
                } catch (ignored) {
                    // A non-JSON error still receives a useful generic message below.
                }
                if (response.ok) {
                    this.resultType = 'success';
                    this.resultMessage = body.message || 'حساب شما فعال شد. اکنون می‌توانید وارد شوید.';
                    this.done = true;
                    // توکنِ دعوت اثباتِ مالکیتِ ایمیل است (مثل ثبت‌نام)؛ با همان رمز کاربر را وارد می‌کنیم. اگر ورود
                    // خودکار شکست بخورد، پیامِ موفقیتِ بالا و دکمه‌ی «ورود به حساب» می‌ماند.
                    const password = this.newPassword;
                    this.newPassword = '';
                    this.confirmPassword = '';
                    if (body.email && await this.signIn(body.email, password)) {
                        window.location.href = '/dashboard';
                    }
                } else {
                    this.resultType = 'danger';
                    this.resultMessage = body.detail || body.title || 'خطایی رخ داد. دوباره تلاش کنید.';
                }
            } catch (e) {
                this.resultType = 'danger';
                this.resultMessage = 'ارتباط با سرور برقرار نشد. اتصال خود را بررسی و دوباره تلاش کنید.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
