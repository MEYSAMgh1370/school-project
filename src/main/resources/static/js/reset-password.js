// صفحه‌ی «تعیین رمز عبور تازه» (password-reset-by-email.md). توکن ورودیِ نامطمئنِ URL است: فقط به‌صورت
// داده‌ی خنثیِ DOM خوانده می‌شود و هرگز داخل عبارتِ Alpine/جاوااسکریپت درج نمی‌شود (مثل activate.js).
document.addEventListener('alpine:init', () => {
    Alpine.data('resetPasswordPage', () => ({
        token: '',
        newPassword: '',
        confirmPassword: '',
        showPassword: false,
        submitting: false,
        done: false,
        resultMessage: '',
        errorMessage: '',
        tokenProblem: false,

        init() {
            this.token = this.$root.dataset.token || '';
            if (!this.token) {
                this.errorMessage = 'لینک بازیابی کامل نیست. یک لینک تازه درخواست کنید.';
                this.tokenProblem = true;
            }
        },

        get passwordsMismatch() {
            return this.confirmPassword.length > 0 && this.newPassword !== this.confirmPassword;
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
            if (this.newPassword !== this.confirmPassword) {
                this.errorMessage = 'رمزهای عبور یکسان نیستند.';
                return;
            }
            this.submitting = true;
            try {
                const response = await fetch('/api/auth/password-reset/confirm', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({ token: this.token, newPassword: this.newPassword })
                });
                let body = {};
                try { body = await response.json(); } catch (e) {}
                if (response.ok) {
                    this.resultMessage = body.message || 'رمز عبور تغییر کرد.';
                    this.done = true;
                    return;
                }
                if (response.status === 400 && /password-reset-token-invalid$/.test(body.type || '')) {
                    this.errorMessage = body.detail || 'این لینک نامعتبر است یا منقضی شده است.';
                    this.tokenProblem = true;
                } else {
                    this.errorMessage = body.detail || 'خطایی رخ داد. دوباره تلاش کنید.';
                }
            } catch (e) {
                this.errorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
