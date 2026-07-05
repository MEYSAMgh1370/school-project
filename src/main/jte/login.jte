<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ورود - رشدِاندیشه</title>
    <link rel="stylesheet" href="/webjars/bulma/1.0.4/css/bulma.min.css">
    <script defer src="/webjars/alpinejs/3.15.12/dist/cdn.min.js"></script>
</head>
<body>
<section class="hero is-fullheight">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box"
                         x-data="{
                            email: '',
                            password: '',
                            submitting: false,
                            errorMessage: '',
                            get canSubmit() {
                                return this.email.length > 0 && this.password.length > 0 && !this.submitting;
                            },
                            csrfToken() {
                                const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
                                return match ? decodeURIComponent(match[1]) : '';
                            },
                            async submit() {
                                this.submitting = true;
                                this.errorMessage = '';
                                try {
                                    const response = await fetch('/api/auth/login', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                                        body: JSON.stringify({ email: this.email, password: this.password })
                                    });
                                    if (response.ok) {
                                        window.location.href = '/dashboard';
                                        return;
                                    }
                                    this.errorMessage = response.status === 401 || response.status === 403
                                        ? 'ایمیل یا رمز عبور نادرست است.'
                                        : 'خطایی رخ داد. دوباره تلاش کنید.';
                                } catch (e) {
                                    this.errorMessage = 'ارتباط با سرور برقرار نشد.';
                                } finally {
                                    this.submitting = false;
                                }
                            }
                         }">
                        <h1 class="title is-4">ورود به رشدِاندیشه</h1>

                        <form @submit.prevent="submit">
                            <div class="field">
                                <label class="label" for="email">ایمیل</label>
                                <div class="control">
                                    <input class="input" id="email" type="email" x-model="email" required dir="ltr">
                                </div>
                            </div>

                            <div class="field">
                                <label class="label" for="password">رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="password" type="password" x-model="password" required>
                                </div>
                            </div>

                            <div class="field">
                                <div class="control">
                                    <button class="button is-link is-fullwidth" type="submit" :disabled="!canSubmit" :class="{ 'is-loading': submitting }">
                                        ورود
                                    </button>
                                </div>
                            </div>
                        </form>

                        <div class="notification is-danger mt-4" x-show="errorMessage" x-text="errorMessage"></div>

                        <p class="has-text-centered mt-4">
                            <a href="/register">حساب ندارید؟ ثبت‌نام کنید</a>
                        </p>
                        <p class="has-text-centered mt-2">
                            <a href="/activate">کد دعوت دارید؟ حساب خود را فعال کنید</a>
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
</body>
</html>
