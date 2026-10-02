@import com.example.roshdeandishe.web.dto.DashboardModel
@import com.example.roshdeandishe.web.model.NavCatalog
@import com.example.roshdeandishe.web.model.NavbarModel
@import com.example.roshdeandishe.web.model.RoleLabels

@param DashboardModel model
@param NavbarModel navbar

@template.layout.main(title = "داشبورد", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/dashboard.js"),
        styles = java.util.List.of("/css/dashboard.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1">
    <div class="container">
        <h1 class="title is-4">عضویت‌های من</h1>
        <p class="subtitle is-6" dir="ltr">${model.email()}</p>

        <%-- «دسترسی سریع»: همان لینک‌های سایدبار، از منبع واحد NavCatalog (فقط
             نمایش - کنترل دسترسی سمت سرور می‌ماند). نقشِ انتخاب‌شده از
             $store.nav (آینه‌ی سوییچر navbar.js) می‌آید؛ کاربر بدون عضویت نقشی
             ندارد و کل بخش پنهان می‌ماند. x-data خالی لازم است: Alpine فقط داخل
             یک x-data ریشه را راه‌اندازی می‌کند و این بخش ریشه‌ی دیگری ندارد. --%>
        <section class="mb-5" aria-labelledby="dashboard-shortcuts-title" x-data
                 x-cloak x-show="${NavCatalog.anyShortcutVisibleWhen("$store.nav.selectedRole")}">
            <h2 class="title is-5" id="dashboard-shortcuts-title">دسترسی سریع</h2>
            <div class="columns is-multiline is-mobile is-variable is-3">
                @for(var link : NavCatalog.shortcuts())
                    <div class="column is-4-desktop is-6-tablet is-6-mobile"
                         x-cloak x-show="${link.shortcutVisibleWhen("$store.nav.selectedRole")}">
                        <a class="box app-shortcut" href="${link.href()}">
                            <h3 class="title is-6 mb-1">${link.label()}</h3>
                            <p class="is-size-7 has-text-grey">${link.description()}</p>
                        </a>
                    </div>
                @endfor
            </div>
        </section>

        <%-- «فرزندان من»: فقط برای کاربری که در حداقل یک مدرسه والدِ فعال است و فقط
             وقتی نقشِ انتخاب‌شده «والد» است (همان مدل کارت‌های دسترسی سریع،
             $store.nav). داده فقط فرزندانِ واقعاً لینک‌شده به همین کاربر است
             (WardsAssembler). وضعیت مالی فقط شمارش فاکتورها بر اساس وضعیتشان است،
             نه مبلغ (واحد پولی هنوز تصمیم‌نگرفته). عددها اینجا ASCII رندر می‌شوند و
             با Jalali.toPersianDigits به رقم فارسی درمی‌آیند (DESIGN.md §8).
             x-data خالی لازم است: Alpine بدون ریشه‌ی x-data x-show را اجرا نمی‌کند. --%>
        @if(model.parent())
            <section class="mb-5" aria-labelledby="dashboard-wards-title" x-data
                     x-cloak x-show="['PARENT'].includes($store.nav.selectedRole)">
                <h2 class="title is-5" id="dashboard-wards-title">فرزندان من</h2>
                @if(model.wards().isEmpty())
                    <div class="notification is-light">
                        هنوز دانش‌آموزی به حساب شما لینک نشده است. مدیر مدرسه می‌تواند از صفحه‌ی
                        «دانش‌آموزان» فرزندتان را به حساب شما متصل کند.
                    </div>
                @else
                    <div class="columns is-multiline is-variable is-3">
                        @for(var ward : model.wards())
                            <div class="column is-6-desktop is-12-tablet is-12-mobile">
                                <article class="box app-ward">
                                    <h3 class="title is-6 mb-2">${ward.name()}</h3>
                                    <p class="is-size-7 has-text-grey mb-2">${ward.schoolName()}</p>
                                    <div class="tags mb-2">
                                        @if(ward.hasClass())
                                            <span class="tag is-light">کلاس ${ward.className()}</span>
                                        @endif
                                        @if(ward.hasSection())
                                            <span class="tag is-light">بخش ${ward.section()}</span>
                                        @endif
                                    </div>
                                    <p class="mb-0">
                                        <span class="is-size-7 has-text-grey">وضعیت مالی:</span>
                                        @if(ward.hasNoInvoices())
                                            <span class="tag is-light">فاکتوری صادر نشده</span>
                                        @elseif(ward.isOverdue())
                                            <span class="tag is-danger"><span x-text="Jalali.toPersianDigits('${ward.overdueCount()}')">${ward.overdueCount()}</span>&nbsp;فاکتور دیرکرد</span>
                                        @elseif(ward.isOpen())
                                            <span class="tag is-warning"><span x-text="Jalali.toPersianDigits('${ward.openCount()}')">${ward.openCount()}</span>&nbsp;فاکتور در انتظار پرداخت</span>
                                        @else
                                            <span class="tag is-success">همه‌ی فاکتورها تسویه‌شده</span>
                                        @endif
                                    </p>
                                    <%-- لینک‌های هر فرزند: فقط شناسه‌ی او در URL می‌رود (نه نام - اطلاعات
                                         کودک نباید در تاریخچه/لاگ نشت کند). مدرسه‌ی همین فرزند قبل از
                                         رفتن به‌عنوان مدرسه‌ی انتخاب‌شده ذخیره می‌شود تا صفحه‌ی مقصد
                                         همان مدرسه را بارگذاری کند. کنترل‌ها اندازه‌ی عادی دارند (۴۴px). --%>
                                    <div class="buttons mt-3 mb-0">
                                        <a class="button is-light" href="/my-grades?student=${ward.studentId()}"
                                           data-school="${ward.schoolId()}"
                                           @click="$store.nav.rememberSchool($el.dataset.school)">نمره‌ها</a>
                                        <a class="button is-light" href="/my-invoices?student=${ward.studentId()}"
                                           data-school="${ward.schoolId()}"
                                           @click="$store.nav.rememberSchool($el.dataset.school)">فاکتورها</a>
                                    </div>
                                </article>
                            </div>
                        @endfor
                    </div>
                @endif
            </section>
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
    </div>
</section>
`)
