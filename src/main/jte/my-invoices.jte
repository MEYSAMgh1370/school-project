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

                <div class="table-container" x-show="!loading && visibleInvoices().length > 0">
                <table class="table is-fullwidth is-striped">
                    <thead>
                    <tr>
                        <th scope="col">دانش‌آموز</th>
                        <th scope="col">مبلغ</th>
                        <th scope="col">پرداخت‌شده</th>
                        <th scope="col">مانده</th>
                        <th scope="col">وضعیت</th>
                        <th scope="col">تاریخ صدور</th>
                        <th scope="col">سررسید</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="invoice in visibleInvoices()" :key="invoice.id">
                        <tr>
                            <td>
                                دانش‌آموز #<span x-text="Calendar.digits(invoice.studentId)"></span>
                                <%-- سالِ فاکتور: فاکتورِ مانده‌دارِ سال‌های قبل هم در این فهرست می‌آید --%>
                                <span class="tag is-light" dir="ltr" x-show="invoice.academicYear"
                                      x-text="invoice.academicYear ? Calendar.digits(invoice.academicYear) : ''"></span>
                            </td>
                            <td x-text="Money.formatAmount(invoice.amount)"></td>
                            <td x-text="Money.formatAmount(invoice.amountPaid)"></td>
                            <td x-text="Money.formatAmount(invoice.amount - invoice.amountPaid)"></td>
                            <td>
                                <span class="tag" :class="statusClass(invoice.status)"
                                      x-text="statusLabel(invoice.status)"></span>
                            </td>
                            <td dir="ltr" x-text="Calendar.formatDate(invoice.issuedAt)"></td>
                            <td dir="ltr" x-text="invoice.dueDate ? Calendar.formatDate(invoice.dueDate) : '—'"></td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                </div>

                @template.layout.empty-state(
                    show = "!loading && !error && visibleInvoices().length === 0",
                    art = "money",
                    message = @`فاکتوری برای شما یا فرزندانتان در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
