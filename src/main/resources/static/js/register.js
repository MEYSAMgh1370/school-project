document.addEventListener('alpine:init', () => {
    Alpine.data('registerPage', () => ({
        postAuthenticationDestination: document.querySelector('[data-post-authentication-destination]')
            ?.dataset.postAuthenticationDestination || '/dashboard',
        fullName: '',
        email: '',
        password: '',
        passwordConfirm: '',
        submitting: false,
        errorMessage: '',

        // اگر لاگین خودکارِ پس از ثبت‌نام شکست بخورد، کاربر به صفحه‌ی ورود
        // می‌رود - اما با همان مقصدِ مجاز، تا قصدِ «ثبت مدرسه» در تلاش دوم
        // از دست نرود.
        get loginPagePath() {
            return this.postAuthenticationDestination === '/schools/new'
                ? '/login?next=/schools/new'
                : '/login';
        },

        get passwordsMismatch() {
            return this.passwordConfirm.length > 0 && this.password !== this.passwordConfirm;
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        async submit() {
            if (this.submitting) {
                return;
            }
            if (this.password !== this.passwordConfirm) {
                this.errorMessage = 'رمز عبور و تکرار آن یکسان نیستند.';
                return;
            }
            this.submitting = true;
            this.errorMessage = '';
            try {
                const response = await fetch('/api/auth/register', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        email: this.email,
                        fullName: this.fullName,
                        password: this.password
                    })
                });
                if (response.ok) {
                    const loginResponse = await fetch('/api/auth/login', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-XSRF-TOKEN': this.csrfToken()
                        },
                        body: JSON.stringify({ email: this.email, password: this.password })
                    });
                    window.location.href = loginResponse.ok
                        ? this.postAuthenticationDestination
                        : this.loginPagePath;
                    return;
                }
                let detail = '';
                try {
                    detail = (await response.json()).detail || '';
                } catch (ignored) {
                    // Fall back to the localized generic message below.
                }
                this.errorMessage = detail || 'خطایی رخ داد. دوباره تلاش کنید.';
            } catch (e) {
                this.errorMessage = 'ارتباط با سرور برقرار نشد. اتصال خود را بررسی و دوباره تلاش کنید.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
