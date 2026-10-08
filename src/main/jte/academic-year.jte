@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "سال تحصیلی", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/modal-focus.js", "/js/academic-year.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="academicYearPage()">
    <div class="container">
        @template.layout.page-header(
            title = "سال تحصیلی",
            subtitle = "سال تحصیلیِ جاریِ این مدرسه؛ صفحه‌های دیگر آن را به‌عنوان سال پیش‌فرض می‌گیرند.")

        @template.layout.school-scope(
            noSchoolMessage = "برای تنظیم سال تحصیلی، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <template x-if="!blocked">
                    <div class="box" x-cloak x-show="!loading && loaded">
                        <p class="is-size-7 has-text-grey mb-1">سال تحصیلی جاری</p>
                        <p class="title is-3 mb-4" dir="ltr" id="academicYearCurrent" x-show="current"
                           x-text="current ? Calendar.digits(current.code) : ''"></p>
                        <p class="mb-4" id="academicYearNone" x-show="!current">
                            هنوز سال تحصیلی‌ای برای این مدرسه انتخاب نشده است.
                        </p>
                        <%-- اولین انتخابِ سالِ جاری دیدِ معلم/دانش‌آموز/ولی را به همان سال محدود می‌کند
                             (academic-year.md فاز ۱)؛ مدیر باید پیش از ثبت بداند --%>
                        <div class="notification is-warning is-light" id="academicYearFirstChoiceWarning" x-show="!current">
                            تا وقتی سال جاری انتخاب نشود، همه‌ی کاربران همه‌ی سال‌ها را می‌بینند. با اولین انتخاب،
                            معلم، دانش‌آموز و ولی فقط اطلاعاتِ همان سال را می‌بینند؛ مثلاً نمره‌های سال‌های دیگر
                            (و نمره‌هایی که سالشان در فهرست نیست) فقط برای مدیر مدرسه قابل مشاهده می‌ماند و
                            کارنامه‌ی سال‌های قبل را مدیر تحویل می‌دهد.
                        </div>

                        <form @submit.prevent="save">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="academicYearSelect">انتخاب سال تحصیلی</label>
                                    <div class="select">
                                        <select id="academicYearSelect" x-model="selectedId" required>
                                            <option value="" disabled>یک سال را انتخاب کنید</option>
                                            <template x-for="year in years" :key="year.id">
                                                <option :value="year.id" x-text="Calendar.digits(year.code)"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-link" type="submit"
                                            :disabled="saving" :class="{ 'is-loading': saving }">
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
                        <div class="notification is-warning is-light mt-4" id="academicYearUnattributed"
                             x-cloak x-show="hasUnattributed()">
                            <p class="mb-3">
                                <span x-text="Calendar.digits(String(unattributed.classes))"></span> کلاس و
                                <span x-text="Calendar.digits(String(unattributed.students))"></span> دانش‌آموزِ قبلی هنوز به هیچ سالی
                                نسبت داده نشده‌اند. تا نسبت داده نشوند، ارتقای سالِ بعد و ثبت‌نامِ سال‌به‌سال برای این مدرسه شروع نمی‌شود.
                                اگر مدرسه همین حالا از سالی گذشته به سالِ جاری رفته، آن‌ها را به سالِ قبل نسبت دهید و بعد ارتقا بدهید.
                            </p>
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="academicYearAttributeYear">نسبت دادن به سال</label>
                                    <div class="select">
                                        <select id="academicYearAttributeYear" dir="ltr"
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
                                    <button class="button is-warning" type="button" id="academicYearAttributeButton"
                                            @click="askAttribute()" :disabled="attributing"
                                            :class="{ 'is-loading': attributing }">
                                        نسبت دادن
                                    </button>
                                </div>
                            </div>
                        </div>

                        <%-- ارتقای خودکار: وقتی سالِ جاری به سالی بعدی عوض شود، همه‌ی دانش‌آموزان به پایه‌ی بعد می‌روند
                             (کلاسِ آن‌ها بعداً مشخص می‌شود) --%>
                        <div class="field mt-4" id="academicYearAutoPromotion">
                            <label class="checkbox" for="academicYearAutoPromotionToggle">
                                <input id="academicYearAutoPromotionToggle" type="checkbox"
                                       :checked="autoPromotion" :disabled="autoSaving"
                                       @change="setAutoPromotion($event.target.checked)">
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
                </template>

                <%-- ------------------------------------------------ تأیید نسبت دادن --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="academicYearConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="academicYearConfirmTitle">تأیید نسبت دادن به سال</p>
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
                            <button class="button is-warning" type="button" id="academicYearAttributeConfirm"
                                    :class="{ 'is-loading': attributing }" @click="attribute">نسبت دادن</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
