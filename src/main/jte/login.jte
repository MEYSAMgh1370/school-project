@param String postAuthenticationDestination
@param boolean otpAvailable
@param boolean justRegistered

@template.layout.main(title = "ورود", scripts = java.util.List.of("/js/login.js"), content = @`
<section class="hero is-fullheight" id="main-content" role="main" tabindex="-1">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box" x-data="loginPage()"
                         data-post-authentication-destination="${postAuthenticationDestination}">
                        <h1 class="title is-4">ورود به رشدِاندیشه</h1>

                        @if(justRegistered)
                            <div class="notification is-success" role="status">
                                حساب شما ساخته شد. برای ادامه با همان ایمیل و رمز عبور وارد شوید.
                            </div>
                        @endif

                        <%-- تبِ «کد پیامکی» فقط وقتی که ارسال واقعاً ممکن است (IamFacade.isOtpDeliveryAvailable)؛
                             در production تا وصل‌شدنِ ارائه‌دهنده‌ی پیامک دیده نمی‌شود. --%>
                        @if(otpAvailable)
                            <div class="tabs is-toggle is-fullwidth">
                                <ul role="tablist" aria-label="روش ورود">
                                    <li :class="{ 'is-active': activeTab === 'password' }" role="presentation">
                                        <a href="#" role="tab" id="tab-password" aria-controls="panel-password"
                                           :aria-selected="activeTab === 'password'"
                                           @click.prevent="activeTab = 'password'">رمز عبور</a>
                                    </li>
                                    <li :class="{ 'is-active': activeTab === 'otp' }" role="presentation">
                                        <a href="#" role="tab" id="tab-otp" aria-controls="panel-otp"
                                           :aria-selected="activeTab === 'otp'"
                                           @click.prevent="activeTab = 'otp'">کد پیامکی</a>
                                    </li>
                                </ul>
                            </div>
                        @endif

                        <div id="panel-password" role="tabpanel" x-show="activeTab === 'password'">
                            <form @submit.prevent="submitPassword">
                                <div class="field">
                                    <label class="label" for="email">ایمیل</label>
                                    <div class="control">
                                        <input class="input" id="email" type="email" x-model="email"
                                               autocomplete="username" required dir="ltr" autofocus
                                               :aria-invalid="!!passwordErrorMessage"
                                               aria-describedby="password-error">
                                    </div>
                                </div>

                                <div class="field">
                                    <label class="label" for="password">رمز عبور</label>
                                    <div class="field has-addons mb-0">
                                        <div class="control is-expanded">
                                            <input class="input" id="password" :type="showPassword ? 'text' : 'password'"
                                                   x-model="password" autocomplete="current-password" required
                                                   :dir="showPassword ? 'ltr' : null"
                                                   :aria-invalid="!!passwordErrorMessage"
                                                   aria-describedby="password-error">
                                        </div>
                                        <div class="control">
                                            <button class="button" type="button" @click="showPassword = !showPassword"
                                                    :aria-pressed="showPassword"
                                                    x-text="showPassword ? 'پنهان' : 'نمایش'">نمایش</button>
                                        </div>
                                    </div>
                                </div>

                                <div class="field">
                                    <div class="control">
                                        <button class="button is-link is-fullwidth" type="submit"
                                                :disabled="submittingPassword" :class="{ 'is-loading': submittingPassword }">
                                            ورود
                                        </button>
                                    </div>
                                </div>
                            </form>

                            <div class="notification is-danger mt-4" role="alert" id="password-error"
                                 x-cloak x-show="passwordErrorMessage"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="passwordErrorMessage"></span></span></div>
                        </div>

                        @if(otpAvailable)
                        <div id="panel-otp" role="tabpanel" x-show="activeTab === 'otp'">
                            <template x-if="otpStep === 'phone'">
                                <form @submit.prevent="requestOtp">
                                    <div class="field">
                                        <label class="label" for="otp-phone">شماره تلفن</label>
                                        <div class="control">
                                            <input class="input" id="otp-phone" type="tel" dir="ltr"
                                                   x-model="otpPhone" autocomplete="tel" inputmode="tel"
                                                   placeholder="۰۹۱۲۳۴۵۶۷۸۹" required>
                                        </div>
                                    </div>

                                    <div class="field">
                                        <div class="control">
                                            <button class="button is-link is-fullwidth" type="submit"
                                                    :disabled="submittingOtp" :class="{ 'is-loading': submittingOtp }">
                                                دریافت کد
                                            </button>
                                        </div>
                                    </div>
                                </form>
                            </template>

                            <template x-if="otpStep === 'code'">
                                <form @submit.prevent="verifyOtp">
                                    <div class="field">
                                        <label class="label" for="otp-code">کد تأیید</label>
                                        <div class="control">
                                            <input class="input" id="otp-code" type="text" inputmode="numeric" dir="ltr"
                                                   x-model="otpCode" autocomplete="one-time-code" required>
                                        </div>
                                    </div>

                                    <div class="field">
                                        <div class="control">
                                            <button class="button is-link is-fullwidth" type="submit"
                                                    :disabled="submittingOtp" :class="{ 'is-loading': submittingOtp }">
                                                ورود
                                            </button>
                                        </div>
                                    </div>

                                    <p class="has-text-centered">
                                        <button class="button is-text" type="button" @click="backToPhoneStep">تغییر شماره یا درخواست کد جدید</button>
                                    </p>
                                </form>
                            </template>

                            <div class="notification is-success mt-4" role="status" aria-live="polite"
                                 x-cloak x-show="otpInfoMessage" x-text="otpInfoMessage"></div>
                            <div class="notification is-danger mt-4" role="alert"
                                 x-cloak x-show="otpErrorMessage"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="otpErrorMessage"></span></span></div>
                        </div>
                        @endif

                        <p class="has-text-centered mt-4">
                            @if(postAuthenticationDestination.equals("/schools/new"))
                                <a href="/register?next=/schools/new">حساب ندارید؟ ثبت‌نام کنید</a>
                            @else
                                <a href="/register">حساب ندارید؟ ثبت‌نام کنید</a>
                            @endif
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
`)
