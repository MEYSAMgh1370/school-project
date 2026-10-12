@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "گزارش‌های سؤال", styles = java.util.List.of("/webjars/katex/0.16.22/dist/katex.min.css"), scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/js/list-search.js", "/js/modal-focus.js", "/webjars/katex/0.16.22/dist/katex.min.js", "/js/math.js", "/js/question-reports.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="questionReportsPage()">
    <div class="container">
        <%-- صف رسیدگی است، نه صفحه‌ی ساخت: گزارش از بانک سؤال ثبت می‌شود، پس هدر عمل اصلی ندارد. --%>
        @template.layout.page-header(
            title = "گزارش‌های سؤال")

        @template.layout.school-scope(
            noSchoolMessage = "برای دیدن گزارش‌های سؤال، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <template x-if="!blocked">
                    <div>
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control is-expanded">
                                <label class="label is-sr-only" for="reportSearch">جست‌وجو در گزارش‌ها</label>
                                <input class="input" id="reportSearch" type="search" x-model="filterText"
                                       placeholder="جست‌وجوی متن سؤال، توضیح یا یادداشت">
                            </div>
                            <div class="control">
                                <label class="label is-sr-only" for="reportStatusFilter">وضعیت</label>
                                <div class="select">
                                    <select id="reportStatusFilter" x-model="statusFilter">
                                        <option value="">همه‌ی وضعیت‌ها</option>
                                        <template x-for="s in statuses" :key="s.value">
                                            <option :value="s.value" x-text="s.label"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>
                        </div>

                        <p class="has-text-grey" x-show="loading">در حال بارگذاری...</p>

                        <div class="box" x-show="reports.length > 0">
                            <div class="table-container">
                                <table class="table is-fullwidth">
                                    <thead>
                                    <tr>
                                        <th scope="col">سؤال</th>
                                        <th scope="col">نوع مشکل</th>
                                        <th scope="col">توضیح</th>
                                        <th scope="col">وضعیت</th>
                                        <th scope="col">تاریخ ثبت</th>
                                        <th scope="col">یادداشت رسیدگی</th>
                                        <th scope="col">اقدام</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="report in visibleReports()" :key="report.id">
                                        <tr>
                                            <td>
                                                <span x-math="truncate(report.questionText, 80)"></span><br>
                                                <span class="tag is-light" x-text="report.platformQuestion ? 'سؤال پلتفرم' : 'سؤال مدرسه'"></span>
                                            </td>
                                            <td x-text="typeLabel(report.reportType)"></td>
                                            <td x-text="report.comment || '—'"></td>
                                            <td><span class="tag" :class="statusClass(report.status)" x-text="statusLabel(report.status)"></span></td>
                                            <td x-text="Calendar.formatDate(report.createdAt)"></td>
                                            <td x-text="report.resolutionNote || '—'"></td>
                                            <td>
                                                <template x-if="report.platformQuestion">
                                                    <span class="is-size-7 has-text-grey">پیگیری توسط مدیر پلتفرم</span>
                                                </template>
                                                <template x-if="!report.platformQuestion">
                                                    <div class="buttons">
                                                        <button type="button" class="button is-light" x-show="report.status === 'OPEN'"
                                                                @click="openAction(report, 'REVIEWED', $event)">بررسی شد</button>
                                                        <button type="button" class="button is-light" x-show="report.status === 'REVIEWED'"
                                                                @click="openAction(report, 'FIXED', $event)">رفع شد</button>
                                                        <button type="button" class="button is-light" x-show="report.status === 'REVIEWED'"
                                                                @click="openAction(report, 'CLOSED', $event)">بستن</button>
                                                        <span class="has-text-grey" x-show="report.status === 'FIXED' || report.status === 'CLOSED'">—</span>
                                                    </div>
                                                </template>
                                            </td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                            </div>
                            <p class="has-text-grey" x-show="filteredReports().length === 0">
                                گزارشی با این جست‌وجو یا وضعیت پیدا نشد.
                            </p>
                            <button type="button" class="button is-light mt-3"
                                    x-show="remainingCount() > 0" @click="showMore()"
                                    x-text="moreLabel()"></button>
                        </div>

                        @template.layout.empty-state(
                            show = "!loading && reports.length === 0",
                            art = "exam",
                            message = @`هنوز گزارشی ثبت نشده است. از <a href="/curriculum">بانک سؤال</a> روی «گزارش مشکل» بزنید.`)
                    </div>
                </template>
            `)
    </div>

    <%-- ------------------------------------------------ تغییر وضعیت گزارش (تنها modal صفحه) --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="reportActionTitle"
         tabindex="-1" x-ref="actionModal" :class="{ 'is-active': action }" x-cloak
         @keydown.tab="window.ModalFocus && window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeAction">
        <div class="modal-background" @click="closeAction"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="reportActionTitle" x-text="action ? action.label : ''"></p>
                <button class="delete" type="button" aria-label="بستن" @click="closeAction"></button>
            </header>
            <section class="modal-card-body">
                <div class="notification is-danger" role="alert" x-show="action && action.error">
                    <span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="action ? action.error : ''"></span></span>
                </div>
                <div class="notification is-light">
                    <span x-math="action ? truncate(action.report.questionText, 200) : ''"></span>
                </div>
                <div class="field">
                    <label class="label" for="reportActionNote">یادداشت (اختیاری)</label>
                    <textarea class="textarea" id="reportActionNote" maxlength="1000" x-model="actionNote"
                              :class="{ 'is-danger': action && action.noteError }"></textarea>
                    <p class="help is-danger" x-show="action && action.noteError" x-text="action ? action.noteError : ''"></p>
                </div>
            </section>
            <footer class="modal-card-foot">
                <button class="button is-link" type="button" :class="{ 'is-loading': submitting }"
                        @click="submitAction">ثبت</button>
                <button class="button is-light" type="button" @click="closeAction">انصراف</button>
            </footer>
        </div>
    </div>
</section>
`)
