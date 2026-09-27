@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "فاکتورهای من", scripts = java.util.List.of("/js/navbar.js", "/js/money.js", "/js/my-invoices.js"), content = @`
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

                <div class="table-container" x-show="!loading && invoices.length > 0">
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
                    <template x-for="invoice in invoices" :key="invoice.id">
                        <tr>
                            <td>دانش‌آموز #<span x-text="invoice.studentId"></span></td>
                            <td x-text="Money.formatAmount(invoice.amount)"></td>
                            <td x-text="Money.formatAmount(invoice.amountPaid)"></td>
                            <td x-text="Money.formatAmount(invoice.amount - invoice.amountPaid)"></td>
                            <td>
                                <span class="tag" :class="statusClass(invoice.status)"
                                      x-text="statusLabel(invoice.status)"></span>
                            </td>
                            <td dir="ltr" x-text="new Date(invoice.issuedAt).toLocaleDateString('fa-IR')"></td>
                            <td dir="ltr" x-text="invoice.dueDate ? new Date(invoice.dueDate).toLocaleDateString('fa-IR') : '—'"></td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                </div>

                @template.layout.empty-state(
                    show = "!loading && !error && invoices.length === 0",
                    art = "money",
                    message = @`فاکتوری برای شما یا فرزندانتان در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
