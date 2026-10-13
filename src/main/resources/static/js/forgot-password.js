// صفحه‌ی «بازیابی رمز عبور» (password-reset-by-email.md). سرور همیشه همان پاسخ را می‌دهد، پس صفحه هم
// هرگز نمی‌گوید حسابی پیدا شد یا نه.
document.addEventListener('alpine:init', () => {
    Alpine.data('forgotPasswordPage', () => ({
        email: '',
        submitting: false,
        sent: false,
        resultMessage: '',
        errorMessage: '',

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        async submit() {
            if (this.submitting) {
                return;
            }
            this.submitting = true;
            this.errorMessage = '';
            try {
                const response = await fetch('/api/auth/password-reset/request', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                    body: JSON.stringify({ email: this.email })
                });
                if (response.ok) {
                    const body = await response.json();
                    this.resultMessage = body.message;
                    this.sent = true;
                    return;
                }
                this.errorMessage = 'خطایی رخ داد. دوباره تلاش کنید.';
            } catch (e) {
                this.errorMessage = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
