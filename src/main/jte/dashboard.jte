@import com.example.roshdeandishe.web.dto.DashboardModel
@import com.example.roshdeandishe.web.model.NavbarModel
@import com.example.roshdeandishe.web.model.RoleLabels

@param DashboardModel model
@param NavbarModel navbar

@template.layout.main(title = "داشبورد", scripts = java.util.List.of("/js/navbar.js", "/js/dashboard.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1">
    <div class="container">
        <h1 class="title is-4">عضویت‌های من</h1>
        <p class="subtitle is-6" dir="ltr">${model.email()}</p>

        <%-- شماره تلفن روی data-attribute (نه داخل عبارت x-data) قرار می‌گیرد
             تا از escape شدنِ صحیحِ HTML مطمئن باشیم - setPhoneNumber فقط طول
             را اعتبارسنجی می‌کند، نه نویسه‌ها، پس درج مستقیم داخل یک عبارت JS
             می‌توانست با یک تکِ‌کوتیشن در مقدار بشکند. --%>
        <div class="box" data-phone-number="${model.phoneNumber() == null ? "" : model.phoneNumber()}"
             x-data="phoneNumberField()" x-init="phoneNumber = $el.dataset.phoneNumber">
            <h2 class="title is-5">شماره تلفن</h2>
            <p class="is-size-7 has-text-grey mb-3">
                با ثبت شماره تلفن می‌توانید ربات بله را به حساب خود متصل کنید و
                با کدِ پیامکی (به‌جای رمز عبور) وارد شوید.
            </p>
            <form @submit.prevent="save">
                <label class="label" for="dashboardPhone">شماره تلفن همراه</label>
                <div class="field has-addons">
                    <div class="control is-expanded">
                        <input class="input" id="dashboardPhone" type="tel" dir="ltr" x-model="phoneNumber"
                               autocomplete="tel" inputmode="tel" placeholder="۰۹۱۲۳۴۵۶۷۸۹" required>
                    </div>
                    <div class="control">
                        <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                            ذخیره
                        </button>
                    </div>
                </div>
            </form>
            <div class="notification is-danger mt-3" role="alert"
                 x-cloak x-show="errorMessage" x-text="errorMessage"></div>
            <div class="notification is-success mt-3" role="status" aria-live="polite"
                 x-cloak x-show="successMessage" x-text="successMessage"></div>
        </div>

        <%-- نشانی ورود مدرسه: فقط برای مدرسه‌هایی که کاربر در آن‌ها مدیرِ
             فعال است. داده سمت سرور رندر می‌شود (بدون fetch، بدون
             X-School-ID) و به سوییچرِ ناوبار واکنش نشان نمی‌دهد چون هر
             مدرسه‌ی مدیریت‌شده هم‌زمان فهرست می‌شود، نه فقط مدرسه‌ی انتخابی. --%>
        @if(!model.schoolEntries().isEmpty())
            <div class="box" x-data="schoolEntryLinks()">
                <h2 class="title is-5">نشانی ورود مدرسه</h2>
                <p class="is-size-7 has-text-grey mb-3">
                    این نشانی را بین معلم‌ها، دانش‌آموزان و والدین پخش کنید تا
                    از درِ اختصاصیِ مدرسه‌ی شما وارد شوند.
                </p>
                @for(var entry : model.schoolEntries())
                    <div class="mb-4">
                        <h3 class="title is-6 mb-2">${entry.schoolName()}</h3>
                        @if(entry.hasSlug())
                            <%-- slug از طریق data-attribute به Alpine می‌رسد، نه
                                 با درج مستقیم در عبارت JS - همان قاعده‌ای که
                                 باکس شماره تلفن بالاتر رعایت می‌کند. --%>
                            <p class="mb-2">
                                <a href="/s/${entry.slug()}" dir="ltr" data-slug="${entry.slug()}"
                                   x-text="entryUrl($el.dataset.slug)"></a>
                            </p>
                            <button class="button is-light is-small" data-slug="${entry.slug()}"
                                    @click="copy($el.dataset.slug)">
                                کپی نشانی
                            </button>
                        @else
                            <p class="is-size-7 has-text-grey">
                                این مدرسه نشانی اختصاصی ندارد. نشانی فقط هنگام
                                ثبت مدرسه انتخاب می‌شود.
                            </p>
                        @endif
                    </div>
                @endfor
                <div class="notification is-danger mt-3" role="alert"
                     x-cloak x-show="errorMessage" x-text="errorMessage"></div>
                <div class="notification is-success mt-3" role="status" aria-live="polite"
                     x-cloak x-show="successMessage" x-text="successMessage"></div>
            </div>
        @endif

        <%-- CTA ثبت مدرسه فقط در حالت بدون عضویت (جریان بنیان‌گذار):
             به دانش‌آموز/والدِ دعوت‌شده پیشنهاد «مدرسه بسازید» نمی‌دهیم
             (تصمیم ۲۰۲۶-۰۷-۰۶؛ فرم به /schools/new منتقل شد). --%>
        @if(model.memberships().isEmpty())
            <div class="notification">
                هنوز در هیچ مدرسه‌ای عضویتی ندارید. می‌توانید همین حالا
                <a href="/schools/new">مدرسه‌ی خودتان را ثبت کنید</a>،
                یا منتظر دعوت‌نامه‌ی مدرسه‌تان بمانید.
            </div>
        @else
            <div class="table-container">
            <table class="table is-fullwidth is-striped">
                <thead>
                <tr>
                    <th scope="col">مدرسه</th>
                    <th scope="col">نقش</th>
                    <th scope="col">وضعیت</th>
                </tr>
                </thead>
                <tbody>
                @for(var membership : model.memberships())
                    <tr>
                        <td>${membership.schoolName()}</td>
                        <td>${RoleLabels.persian(membership.roleCode())}</td>
                        <td>
                            @if(membership.active())
                                <span class="tag is-success">فعال</span>
                            @else
                                <span class="tag is-danger">غیرفعال</span>
                            @endif
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
            </div>
        @endif
    </div>
</section>
`)
