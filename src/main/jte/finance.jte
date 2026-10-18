@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- صفحه‌ی مالی دانش‌آموز-محور (school-settings): فهرست دانش‌آموزان با دامنه‌ی «همه / فقط امسال / سال‌های پیش»، فیلتر بدهی و
     جست‌وجوی نام (سمت سرور، صفحه‌بندی‌شده)؛ باز کردن یک ردیف همه‌ی فاکتورهای آن دانش‌آموز را در همه‌ی سال‌ها می‌آورد.
     انتخاب سال، هشدار «بدون سال» و کلید اقساط به /settings رفته‌اند. شهریه و «صدور فاکتور» همیشه برای سال جاریِ مدرسه‌اند.
     chip ها از grades.css می‌آیند (DESIGN.md §5a). --%>
@template.layout.main(title = "امور مالی مدرسه", styles = java.util.List.of("/css/grades.css"), scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/js/money.js", "/js/finance-logic.js", "/js/date-input.js", "/js/modal-focus.js", "/js/finance.js"), content = @`
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
                        <div class="box">
                            <h2 class="title is-6">شهریه‌ی مصوب - سال جاری</h2>
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
                                <h2 class="title is-6 mb-0">دانش‌آموزان و فاکتورها</h2>
                            </div>
                            <div class="level-right">
                                <button class="button is-small" type="button" @click="askBackfill" :class="{ 'is-loading': backfilling }">
                                    صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور
                                </button>
                            </div>
                        </div>

                        <%-- جست‌وجو (سمت سرور، با تأخیر ۳۰۰ میلی‌ثانیه): نام، نام‌خانوادگی یا هر دو در یک کادر --%>
                        <div class="columns">
                            <div class="column">
                                <label class="label" for="financeFirstName">نام</label>
                                <input class="input" id="financeFirstName" type="search" autocomplete="off" x-model="firstName">
                            </div>
                            <div class="column">
                                <label class="label" for="financeLastName">نام خانوادگی</label>
                                <input class="input" id="financeLastName" type="search" autocomplete="off" x-model="lastName">
                            </div>
                            <div class="column">
                                <label class="label" for="financeSearch">جست‌وجوی نام</label>
                                <input class="input" id="financeSearch" type="search" autocomplete="off"
                                       placeholder="بخشی از نام کامل" x-model="q">
                            </div>
                        </div>

                        <%-- دامنه و فیلتر بدهی: دو گروه chip (§5a)؛ شمارنده‌ی دامنه‌ها زیر همان فیلتر نام/بدهی حساب می‌شود --%>
                        <div class="app-chips" role="group" aria-label="دامنه‌ی فهرست">
                            <button class="app-chip" type="button" data-scope-chip="all" :class="{ 'is-selected': scope === 'all' }"
                                    :aria-pressed="scope === 'all' ? 'true' : 'false'" @click="selectScope('all')">
                                <span>همه</span>
                                (<span x-text="fa(counts.all)"></span>)
                            </button>
                            <button class="app-chip" type="button" data-scope-chip="current" :class="{ 'is-selected': scope === 'current' }"
                                    :aria-pressed="scope === 'current' ? 'true' : 'false'" @click="selectScope('current')">
                                <span>فقط امسال</span>
                                (<span x-text="fa(counts.current)"></span>)
                            </button>
                            <button class="app-chip" type="button" data-scope-chip="previous" :class="{ 'is-selected': scope === 'previous' }"
                                    :aria-pressed="scope === 'previous' ? 'true' : 'false'" @click="selectScope('previous')">
                                <span>سال‌های پیش</span>
                                (<span x-text="fa(counts.previous)"></span>)
                            </button>
                        </div>
                        <div class="app-chips" role="group" aria-label="فیلتر بدهی">
                            <template x-for="item in debtChips" :key="item.key">
                                <button class="app-chip" type="button" data-debt-chip :class="{ 'is-selected': debt === item.key }"
                                        :aria-pressed="debt === item.key ? 'true' : 'false'" @click="selectDebt(item.key)"
                                        x-text="item.label"></button>
                            </template>
                        </div>

                        <p class="has-text-grey" role="status" x-show="listLoading && students.length === 0">در حال بارگذاری...</p>

                        <div class="app-list" role="list" x-show="students.length > 0">
                            <template x-for="student in students" :key="student.studentId">
                                <div role="listitem" data-finance-student>
                                    <div class="app-invoice-row">
                                        <div class="app-invoice-row__main">
                                            <span class="app-invoice-row__name" x-text="student.fullName"></span>
                                            <span class="is-size-7 has-text-grey">
                                                بدهی پیشین: <span x-text="Money.formatAmount(student.previousDebt)"></span>
                                                · مانده‌ی کل: <strong x-text="Money.formatAmount(student.totalRemaining)"></strong>
                                            </span>
                                        </div>
                                        <span class="tag is-light" x-show="studentStatusLabel(student)" x-text="studentStatusLabel(student)"></span>
                                        <span class="tag is-danger" x-show="student.overdue && student.currentStatus !== 'OVERDUE'">دیرکرد</span>
                                        <span class="tag" :class="student.currentStatus ? statusClass(student.currentStatus) : 'is-light'"
                                              x-text="student.currentStatus ? statusLabel(student.currentStatus) : 'بدون فاکتور امسال'"></span>
                                        <button class="button is-light" type="button" data-student-toggle
                                                :class="{ 'is-link': expandedStudentId === student.studentId }"
                                                :aria-expanded="expandedStudentId === student.studentId ? 'true' : 'false'"
                                                @click="toggleStudent(student)">
                                            فاکتورها
                                        </button>
                                    </div>

                                    <template x-if="expandedStudentId === student.studentId">
                                        <div class="px-4 py-3" data-student-invoices>
                                            <p class="has-text-grey" role="status" x-show="invoicesLoading">در حال بارگذاری فاکتورها...</p>
                                            <div class="notification is-danger" role="alert" x-show="invoicesError"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="invoicesError"></span></span></div>
                                            <p class="has-text-grey" x-show="!invoicesLoading && !invoicesError && studentInvoices.length === 0">
                                                این دانش‌آموز هنوز فاکتوری ندارد. پس از ثبت شهریه‌ی سال جاری، با «صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور» برایش فاکتور بسازید.
                                            </p>
                                            <template x-for="invoice in studentInvoices" :key="'invoice-' + invoice.id">
                                                <div class="box" data-invoice-panel>
                                                    <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap mb-2">
                                                        <h3 class="title is-6 mb-0">
                                                            <span x-show="invoice.academicYear">سال تحصیلی <span dir="ltr" x-text="Calendar.digits(invoice.academicYear)"></span></span>
                                                            <span x-show="!invoice.academicYear">بدون سال</span>
                                                        </h3>
                                                        <span class="tag" :class="statusClass(invoice.status)" x-text="statusLabel(invoice.status)"></span>
                                                    </div>
                                                    <p class="is-size-7 has-text-grey mb-3">
                                                        قابل پرداخت <span x-text="Money.formatAmount(payable(invoice))"></span>
                                                        · پرداخت‌شده <span x-text="Money.formatAmount(invoice.amountPaid)"></span>
                                                        · مانده <strong x-text="Money.formatAmount(remaining(invoice))"></strong>
                                                        · <span dir="ltr" x-text="invoice.dueDate ? 'سررسید ' + Calendar.formatDate(invoice.dueDate) : 'بدون سررسید'"></span>
                                                    </p>
                                                    <button class="button is-light" type="button" data-invoice-toggle
                                                            :class="{ 'is-link': expandedInvoiceId === invoice.id }"
                                                            :aria-expanded="expandedInvoiceId === invoice.id ? 'true' : 'false'"
                                                            @click="togglePayments(invoice)">
                                                        پرداخت، تخفیف و اقساط
                                                    </button>
                                                    <template x-if="expandedInvoiceId === invoice.id">
                                                        <div class="mt-4">
                                        <p class="mb-3" data-invoice-summary>
                                            مبلغ فاکتور: <strong x-text="Money.formatAmount(invoice.amount)"></strong>
                                            · تخفیف: <strong x-text="Money.formatAmount(invoice.discountTotal || 0)"></strong>
                                            · قابل پرداخت: <strong x-text="Money.formatAmount(payable(invoice))"></strong>
                                            · پرداخت‌شده: <strong x-text="Money.formatAmount(invoice.amountPaid)"></strong>
                                            · مانده: <strong x-text="Money.formatAmount(remaining(invoice))"></strong>
                                        </p>
                                        <%-- سابقه‌ی تغییرات همین فاکتور (صفحه‌ی فقط‌خواندنیِ /finance-audit؛ فیلتر از query string پر می‌شود) --%>
                                        <p class="mb-3">
                                            <a data-invoice-audit-link :href="'/finance-audit?invoiceId=' + encodeURIComponent(invoice.id)">سابقه‌ی این فاکتور</a>
                                        </p>
                                        <template x-for="pending in pendingDiscounts()" :key="'pending-' + pending.id">
                                            <p class="help is-warning mb-3" data-pending-discount>
                                                تخفیف مشروط (<span x-text="Money.formatAmount(pending.amount)"></span>) تا
                                                <span x-text="Calendar.formatDate(pending.deadline)"></span>
                                                — در صورت عدم تسویه‌ی کامل ساقط می‌شود
                                            </p>
                                        </template>

                                        <div class="notification is-danger" role="alert" x-show="panelError"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="panelError"></span></span></div>
                                        <div class="notification is-success" role="status" x-show="panelNotice" x-text="panelNotice"></div>

                                        <h4 class="title is-6">پرداخت‌ها</h4>
                                        <p x-show="paymentsLoading">در حال بارگذاری...</p>
                                        <ul aria-label="پرداخت‌ها" x-show="!paymentsLoading && payments.length > 0">
                                            <template x-for="payment in payments" :key="payment.id">
                                                <li class="py-3" data-payment-row>
                                                    <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap">
                                                        <strong x-text="Money.formatAmount(payment.amount)"></strong>
                                                        <span class="tag is-light" x-text="methodLabel(payment.method)"></span>
                                                    </div>
                                                    <div class="is-flex is-justify-content-space-between is-flex-wrap-wrap mt-1">
                                                        <span x-text="payment.note || '—'"></span>
                                                        <span dir="ltr" x-text="Calendar.formatDate(payment.paidAt)"></span>
                                                    </div>
                                                </li>
                                            </template>
                                        </ul>
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

                                        <%-- ------------------------------------------------ تخفیف --%>
                                        <hr>
                                        <h4 class="title is-6">تخفیف</h4>
                                        <form @submit.prevent="applyDiscount(invoice)" x-show="remaining(invoice) > 0">
                                            <div class="field is-grouped is-grouped-multiline">
                                                <div class="control">
                                                    <label class="label" :for="'discountAmount-' + invoice.id">مبلغ تخفیف</label>
                                                    <input class="input" dir="ltr" :id="'discountAmount-' + invoice.id"
                                                           type="text" inputmode="numeric" pattern="[0-9]+"
                                                           x-model="discountForm.amount"
                                                           @input="discountForm.amount = Money.normalizeAmountInput($event)" required>
                                                    <p class="help" x-show="Money.tomanWords(discountForm.amount)" x-text="Money.tomanWords(discountForm.amount)"></p>
                                                </div>
                                                <div class="control is-expanded">
                                                    <label class="label" :for="'discountReason-' + invoice.id">دلیل (اختیاری)</label>
                                                    <input class="input" :id="'discountReason-' + invoice.id" type="text"
                                                           maxlength="255" x-model="discountForm.reason">
                                                </div>
                                                <div class="control app-control-align-end">
                                                    <button class="button is-link" type="submit"
                                                            :disabled="discounting || !discountDeadlineValid()" :class="{ 'is-loading': discounting }">
                                                        ثبت تخفیف
                                                    </button>
                                                </div>
                                            </div>
                                            <div class="field">
                                                <label class="checkbox" :for="'discountConditional-' + invoice.id">
                                                    <input type="checkbox" :id="'discountConditional-' + invoice.id"
                                                           x-model="discountForm.conditional">
                                                    تخفیف مشروط
                                                </label>
                                            </div>
                                            <div class="field" x-show="discountForm.conditional">
                                                <label class="label" :for="'discountDeadline-' + invoice.id">در صورت تسویه‌ی کامل تا تاریخ</label>
                                                <div class="control">
                                                    <input class="input" dir="ltr" :id="'discountDeadline-' + invoice.id" type="text"
                                                           inputmode="numeric" autocomplete="off" :placeholder="exampleDate()"
                                                           x-model="discountForm.deadline" :required="discountForm.conditional">
                                                </div>
                                                <p class="help">اگر تا پایان این روز کل مبلغ قابل پرداخت تسویه نشود، تخفیف خودکار لغو می‌شود.</p>
                                            </div>
                                            <p class="help">تخفیف از مانده کم می‌شود و بیشتر از مانده‌ی فاکتور نمی‌شود. تخفیف قابل حذف نیست.</p>
                                        </form>
                                        <p class="help" x-show="remaining(invoice) <= 0">این فاکتور تسویه شده است؛ تخفیف تازه ممکن نیست.</p>

                                        <p class="mt-3" x-show="discountsLoading">در حال بارگذاری...</p>
                                        <ul aria-label="تخفیف‌ها" x-show="!discountsLoading && discounts.length > 0">
                                            <template x-for="discount in discounts" :key="discount.id">
                                                <li class="py-3" data-discount-row :class="{ 'has-text-grey': discount.status === 'LAPSED' }">
                                                    <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap">
                                                        <strong>
                                                            <s x-show="discount.status === 'LAPSED'" x-text="Money.formatAmount(discount.amount)"></s>
                                                            <span x-show="discount.status !== 'LAPSED'" x-text="Money.formatAmount(discount.amount)"></span>
                                                        </strong>
                                                        <span class="tag" :class="discountStatusClass(discount)"
                                                              x-text="discountStatusLabel(discount)"></span>
                                                    </div>
                                                    <div class="is-flex is-justify-content-space-between is-flex-wrap-wrap mt-1">
                                                        <span x-text="discount.reason || '—'"></span>
                                                        <span dir="ltr" x-text="Calendar.formatDate(discount.createdAt)"></span>
                                                    </div>
                                                </li>
                                            </template>
                                        </ul>
                                        <p class="help" x-show="!discountsLoading && discounts.some(d => d.status === 'LAPSED')">
                                            تخفیف‌های «ساقط‌شده» در مبلغ قابل پرداخت حساب نمی‌شوند.
                                        </p>
                                        <p class="mt-3" x-show="!discountsLoading && discounts.length === 0">هنوز تخفیفی برای این فاکتور ثبت نشده است.</p>

                                        <%-- ------------------------------------------------ اقساط --%>
                                        <hr>
                                        <h4 class="title is-6">اقساط</h4>
                                        <p x-show="installmentsLoading">در حال بارگذاری...</p>

                                        <p class="has-text-grey" id="installmentsOffHint"
                                           x-show="!installmentsLoading && !installmentsEnabled && installments.length === 0">
                                            تقسیط شهریه برای این مدرسه خاموش است؛ گزینه‌ی «اقساط شهریه» را در تنظیمات مدرسه فعال کنید.
                                        </p>

                                        <template x-if="!installmentsLoading && installments.length > 0">
                                            <div>
                                                <ul aria-label="اقساط">
                                                    <template x-for="item in installments" :key="item.id">
                                                        <li class="py-3" data-installment-row>
                                                            <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap">
                                                                <span>
                                                                    <strong>قسط <span x-text="fa(item.seq)"></span></strong>
                                                                    <span class="tag is-light" x-show="!(editing && FinanceLogic.isEditable(item)) && FinanceLogic.isUndated(item)">بدون سررسید</span>
                                                                    <span dir="ltr" x-show="!(editing && FinanceLogic.isEditable(item)) && !FinanceLogic.isUndated(item)"
                                                                          x-text="FinanceLogic.isUndated(item) ? '' : Calendar.formatDate(item.dueDate)"></span>
                                                                </span>
                                                                <span class="tag" :class="statusClass(item.status)"
                                                                      x-text="statusLabel(item.status)"></span>
                                                            </div>
                                                            <template x-if="editing && FinanceLogic.isEditable(item) && editRow(item)">
                                                                <div class="mt-2">
                                                                    <div class="field">
                                                                        <label class="label" :for="'installmentAmount-' + item.id"
                                                                               x-text="'مبلغ قسط ' + fa(item.seq)"></label>
                                                                        <div class="control">
                                                                            <input class="input" dir="ltr" :id="'installmentAmount-' + item.id"
                                                                                   type="text" inputmode="numeric" pattern="[0-9]+"
                                                                                   x-model="editRow(item).amount"
                                                                                   @input="editRow(item).amount = Money.normalizeAmountInput($event)">
                                                                        </div>
                                                                        <p class="help" x-show="Money.tomanWords(editRow(item).amount)"
                                                                           x-text="Money.tomanWords(editRow(item).amount)"></p>
                                                                    </div>
                                                                    <div class="field">
                                                                        <label class="label" :for="'installmentDue-' + item.id"
                                                                               x-text="'سررسید قسط ' + fa(item.seq)"></label>
                                                                        <div class="control">
                                                                            <input class="input" dir="ltr" :id="'installmentDue-' + item.id"
                                                                                   type="text" inputmode="numeric" autocomplete="off"
                                                                                   :placeholder="editRow(item).undated ? 'اختیاری' : exampleDate()"
                                                                                   :aria-describedby="editRow(item).undated ? 'installmentDueHelp-' + item.id : null"
                                                                                   :class="{ 'is-danger': editRow(item).dateError }"
                                                                                   x-model="editRow(item).dueDate">
                                                                        </div>
                                                                        <p class="help" :id="'installmentDueHelp-' + item.id"
                                                                           x-show="editRow(item).undated">اختیاری — تاریخ را بعداً هم می‌شود تعیین کرد</p>
                                                                        <p class="help is-danger" x-show="editRow(item).dateError"
                                                                           x-text="editRow(item).dateError"></p>
                                                                    </div>
                                                                </div>
                                                            </template>
                                                            <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap mt-1"
                                                                 x-show="!(editing && FinanceLogic.isEditable(item))">
                                                                <span x-text="Money.formatAmount(item.amount)"></span>
                                                                <span class="has-text-grey">پرداخت‌شده
                                                                    <span x-text="Money.formatAmount(item.amountPaid)"></span></span>
                                                            </div>
                                                        </li>
                                                    </template>
                                                </ul>

                                                <p class="has-text-grey" x-show="!installmentsEnabled">
                                                    اقساط تعریف‌شده فقط‌خواندنی است؛ برای تغییر، «اقساط شهریه» را در تنظیمات مدرسه فعال کنید.
                                                </p>

                                                <button class="button is-light" type="button" x-ref="editInstallmentsButton"
                                                        x-show="installmentsEnabled && !editing && installments.some(i => FinanceLogic.isEditable(i))"
                                                        @click="startEditing()">
                                                    ویرایش مبالغ و تاریخ‌ها
                                                </button>

                                                <div x-show="editing">
                                                    <p role="status" :class="FinanceLogic.sumMatches(editRows, expectedSum(invoice)) ? 'has-text-success' : 'has-text-danger'">
                                                        جمع اقساط: <strong x-text="Money.formatAmount(editedSum())"></strong>
                                                        - باید برابر باشد با <strong x-text="Money.formatAmount(expectedSum(invoice))"></strong>
                                                        <span x-text="FinanceLogic.sumMatches(editRows, expectedSum(invoice)) ? '(برابر است)' : '(برابر نیست)'"></span>
                                                    </p>
                                                    <p class="help">اقساط پرداخت‌شده قابل ویرایش نیست و در جمع نمی‌آید.</p>
                                                    <div class="buttons mt-3">
                                                        <button class="button is-link" type="button" @click="saveEditing(invoice)"
                                                                :disabled="planSaving || !canSaveEdit(invoice)"
                                                                :class="{ 'is-loading': planSaving }">
                                                            ذخیره‌ی اقساط
                                                        </button>
                                                        <button class="button" type="button" @click="cancelEditing()">انصراف</button>
                                                    </div>
                                                </div>
                                            </div>
                                        </template>

                                        <%-- تعریف اقساط / تغییر تعداد: یک فرم؛ فقط اقساطِ پرداخت‌نشده جایگزین می‌شوند --%>
                                        <form class="mt-4" @submit.prevent="generateInstallments(invoice)"
                                              x-show="installmentsEnabled && !editing && !installmentsLoading && remaining(invoice) > 0">
                                            <h5 class="label" x-text="installments.length > 0 ? 'تغییر تعداد اقساط' : 'تعریف اقساط'"></h5>
                                            <p class="help mb-3" x-show="installments.length > 0">
                                                فقط اقساط پرداخت‌نشده جایگزین می‌شوند؛ اقساط پرداخت‌شده می‌مانند و مانده به تعداد جدید تقسیم می‌شود.
                                            </p>
                                            <div class="field is-grouped is-grouped-multiline">
                                                <div class="control">
                                                    <label class="label" :for="'planCount-' + invoice.id">تعداد اقساط</label>
                                                    <input class="input" dir="ltr" :id="'planCount-' + invoice.id" type="text"
                                                           inputmode="numeric" pattern="[0-9]+" x-model="planForm.count"
                                                           @input="planForm.count = digitsOnly($event)" required>
                                                </div>
                                                <div class="control">
                                                    <label class="label" :for="'planFirstDue-' + invoice.id">سررسید قسط اول</label>
                                                    <input class="input" dir="ltr" :id="'planFirstDue-' + invoice.id" type="text"
                                                           inputmode="numeric" autocomplete="off" :placeholder="exampleDate()"
                                                           x-model="planForm.firstDue">
                                                </div>
                                                <div class="control">
                                                    <label class="label" :for="'planInterval-' + invoice.id">فاصله (ماه)</label>
                                                    <input class="input" dir="ltr" :id="'planInterval-' + invoice.id" type="text"
                                                           inputmode="numeric" pattern="[0-9]+" x-model="planForm.interval"
                                                           @input="planForm.interval = digitsOnly($event)" required>
                                                </div>
                                                <div class="control app-control-align-end">
                                                    <button class="button is-link" type="submit"
                                                            :disabled="planSaving" :class="{ 'is-loading': planSaving }"
                                                            x-text="installments.length > 0 ? 'تغییر تعداد' : 'تعریف اقساط'">
                                                    </button>
                                                </div>
                                            </div>
                                            <p class="help is-danger" role="alert" x-show="planForm.error" x-text="planForm.error"></p>
                                            <p class="help" x-show="!planForm.error">تاریخ را به شکل سال/ماه/روز بنویسید؛ ارقام فارسی هم پذیرفته می‌شود. مبلغ هر قسط به‌طور مساوی تقسیم می‌شود و بعداً قابل ویرایش است.</p>
                                        </form>
                                                        </div>
                                                    </template>
                                                </div>
                                            </template>
                                        </div>
                                    </template>
                                </div>
                            </template>
                        </div>

                        <button class="button is-light mt-3" type="button" x-show="hasMoreStudents()" @click="loadMoreStudents()"
                                :class="{ 'is-loading': loadingMore }"
                                x-text="'نمایش بیشتر (' + moreStudentsLabel() + ' مورد دیگر)'"></button>

                        <%-- حالت‌های خالی: بدون فیلتر = مدرسه هنوز فاکتوری ندارد؛ با فیلتر = نتیجه‌ای نیست و راه برگشت می‌دهیم --%>
                        @template.layout.empty-state(
                            show = "listLoaded && !listLoading && students.length === 0 && !hasFilters() && !error",
                            art = "money",
                            message = @`هنوز فاکتوری برای این مدرسه صادر نشده است. ابتدا شهریه‌ی سال جاری را بالا ثبت کنید، سپس «صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور» را بزنید.`)
                        <div x-show="listLoaded && !listLoading && students.length === 0 && hasFilters() && !error">
                            @template.layout.empty-state(
                                show = "true",
                                art = "list",
                                message = @`دانش‌آموزی با این جست‌وجو و فیلتر پیدا نشد.`)
                            <button class="button is-light" type="button" id="financeClearFilters" @click="clearFilters()">پاک‌کردن جست‌وجو و فیلترها</button>
                        </div>
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
                                برای همه‌ی دانش‌آموزانِ بدون فاکتور در سال جاری فاکتور صادر شود؟
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
            `)
    </div>
</section>
`)
