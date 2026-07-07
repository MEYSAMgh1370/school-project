@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "امور مالی مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/finance.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="financePage()">
    <div class="container">
        <h1 class="title is-4">امور مالی مدرسه</h1>

        <div class="notification is-warning" x-show="!schoolId">
            برای مدیریت امور مالی، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                <template x-if="!blocked">
                    <div>
                        <div class="box">
                            <h2 class="title is-6">شهریه‌ی مصوب</h2>
                            <p class="mb-3" x-show="tuitionFee !== null">
                                شهریه‌ی فعلی: <strong dir="ltr" x-text="tuitionFee"></strong> تومان
                            </p>
                            <p class="mb-3" x-show="tuitionFee === null">
                                هنوز شهریه‌ای برای این مدرسه تنظیم نشده است.
                            </p>
                            <form @submit.prevent="setTuitionFee">
                                <div class="field is-grouped">
                                    <div class="control is-expanded">
                                        <label class="label" for="tuitionAmount">مبلغ شهریه‌ی جدید (تومان)</label>
                                        <input class="input" dir="ltr" id="tuitionAmount" type="number" min="1" step="1" x-model="form.amount" required>
                                    </div>
                                    <div class="control" style="align-self: flex-end">
                                        <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                            ثبت شهریه
                                        </button>
                                    </div>
                                </div>
                            </form>
                        </div>

                        <h2 class="title is-6">فاکتورها</h2>
                        <table class="table is-fullwidth is-striped">
                            <thead>
                            <tr>
                                <th>دانش‌آموز</th>
                                <th>مبلغ</th>
                                <th>تاریخ صدور</th>
                            </tr>
                            </thead>
                            <tbody>
                            <template x-for="invoice in invoices" :key="invoice.id">
                                <tr>
                                    <td x-text="studentName(invoice.studentId)"></td>
                                    <td dir="ltr" x-text="invoice.amount"></td>
                                    <td dir="ltr" x-text="new Date(invoice.issuedAt).toLocaleDateString('fa-IR')"></td>
                                </tr>
                            </template>
                            </tbody>
                        </table>

                        <div class="notification" x-show="!loading && invoices.length === 0">
                            هنوز فاکتوری برای این مدرسه صادر نشده است.
                        </div>
                    </div>
                </template>
            </div>
        </template>
    </div>
</section>
`)
