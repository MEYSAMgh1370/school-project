@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "برنامه‌ی کلاسی من",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/timetable-grid.js", "/js/my-timetable.js"),
        styles = java.util.List.of("/css/my-grades.css", "/css/timetable.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="myTimetablePage()">
    <div class="container">
        @template.layout.page-header(
            title = "برنامه‌ی کلاسی من",
            subtitle = "برنامه‌ی هفتگیِ شما یا فرزندانِ لینک‌شده به شما در سال تحصیلیِ جاریِ این مدرسه.")

        @template.layout.school-scope(
            noSchoolMessage = "برای مشاهده‌ی برنامه، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <div x-cloak x-show="!loading && loaded && !error && views.length > 0">
                    <%-- انتخابگر: فقط وقتی بیش از یک برنامه هست (چند فرزند، یا فرزند و ساعت‌های تدریس)؛
                         همان کنترلِ /my-grades. --%>
                    <div class="app-picker" x-show="views.length > 1"
                         @click.outside="pickerOpen = false" @keydown.escape="closePicker(true)">
                        <p class="is-size-7 has-text-grey mb-2" id="tt-picker-label">انتخاب برنامه</p>
                        <button type="button" class="app-picker-toggle" x-ref="pickerToggle" aria-controls="tt-picker-list"
                                aria-labelledby="tt-picker-label tt-picker-current"
                                :aria-expanded="pickerOpen" @click="pickerOpen = !pickerOpen">
                            <span id="tt-picker-current" x-text="selectedView() ? viewLabel(selectedView()) : ''"></span>
                            <span class="app-picker-chevron" aria-hidden="true"></span>
                        </button>
                        <ul class="app-picker-list" id="tt-picker-list" x-show="pickerOpen">
                            <template x-for="view in views" :key="view.key">
                                <li>
                                    <button type="button" class="app-picker-option"
                                            :class="{ 'is-selected': isSelected(view.key) }"
                                            :aria-current="isSelected(view.key) ? 'true' : null"
                                            @click="select(view.key)">
                                        <span x-text="viewLabel(view)"></span>
                                    </button>
                                </li>
                            </template>
                        </ul>
                    </div>
                    <h2 class="title is-5 mb-2" x-show="views.length === 1"
                        x-text="selectedView() ? viewLabel(selectedView()) : ''"></h2>

                    <p class="mb-4">
                        <span class="is-size-7 has-text-grey">سال تحصیلی:</span>
                        <span class="tag is-light" dir="ltr" id="myTtYear" x-show="academicYear"
                              x-text="academicYear ? Calendar.digits(academicYear) : ''"></span>
                        <span class="tag is-light" x-show="selectedView() && selectedView().kind !== 'TEACHING' && selectedView().className"
                              x-text="selectedView() ? 'کلاس ' + selectedView().className : ''"></span>
                    </p>

                    <div class="notification app-empty" x-show="academicYear === null" id="myTtNoYear">
                        <span class="app-empty__text">
                            مدیر مدرسه هنوز سال تحصیلیِ جاری را انتخاب نکرده است.
                        </span>
                    </div>

                    <div class="notification app-empty" x-show="academicYear !== null && periods.length === 0" id="myTtNoPeriods">
                        <span class="app-empty__text">
                            مدیر مدرسه هنوز زنگ‌های این سال تحصیلی را تعریف نکرده است.
                        </span>
                    </div>

                    <div class="notification app-empty" x-show="academicYear !== null && periods.length > 0 && !hasAnyEntry()" id="myTtNoEntries">
                        <span class="app-empty__text">
                            برای این برنامه هنوز درسی در جدول گذاشته نشده است.
                        </span>
                    </div>

                    <div class="table-container" x-show="periods.length > 0 && hasAnyEntry()">
                        <table class="table app-tt-grid" id="myTtGrid">
                            <caption class="is-sr-only">برنامه‌ی هفتگی</caption>
                            <thead>
                            <tr>
                                <th scope="col">زنگ</th>
                                <template x-for="day in days" :key="day.code">
                                    <th scope="col" :class="{ 'app-tt-today': day.code === todayCode }"
                                        :aria-current="day.code === todayCode ? 'date' : null" x-text="day.label"></th>
                                </template>
                            </tr>
                            </thead>
                            <tbody>
                            <template x-for="period in periods" :key="period.id">
                                <tr>
                                    <th scope="row">
                                        <span x-text="period.label"></span>
                                        <span class="app-tt-meta" x-text="TimetableGrid.rangeLabel(period)"></span>
                                    </th>
                                    <template x-for="day in days" :key="day.code">
                                        <td :class="{ 'app-tt-today': day.code === todayCode }">
                                            <template x-if="entryAt(day.code, period.id)">
                                                <span>
                                                    <span class="app-tt-subject" x-text="subjectOf(entryAt(day.code, period.id))"></span>
                                                    <span class="app-tt-meta" x-show="metaOf(entryAt(day.code, period.id))"
                                                          x-text="metaOf(entryAt(day.code, period.id))"></span>
                                                </span>
                                            </template>
                                            <template x-if="!entryAt(day.code, period.id)">
                                                <span class="has-text-grey" aria-label="خالی">—</span>
                                            </template>
                                        </td>
                                    </template>
                                </tr>
                            </template>
                            </tbody>
                        </table>
                    </div>
                </div>

                @template.layout.empty-state(
                    show = "!loading && loaded && !error && views.length === 0",
                    art = "calendar",
                    message = @`برنامه‌ای برای شما یا فرزندانتان در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
