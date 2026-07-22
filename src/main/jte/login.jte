@template.layout.main(title = "ورود", scripts = java.util.List.of("/js/login.js"), content = @`
<section class="hero is-fullheight">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <div class="box" x-data="loginPage()">
                        <h1 class="title is-4">ورود به رشدِاندیشه</h1>

                        <div class="tabs is-toggle is-fullwidth">
                            <ul>
                                <li :class="{ 'is-active': activeTab === 'password' }">
                                    <a @click="activeTab = 'password'">رمز عبور</a>
                                </li>
                                <li :class="{ 'is-active': activeTab === 'otp' }">
                                    <a @click="activeTab = 'otp'">کد پیامکی</a>
                                </li>
                            </ul>
                        </div>

                        <div x-show="activeTab === 'password'">
                            <form @submit.prevent="submitPassword">
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
                                        <button class="button is-link is-fullwidth" type="submit"
                                                :disabled="!canSubmitPassword" :class="{ 'is-loading': submittingPassword }">
                                            ورود
                                        </button>
                                    </div>
                                </div>
                            </form>

                            <div class="notification is-danger mt-4" x-show="passwordErrorMessage" x-text="passwordErrorMessage"></div>
                        </div>

                        <div x-show="activeTab === 'otp'">
                            <template x-if="otpStep === 'phone'">
                                <form @submit.prevent="requestOtp">
                                    <div class="field">
                                        <label class="label" for="otp-phone">شماره تلفن</label>
                                        <div class="control">
                                            <input class="input" id="otp-phone" type="tel" dir="ltr"
                                                   x-model="otpPhone" placeholder="۰۹۱۲۳۴۵۶۷۸۹" required>
                                        </div>
                                    </div>

                                    <div class="field">
                                        <div class="control">
                                            <button class="button is-link is-fullwidth" type="submit"
                                                    :disabled="!canRequestOtp" :class="{ 'is-loading': submittingOtp }">
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
                                                   x-model="otpCode" required>
                                        </div>
                                    </div>

                                    <div class="field">
                                        <div class="control">
                                            <button class="button is-link is-fullwidth" type="submit"
                                                    :disabled="!canVerifyOtp" :class="{ 'is-loading': submittingOtp }">
                                                ورود
                                            </button>
                                        </div>
                                    </div>

                                    <p class="has-text-centered">
                                        <a @click="backToPhoneStep">تغییر شماره یا درخواست کد جدید</a>
                                    </p>
                                </form>
                            </template>

                            <div class="notification is-success mt-4" x-show="otpInfoMessage" x-text="otpInfoMessage"></div>
                            <div class="notification is-danger mt-4" x-show="otpErrorMessage" x-text="otpErrorMessage"></div>
                        </div>

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
`)
