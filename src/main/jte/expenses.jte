@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "هزینه‌های مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/money.js", "/js/expenses.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="expensesPage()">
    <div class="container">
        <h1 class="title is-4">هزینه‌های مدرسه</h1>

        <div class="notification is-warning" x-cloak x-show="!schoolId">
            برای مدیریت هزینه‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                <template x-if="!blocked">
                    <div>
                        <div class="columns">
                            <div class="column">
                                <div class="box">
                                    <h2 class="title is-6">دسته‌های هزینه</h2>
                                    <button class="button is-small mb-3" @click="seedDefaults" :class="{ 'is-loading': seeding }">
                                        ساخت دسته‌های پیش‌فرض
                                    </button>
                                    <div class="table-container">
                                    <table class="table is-fullwidth is-narrow">
                                        <tbody>
                                        <template x-for="category in categories" :key="category.id">
                                            <tr>
                                                <td x-text="category.name"></td>
                                                <td>
                                                    <button class="button is-small"
                                                            :class="category.active ? 'is-success' : 'is-light'"
                                                            @click="toggleCategoryActive(category)">
                                                        <span x-text="category.active ? 'فعال' : 'غیرفعال'"></span>
                                                    </button>
                                                </td>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                    </div>
                                    <form @submit.prevent="createCategory">
                                        <label class="label" for="expenseCategoryName">نام دسته‌ی تازه</label>
                                        <div class="field has-addons">
                                            <div class="control is-expanded">
                                                <input class="input" id="expenseCategoryName" type="text" placeholder="نام دسته‌ی تازه"
                                                       x-model="categoryForm.name" required>
                                            </div>
                                            <div class="control">
                                                <button class="button is-link" type="submit"
                                                        :class="{ 'is-loading': submitting }">افزودن</button>
                                            </div>
                                        </div>
                                    </form>
                                </div>
                            </div>

                            <div class="column">
                                <div class="box">
                                    <h2 class="title is-6">طرف‌حساب‌ها</h2>
                                    <div class="table-container">
                                    <table class="table is-fullwidth is-narrow">
                                        <tbody>
                                        <template x-for="vendor in vendors" :key="vendor.id">
                                            <tr>
                                                <td x-text="vendor.name"></td>
                                                <td>
                                                    <button class="button is-small"
                                                            :class="vendor.active ? 'is-success' : 'is-light'"
                                                            @click="toggleVendorActive(vendor)">
                                                        <span x-text="vendor.active ? 'فعال' : 'غیرفعال'"></span>
                                                    </button>
                                                </td>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                    </div>
                                    <form @submit.prevent="createVendor">
                                        <label class="label" for="expenseVendorName">نام طرف حساب تازه</label>
                                        <div class="field has-addons">
                                            <div class="control is-expanded">
                                                <input class="input" id="expenseVendorName" type="text" placeholder="نام طرف حساب تازه"
                                                       x-model="vendorForm.name" required>
                                            </div>
                                            <div class="control">
                                                <button class="button is-link" type="submit"
                                                        :class="{ 'is-loading': submitting }">افزودن</button>
                                            </div>
                                        </div>
                                    </form>
                                </div>
                            </div>
                        </div>

                        <div class="box">
                            <h2 class="title is-6">ثبت هزینه‌ی تازه</h2>
                            <form @submit.prevent="createExpense">
                                <div class="field is-grouped is-grouped-multiline">
                                    <div class="control">
                                        <label class="label" for="expenseDate">تاریخ</label>
                                        <input class="input" id="expenseDate" dir="ltr" type="date" x-model="expenseForm.expenseDate" required>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="expenseAmount">مبلغ</label>
                                        <input class="input" id="expenseAmount" dir="ltr" type="text" inputmode="numeric"
                                               pattern="[0-9]+" x-model="expenseForm.amount"
                                               @input="expenseForm.amount = Money.normalizeAmountInput($event)" required>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="expenseCategory">دسته</label>
                                        <div class="select">
                                            <select id="expenseCategory" x-model="expenseForm.categoryId" required>
                                                <option value="">— انتخاب کنید —</option>
                                                <template x-for="category in categories.filter(c => c.active)" :key="category.id">
                                                    <option :value="category.id" x-text="category.name"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="expenseVendor">طرف حساب (اختیاری)</label>
                                        <div class="select">
                                            <select id="expenseVendor" x-model="expenseForm.vendorId">
                                                <option value="">—</option>
                                                <template x-for="vendor in vendors.filter(v => v.active)" :key="vendor.id">
                                                    <option :value="vendor.id" x-text="vendor.name"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="expensePaymentMethod">روش پرداخت</label>
                                        <div class="select">
                                            <select id="expensePaymentMethod" x-model="expenseForm.paymentMethod">
                                                <option value="CASH">نقدی</option>
                                                <option value="CARD">کارت</option>
                                                <option value="BANK_TRANSFER">انتقال بانکی</option>
                                                <option value="CHEQUE">چک</option>
                                                <option value="OTHER">سایر</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control is-expanded">
                                        <label class="label" for="expenseDescription">شرح</label>
                                        <input class="input" id="expenseDescription" type="text" x-model="expenseForm.description">
                                    </div>
                                    <div class="control app-control-align-end">
                                        <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                            ثبت هزینه
                                        </button>
                                    </div>
                                </div>
                            </form>
                        </div>

                        <div class="box">
                            <h2 class="title is-6">فیلتر</h2>
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="expenseFilterCategory">دسته</label>
                                    <div class="select">
                                        <select id="expenseFilterCategory" x-model="filters.categoryId" @change="load">
                                            <option value="">همه‌ی دسته‌ها</option>
                                            <template x-for="category in categories" :key="category.id">
                                                <option :value="category.id" x-text="category.name"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control">
                                    <label class="label" for="expenseFilterStatus">وضعیت</label>
                                    <div class="select">
                                        <select id="expenseFilterStatus" x-model="filters.status" @change="load">
                                            <option value="">همه‌ی وضعیت‌ها</option>
                                            <option value="DRAFT">پیش‌نویس</option>
                                            <option value="APPROVED">تأییدشده</option>
                                            <option value="PAID">پرداخت‌شده</option>
                                            <option value="CANCELLED">لغوشده</option>
                                        </select>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <h2 class="title is-6">فهرست هزینه‌ها</h2>
                        <div class="table-container">
                        <table class="table is-fullwidth is-striped">
                            <thead>
                            <tr>
                                <th scope="col">تاریخ</th>
                                <th scope="col">دسته</th>
                                <th scope="col">طرف حساب</th>
                                <th scope="col">مبلغ</th>
                                <th scope="col">وضعیت</th>
                                <th scope="col"><span class="is-sr-only">عملیات</span></th>
                            </tr>
                            </thead>
                            <tbody>
                            <template x-for="expense in expenses" :key="expense.id">
                                <tr>
                                    <td dir="ltr" x-text="expense.expenseDate"></td>
                                    <td x-text="categoryName(expense.categoryId)"></td>
                                    <td x-text="vendorName(expense.vendorId)"></td>
                                    <td x-text="Money.formatAmount(expense.amount)"></td>
                                    <td>
                                        <span class="tag" :class="statusClass(expense.status)" x-text="statusLabel(expense.status)"></span>
                                    </td>
                                    <td>
                                        <div class="buttons are-small">
                                            <button class="button" x-show="expense.status === 'DRAFT'"
                                                    @click="approveExpense(expense)">تأیید</button>
                                            <button class="button" x-show="expense.status === 'APPROVED'"
                                                    @click="payExpense(expense)">پرداخت</button>
                                            <button class="button is-danger is-light"
                                                    x-show="expense.status === 'DRAFT' || expense.status === 'APPROVED'"
                                                    @click="cancelExpense(expense)">لغو</button>
                                            <button class="button" :class="{ 'is-link': expandedExpenseId === expense.id }"
                                                    @click="toggleAttachments(expense)">پیوست‌ها</button>
                                        </div>
                                    </td>
                                </tr>
                            </template>
                            </tbody>
                        </table>
                        </div>
                        <div class="notification" x-show="!loading && expenses.length === 0">
                            هیچ هزینه‌ای با این فیلترها ثبت نشده است.
                        </div>

                        <template x-for="expense in expenses" :key="'attachments-' + expense.id">
                            <div class="box" x-show="expandedExpenseId === expense.id" x-cloak>
                                <h3 class="title is-6">پیوست‌های هزینه (<span x-text="categoryName(expense.categoryId)"></span>)</h3>
                                <p x-show="attachmentsLoading">در حال بارگذاری...</p>
                                <div class="table-container" x-show="!attachmentsLoading && attachments.length > 0">
                                <table class="table is-fullwidth">
                                    <tbody>
                                    <template x-for="attachment in attachments" :key="attachment.id">
                                        <tr>
                                            <td x-text="attachment.fileName"></td>
                                            <td dir="ltr" x-text="Math.round(attachment.sizeBytes / 1024) + ' KB'"></td>
                                            <td>
                                                <div class="buttons are-small">
                                                    <button class="button" @click="downloadAttachment(expense, attachment)">دانلود</button>
                                                    <button class="button is-danger is-light"
                                                            @click="deleteAttachment(expense, attachment)">حذف</button>
                                                </div>
                                            </td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                                </div>
                                <p x-show="!attachmentsLoading && attachments.length === 0">هنوز پیوستی ثبت نشده است.</p>
                                <div class="field" x-show="expense.status !== 'CANCELLED'">
                                    <label class="label" :for="'expenseAttachment-' + expense.id">افزودن پیوست (رسید/فاکتور - حداکثر ۵ مگابایت)</label>
                                    <input class="input" :id="'expenseAttachment-' + expense.id" type="file" @change="uploadAttachment(expense, $event)">
                                </div>
                            </div>
                        </template>

                        <div class="box">
                            <h2 class="title is-6">گزارش هزینه‌ها (بازه‌ی جلالی)</h2>
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="expenseSummaryYear">سال</label>
                                    <input class="input app-input-w-6" id="expenseSummaryYear" dir="ltr" type="number" x-model="summaryYear">
                                </div>
                                <div class="control">
                                    <label class="label" for="expenseSummaryPeriodKind">بازه</label>
                                    <div class="select">
                                        <select id="expenseSummaryPeriodKind" x-model="summaryPeriodKind">
                                            <option value="month">ماه</option>
                                            <option value="quarter">فصل</option>
                                            <option value="year">کل سال</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="control" x-show="summaryPeriodKind !== 'year'">
                                    <label class="label" for="expenseSummaryPeriodIndex" x-text="summaryPeriodKind === 'month' ? 'شماره‌ی ماه (۱ تا ۱۲)' : 'شماره‌ی فصل (۱ تا ۴)'"></label>
                                    <input class="input app-input-w-6" id="expenseSummaryPeriodIndex" dir="ltr" type="number" min="1"
                                           :max="summaryPeriodKind === 'month' ? 12 : 4"
                                           x-model="summaryPeriodIndex">
                                </div>
                                <div class="control">
                                    <label class="label" for="expenseSummaryGroupBy">گروه‌بندی</label>
                                    <div class="select">
                                        <select id="expenseSummaryGroupBy" x-model="summaryGroupBy">
                                            <option value="category">دسته</option>
                                            <option value="vendor">طرف حساب</option>
                                            <option value="paymentMethod">روش پرداخت</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-link" @click="loadSummary" :class="{ 'is-loading': summaryLoading }">
                                        نمایش گزارش
                                    </button>
                                </div>
                            </div>

                            <div class="table-container" x-show="summaryRows.length > 0">
                            <table class="table is-fullwidth">
                                <thead>
                                <tr><th scope="col">مورد</th><th scope="col">مبلغ</th><th scope="col">تعداد</th></tr>
                                </thead>
                                <tbody>
                                <template x-for="(row, index) in summaryRows" :key="index">
                                    <tr>
                                        <td x-text="summaryRowLabel(row)"></td>
                                        <td x-text="Money.formatAmount(row.total)"></td>
                                        <td dir="ltr" x-text="row.count"></td>
                                    </tr>
                                </template>
                                </tbody>
                            </table>
                            </div>

                            <div class="notification is-info" x-show="financeSummaryResult" x-cloak>
                                درآمد بازه: <strong x-text="Money.formatAmount(financeSummaryResult ? financeSummaryResult.income : null)"></strong> -
                                هزینه‌ی بازه: <strong x-text="Money.formatAmount(financeSummaryResult ? financeSummaryResult.expense : null)"></strong>
                            </div>
                        </div>
                    </div>
                </template>
            </div>
        </template>
    </div>
</section>
`)
