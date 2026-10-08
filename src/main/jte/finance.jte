@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "امور مالی مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/js/member-names.js", "/js/money.js", "/js/list-search.js", "/js/modal-focus.js", "/js/finance.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="financePage()">
    <div class="container">
        @template.layout.page-header(
            title = "امور مالی مدرسه")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت امور مالی، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <template x-if="!blocked">
                    <div>
                        <%-- سال تحصیلی (academic-year.md فاز ۲): شهریه و فاکتورها به‌تفکیک سال‌اند؛ مدیر مالی فاکتورِ
                             سال‌های قبل را هم می‌بیند تا بتواند پرداخت ثبت کند. مدرسه‌ای که سالِ جاری ندارد این بخش را
                             نمی‌بیند و مثل پیش از این بدون سال کار می‌کند. --%>
                        <div class="box" id="financeYearBox" x-cloak x-show="years.length > 0">
                            <div class="field">
                                <label class="label" for="financeYear">سال تحصیلی</label>
                                <div class="select">
                                    <select id="financeYear" dir="ltr" x-model="year" @change="onYearChange()">
                                        <template x-for="y in years" :key="y">
                                            <option :value="y" x-text="Calendar.digits(y) + (y === currentYear ? ' (جاری)' : '')"></option>
                                        </template>
                                    </select>
                                </div>
                                <p class="help">شهریه، فاکتورها و «صدور فاکتور» برای همین سال‌اند. سالِ جاری پیش‌فرض است.</p>
                            </div>
                        </div>

                        <div class="notification is-warning is-light" id="financeUnattributedBanner" x-cloak x-show="hasUnattributed()">
                            <p class="mb-3">
                                اطلاعاتِ مالیِ قبلی هنوز به هیچ سالی نسبت داده نشده است
                                (<span x-show="unattributed.tuitionFee">شهریه و </span><span x-text="Calendar.digits(String(unattributed.invoices))"></span> فاکتور).
                                تا نسبت داده نشود، «صدور فاکتور» برای یک سال انجام نمی‌شود، چون دانش‌آموزِ دارای فاکتورِ قدیمی دوباره فاکتور می‌گرفت.
                                ثبت‌نام‌های تازه هم تا وقتی شهریه‌ی سالِ جاری ثبت نشده (یا شهریه‌ی قدیمی به سالِ جاری نسبت داده نشده) فاکتور نمی‌گیرند.
                            </p>
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="financeAttributeYear">نسبت دادن به سال</label>
                                    <div class="select">
                                        <select id="financeAttributeYear" dir="ltr" x-model="attributeYear">
                                            <option value="" disabled>یک سال را انتخاب کنید</option>
                                            <template x-for="y in years" :key="'attr-' + y">
                                                <option :value="y" x-text="Calendar.digits(y)"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-warning" type="button" @click="askAttribute()"
                                            :disabled="attributing" :class="{ 'is-loading': attributing }">
                                        نسبت دادن
                                    </button>
                                </div>
                            </div>
                        </div>

                        <div class="box">
                            <h2 class="title is-6">شهریه‌ی مصوب<span x-show="year"> - سال <span dir="ltr" x-text="Calendar.digits(year)"></span></span></h2>
                            <p class="mb-3" x-show="tuitionFee !== null">
                                شهریه‌ی فعلی: <strong x-text="Money.formatAmount(tuitionFee)"></strong>
                            </p>
                            <p class="mb-3" x-show="tuitionFee === null">
                                هنوز شهریه‌ای برای این مدرسه تنظیم نشده است.
                            </p>
                            <form @submit.prevent="setTuitionFee">
                                <div class="field is-grouped">
                                    <div class="control is-expanded">
                                        <label class="label" for="tuitionAmount">مبلغ شهریه‌ی جدید</label>
                                        <input class="input" dir="ltr" id="tuitionAmount" type="text" inputmode="numeric"
                                               pattern="[0-9]+" x-model="form.amount"
                                               @input="form.amount = Money.normalizeAmountInput($event)" required>
                                        <p class="help" x-show="Money.tomanWords(form.amount)" x-text="Money.tomanWords(form.amount)"></p>
                                    </div>
                                    <div class="control app-control-align-end">
                                        <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                            ثبت شهریه
                                        </button>
                                    </div>
                                </div>
                            </form>
                        </div>

                        <div class="level mb-2">
                            <div class="level-left">
                                <h2 class="title is-6 mb-0">فاکتورها</h2>
                            </div>
                            <div class="level-right">
                                <button class="button is-small" type="button" @click="askBackfill" :class="{ 'is-loading': backfilling }">
                                    صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور
                                </button>
                            </div>
                        </div>
                        <%-- فهرست فاکتورها ردیفی شد (DESIGN.md §5a): جست‌وجو بر نام دانش‌آموز، هر فاکتور یک ردیف با مبلغ،
                             پرداخت‌شده، وضعیت به کلمه و سررسید. جزئیات پرداخت هنوز جدول‌ها و فرم‌های پایین صفحه است. --%>
                        <template x-if="invoices.length > 0">
                            <div>
                                <div class="field">
                                    <label class="label is-sr-only" for="invoiceSearch">جست‌وجو در فاکتورها</label>
                                    <div class="control">
                                        <input class="input" id="invoiceSearch" type="search" x-model="filterText"
                                               placeholder="جست‌وجوی نام دانش‌آموز">
                                    </div>
                                </div>

                                <%-- chip وضعیت پرداخت (§5a): همیشه نمایش داده می‌شود؛ برچسب‌ها همان statusLabel() است --%>
                                <div class="app-chips" role="group" aria-label="فیلتر وضعیت پرداخت">
                                    <template x-for="item in chips()" :key="item.key">
                                        <button class="app-chip" type="button" :class="{ 'is-selected': activeChip() === item.key }"
                                                :aria-pressed="activeChip() === item.key ? 'true' : 'false'" @click="selectChip(item.key)">
                                            <span x-text="item.label"></span>
                                            (<span x-text="fa(item.count)"></span>)
                                        </button>
                                    </template>
                                </div>

                                <div class="app-list" role="list">
                                    <template x-for="invoice in visibleInvoices()" :key="invoice.id">
                                        <div class="app-invoice-row" role="listitem">
                                            <div class="app-invoice-row__main">
                                                <span class="app-invoice-row__name" x-text="studentName(invoice.studentId)"></span>
                                                <span class="tag is-light" x-show="currentYear && !invoice.academicYear"
                                                      title="این فاکتور هنوز به سالی نسبت داده نشده است">بدون سال</span>
                                                <span class="is-size-7 has-text-grey">
                                                    <span x-text="Money.formatAmount(invoice.amount)"></span>
                                                    · پرداخت‌شده <span x-text="Money.formatAmount(invoice.amountPaid)"></span>
                                                </span>
                                                <span class="is-size-7 has-text-grey" dir="ltr"
                                                      x-text="invoice.dueDate ? 'سررسید ' + Calendar.formatDate(invoice.dueDate) : 'بدون سررسید'"></span>
                                            </div>
                                            <span class="tag" :class="statusClass(invoice.status)"
                                                  x-text="statusLabel(invoice.status)"></span>
                                            <button class="button is-light"
                                                    :class="{ 'is-link': expandedInvoiceId === invoice.id }"
                                                    @click="togglePayments(invoice)">
                                                پرداخت‌ها
                                            </button>
                                        </div>
                                    </template>
                                </div>
                                <button class="button is-light mt-3" type="button" x-show="hasMoreInvoices()" @click="showMoreInvoices()"
                                        x-text="'نمایش بیشتر (' + moreInvoicesLabel() + ' مورد دیگر)'"></button>
                                <p class="has-text-grey mt-3" x-show="visibleInvoices().length === 0">
                                    فاکتوری با این جست‌وجو پیدا نشد.
                                </p>
                            </div>
                        </template>

                        <template x-for="invoice in visibleInvoices()" :key="'payments-' + invoice.id">
                            <div class="box" x-show="expandedInvoiceId === invoice.id" x-cloak>
                                <h3 class="title is-6">
                                    پرداخت‌های فاکتور <span x-text="studentName(invoice.studentId)"></span>
                                    (مانده: <span x-text="Money.formatAmount(remaining(invoice))"></span>)
                                </h3>

                                <p x-show="paymentsLoading">در حال بارگذاری...</p>
                                <div class="table-container" x-show="!paymentsLoading && payments.length > 0">
                                <table class="table is-fullwidth">
                                    <thead>
                                    <tr>
                                        <th scope="col">مبلغ</th>
                                        <th scope="col">روش</th>
                                        <th scope="col">توضیح</th>
                                        <th scope="col">تاریخ</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="payment in payments" :key="payment.id">
                                        <tr>
                                            <td x-text="Money.formatAmount(payment.amount)"></td>
                                            <td x-text="methodLabel(payment.method)"></td>
                                            <td x-text="payment.note || '—'"></td>
                                            <td dir="ltr" x-text="Calendar.formatDate(payment.paidAt)"></td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                                </div>
                                <p x-show="!paymentsLoading && payments.length === 0">
                                    هنوز پرداختی برای این فاکتور ثبت نشده است.
                                </p>

                                <form @submit.prevent="recordPayment(invoice)" x-show="invoice.status !== 'PAID'">
                                    <div class="field is-grouped is-grouped-multiline">
                                        <div class="control">
                                            <label class="label" :for="'paymentAmount-' + invoice.id">مبلغ</label>
                                            <input class="input" dir="ltr" :id="'paymentAmount-' + invoice.id"
                                                   type="text" inputmode="numeric" pattern="[0-9]+"
                                                   x-model="paymentForm.amount"
                                                   @input="paymentForm.amount = Money.normalizeAmountInput($event)" required>
                                            <p class="help" x-show="Money.tomanWords(paymentForm.amount)" x-text="Money.tomanWords(paymentForm.amount)"></p>
                                        </div>
                                        <div class="control">
                                            <label class="label" :for="'paymentMethod-' + invoice.id">روش پرداخت</label>
                                            <div class="select">
                                                <select :id="'paymentMethod-' + invoice.id" x-model="paymentForm.method">
                                                    <option value="CASH">نقدی</option>
                                                    <option value="CARD">کارت</option>
                                                    <option value="BANK_TRANSFER">انتقال بانکی</option>
                                                    <option value="CHEQUE">چک</option>
                                                    <option value="OTHER">سایر</option>
                                                </select>
                                            </div>
                                        </div>
                                        <div class="control is-expanded">
                                            <label class="label" :for="'paymentNote-' + invoice.id">توضیح (اختیاری)</label>
                                            <input class="input" :id="'paymentNote-' + invoice.id" type="text" x-model="paymentForm.note">
                                        </div>
                                        <div class="control app-control-align-end">
                                            <button class="button is-link" type="submit"
                                                    :disabled="recordingPayment"
                                                    :class="{ 'is-loading': recordingPayment }">
                                                ثبت پرداخت
                                            </button>
                                        </div>
                                    </div>
                                </form>
                            </div>
                        </template>

                        @template.layout.empty-state(
                            show = "!loading && invoices.length === 0",
                            art = "money",
                            message = @`هنوز فاکتوری برای این مدرسه صادر نشده است.`)
                    </div>
                </template>

                <%-- ------------------------------------------------ تأیید صدور گروهی فاکتور --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="financeConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="financeConfirmTitle">تأیید صدور فاکتورها</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="confirm && confirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="confirm ? confirm.error : ''"></span></span></div>
                            <p class="has-text-weight-semibold">
                                برای همه‌ی دانش‌آموزانِ بدون فاکتور<span x-show="confirm && confirm.year"> در سال
                                <span dir="ltr" x-text="confirm ? Calendar.digits(confirm.year) : ''"></span></span>
                                فاکتور صادر شود؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                دانش‌آموزی که از قبل فاکتور دارد دوباره فاکتور نمی‌گیرد. فاکتورهای صادرشده در همین صفحه دیده می‌شوند.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-link" type="button" id="financeConfirmBackfill"
                                    :class="{ 'is-loading': backfilling }" @click="backfill">صدور فاکتورها</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>

                <%-- ------------------------------------------------ تأیید نسبت دادن به سال --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="financeAttributeConfirmTitle"
                     tabindex="-1" x-ref="attributeModal" :class="{ 'is-active': attributeConfirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeAttributeConfirm">
                    <div class="modal-background" @click="closeAttributeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="financeAttributeConfirmTitle">تأیید نسبت دادن به سال</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeAttributeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="attributeConfirm && attributeConfirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="attributeConfirm ? attributeConfirm.error : ''"></span></span></div>
                            <p class="has-text-weight-semibold">
                                <span x-text="attributeConfirm ? Calendar.digits(String(attributeConfirm.invoices)) : ''"></span> فاکتورِ قبلی<span x-show="attributeConfirm && attributeConfirm.tuitionFee"> و شهریه‌ی قبلی</span> به سال
                                <span dir="ltr" x-text="attributeConfirm ? Calendar.digits(attributeConfirm.year) : ''"></span>
                                نسبت داده شود؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                بعد از این کار، این اطلاعات جزو آن سال حساب می‌شوند و «صدور فاکتور» برای یک سال دوباره ممکن می‌شود.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-link" type="button" id="financeConfirmAttribute"
                                    :class="{ 'is-loading': attributing }" @click="attribute">نسبت دادن</button>
                            <button class="button" type="button" @click="closeAttributeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
