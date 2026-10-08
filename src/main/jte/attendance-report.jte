@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «گزارش حضور و غیاب» (openspec/changes/attendance.md «Report (admin)» و «The admin figure»): دو بخش روی یک
     صفحه. اقدامِ اصلیِ صفحه «نمایش گزارش» در inline-end هدر است و بخشِ فعال را (دوباره) می‌خواند. روز و بازه در
     ورودی جلالی با ارقام فارسی‌اند و ISO به سرور می‌رود (date-input.js). دو بخشِ اول خواندنی‌اند؛ بخشِ سومِ
     «عذرخواهی‌ها» تأیید/ردِ عذر را می‌نویسد، پس ناحیه‌ی پیامِ موفقیتِ مشترک (notice) فعال است. --%>
@template.layout.main(title = "گزارش حضور و غیاب",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/date-input.js", "/js/school-api.js", "/js/attendance-report.js"),
        styles = java.util.List.of("/css/attendance.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="attendanceReportPage()">
    <div class="container">
        <div class="level is-mobile">
            <div class="level-left">
                <div>
                    @template.layout.page-header(
                        title = "گزارش حضور و غیاب",
                        subtitle = "غایبینِ یک روز، یا غیبت و تأخیرِ دانش‌آموزان در یک بازه.")
                </div>
            </div>
            <div class="level-right" x-cloak x-show="schoolId">
                <button class="button is-link" type="button" id="attendanceRun"
                        :class="{ 'is-loading': dayLoading || rangeLoading || excusesLoading }" @click="run">نمایش گزارش</button>
            </div>
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای دیدنِ گزارش، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <div x-show="!blocked">
                    <div class="tabs">
                        <ul role="tablist">
                            <li :class="{ 'is-active': tab === 'day' }" role="presentation">
                                <a href="#" role="tab" id="attTabDay" :aria-selected="tab === 'day'"
                                   @click.prevent="showTab('day')">غایبین روز</a>
                            </li>
                            <li :class="{ 'is-active': tab === 'range' }" role="presentation">
                                <a href="#" role="tab" id="attTabRange" :aria-selected="tab === 'range'"
                                   @click.prevent="showTab('range')">گزارش بازه‌ای</a>
                            </li>
                            <li :class="{ 'is-active': tab === 'excuses' }" role="presentation">
                                <a href="#" role="tab" id="attTabExcuses" :aria-selected="tab === 'excuses'"
                                   @click.prevent="showTab('excuses')">عذرخواهی‌ها</a>
                            </li>
                        </ul>
                    </div>

                    <%-- ------------------------------------------------ غایبین روز --%>
                    <div x-show="tab === 'day'" id="attPanelDay">
                        <div class="box">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="attReportDay">روز</label>
                                    <input class="input app-input-w-8" id="attReportDay" type="text" dir="ltr" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="dayText"
                                           :class="{ 'is-danger': dayError }"
                                           @change="onDayInput()" @keydown.enter.prevent="onDayInput()">
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-light" type="button" id="attReportToday" @click="goToday">امروز</button>
                                </div>
                            </div>
                            <p class="help is-danger" role="alert" id="attReportDayError" x-show="dayError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="dayError"></span></span></p>
                            <p class="is-size-7 has-text-grey" x-show="!dayError" x-text="dayTitle()"></p>
                        </div>

                        <p x-show="dayLoading && !dayLoaded">در حال بارگذاری...</p>

                        <%-- رقمِ مدیر: فقط روی کلاس‌هایی که حضور و غیابشان گرفته شده؛ کلاسِ ثبت‌نشده هرگز حاضر شمرده نمی‌شود --%>
                        <div class="box" id="attSummaryBox" x-show="summaryClosed() || summaryEmpty() || summaryFigure()">
                            <h2 class="title is-5 mb-2">رقمِ روز</h2>
                            <p id="attSummaryClosed" x-show="summaryClosed()">
                                این روز، روز درسیِ مدرسه نیست (آخرِ هفته یا تعطیلیِ ثبت‌شده در
                                <a href="/calendar">تقویم مدرسه</a>)؛ رقمی ندارد.
                            </p>
                            <p id="attSummaryEmpty" x-show="summaryEmpty()">
                                هنوز حضور و غیابی ثبت نشده است. بعد از اینکه معلم‌ها از صفحه‌ی
                                <a href="/attendance">حضور و غیاب</a> کلاس‌هایشان را ثبت کنند، رقم اینجا می‌آید.
                            </p>
                            <div id="attSummaryFigure" x-show="summaryFigure()">
                                <p class="mb-2">
                                    <strong><span x-text="Calendar.digits(summary ? summary.absent : 0)"></span> غایب</strong>
                                    ·
                                    <strong><span x-text="Calendar.digits(summary && summary.presentPercent !== null ? summary.presentPercent : 0)"></span>٪ حضور</strong>
                                </p>
                                <progress class="progress is-primary" max="100" aria-label="درصد حضور"
                                          :value="summary && summary.presentPercent !== null ? summary.presentPercent : 0"></progress>
                                <p class="is-size-7 has-text-grey mb-0">
                                    <span x-show="summary && summary.excusedAbsent > 0">
                                        از غایب‌ها <span x-text="Calendar.digits(summary ? summary.excusedAbsent : 0)"></span> نفر موجه‌اند.
                                    </span>
                                    <span x-text="Calendar.digits(summary ? summary.classesTaken : 0)"></span>
                                    کلاس از
                                    <span x-text="Calendar.digits(summary ? summary.classesTotal : 0)"></span>
                                    کلاس ثبت شده؛
                                    <span id="attClassesWithoutRoll"><span x-text="Calendar.digits(classesWithoutRoll())"></span> کلاس بدون ثبت</span>
                                    در درصد نیامده است.
                                </p>
                            </div>
                        </div>

                        @template.layout.empty-state(
                            show = "dayLoaded && summary && summary.schoolDay && cards.length === 0",
                            art = "people",
                            message = @`برای سال تحصیلی جاری هنوز کلاسی ندارید. از صفحه‌ی <a href="/classes">کلاس‌ها</a> کلاس بسازید.`)

                        <div class="columns is-multiline is-variable is-3" id="attCards" x-show="summary && summary.schoolDay">
                            <template x-for="card in cards" :key="card.classId">
                                <div class="column is-6-desktop is-12-tablet is-12-mobile">
                                    <article class="box app-att-class">
                                        <h3 class="title is-6 mb-2">
                                            <span x-text="card.className"></span>
                                            <span class="tag is-success is-light" x-show="card.taken">ثبت شد</span>
                                            <span class="tag is-warning is-light" x-show="!card.taken">ثبت نشده</span>
                                        </h3>
                                        <p class="is-size-7 has-text-grey mb-2" x-show="card.taken">
                                            غایب: <span x-text="Calendar.digits(card.absent)"></span>
                                            · موجه: <span x-text="Calendar.digits(card.excusedAbsent)"></span>
                                            · تأخیر: <span x-text="Calendar.digits(card.late)"></span>
                                            · از <span x-text="Calendar.digits(card.expected)"></span> دانش‌آموز
                                        </p>
                                        <p class="mb-0" x-show="!card.taken">حضور و غیابِ این کلاس در این روز هنوز ثبت نشده است.</p>
                                        <p class="mb-0" x-show="card.taken && card.students.length === 0">همه حاضر بودند.</p>
                                        <ul>
                                            <template x-for="student in card.students" :key="student.studentId">
                                                <li class="app-att-absentee">
                                                    <span x-text="student.studentName"></span>
                                                    <span class="tag is-light" x-text="statusLabel(student.status)"></span>
                                                    <span class="tag is-light" x-show="student.excused">موجه</span>
                                                    <span class="is-size-7 has-text-grey" x-show="student.note" x-text="student.note"></span>
                                                </li>
                                            </template>
                                        </ul>
                                    </article>
                                </div>
                            </template>
                        </div>
                    </div>

                    <%-- ------------------------------------------------ گزارش بازه‌ای --%>
                    <div x-show="tab === 'range'" id="attPanelRange">
                        <div class="box">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="attFrom">از تاریخ</label>
                                    <input class="input app-input-w-8" id="attFrom" type="text" dir="ltr" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="fromText"
                                           :class="{ 'is-danger': fromError }" @keydown.enter.prevent="loadRange()">
                                    <p class="help is-danger" role="alert" id="attFromError" x-show="fromError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="fromError"></span></span></p>
                                </div>
                                <div class="control">
                                    <label class="label" for="attTo">تا تاریخ</label>
                                    <input class="input app-input-w-8" id="attTo" type="text" dir="ltr" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="toText"
                                           :class="{ 'is-danger': toError }" @keydown.enter.prevent="loadRange()">
                                    <p class="help is-danger" role="alert" id="attToError" x-show="toError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="toError"></span></span></p>
                                </div>
                                <div class="control">
                                    <label class="label" for="attClassFilter">کلاس</label>
                                    <div class="select">
                                        <select id="attClassFilter" x-model="classFilter">
                                            <option value="">همه‌ی کلاس‌ها</option>
                                            <template x-for="item in classes" :key="item.classId">
                                                <option :value="String(item.classId)" :selected="String(item.classId) === String(classFilter)" x-text="classLabel(item)"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control is-expanded">
                                    <label class="label" for="attName">نام دانش‌آموز</label>
                                    <input class="input" id="attName" type="text" maxlength="100" autocomplete="off"
                                           x-model="name" @keydown.enter.prevent="loadRange()">
                                </div>
                            </div>
                            <p class="help">بخشی از نام کافی است. بازه حداکثر ۳۶۶ روز است.</p>
                        </div>

                        <p x-show="rangeLoading && !rangeLoaded">در حال بارگذاری...</p>

                        @template.layout.empty-state(
                            show = "rangeLoaded && !rangeLoading && rows.length === 0",
                            art = "list",
                            message = @`در این بازه حضور و غیابی ثبت نشده است. بازه را بزرگ‌تر کنید یا فیلتر کلاس و نام را بردارید؛ حضور و غیاب را معلم‌ها از صفحه‌ی <a href="/attendance">حضور و غیاب</a> ثبت می‌کنند.`)

                        <div x-show="rows.length > 0">
                            <p class="is-size-7 has-text-grey mb-2" id="attRangeLabel">
                                از <span x-text="rangeLabel()"></span> ·
                                <span x-text="Calendar.digits(rows.length)"></span> دانش‌آموز
                            </p>
                            <div class="table-container">
                                <table class="table is-fullwidth is-striped" id="attRangeTable">
                                    <caption class="is-sr-only">گزارش بازه‌ای حضور و غیاب</caption>
                                    <thead>
                                    <tr>
                                        <th scope="col">دانش‌آموز</th>
                                        <th scope="col">کلاس</th>
                                        <th scope="col">روزهای غیبت</th>
                                        <th scope="col">روزهای تأخیر</th>
                                        <th scope="col">روزهای حضور</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="row in pagedRows()" :key="row.studentId + '-' + row.classId">
                                        <tr>
                                            <td x-text="row.studentName"></td>
                                            <td x-text="row.className"></td>
                                            <td x-text="Calendar.digits(row.absentDays)"></td>
                                            <td x-text="Calendar.digits(row.lateDays)"></td>
                                            <td x-text="Calendar.digits(row.presentDays)"></td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                            </div>
                            <nav class="pagination is-centered" role="navigation" aria-label="صفحه‌بندی گزارش" x-show="paginated()" id="attPagination">
                                <button class="pagination-previous" type="button" :disabled="page <= 1" @click="previousPage">قبلی</button>
                                <button class="pagination-next" type="button" :disabled="page >= pageCount()" @click="nextPage">بعدی</button>
                                <ul class="pagination-list">
                                    <li>
                                        <span class="pagination-link is-current" aria-current="page">
                                            صفحه <span x-text="Calendar.digits(page)"></span> از <span x-text="Calendar.digits(pageCount())"></span>
                                        </span>
                                    </li>
                                </ul>
                            </nav>
                        </div>
                    </div>

                    <%-- ------------------------------------------------ عذرخواهی‌ها
                         تأیید/ردّ مستقیم و بدونِ modal (ردّ مخرب نیست)؛ دکمه‌ها برای هر نقش دیده می‌شوند و بدون
                         ATTENDANCE_RECORD_ANY با 403 به ناحیه‌ی پیامِ مشترک می‌روند. --%>
                    <div x-show="tab === 'excuses'" id="attPanelExcuses">
                        <div class="box">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="attExcStatus">وضعیت</label>
                                    <div class="select">
                                        <select id="attExcStatus" x-model="excStatus" @change="loadExcuses()">
                                            <option value="PENDING">در انتظار</option>
                                            <option value="APPROVED">تأیید شده</option>
                                            <option value="REJECTED">رد شده</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="control">
                                    <label class="label" for="attExcFrom">از تاریخ</label>
                                    <input class="input app-input-w-8" id="attExcFrom" type="text" dir="ltr" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="excFromText"
                                           :class="{ 'is-danger': excFromError }" @keydown.enter.prevent="loadExcuses()">
                                    <p class="help is-danger" role="alert" id="attExcFromError" x-show="excFromError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="excFromError"></span></span></p>
                                </div>
                                <div class="control">
                                    <label class="label" for="attExcTo">تا تاریخ</label>
                                    <input class="input app-input-w-8" id="attExcTo" type="text" dir="ltr" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="excToText"
                                           :class="{ 'is-danger': excToError }" @keydown.enter.prevent="loadExcuses()">
                                    <p class="help is-danger" role="alert" id="attExcToError" x-show="excToError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="excToError"></span></span></p>
                                </div>
                            </div>
                            <p class="help">تاریخ‌ها اختیاری‌اند و روزِ غیبت را فیلتر می‌کنند؛ ارقام فارسی هم پذیرفته می‌شود.</p>
                        </div>

                        <p x-show="excusesLoading && !excusesLoaded">در حال بارگذاری...</p>

                        @template.layout.empty-state(
                            show = "excusesLoaded && !excusesLoading && excuses.length === 0",
                            art = "list",
                            message = @`<span id="attExcEmpty" x-text="excusesEmptyText()"></span> عذرها را والدین از صفحه‌ی «غیبت‌های من» می‌فرستند.`)

                        <div x-show="excuses.length > 0">
                            <div class="table-container">
                                <table class="table is-fullwidth is-striped" id="attExcTable">
                                    <caption class="is-sr-only">عذرخواهی‌های غیبت</caption>
                                    <thead>
                                    <tr>
                                        <th scope="col">دانش‌آموز</th>
                                        <th scope="col">کلاس</th>
                                        <th scope="col">روزِ غیبت</th>
                                        <th scope="col">یادداشت</th>
                                        <th scope="col">وضعیت</th>
                                        <th scope="col"><span class="is-sr-only">عملیات</span></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="row in pagedExcuses()" :key="row.id">
                                        <tr>
                                            <td x-text="row.studentName"></td>
                                            <td x-text="row.className"></td>
                                            <td x-text="dayShort(row.day)"></td>
                                            <td x-text="row.note"></td>
                                            <td><span class="tag is-light" x-text="excuseLabel(row.status)"></span></td>
                                            <td>
                                                <template x-if="row.status === 'PENDING'">
                                                    <div class="buttons">
                                                        <button class="button is-light" type="button"
                                                                :class="{ 'is-loading': isDeciding(row, 'APPROVED') }"
                                                                :aria-label="'تأیید عذر ' + row.studentName"
                                                                @click="decide(row, 'APPROVED')">تأیید</button>
                                                        <button class="button is-light" type="button"
                                                                :class="{ 'is-loading': isDeciding(row, 'REJECTED') }"
                                                                :aria-label="'رد عذر ' + row.studentName"
                                                                @click="decide(row, 'REJECTED')">رد</button>
                                                    </div>
                                                </template>
                                            </td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                            </div>
                            <nav class="pagination is-centered" role="navigation" aria-label="صفحه‌بندی عذرها" x-show="excPaginated()" id="attExcPagination">
                                <button class="pagination-previous" type="button" :disabled="excPage <= 1" @click="previousExcPage">قبلی</button>
                                <button class="pagination-next" type="button" :disabled="excPage >= excPageCount()" @click="nextExcPage">بعدی</button>
                                <ul class="pagination-list">
                                    <li>
                                        <span class="pagination-link is-current" aria-current="page">
                                            صفحه <span x-text="Calendar.digits(excPage)"></span> از <span x-text="Calendar.digits(excPageCount())"></span>
                                        </span>
                                    </li>
                                </ul>
                            </nav>
                        </div>
                    </div>
                </div>
            `)
    </div>
</section>
`)
