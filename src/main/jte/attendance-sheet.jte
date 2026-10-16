@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar
@param Long studentId

<%-- «برگه‌ی حضور و غیابِ دانش‌آموز» (openspec/changes/attendance-reports.md): صفحه‌ی چاپ، عمداً بدون ناوبار تا فقط
     خودِ برگه روی کاغذ بیاید (مثل question-sheet-print.jte). بازه از آدرس می‌آید و روی سیم ISO است؛ نمایش جلالی با
     ارقام فارسی. کنترل‌ها با no-print در چاپ پنهان‌اند. navbar فقط برای نامِ مدرسه‌ها (data-attribute) است. --%>
@template.layout.main(title = "برگه‌ی حضور و غیاب",
        scripts = java.util.List.of("/js/jalali.js", "/js/calendar.js", "/js/date-input.js", "/js/school-api.js", "/js/attendance-sheet-logic.js", "/js/attendance-sheet-print.js"),
        styles = java.util.List.of("/css/attendance-print.css"), content = @`
<main id="main-content" role="main" tabindex="-1" class="section app-sheet-page" x-data="attendanceSheetPage()" data-student-id="${studentId}">
    @for(var school : navbar.schools())
        <span hidden data-school-id="${school.id()}" data-school-name="${school.name()}"></span>
    @endfor

    <div class="app-sheet-toolbar no-print">
        <div>
            <p class="mb-1" x-show="range" x-cloak>بازه: <strong x-text="rangeText()"></strong></p>
            <p class="help">بازه از صفحه‌ی گزارش می‌آید؛ برای تغییر آن به گزارش برگردید و دوباره «برگه‌ی حضور» را بزنید.
                برای ذخیره به‌صورت PDF، در پنجره‌ی چاپ مقصد را «Save as PDF» بگذارید.</p>
        </div>
        <div class="buttons">
            <a class="button is-light" id="sheetBack" href="/attendance/report">بازگشت به گزارش</a>
            <button type="button" class="button is-link" id="sheetPrint" :disabled="!sheet"
                    :class="{ 'is-loading': loading }" @click="printSheet">چاپ / ذخیره PDF</button>
        </div>
    </div>

    <div class="notification is-danger app-sheet-notice no-print" role="alert" id="sheetError" x-show="error" x-cloak>
        <span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="error"></span></span>
    </div>
    <p class="app-sheet-notice no-print" id="sheetLoading" x-show="loading" x-cloak>در حال بارگذاری...</p>

    <template x-if="sheet">
        <article class="box app-sheet" id="attendanceSheet">
            <header>
                <h1 class="title is-4 mb-1">برگه‌ی حضور و غیاب</h1>
                <p class="subtitle is-6 mb-2" x-show="schoolName" x-text="schoolName"></p>
            </header>
            <p class="mb-1">دانش‌آموز: <strong x-text="sheet.studentName"></strong></p>
            <p class="mb-0">بازه: <span x-text="rangeText()"></span></p>

            <section class="app-sheet-section">
                <h2 class="title is-5 mb-2">درصد حضور</h2>
                <div class="app-sheet-percent" x-show="sheet.presentPercent !== null">
                    <strong x-text="percentText(sheet.presentPercent)"></strong>
                    <progress class="progress is-primary" max="100" aria-label="درصد حضور" :value="barValue(sheet.presentPercent)"></progress>
                </div>
                <p x-show="sheet.presentPercent === null" id="sheetNoPercent">در این بازه حضور و غیابی برای این دانش‌آموز ثبت نشده است.</p>

                <div class="table-container mt-3" x-show="classRows.length > 0">
                    <table class="table is-fullwidth" id="sheetClassTable">
                        <caption class="is-sr-only">خلاصه‌ی حضور و غیاب به تفکیک کلاس</caption>
                        <thead>
                        <tr>
                            <th scope="col">کلاس</th>
                            <th scope="col">روزهای غیبت</th>
                            <th scope="col">روزهای تأخیر</th>
                            <th scope="col">روزهای حضور</th>
                            <th scope="col">درصد حضور</th>
                        </tr>
                        </thead>
                        <tbody>
                        <template x-for="row in classRows" :key="row.classId">
                            <tr>
                                <td x-text="row.className"></td>
                                <td x-text="num(row.absentDays)"></td>
                                <td x-text="num(row.lateDays)"></td>
                                <td x-text="num(row.presentDays)"></td>
                                <td x-text="percentText(row.presentPercent)"></td>
                            </tr>
                        </template>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="app-sheet-section">
                <h2 class="title is-5 mb-2">فهرست غیبت و تأخیر</h2>
                <p id="sheetEmpty" x-show="dayRows.length === 0">در این بازه غیبت یا تأخیری ثبت نشده است.</p>
                <div class="table-container" x-show="dayRows.length > 0">
                    <table class="table is-fullwidth" id="sheetDaysTable">
                        <caption class="is-sr-only">فهرست روزهای غیبت و تأخیر</caption>
                        <thead>
                        <tr>
                            <th scope="col">تاریخ</th>
                            <th scope="col">روز هفته</th>
                            <th scope="col">کلاس</th>
                            <th scope="col">وضعیت</th>
                            <th scope="col">یادداشت</th>
                        </tr>
                        </thead>
                        <tbody>
                        <template x-for="item in dayRows" :key="item.key">
                            <tr>
                                <td x-text="item.date"></td>
                                <td x-text="item.weekday"></td>
                                <td x-text="item.className"></td>
                                <td>
                                    <span x-text="item.statusLabel"></span>
                                    <span class="tag is-light" x-show="item.excused">موجه</span>
                                </td>
                                <td x-text="item.note"></td>
                            </tr>
                        </template>
                        </tbody>
                    </table>
                </div>
                <p class="mb-0" x-show="dayRows.length > 0">
                    جمع: <span x-text="num(absentTotal())"></span> روز غیبت و <span x-text="num(lateTotal())"></span> روز تأخیر
                </p>
            </section>

            <p class="is-size-7 app-sheet-foot mb-0">تاریخ چاپ: <span x-text="printDate()"></span></p>
        </article>
    </template>
</main>
`)
