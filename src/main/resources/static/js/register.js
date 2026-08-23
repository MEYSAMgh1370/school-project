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

        get passwordsMismatch() {
            return this.passwordConfirm.length > 0 && this.password !== this.passwordConfirm;
        },

        get canSubmit() {
            return this.fullName.length > 0 && this.email.length > 0
                && this.password.length >= 8 && this.password === this.passwordConfirm
                && !this.submitting;
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        async submit() {
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
                        : '/login';
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
