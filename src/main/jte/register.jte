@template.layout.main(title = "ثبت‌نام", content = @`
<section class="hero is-fullheight">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box"
                         x-data="{
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
                                        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                                        body: JSON.stringify({ email: this.email, fullName: this.fullName, password: this.password })
                                    });
                                    if (response.ok) {
                                        // ورود خودکار با همان اعتبارنامه‌ها و رفتن به داشبورد
                                        const loginResponse = await fetch('/api/auth/login', {
                                            method: 'POST',
                                            headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                                            body: JSON.stringify({ email: this.email, password: this.password })
                                        });
                                        window.location.href = loginResponse.ok ? '/dashboard' : '/login';
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
                         }">
                        <h1 class="title is-4">ثبت‌نام در رشدِاندیشه</h1>

                        <form @submit.prevent="submit">
                            <div class="field">
                                <label class="label" for="fullName">نام و نام خانوادگی</label>
                                <div class="control">
                                    <input class="input" id="fullName" type="text" x-model="fullName" required>
                                </div>
                            </div>

                            <div class="field">
                                <label class="label" for="email">ایمیل</label>
                                <div class="control">
                                    <input class="input" id="email" type="email" x-model="email" required dir="ltr">
                                </div>
                            </div>

                            <div class="field">
                                <label class="label" for="password">رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="password" type="password" x-model="password" required minlength="8">
                                </div>
                                <p class="help">حداقل ۸ کاراکتر</p>
                            </div>

                            <div class="field">
                                <label class="label" for="passwordConfirm">تکرار رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="passwordConfirm" type="password" x-model="passwordConfirm" required>
                                </div>
                                <p class="help is-danger" x-show="passwordsMismatch">رمز عبور و تکرار آن یکسان نیستند.</p>
                            </div>

                            <div class="field">
                                <div class="control">
                                    <button class="button is-link is-fullwidth" type="submit" :disabled="!canSubmit" :class="{ 'is-loading': submitting }">
                                        ثبت‌نام
                                    </button>
                                </div>
                            </div>
                        </form>

                        <div class="notification is-danger mt-4" x-show="errorMessage" x-text="errorMessage"></div>

                        <p class="has-text-centered mt-4">
                            <a href="/login">حساب دارید؟ وارد شوید</a>
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
`)
