@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "امور مالی مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/member-names.js", "/js/money.js", "/js/finance.js"), content = @`
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
                            <h2 class="title is-6">شهریه‌ی مصوب</h2>
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
                                <button class="button is-small" @click="backfill" :class="{ 'is-loading': backfilling }">
                                    صدور فاکتور برای دانش‌آموزان بدون فاکتور
                                </button>
                            </div>
                        </div>
                        <div class="table-container">
                        <table class="table is-fullwidth is-striped">
                            <thead>
                            <tr>
                                <th scope="col">دانش‌آموز</th>
                                <th scope="col">مبلغ</th>
                                <th scope="col">پرداخت‌شده</th>
                                <th scope="col">وضعیت</th>
                                <th scope="col">تاریخ صدور</th>
                                <th scope="col">سررسید</th>
                                <th scope="col"><span class="is-sr-only">عملیات</span></th>
                            </tr>
                            </thead>
                            <tbody>
                            <template x-for="invoice in invoices" :key="invoice.id">
                                <tr>
                                    <td x-text="studentName(invoice.studentId)"></td>
                                    <td x-text="Money.formatAmount(invoice.amount)"></td>
                                    <td x-text="Money.formatAmount(invoice.amountPaid)"></td>
                                    <td>
                                        <span class="tag" :class="statusClass(invoice.status)"
                                              x-text="statusLabel(invoice.status)"></span>
                                    </td>
                                    <td dir="ltr" x-text="new Date(invoice.issuedAt).toLocaleDateString('fa-IR')"></td>
                                    <td dir="ltr" x-text="invoice.dueDate ? new Date(invoice.dueDate).toLocaleDateString('fa-IR') : '—'"></td>
                                    <td>
                                        <button class="button is-small"
                                                :class="{ 'is-link': expandedInvoiceId === invoice.id }"
                                                @click="togglePayments(invoice)">
                                            پرداخت‌ها
                                        </button>
                                    </td>
                                </tr>
                            </template>
                            </tbody>
                        </table>
                        </div>

                        <template x-for="invoice in invoices" :key="'payments-' + invoice.id">
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
                                            <td dir="ltr" x-text="new Date(payment.paidAt).toLocaleDateString('fa-IR')"></td>
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
                                                    :class="{ 'is-loading': submitting }">
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
            `)
    </div>
</section>
`)
