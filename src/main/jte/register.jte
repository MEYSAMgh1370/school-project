@param String postAuthenticationDestination

@template.layout.main(title = "ثبت‌نام", scripts = java.util.List.of("/js/register.js"), content = @`
<section class="hero is-fullheight" id="main-content" role="main" tabindex="-1">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box" x-data="registerPage()"
                         data-post-authentication-destination="${postAuthenticationDestination}">
                        <h1 class="title is-4">ثبت‌نام در رشدِاندیشه</h1>

                        <form @submit.prevent="submit">
                            <div class="field">
                                <label class="label" for="fullName">نام و نام خانوادگی</label>
                                <div class="control">
                                    <input class="input" id="fullName" type="text" x-model="fullName"
                                           autocomplete="name" required>
                                </div>
                            </div>

                            <div class="field">
                                <label class="label" for="email">ایمیل</label>
                                <div class="control">
                                    <input class="input" id="email" type="email" x-model="email"
                                           autocomplete="email" required dir="ltr">
                                </div>
                            </div>

                            <div class="field">
                                <label class="label" for="password">رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="password" type="password" x-model="password"
                                           autocomplete="new-password" required minlength="8">
                                </div>
                                <p class="help">حداقل ۸ کاراکتر</p>
                            </div>

                            <div class="field">
                                <label class="label" for="passwordConfirm">تکرار رمز عبور</label>
                                <div class="control">
                                    <input class="input" id="passwordConfirm" type="password" x-model="passwordConfirm"
                                           autocomplete="new-password" required>
                                </div>
                                <p class="help is-danger" role="alert" x-cloak x-show="passwordsMismatch"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span>رمز عبور و تکرار آن یکسان نیستند.</span></span></p>
                            </div>

                            <div class="field">
                                <div class="control">
                                    <button class="button is-link is-fullwidth" type="submit" :disabled="!canSubmit" :class="{ 'is-loading': submitting }">
                                        ثبت‌نام
                                    </button>
                                </div>
                            </div>
                        </form>

                        <div class="notification is-danger mt-4" role="alert"
                             x-cloak x-show="errorMessage"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="errorMessage"></span></span></div>

                        <p class="has-text-centered mt-4">
                            @if(postAuthenticationDestination.equals("/schools/new"))
                                <a href="/login?next=/schools/new">حساب دارید؟ وارد شوید</a>
                            @else
                                <a href="/login">حساب دارید؟ وارد شوید</a>
                            @endif
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
`)
