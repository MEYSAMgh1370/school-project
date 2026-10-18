@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "تنظیمات مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/modal-focus.js", "/js/settings-logic.js", "/js/settings.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="settingsPage()">
    <div class="container">
        @template.layout.page-header(
            title = "تنظیمات مدرسه",
            subtitle = "تنظیمات هر بخش جداگانه ذخیره می‌شود؛ فقط بخش‌هایی که اجازه‌ی تغییرشان را دارید نمایش داده می‌شود.")

        @template.layout.school-scope(
            noSchoolMessage = "برای دیدن تنظیمات، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <p role="status" x-show="loading">در حال بارگذاری...</p>

                <template x-if="!blocked">
                    <div x-cloak x-show="!loading && loaded">

                        <%-- ------------------------------------------------ سال تحصیلی --%>
                        <div class="box" id="settingsYear" x-cloak x-show="hasYear">
                            <h2 class="title is-5">سال تحصیلی</h2>
                            <div class="notification is-danger" role="alert" id="settingsYearError" x-show="sec.year.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="sec.year.error"></span></span></div>
                            <div class="notification is-success" role="status" id="settingsYearNotice" x-show="sec.year.notice" x-text="sec.year.notice"></div>

                            <p class="is-size-7 has-text-grey mb-1">سال تحصیلی جاری</p>
                            <p class="title is-3 mb-4" dir="ltr" id="settingsYearCurrent" x-show="current"
                               x-text="current ? Calendar.digits(current.code) : ''"></p>
                            <p class="mb-4" id="settingsYearNone" x-show="!current">
                                هنوز سال تحصیلی‌ای برای این مدرسه انتخاب نشده است.
                            </p>
                            <%-- اولین انتخابِ سالِ جاری دیدِ معلم/دانش‌آموز/ولی را به همان سال محدود می‌کند
                                 (academic-year.md فاز ۱)؛ مدیر باید پیش از ثبت بداند --%>
                            <div class="notification is-warning is-light" id="settingsYearFirstChoiceWarning" x-show="!current">
                                تا وقتی سال جاری انتخاب نشود، همه‌ی کاربران همه‌ی سال‌ها را می‌بینند. با اولین انتخاب،
                                معلم، دانش‌آموز و ولی فقط اطلاعاتِ همان سال را می‌بینند؛ مثلاً نمره‌های سال‌های دیگر
                                (و نمره‌هایی که سالشان در فهرست نیست) فقط برای مدیر مدرسه قابل مشاهده می‌ماند و
                                کارنامه‌ی سال‌های قبل را مدیر تحویل می‌دهد.
                            </div>

                            <form @submit.prevent="saveYear">
                                <div class="field is-grouped is-grouped-multiline">
                                    <div class="control">
                                        <label class="label" for="settingsYearSelect">انتخاب سال تحصیلی</label>
                                        <div class="select">
                                            <select id="settingsYearSelect" x-model="selectedId" required>
                                                <option value="" disabled>یک سال را انتخاب کنید</option>
                                                <template x-for="year in years" :key="year.id">
                                                    <option :value="year.id" x-text="Calendar.digits(year.code)"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control app-control-align-end">
                                        <button class="button is-link" type="submit"
                                                :disabled="sec.year.saving" :class="{ 'is-loading': sec.year.saving }">
                                            ثبت سال تحصیلی
                                        </button>
                                    </div>
                                </div>
                                <p class="help">
                                    شروع و پایانِ هر سال با انتخابِ شما تعیین می‌شود. با تغییرِ سال جاری هیچ داده‌ای
                                    حذف یا تغییر نمی‌کند؛ سالِ پیش‌فرضِ صفحه‌ها عوض می‌شود و معلم، دانش‌آموز و ولی
                                    فقط اطلاعاتِ سالِ جاری را می‌بینند (شما هر سالی را می‌بینید). هر سالِ تحصیلیِ تازه
                                    خودکار، در تاریخِ تعیین‌شده در تنظیمات سیستم، به این فهرست اضافه می‌شود.
                                </p>
                            </form>

                            <%-- داده‌ی موجود که هنوز به هیچ سالی نسبت داده نشده (academic-year.md فاز ۳): با یک اقدامِ صریح به یک سال
                                 نسبت داده می‌شود، هیچ سالی حدس زده نمی‌شود --%>
                            <div class="notification is-warning is-light mt-4" id="settingsYearUnattributed"
                                 x-cloak x-show="hasUnattributed()">
                                <p class="mb-3">
                                    <span x-text="Calendar.digits(String(unattributed.classes))"></span> کلاس و
                                    <span x-text="Calendar.digits(String(unattributed.students))"></span> دانش‌آموزِ قبلی هنوز به هیچ سالی
                                    نسبت داده نشده‌اند. تا نسبت داده نشوند، ارتقای سالِ بعد و ثبت‌نامِ سال‌به‌سال برای این مدرسه شروع نمی‌شود.
                                    اگر مدرسه همین حالا از سالی گذشته به سالِ جاری رفته، آن‌ها را به سالِ قبل نسبت دهید و بعد ارتقا بدهید.
                                </p>
                                <div class="field is-grouped is-grouped-multiline">
                                    <div class="control">
                                        <label class="label" for="settingsYearAttributeYear">نسبت دادن به سال</label>
                                        <div class="select">
                                            <select id="settingsYearAttributeYear" dir="ltr"
                                                    @change="attributeYear = $event.target.value">
                                                <option value="" :selected="!attributeYear">یک سال را انتخاب کنید</option>
                                                <template x-for="year in attributableYears" :key="'attr-' + year.id">
                                                    <option :value="year.code" x-text="Calendar.digits(year.code)"
                                                            :selected="year.code === attributeYear"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control app-control-align-end">
                                        <button class="button is-warning" type="button" id="settingsYearAttributeButton"
                                                @click="askAttribute()" :disabled="attributing"
                                                :class="{ 'is-loading': attributing }">
                                            نسبت دادن
                                        </button>
                                    </div>
                                </div>
                            </div>

                            <%-- اطلاعاتِ مالیِ بدون سال (شهریه و فاکتور)؛ فقط برای دارنده‌ی FINANCE_MANAGE پر می‌شود --%>
                            <div class="notification is-warning is-light mt-4" id="settingsFinanceUnattributed"
                                 x-cloak x-show="hasFinanceUnattributed()">
                                <p class="mb-3">
                                    اطلاعاتِ مالیِ قبلی هنوز به هیچ سالی نسبت داده نشده است
                                    (<span x-show="financeUnattributed.tuitionFee">شهریه و </span><span x-text="Calendar.digits(String(financeUnattributed.invoices))"></span> فاکتور).
                                    تا نسبت داده نشود، «صدور فاکتور» برای یک سال انجام نمی‌شود، چون دانش‌آموزِ دارای فاکتورِ قدیمی دوباره فاکتور می‌گرفت.
                                    ثبت‌نام‌های تازه هم تا وقتی شهریه‌ی سالِ جاری ثبت نشده (یا شهریه‌ی قدیمی به سالِ جاری نسبت داده نشده) فاکتور نمی‌گیرند.
                                </p>
                                <div class="field is-grouped is-grouped-multiline">
                                    <div class="control">
                                        <label class="label" for="settingsFinanceAttributeYear">نسبت دادن به سال</label>
                                        <div class="select">
                                            <select id="settingsFinanceAttributeYear" dir="ltr"
                                                    @change="financeAttributeYear = $event.target.value">
                                                <option value="" :selected="!financeAttributeYear">یک سال را انتخاب کنید</option>
                                                <template x-for="year in attributableYears" :key="'fattr-' + year.id">
                                                    <option :value="year.code" x-text="Calendar.digits(year.code)"
                                                            :selected="year.code === financeAttributeYear"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control app-control-align-end">
                                        <button class="button is-warning" type="button" id="settingsFinanceAttributeButton"
                                                @click="askFinanceAttribute()" :disabled="financeAttributing"
                                                :class="{ 'is-loading': financeAttributing }">
                                            نسبت دادن
                                        </button>
                                    </div>
                                </div>
                            </div>

                            <%-- ارتقای خودکار: وقتی سالِ جاری به سالی بعدی عوض شود، همه‌ی دانش‌آموزان به پایه‌ی بعد می‌روند
                                 (کلاسِ آن‌ها بعداً مشخص می‌شود) --%>
                            <div class="field mt-4" id="settingsAutoPromotion">
                                <label class="checkbox" for="settingsAutoPromotionToggle">
                                    <input id="settingsAutoPromotionToggle" type="checkbox"
                                           :checked="autoPromotion" :disabled="autoSaving"
                                           @change="askAutoPromotion($event)">
                                    ارتقای خودکار دانش‌آموزان هنگام تعویض سال تحصیلی
                                </label>
                                <p class="help">
                                    با روشن بودن، هر بار که سالِ جاری به سالی بعدی عوض شود همه‌ی دانش‌آموزان به پایه‌ی بعد می‌روند
                                    و در «انتظار جا دادن در کلاس» می‌مانند تا کلاس‌های سالِ جدید را بسازید و آن‌ها را جا بدهید؛
                                    آخرین پایه‌ی مدرسه فارغ‌التحصیل می‌شود. تکرارِ پایه را خودتان تعیین می‌کنید و هر اجرا قابل برگرداندن است.
                                    خاموش بودن: ارتقا فقط دستی و از صفحه‌ی <a href="/promotion">ارتقای پایه</a>.
                                </p>
                            </div>
                        </div>

                        <%-- ------------------------------------------------ مالی --%>
                        <%-- اقساط شهریه (tuition-installments.md): کلیدِ مدرسه‌ای. خاموش کردن فقط ساختن/ویرایشِ طرح را می‌بندد؛
                             طرح‌های موجود همچنان کار می‌کنند و فقط‌خواندنی دیده می‌شوند. --%>
                        <div class="box" id="settingsFinance" x-cloak x-show="hasFinance">
                            <h2 class="title is-5">مالی</h2>
                            <div class="notification is-danger" role="alert" id="settingsFinanceError" x-show="sec.finance.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="sec.finance.error"></span></span></div>
                            <div class="notification is-success" role="status" id="settingsFinanceNotice" x-show="sec.finance.notice" x-text="sec.finance.notice"></div>

                            <form @submit.prevent="saveFinance">
                                <div class="field">
                                    <label class="checkbox" for="settingsInstallmentsEnabled">
                                        <input id="settingsInstallmentsEnabled" type="checkbox" x-model="installmentsEnabled">
                                        تقسیط شهریه در این مدرسه فعال باشد
                                    </label>
                                    <p class="help">
                                        وقتی فعال است، می‌توانید شهریه‌ی هر فاکتور را به چند قسط تقسیم کنید. با غیرفعال کردن، اقساطِ
                                        ثبت‌شده همچنان کار می‌کنند؛ فقط تعریف یا ویرایشِ اقساط بسته می‌شود.
                                    </p>
                                </div>
                                <button class="button is-link" type="submit"
                                        :disabled="sec.finance.saving" :class="{ 'is-loading': sec.finance.saving }">
                                    ذخیره‌ی تنظیمات مالی
                                </button>
                                <p class="help" role="status" x-show="sec.finance.saving">در حال ذخیره...</p>
                            </form>
                        </div>

                        <%-- ------------------------------------------------ یادآوری‌ها --%>
                        <div class="box" id="settingsReminders" x-cloak x-show="hasReminders">
                            <h2 class="title is-5">یادآوری‌ها</h2>
                            <div class="notification is-danger" role="alert" id="settingsRemindersError" x-show="sec.reminders.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="sec.reminders.error"></span></span></div>
                            <div class="notification is-success" role="status" id="settingsRemindersNotice" x-show="sec.reminders.notice" x-text="sec.reminders.notice"></div>

                            <form @submit.prevent="saveReminders" novalidate>
                                <p class="help mb-3" role="status" x-show="rolesLoading">در حال بارگذاری فهرست نقش‌ها...</p>
                                <fieldset class="field" x-show="roles.length > 0">
                                    <legend class="label">گیرندگان یادآوری رویدادها</legend>
                                    <template x-for="(role, index) in roles" :key="role.code">
                                        <div class="control">
                                            <label class="checkbox" :for="'settingsReminderRole-' + index">
                                                <input type="checkbox" :id="'settingsReminderRole-' + index" :value="role.code"
                                                       x-model="reminderRoles">
                                                <span x-text="role.title"></span>
                                            </label>
                                        </div>
                                    </template>
                                    <p class="help" id="settingsRemindersAllRoles" x-show="remindersAll()">
                                        تا وقتی نقش‌ها را انتخاب نکنید، همه‌ی اعضای مدرسه یادآوری می‌گیرند.
                                    </p>
                                    <p class="help is-danger" role="alert" id="settingsRemindersFieldError" x-show="remindersFieldError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="remindersFieldError"></span></span></p>
                                </fieldset>
                                <button class="button is-link" type="submit"
                                        :disabled="sec.reminders.saving" :class="{ 'is-loading': sec.reminders.saving }">
                                    ذخیره‌ی گیرندگان
                                </button>
                                <p class="help" role="status" x-show="sec.reminders.saving">در حال ذخیره...</p>
                            </form>
                        </div>

                        <%-- ------------------------------------------------ بات بله --%>
                        <div class="box" id="settingsBot" x-cloak x-show="hasBot">
                            <h2 class="title is-5">بات بله</h2>
                            <div class="notification is-danger" role="alert" id="settingsBotError" x-show="sec.bot.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="sec.bot.error"></span></span></div>
                            <div class="notification is-success" role="status" id="settingsBotNotice" x-show="sec.bot.notice" x-text="sec.bot.notice"></div>

                            <form @submit.prevent="saveBot">
                                <div class="field">
                                    <label class="checkbox" for="settingsBotEnabled">
                                        <input id="settingsBotEnabled" type="checkbox" role="switch" x-model="botEnabled">
                                        بات بله برای این مدرسه روشن باشد
                                    </label>
                                </div>
                                <p class="help mb-3" role="status" x-show="rolesLoading">در حال بارگذاری فهرست نقش‌ها...</p>
                                <fieldset class="field" x-show="roles.length > 0">
                                    <legend class="label">نقش‌هایی که می‌توانند از بات استفاده کنند</legend>
                                    <template x-for="(role, index) in roles" :key="role.code">
                                        <div class="control">
                                            <label class="checkbox" :for="'settingsBotRole-' + index">
                                                <input type="checkbox" :id="'settingsBotRole-' + index" :value="role.code"
                                                       x-model="botRoles">
                                                <span x-text="role.title"></span>
                                            </label>
                                        </div>
                                    </template>
                                    <p class="help">اگر هیچ نقشی انتخاب نشود، همه‌ی نقش‌ها می‌توانند از بات استفاده کنند.</p>
                                    <p class="help" id="settingsBotAllRoles" x-show="botAll()">اکنون همه‌ی نقش‌ها مجازند.</p>
                                </fieldset>
                                <button class="button is-link" type="submit"
                                        :disabled="sec.bot.saving" :class="{ 'is-loading': sec.bot.saving }">
                                    ذخیره‌ی تنظیمات بات
                                </button>
                                <p class="help" role="status" x-show="sec.bot.saving">در حال ذخیره...</p>
                            </form>
                        </div>
                    </div>
                </template>

                <%-- ------------------------------------------------ تأیید نسبت دادن --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="settingsAttributeConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="settingsAttributeConfirmTitle">تأیید نسبت دادن به سال</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="confirm && confirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="confirm ? confirm.error : ''"></span></span></div>
                            <p class="has-text-weight-semibold">
                                <span x-text="confirm ? Calendar.digits(String(confirm.classes)) : ''"></span> کلاس و
                                <span x-text="confirm ? Calendar.digits(String(confirm.students)) : ''"></span> دانش‌آموزِ قبلی به سال
                                <span dir="ltr" x-text="confirm ? Calendar.digits(confirm.year) : ''"></span>
                                نسبت داده شوند؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                بعد از این کار، این کلاس‌ها و دانش‌آموزان جزو آن سال حساب می‌شوند و ارتقای سالِ بعد و ثبت‌نامِ سال‌به‌سال برای این مدرسه شروع می‌شود.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-warning" type="button" id="settingsAttributeConfirm"
                                    :class="{ 'is-loading': attributing }" @click="attribute">نسبت دادن</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>

                <%-- ------------------------------------------------ تأیید ارتقای خودکار --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="settingsAutoConfirmTitle"
                     tabindex="-1" x-ref="autoConfirmModal" :class="{ 'is-active': autoConfirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeAutoConfirm">
                    <div class="modal-background" @click="closeAutoConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="settingsAutoConfirmTitle"
                               x-text="autoConfirm && autoConfirm.enabled ? 'تأیید روشن کردن ارتقای خودکار' : 'تأیید خاموش کردن ارتقای خودکار'"></p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeAutoConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="autoConfirm && autoConfirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="autoConfirm ? autoConfirm.error : ''"></span></span></div>
                            <template x-if="autoConfirm && autoConfirm.enabled">
                                <div>
                                    <p class="has-text-weight-semibold">ارتقای خودکار دانش‌آموزان روشن شود؟</p>
                                    <p class="has-text-grey is-size-7">
                                        از تعویض بعدیِ سالِ جاری، هر بار که سالِ جاری به سالی بعدی عوض شود همه‌ی دانش‌آموزان به پایه‌ی بعد می‌روند
                                        و در «انتظار جا دادن در کلاس» می‌مانند تا کلاس‌های سالِ جدید را بسازید و آن‌ها را جا بدهید؛
                                        آخرین پایه‌ی مدرسه فارغ‌التحصیل می‌شود. تا پیش از جا دادن دانش‌آموزان در کلاس، هر اجرا از صفحه‌ی ارتقای پایه قابل برگرداندن است.
                                    </p>
                                </div>
                            </template>
                            <template x-if="autoConfirm && !autoConfirm.enabled">
                                <div>
                                    <p class="has-text-weight-semibold">ارتقای خودکار دانش‌آموزان خاموش شود؟</p>
                                    <p class="has-text-grey is-size-7">
                                        از این پس با عوض شدنِ سالِ جاری هیچ دانش‌آموزی خودکار ارتقا نمی‌گیرد؛ ارتقا فقط دستی و از صفحه‌ی ارتقای پایه انجام می‌شود.
                                        ارتقاهای قبلی تغییر نمی‌کنند.
                                    </p>
                                </div>
                            </template>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-link" type="button" id="settingsAutoConfirmButton"
                                    :class="{ 'is-loading': autoSaving }" @click="setAutoPromotion"
                                    x-text="autoConfirm && autoConfirm.enabled ? 'روشن کردن' : 'خاموش کردن'"></button>
                            <button class="button" type="button" @click="closeAutoConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>

                <%-- ------------------------------------------------ تأیید نسبت دادنِ اطلاعاتِ مالی --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="settingsFinanceAttributeConfirmTitle"
                     tabindex="-1" x-ref="financeConfirmModal" :class="{ 'is-active': financeConfirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeFinanceConfirm">
                    <div class="modal-background" @click="closeFinanceConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="settingsFinanceAttributeConfirmTitle">تأیید نسبت دادن به سال</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeFinanceConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="financeConfirm && financeConfirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="financeConfirm ? financeConfirm.error : ''"></span></span></div>
                            <p class="has-text-weight-semibold">
                                <span x-text="financeConfirm ? Calendar.digits(String(financeConfirm.invoices)) : ''"></span> فاکتورِ قبلی<span x-show="financeConfirm && financeConfirm.tuitionFee"> و شهریه‌ی قبلی</span> به سال
                                <span dir="ltr" x-text="financeConfirm ? Calendar.digits(financeConfirm.year) : ''"></span>
                                نسبت داده شود؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                بعد از این کار، این اطلاعات جزو آن سال حساب می‌شوند و «صدور فاکتور» برای یک سال دوباره ممکن می‌شود.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-warning" type="button" id="settingsFinanceAttributeConfirm"
                                    :class="{ 'is-loading': financeAttributing }" @click="attributeFinance">نسبت دادن</button>
                            <button class="button" type="button" @click="closeFinanceConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
