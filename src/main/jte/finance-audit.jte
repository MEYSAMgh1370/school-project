@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- سابقه‌ی تغییرات مالی: فقط‌خواندنی. هدف: مدیر مدرسه یا مدیر مالی هر تغییرِ پول را می‌بیند - چه کسی، کی، روی کدام
     فاکتور و دانش‌آموز، و چه چیزی عوض شد (قبل ← بعد). API: GET /api/admin/schools/{id}/finance/audit
     (ماژول FINANCE + مجوز FINANCE_MANAGE). هیچ کنترلی با مجوز پنهان نمی‌شود؛ 403 به ناحیه‌ی پیامِ مشترک می‌رود. --%>
@template.layout.main(title = "سابقه‌ی تغییرات مالی", scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/js/money.js", "/js/date-input.js", "/js/finance-audit-logic.js", "/js/finance-audit.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="financeAuditPage()">
    <div class="container">
        @template.layout.page-header(
            title = "سابقه‌ی تغییرات مالی",
            subtitle = "هر پرداخت، تخفیف و تغییر اقساط با مجری، زمان و مقدارِ قبل و بعد. این صفحه فقط برای مشاهده است.")

        @template.layout.school-scope(
            noSchoolMessage = "برای مشاهده‌ی سابقه‌ی تغییرات مالی، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <template x-if="!blocked">
                    <div>
                        <%-- ------------------------------------------------ فیلترها --%>
                        <form class="box" id="financeAuditFilters" novalidate @submit.prevent="apply()">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="auditAction">نوع تغییر</label>
                                    <div class="select">
                                        <select id="auditAction" x-model="form.action">
                                            <option value="">همه‌ی تغییرها</option>
                                            <template x-for="option in actionOptions()" :key="option.code">
                                                <option :value="option.code" x-text="option.label"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control">
                                    <label class="label" for="auditFrom">از تاریخ</label>
                                    <input class="input" dir="ltr" id="auditFrom" type="text" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="form.from"
                                           :class="{ 'is-danger': errors.from }" :aria-invalid="errors.from ? 'true' : 'false'"
                                           aria-describedby="auditFromHelp">
                                    <p class="help is-danger" id="auditFromHelp" x-show="errors.from" x-text="errors.from"></p>
                                </div>
                                <div class="control">
                                    <label class="label" for="auditTo">تا تاریخ</label>
                                    <input class="input" dir="ltr" id="auditTo" type="text" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="form.to"
                                           :class="{ 'is-danger': errors.to }" :aria-invalid="errors.to ? 'true' : 'false'"
                                           aria-describedby="auditToHelp">
                                    <p class="help is-danger" id="auditToHelp" x-show="errors.to" x-text="errors.to"></p>
                                </div>
                                <div class="control">
                                    <label class="label" for="auditInvoice">شماره‌ی فاکتور</label>
                                    <input class="input" dir="ltr" id="auditInvoice" type="text" inputmode="numeric"
                                           autocomplete="off" x-model="form.invoiceId"
                                           :class="{ 'is-danger': errors.invoiceId }" :aria-invalid="errors.invoiceId ? 'true' : 'false'"
                                           aria-describedby="auditInvoiceHelp">
                                    <p class="help is-danger" id="auditInvoiceHelp" x-show="errors.invoiceId" x-text="errors.invoiceId"></p>
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-link" type="submit" id="financeAuditApply"
                                            :class="{ 'is-loading': loading }">اعمال فیلتر</button>
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-light" type="button" id="financeAuditClear"
                                            @click="clear()">پاک کردن فیلترها</button>
                                </div>
                            </div>
                            <p class="help">تاریخ را به شکل سال/ماه/روز بنویسید؛ ارقام فارسی هم پذیرفته می‌شود. هر دو سر بازه شاملِ همان روز است و خالی‌گذاشتنشان یعنی بدون محدودیت.</p>
                            <div class="field is-grouped is-grouped-multiline mt-3" id="financeAuditStudentFilter" x-show="studentId">
                                <p class="control">فقط تغییرهای دانش‌آموز شماره‌ی <strong x-text="fa(studentId)"></strong></p>
                                <div class="control">
                                    <button class="button is-light" type="button" @click="clearStudent()">نمایش همه‌ی دانش‌آموزان</button>
                                </div>
                            </div>
                        </form>

                        <%-- ------------------------------------------------ نتایج (جدیدترین اول) --%>
                        <p role="status" x-show="loading && items.length === 0">در حال بارگذاری...</p>

                        <div x-show="items.length > 0" id="financeAuditResults">
                            <%-- یک کارت برای هر تغییر (جدیدترین اول): بدون جدول و بدون اسکرولِ افقی --%>
                            <h2 class="is-sr-only">سابقه‌ی تغییرات مالی، جدیدترین اول</h2>
                            <template x-for="item in items" :key="item.id">
                                <article class="box" data-audit-card data-audit-row>
                                    <div class="is-flex is-justify-content-space-between is-align-items-center is-flex-wrap-wrap mb-2">
                                        <button class="button is-light is-small" type="button" x-show="hasDetails(item)"
                                                :aria-expanded="isExpanded(item) ? 'true' : 'false'"
                                                :aria-controls="'auditDetail-' + item.id"
                                                :class="{ 'is-link': isExpanded(item) }"
                                                @click="toggle(item)">جزئیات</button>
                                        <p>
                                            <span dir="ltr"><span x-text="whenDate(item)"></span> <span x-text="whenTime(item)"></span></span>
                                            <span class="has-text-grey">·</span>
                                            <span x-text="actor(item)"></span>
                                        </p>
                                    </div>
                                    <p>
                                        <span class="tag is-light" x-text="actionLabel(item)"></span>
                                        <span x-text="summary(item) || '—'"></span>
                                    </p>
                                    <p class="has-text-grey mt-2">
                                        دانش‌آموز: <span x-text="item.studentName || '—'"></span>
                                        · فاکتور <span x-text="item.invoiceId == null ? '—' : fa(item.invoiceId)"></span>
                                    </p>
                                    <p class="mt-1" x-show="item.amount != null">
                                        <span class="has-text-grey">مبلغ:</span>
                                        <span x-text="amountText(item)"></span>
                                    </p>
                                    <div class="mt-3" x-show="isExpanded(item)" x-cloak :id="'auditDetail-' + item.id" data-audit-detail>
                                        <template x-if="tables(item)">
                                            <div>
                                                <template x-for="side in [{ label: 'قبل', rows: tables(item).before }, { label: 'بعد', rows: tables(item).after }]" :key="side.label">
                                                    <div class="mb-3">
                                                        <p class="label" x-text="side.label"></p>
                                                        <p class="has-text-grey" x-show="side.rows.length === 0">قسطی ثبت نشده بود.</p>
                                                        <%-- هر قسط یک سطرِ چیده‌شده (نه جدول) تا در ۳۲۰ پیکسل هم بریده نشود --%>
                                                        <ul x-show="side.rows.length > 0">
                                                            <template x-for="(row, index) in side.rows" :key="index">
                                                                <li class="py-1">
                                                                    <span class="has-text-grey">مبلغ</span> <span x-text="Money.formatAmount(row.amount)"></span>
                                                                    · <span class="has-text-grey">پرداخت‌شده</span> <span x-text="Money.formatAmount(row.amountPaid)"></span>
                                                                    · <span class="has-text-grey">سررسید</span> <span :dir="row.dueDate ? 'ltr' : null" x-text="dueLabel(row)"></span>
                                                                </li>
                                                            </template>
                                                        </ul>
                                                    </div>
                                                </template>
                                            </div>
                                        </template>
                                        <template x-if="!tables(item)">
                                            <dl>
                                                <template x-for="entry in detailEntries(item)" :key="entry.label">
                                                    <div class="py-1">
                                                        <dt class="has-text-grey" x-text="entry.label"></dt>
                                                        <dd x-text="entry.value"></dd>
                                                    </div>
                                                </template>
                                            </dl>
                                        </template>
                                    </div>
                                </article>
                            </template>

                            <p class="help" x-text="rangeText()"></p>

                            <%-- صفحه‌بندی سمتِ سرور (۵۰ مورد در هر صفحه) --%>
                            <nav class="pagination is-centered mt-3" role="navigation" aria-label="صفحه‌بندی سابقه" id="financeAuditPagination"
                                 x-show="pageCount() > 1">
                                <button class="pagination-previous" type="button" :disabled="page <= 0 || loading" @click="goTo(page - 1)">قبلی</button>
                                <button class="pagination-next" type="button" :disabled="page + 1 >= pageCount() || loading" @click="goTo(page + 1)">بعدی</button>
                                <ul class="pagination-list">
                                    <li>
                                        <span class="pagination-link is-current" aria-current="page">
                                            صفحه <span x-text="fa(page + 1)"></span> از <span x-text="fa(pageCount())"></span>
                                        </span>
                                    </li>
                                </ul>
                            </nav>
                        </div>

                        @template.layout.empty-state(
                            show = "!loading && !error && items.length === 0",
                            art = "money",
                            message = @`در این بازه تغییر مالی‌ای ثبت نشده است. هر پرداخت، تخفیف و تغییر اقساط اینجا ثبت می‌شود.`)
                    </div>
                </template>
            `)
    </div>
</section>
`)
