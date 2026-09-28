@import com.example.roshdeandishe.iam.dto.ActivatePageModel

@param ActivatePageModel model

@template.layout.main(title = "فعال‌سازی حساب کاربری", scripts = java.util.List.of("/js/activate.js"), content = @`
<section class="hero is-fullheight" id="main-content" role="main" tabindex="-1">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box" x-data="activatePage()" data-token="${model.token()}">
                        <h1 class="title is-4">فعال‌سازی حساب و تعیین رمز عبور</h1>

                        <form @submit.prevent="submit">
                            <div class="field">
                                <label class="label" for="token">کد دعوت</label>
                                <div class="control">
                                    <input class="input" id="token" type="text" x-model="token"
                                           autocomplete="one-time-code" required>
                                </div>
                            </div>

                            <div class="field">
                                <label class="label" for="newPassword">رمز عبور جدید</label>
                                <div class="control">
                                    <input class="input" id="newPassword" type="password" x-model="newPassword"
                                           autocomplete="new-password" required minlength="8">
                                </div>
                                <p class="help is-danger" x-cloak x-show="passwordTooShort">رمز عبور باید حداقل ۸ کاراکتر باشد.</p>
                            </div>

                            <div class="field">
                                <label class="label" for="confirmPassword">تکرار رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="confirmPassword" type="password" x-model="confirmPassword"
                                           autocomplete="new-password" required>
                                </div>
                                <p class="help is-danger" x-cloak x-show="passwordsMismatch">رمزهای عبور یکسان نیستند.</p>
                            </div>

                            <div class="field">
                                <div class="control">
                                    <button class="button is-link is-fullwidth" type="submit" :disabled="!canSubmit" :class="{ 'is-loading': submitting }">
                                        فعال‌سازی حساب
                                    </button>
                                </div>
                            </div>
                        </form>

                        <div class="notification is-success mt-4" role="status" aria-live="polite"
                             x-cloak x-show="resultType === 'success'" x-text="resultMessage"></div>
                        <div class="notification is-danger mt-4" role="alert"
                             x-cloak x-show="resultType === 'danger'" x-text="resultMessage"></div>
                        <p class="has-text-centered mt-4" x-cloak x-show="resultType === 'success'">
                            <a class="button is-link is-light" href="/login">ورود به حساب</a>
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
`)
