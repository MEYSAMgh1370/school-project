@import com.example.roshdeandishe.iam.shared.AccountActivationPageController.ActivatePageModel

@param ActivatePageModel model

@template.layout.main(title = "فعال‌سازی حساب کاربری", content = @`
<section class="hero is-fullheight">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box"
                         x-data="{
                            token: '${model.token()}',
                            newPassword: '',
                            confirmPassword: '',
                            submitting: false,
                            resultMessage: '',
                            resultType: '',
                            get passwordTooShort() { return this.newPassword.length > 0 && this.newPassword.length < 8; },
                            get passwordsMismatch() { return this.confirmPassword.length > 0 && this.newPassword !== this.confirmPassword; },
                            get canSubmit() {
                                return this.token.length > 0 && this.newPassword.length >= 8
                                    && this.newPassword === this.confirmPassword && !this.submitting;
                            },
                            csrfToken() {
                                const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
                                return match ? decodeURIComponent(match[1]) : '';
                            },
                            async submit() {
                                this.submitting = true;
                                this.resultMessage = '';
                                this.resultType = '';
                                try {
                                    const response = await fetch('/api/auth/activate', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': this.csrfToken() },
                                        body: JSON.stringify({ token: this.token, newPassword: this.newPassword })
                                    });
                                    const body = await response.json();
                                    if (response.ok) {
                                        this.resultType = 'success';
                                        this.resultMessage = body.message;
                                    } else {
                                        this.resultType = 'danger';
                                        this.resultMessage = body.detail || body.title || 'خطایی رخ داد.';
                                    }
                                } catch (e) {
                                    this.resultType = 'danger';
                                    this.resultMessage = 'ارتباط با سرور برقرار نشد.';
                                } finally {
                                    this.submitting = false;
                                }
                            }
                         }">
                        <h1 class="title is-4">فعال‌سازی حساب و تعیین رمز عبور</h1>

                        <form @submit.prevent="submit">
                            <div class="field">
                                <label class="label" for="token">کد دعوت</label>
                                <div class="control">
                                    <input class="input" id="token" type="text" x-model="token" required>
                                </div>
                            </div>

                            <div class="field">
                                <label class="label" for="newPassword">رمز عبور جدید</label>
                                <div class="control">
                                    <input class="input" id="newPassword" type="password" x-model="newPassword" required minlength="8">
                                </div>
                                <p class="help is-danger" x-show="passwordTooShort">رمز عبور باید حداقل ۸ کاراکتر باشد.</p>
                            </div>

                            <div class="field">
                                <label class="label" for="confirmPassword">تکرار رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="confirmPassword" type="password" x-model="confirmPassword" required>
                                </div>
                                <p class="help is-danger" x-show="passwordsMismatch">رمزهای عبور یکسان نیستند.</p>
                            </div>

                            <div class="field">
                                <div class="control">
                                    <button class="button is-link is-fullwidth" type="submit" :disabled="!canSubmit" :class="{ 'is-loading': submitting }">
                                        فعال‌سازی حساب
                                    </button>
                                </div>
                            </div>
                        </form>

                        <div class="notification mt-4" :class="'is-' + resultType" x-show="resultMessage" x-text="resultMessage"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
`)
