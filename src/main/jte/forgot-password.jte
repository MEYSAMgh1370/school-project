@param boolean available

@template.layout.main(title = "بازیابی رمز عبور", scripts = java.util.List.of("/js/forgot-password.js"), content = @`
<section class="hero is-fullheight" id="main-content" role="main" tabindex="-1">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box" x-data="forgotPasswordPage()">
                        <h1 class="title is-4">بازیابی رمز عبور</h1>

                        @if(available)
                            <p class="mb-4">ایمیل حساب خود را وارد کنید تا لینک تعیین رمز تازه برایتان فرستاده شود.</p>

                            <form @submit.prevent="submit" x-show="!sent">
                                <div class="field">
                                    <label class="label" for="email">ایمیل</label>
                                    <div class="control">
                                        <input class="input" id="email" type="email" x-model="email"
                                               autocomplete="username" required dir="ltr" autofocus
                                               :aria-invalid="!!errorMessage" aria-describedby="forgot-error">
                                    </div>
                                </div>

                                <div class="field">
                                    <div class="control">
                                        <button class="button is-link is-fullwidth" type="submit"
                                                :disabled="submitting" :class="{ 'is-loading': submitting }">
                                            ارسال لینک بازیابی
                                        </button>
                                    </div>
                                </div>
                            </form>

                            <div class="notification is-success" role="status" aria-live="polite"
                                 x-cloak x-show="sent" x-text="resultMessage"></div>
                            <div class="notification is-danger mt-4" role="alert" id="forgot-error"
                                 x-cloak x-show="errorMessage"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="errorMessage"></span></span></div>
                            <p class="help" x-show="!sent">اگر ایمیلی نرسید، پوشه‌ی هرزنامه را هم بررسی کنید. لینک یک ساعت معتبر است.</p>
                        @else
                            <div class="notification is-warning" role="status">
                                بازیابی رمز عبور با ایمیل فعلاً در دسترس نیست. برای تعیین رمز تازه با مدیر مدرسه‌تان تماس بگیرید.
                            </div>
                        @endif

                        <p class="has-text-centered mt-4">
                            <a href="/login">بازگشت به صفحه‌ی ورود</a>
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
`)
