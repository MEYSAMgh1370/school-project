@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "فاکتورهای من", scripts = java.util.List.of("/js/navbar.js", "/js/my-invoices.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="myInvoicesPage()">
    <div class="container">
        <h1 class="title is-4">فاکتورهای من</h1>
        <p class="subtitle is-6">فاکتورهای شهریه‌ی خودتان و فرزندانِ لینک‌شده‌ به شما در این مدرسه.</p>

        <div class="notification is-warning" x-show="!schoolId">
            برای مشاهده‌ی فاکتورها، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>

                <p x-show="loading">در حال بارگذاری...</p>

                <table class="table is-fullwidth is-striped" x-show="!loading && invoices.length > 0">
                    <thead>
                    <tr>
                        <th>دانش‌آموز</th>
                        <th>مبلغ</th>
                        <th>پرداخت‌شده</th>
                        <th>مانده</th>
                        <th>وضعیت</th>
                        <th>تاریخ صدور</th>
                        <th>سررسید</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="invoice in invoices" :key="invoice.id">
                        <tr>
                            <td>دانش‌آموز #<span x-text="invoice.studentId"></span></td>
                            <td dir="ltr" x-text="invoice.amount"></td>
                            <td dir="ltr" x-text="invoice.amountPaid"></td>
                            <td dir="ltr" x-text="invoice.amount - invoice.amountPaid"></td>
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

                <div class="notification" x-show="!loading && !error && invoices.length === 0">
                    فاکتوری برای شما یا فرزندانتان در این مدرسه ثبت نشده است.
                </div>
            </div>
        </template>
    </div>
</section>
`)
