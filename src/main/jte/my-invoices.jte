@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "فاکتورهای من", scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/js/money.js", "/js/my-invoices.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="myInvoicesPage()">
    <div class="container">
        @template.layout.page-header(
            title = "فاکتورهای من",
            subtitle = "فاکتورهای شهریه‌ی خودتان و فرزندانِ لینک‌شده‌ به شما در این مدرسه.")

        @template.layout.school-scope(
            noSchoolMessage = "برای مشاهده‌ی فاکتورها، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <%-- فیلتر یک فرزند (از لینک پیشخوان): همیشه دیده می‌شود و راه برگشت دارد. --%>
                <div class="notification is-light" x-cloak x-show="studentFilter !== null">
                    فقط فاکتورهای دانش‌آموز #<span x-text="Calendar.digits(studentFilter)"></span> نمایش داده می‌شود.
                    <a href="/my-invoices">نمایش همه</a>
                </div>

                <%-- یک کارت برای هر فاکتور: بدون اسکرولِ افقی در هر عرضی (موبایلِ والدین) --%>
                <div x-show="!loading && visibleInvoices().length > 0">
                    <template x-for="invoice in visibleInvoices()" :key="invoice.id">
                        <article class="box" data-invoice-card>
                            <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap mb-3">
                                <p class="has-text-weight-semibold">
                                    دانش‌آموز #<span x-text="Calendar.digits(invoice.studentId)"></span>
                                    <%-- سالِ فاکتور: فاکتورِ مانده‌دارِ سال‌های قبل هم در این فهرست می‌آید --%>
                                    <span class="tag is-light" dir="ltr" x-show="invoice.academicYear"
                                          x-text="invoice.academicYear ? Calendar.digits(invoice.academicYear) : ''"></span>
                                </p>
                                <span class="tag" :class="statusClass(invoice.status)"
                                      x-text="statusLabel(invoice.status)"></span>
                            </div>

                            <dl class="columns is-mobile is-multiline is-variable is-1 mb-0">
                                <div class="column is-half">
                                    <dt class="has-text-grey">مبلغ</dt>
                                    <dd x-text="Money.formatAmount(invoice.amount)"></dd>
                                </div>
                                <div class="column is-half" x-cloak x-show="discount(invoice) > 0">
                                    <dt class="has-text-grey">تخفیف</dt>
                                    <dd x-text="Money.formatAmount(discount(invoice))"></dd>
                                </div>
                                <div class="column is-half">
                                    <dt class="has-text-grey">قابل پرداخت</dt>
                                    <dd x-text="Money.formatAmount(payable(invoice))"></dd>
                                </div>
                                <div class="column is-half">
                                    <dt class="has-text-grey">پرداخت‌شده</dt>
                                    <dd x-text="Money.formatAmount(invoice.amountPaid)"></dd>
                                </div>
                                <div class="column is-half">
                                    <dt class="has-text-grey">مانده</dt>
                                    <dd class="has-text-weight-semibold" x-text="Money.formatAmount(remaining(invoice))"></dd>
                                </div>
                            </dl>

                            <p>
                                <span class="has-text-grey">تاریخ صدور:</span>
                                <span dir="ltr" x-text="Calendar.formatDate(invoice.issuedAt)"></span>
                            </p>
                            <p>
                                <span class="has-text-grey">سررسید:</span>
                                <span x-show="!invoice.dueDate">بدون سررسید</span>
                                <span dir="ltr" x-show="invoice.dueDate"
                                      x-text="invoice.dueDate ? Calendar.formatDate(invoice.dueDate) : ''"></span>
                            </p>

                            <div class="notification is-warning is-light mt-3 mb-0" x-cloak x-show="hasPendingDiscount(invoice)">
                                <span class="tag is-warning">تخفیف مشروط</span>
                                <span class="tag is-warning is-light" x-text="Money.formatAmount(Number(invoice.pendingDiscount)) + ' ریال'"></span>
                                — در صورت تسویه‌ی کامل تا
                                <strong x-text="invoice.pendingDiscountDeadline ? Calendar.formatDate(invoice.pendingDiscountDeadline) : ''"></strong>
                                این تخفیف باقی می‌ماند؛ در غیر این صورت مبلغ قابل پرداخت
                                <strong x-text="Money.formatAmount(Number(invoice.pendingDiscount)) + ' ریال'"></strong>
                                بیشتر می‌شود.
                            </div>

                            <button type="button" class="button is-small is-light mt-3" x-cloak
                                    x-show="installmentsOf(invoice).length > 0"
                                    @click="toggleInstallments(invoice)"
                                    :aria-expanded="isExpanded(invoice) ? 'true' : 'false'"
                                    :aria-controls="'installments-' + invoice.id">
                                اقساط (<span x-text="Calendar.digits(installmentsOf(invoice).length)"></span>)
                            </button>

                            <ul class="mt-3" :id="'installments-' + invoice.id" x-cloak
                                x-show="installmentsOf(invoice).length > 0 && isExpanded(invoice)"
                                aria-label="اقساط">
                                <template x-for="item in installmentsOf(invoice)" :key="item.id">
                                    <li class="py-2">
                                        <div class="is-flex is-justify-content-space-between is-flex-wrap-wrap">
                                            <span>
                                                <strong>قسط <span x-text="Calendar.digits(item.seq)"></span></strong>
                                                <span class="tag is-light" x-show="isUndated(item)">بدون سررسید</span>
                                                <span dir="ltr" x-show="!isUndated(item)" x-text="isUndated(item) ? '' : Calendar.formatDate(item.dueDate)"></span>
                                                <span class="tag is-link is-light" x-show="item.id === nextInstallmentId(invoice)">قسط بعدی</span>
                                            </span>
                                            <span x-text="Money.formatAmount(item.amount)"></span>
                                        </div>
                                        <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap">
                                            <span class="has-text-grey">پرداخت‌شده
                                                <span x-text="Money.formatAmount(item.amountPaid)"></span></span>
                                            <span class="tag" :class="statusClass(item.status)"
                                                  x-text="statusLabel(item.status)"></span>
                                        </div>
                                    </li>
                                </template>
                            </ul>
                        </article>
                    </template>
                </div>

                @template.layout.empty-state(
                    show = "!loading && !error && visibleInvoices().length === 0",
                    art = "money",
                    message = @`فاکتوری برای شما یا فرزندانتان در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
