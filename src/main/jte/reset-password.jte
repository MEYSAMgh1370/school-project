@param String token

@template.layout.main(title = "تعیین رمز عبور تازه", scripts = java.util.List.of("/js/reset-password.js"), content = @`
<section class="hero is-fullheight" id="main-content" role="main" tabindex="-1">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box" x-data="resetPasswordPage()" data-token="${token}">
                        <h1 class="title is-4">تعیین رمز عبور تازه</h1>

                        <form @submit.prevent="submit" x-show="!done">
                            <div class="field">
                                <label class="label" for="newPassword">رمز عبور تازه</label>
                                <div class="field has-addons mb-0">
                                    <div class="control is-expanded">
                                        <input class="input" id="newPassword" :type="showPassword ? 'text' : 'password'"
                                               x-model="newPassword" autocomplete="new-password" required minlength="8"
                                               :dir="showPassword ? 'ltr' : null" autofocus aria-describedby="newPassword-help">
                                    </div>
                                    <div class="control">
                                        <button class="button" type="button" @click="showPassword = !showPassword"
                                                :aria-pressed="showPassword"
                                                x-text="showPassword ? 'پنهان' : 'نمایش'">نمایش</button>
                                    </div>
                                </div>
                                <p class="help" id="newPassword-help">حداقل ۸ کاراکتر</p>
                            </div>

                            <div class="field">
                                <label class="label" for="confirmPassword">تکرار رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="confirmPassword" :type="showPassword ? 'text' : 'password'"
                                           x-model="confirmPassword" autocomplete="new-password" required
                                           :dir="showPassword ? 'ltr' : null"
                                           :aria-invalid="passwordsMismatch" aria-describedby="password-mismatch">
                                </div>
                                <p class="help is-danger" role="alert" id="password-mismatch" x-cloak x-show="passwordsMismatch"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span>رمزهای عبور یکسان نیستند.</span></span></p>
                            </div>

                            <div class="field">
                                <div class="control">
                                    <button class="button is-link is-fullwidth" type="submit"
                                            :disabled="submitting" :class="{ 'is-loading': submitting }">
                                        تغییر رمز عبور
                                    </button>
                                </div>
                            </div>
                        </form>

                        <div class="notification is-success" role="status" aria-live="polite"
                             x-cloak x-show="done" x-text="resultMessage"></div>
                        <div class="notification is-danger mt-4" role="alert"
                             x-cloak x-show="errorMessage"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="errorMessage"></span></span></div>

                        <p class="has-text-centered mt-4" x-cloak x-show="done">
                            <a class="button is-link is-light" href="/login">ورود به حساب</a>
                        </p>
                        <p class="has-text-centered mt-4" x-cloak x-show="tokenProblem">
                            <a href="/forgot-password">درخواست لینک تازه</a>
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
`)
