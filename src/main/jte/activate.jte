@import com.example.roshdeandishe.iam.model.ActivatePageModel

@param ActivatePageModel model

@template.layout.main(title = "فعال‌سازی حساب کاربری", scripts = java.util.List.of("/js/activate.js"), content = @`
<section class="hero is-fullheight" id="main-content" role="main" tabindex="-1">
    <div class="hero-body">
        <div class="container">
            <div class="columns is-centered">
                <div class="column is-4-desktop is-6-tablet">
                    <%-- توکن ورودیِ نامطمئنِ URL است: فقط به‌صورت data-attributeِ escape‌شده به صفحه می‌رسد، نه داخل عبارتِ Alpine. --%>
                    <div class="box" x-data="activatePage()" data-token="${model.token()}"
                         data-manual-token="${model.hasToken() ? "false" : "true"}">
                        <h1 class="title is-4">فعال‌سازی حساب و تعیین رمز عبور</h1>

                        @if(model.broken())
                            <div class="notification is-warning" role="status">
                                @if(model.used())
                                    این دعوت‌نامه قبلاً استفاده شده است. اگر حسابتان را فعال کرده‌اید، با همان رمز وارد شوید.
                                @elseif(model.expired())
                                    این لینک دعوت منقضی شده است. از مدیر مدرسه‌تان بخواهید دعوت‌نامه‌ی تازه‌ای برایتان بفرستد.
                                @else
                                    این لینک دعوت نامعتبر است. آن را کامل کپی کرده‌اید؟ اگر مشکل ادامه داشت، از مدیر مدرسه‌تان بخواهید دعوت‌نامه‌ی تازه‌ای بفرستد.
                                @endif
                            </div>
                            <p class="has-text-centered mt-4">
                                <a class="button is-link is-light" href="/login">رفتن به صفحه‌ی ورود</a>
                            </p>
                        @else
                            @if(model.valid())
                                <div class="notification is-light" role="status">
                                    <p><strong>${model.invite().fullName()}</strong></p>
                                    <p class="is-size-7" dir="ltr">${model.invite().email()}</p>
                                    @for(var membership : model.invite().memberships())
                                        <p class="mt-2">
                                            دعوت‌شده به‌عنوان <strong>${membership.roleLabel()}</strong>
                                            @if(membership.schoolName() != null)
                                                در <strong>${membership.schoolName()}</strong>
                                            @endif
                                        </p>
                                    @endfor
                                </div>
                            @endif

                            <form @submit.prevent="submit" x-show="!done">
                                <%-- فقط وقتی کاربر بدونِ لینک به این صفحه آمده و کد را دستی می‌زند دیده می‌شود. --%>
                                <div class="field" x-show="manualToken">
                                    <label class="label" for="token">کد دعوت</label>
                                    <div class="control">
                                        <input class="input" id="token" type="text" x-model="token"
                                               autocomplete="one-time-code" dir="ltr">
                                    </div>
                                </div>

                                <div class="field">
                                    <label class="label" for="newPassword">رمز عبور جدید</label>
                                    <div class="field has-addons mb-0">
                                        <div class="control is-expanded">
                                            <input class="input" id="newPassword" :type="showPassword ? 'text' : 'password'"
                                                   x-model="newPassword" autocomplete="new-password" required minlength="8"
                                                   :dir="showPassword ? 'ltr' : null" autofocus>
                                        </div>
                                        <div class="control">
                                            <button class="button" type="button" @click="showPassword = !showPassword"
                                                    :aria-pressed="showPassword"
                                                    x-text="showPassword ? 'پنهان' : 'نمایش'">نمایش</button>
                                        </div>
                                    </div>
                                    <p class="help is-danger" role="alert" x-cloak x-show="passwordTooShort"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span>رمز عبور باید حداقل ۸ کاراکتر باشد.</span></span></p>
                                </div>

                                <div class="field">
                                    <label class="label" for="confirmPassword">تکرار رمز عبور</label>
                                    <div class="control">
                                        <input class="input" id="confirmPassword" :type="showPassword ? 'text' : 'password'"
                                               x-model="confirmPassword" autocomplete="new-password" required
                                               :dir="showPassword ? 'ltr' : null">
                                    </div>
                                    <p class="help is-danger" role="alert" x-cloak x-show="passwordsMismatch"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span>رمزهای عبور یکسان نیستند.</span></span></p>
                                </div>

                                <div class="field">
                                    <div class="control">
                                        <button class="button is-link is-fullwidth" type="submit" :disabled="submitting" :class="{ 'is-loading': submitting }">
                                            فعال‌سازی حساب
                                        </button>
                                    </div>
                                </div>
                            </form>

                            <div class="notification is-success mt-4" role="status" aria-live="polite"
                                 x-cloak x-show="resultType === 'success'" x-text="resultMessage"></div>
                            <div class="notification is-danger mt-4" role="alert"
                                 x-cloak x-show="resultType === 'danger'"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="resultMessage"></span></span></div>
                            <p class="has-text-centered mt-4" x-cloak x-show="resultType === 'success'">
                                <a class="button is-link is-light" href="/login">ورود به حساب</a>
                            </p>
                        @endif
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
`)
