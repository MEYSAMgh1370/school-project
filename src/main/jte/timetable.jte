@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "برنامه‌ی کلاسی",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/modal-focus.js", "/js/timetable-grid.js", "/js/timetable.js"),
        styles = java.util.List.of("/css/timetable.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="timetablePage()">
    <div class="container">
        @template.layout.page-header(
            title = "برنامه‌ی کلاسی",
            subtitle = "زنگ‌ها، فعالیت‌های فوق‌برنامه و برنامه‌ی هفتگیِ هر کلاس؛ شنبه تا چهارشنبه.")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت برنامه‌ی کلاسی، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <template x-if="!blocked && loaded">
                    <div>
                        <%-- سالِ تحصیلی: مدیر هر سالِ فهرست را می‌تواند باز کند؛ پیش‌فرض سالِ جاریِ
                             مدرسه است. زنگ‌ها و خانه‌ها به‌ازای سال‌اند (openspec/changes/class-timetable.md). --%>
                        <div class="box">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="ttYear">سال تحصیلی</label>
                                    <div class="select">
                                        <select id="ttYear" x-model="year" @change="onYearChange()">
                                            <template x-for="code in years" :key="code">
                                                <option :value="code" :selected="code === year" x-text="Jalali.toPersianDigits(code)"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control app-control-align-end" x-show="year">
                                    <span class="tag is-success is-light" id="ttYearCurrent" x-show="isCurrentYear()">سال تحصیلی جاری</span>
                                    <span class="tag is-warning is-light" id="ttYearOther" x-show="!isCurrentYear()">سالِ جاری نیست</span>
                                </div>
                            </div>
                            <p class="help" x-show="year && !isCurrentYear()">
                                دانش‌آموزان، والدین و معلم‌ها فقط برنامه‌ی سالِ تحصیلیِ جاری را می‌بینند.
                            </p>
                            @template.layout.empty-state(
                                show = "years.length === 0",
                                art = "calendar",
                                message = @`هنوز سال تحصیلی‌ای در سیستم ساخته نشده است.`)
                        </div>

                        <div class="tabs" x-show="year">
                            <ul role="tablist">
                                <li :class="{ 'is-active': tab === 'grid' }" role="presentation">
                                    <a href="#" role="tab" id="ttTabGrid" :aria-selected="tab === 'grid'"
                                       @click.prevent="tab = 'grid'">برنامه‌ی کلاس‌ها</a>
                                </li>
                                <li :class="{ 'is-active': tab === 'periods' }" role="presentation">
                                    <a href="#" role="tab" id="ttTabPeriods" :aria-selected="tab === 'periods'"
                                       @click.prevent="tab = 'periods'">زنگ‌ها</a>
                                </li>
                                <li :class="{ 'is-active': tab === 'activities' }" role="presentation">
                                    <a href="#" role="tab" id="ttTabActivities" :aria-selected="tab === 'activities'"
                                       @click.prevent="tab = 'activities'">فعالیت‌ها</a>
                                </li>
                            </ul>
                        </div>

                        <%-- ------------------------------------------------ برنامه‌ی کلاس‌ها --%>
                        <div x-show="year && tab === 'grid'" id="ttPanelGrid">
                            @template.layout.empty-state(
                                show = "classes.length === 0",
                                art = "people",
                                message = @`هنوز کلاسی ندارید. از صفحه‌ی <a href="/classes">کلاس‌ها</a> یک کلاس بسازید.`)

                            <div x-show="classes.length > 0">
                                <div class="field">
                                    <label class="label" for="ttClass">کلاس</label>
                                    <div class="control">
                                        <div class="select">
                                            <select id="ttClass" x-model="classId" @change="onClassChange()">
                                                <template x-for="classItem in classes" :key="classItem.id">
                                                    <option :value="classItem.id" :selected="String(classItem.id) === String(classId)" x-text="classLabel(classItem)"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                </div>

                                <%-- هنوز زنگی ندارد: اول زنگ‌ها --%>
                                <div class="notification app-empty" x-show="periods.length === 0 && !busy() && periodsOk" id="ttNoPeriods">
                                    <span class="app-empty__text">
                                        برای این سال تحصیلی هنوز زنگی تعریف نشده است. اول از تبِ
                                        <a href="#" @click.prevent="tab = 'periods'">زنگ‌ها</a> زنگ‌ها را بسازید.
                                    </span>
                                </div>

                                <div class="table-container" x-show="gridReady()">
                                    <table class="table app-tt-grid" id="ttGrid">
                                        <caption class="is-sr-only">برنامه‌ی هفتگی کلاس</caption>
                                        <thead>
                                        <tr>
                                            <th scope="col">زنگ</th>
                                            <template x-for="day in days" :key="day.code">
                                                <th scope="col" x-text="day.label"></th>
                                            </template>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <template x-for="period in periods" :key="period.id">
                                            <tr>
                                                <th scope="row">
                                                    <span x-text="period.label"></span>
                                                    <span class="app-tt-meta"
                                                          x-text="TimetableGrid.rangeLabel(period)"></span>
                                                </th>
                                                <template x-for="day in days" :key="day.code">
                                                    <td>
                                                        <button type="button" class="app-tt-cell"
                                                                :class="{ 'is-empty': !entryAt(day.code, period.id) }"
                                                                :aria-label="cellLabel(day.code, period)"
                                                                @click="openCell(day.code, period)">
                                                            <template x-if="entryAt(day.code, period.id)">
                                                                <span>
                                                                    <span class="app-tt-subject"
                                                                          x-text="subjectOf(entryAt(day.code, period.id))"></span>
                                                                    <span class="app-tt-meta"
                                                                          x-show="entryAt(day.code, period.id).teacherName"
                                                                          x-text="entryAt(day.code, period.id).teacherName"></span>
                                                                </span>
                                                            </template>
                                                            <template x-if="!entryAt(day.code, period.id)">
                                                                <span aria-hidden="true">+</span>
                                                            </template>
                                                        </button>
                                                    </td>
                                                </template>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                </div>
                                <p class="help" x-show="gridReady()">
                                    روی هر خانه بزنید تا درس یا فعالیت و معلمِ آن را انتخاب کنید.
                                </p>
                            </div>
                        </div>

                        <%-- ------------------------------------------------ زنگ‌ها --%>
                        <div x-show="year && tab === 'periods'" id="ttPanelPeriods">
                            <div class="box">
                                <h2 class="title is-6" x-text="periodForm.id === null ? 'زنگ تازه' : 'ویرایش زنگ'"></h2>
                                <form @submit.prevent="savePeriod" novalidate>
                                    <div class="field is-grouped is-grouped-multiline">
                                        <div class="control">
                                            <label class="label" for="ttPeriodLabel">برچسب</label>
                                            <input class="input" id="ttPeriodLabel" type="text" maxlength="64"
                                                   x-model="periodForm.label" :class="{ 'is-danger': periodErrors.label }">
                                            <p class="help is-danger" x-show="periodErrors.label" x-text="periodErrors.label"></p>
                                        </div>
                                        <div class="control">
                                            <label class="label" for="ttPeriodStart">شروع</label>
                                            <input class="input" id="ttPeriodStart" type="time" dir="ltr"
                                                   x-model="periodForm.start" :class="{ 'is-danger': periodErrors.start }">
                                            <p class="help is-danger" x-show="periodErrors.start" x-text="periodErrors.start"></p>
                                        </div>
                                        <div class="control">
                                            <label class="label" for="ttPeriodEnd">پایان</label>
                                            <input class="input" id="ttPeriodEnd" type="time" dir="ltr"
                                                   x-model="periodForm.end" :class="{ 'is-danger': periodErrors.end }">
                                            <p class="help is-danger" x-show="periodErrors.end" x-text="periodErrors.end"></p>
                                        </div>
                                        <div class="control app-control-align-end">
                                            <button class="button is-link" type="submit"
                                                    :class="{ 'is-loading': saving }">
                                                <span x-text="periodForm.id === null ? 'افزودن زنگ' : 'ذخیره‌ی زنگ'"></span>
                                            </button>
                                            <button class="button" type="button" x-show="periodForm.id !== null"
                                                    @click="resetPeriodForm()">انصراف</button>
                                        </div>
                                    </div>
                                    <p class="help">زنگ‌ها نباید با هم هم‌پوشانی داشته باشند. هر سال تحصیلی زنگ‌های خودش را دارد.</p>
                                </form>
                            </div>

                            @template.layout.empty-state(
                                show = "periods.length === 0 && !busy() && periodsOk",
                                art = "calendar",
                                message = @`برای این سال تحصیلی هنوز زنگی تعریف نشده است. با فرمِ بالا اولین زنگ را بسازید.`)

                            <div class="table-container" x-show="periods.length > 0">
                                <table class="table is-fullwidth is-striped" id="ttPeriodsTable">
                                    <thead>
                                    <tr>
                                        <th scope="col">ردیف</th>
                                        <th scope="col">برچسب</th>
                                        <th scope="col">شروع</th>
                                        <th scope="col">پایان</th>
                                        <th scope="col"><span class="is-sr-only">عملیات</span></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="period in periods" :key="period.id">
                                        <tr>
                                            <td x-text="Jalali.toPersianDigits(period.position)"></td>
                                            <td x-text="period.label"></td>
                                            <td dir="ltr" x-text="TimetableGrid.timeLabel(period.startTime)"></td>
                                            <td dir="ltr" x-text="TimetableGrid.timeLabel(period.endTime)"></td>
                                            <td>
                                                <div class="buttons">
                                                    <button class="button is-light" type="button"
                                                            :aria-label="'ویرایش ' + period.label"
                                                            @click="editPeriod(period)">ویرایش</button>
                                                    <button class="button is-danger is-light" type="button"
                                                            :aria-label="'حذف ' + period.label"
                                                            @click="askDelete('period', period)">حذف</button>
                                                </div>
                                            </td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        <%-- ------------------------------------------------ فعالیت‌ها --%>
                        <div x-show="year && tab === 'activities'" id="ttPanelActivities">
                            <div class="box">
                                <h2 class="title is-6" x-text="activityForm.id === null ? 'فعالیتِ تازه' : 'ویرایش فعالیت'"></h2>
                                <form @submit.prevent="saveActivity" novalidate>
                                    <div class="field is-grouped is-grouped-multiline">
                                        <div class="control is-expanded">
                                            <label class="label" for="ttActivityName">نام فعالیت</label>
                                            <input class="input" id="ttActivityName" type="text" maxlength="128"
                                                   x-model="activityForm.name" :class="{ 'is-danger': activityError }">
                                            <p class="help is-danger" x-show="activityError" x-text="activityError"></p>
                                        </div>
                                        <div class="control app-control-align-end">
                                            <button class="button is-link" type="submit"
                                                    :class="{ 'is-loading': saving }">
                                                <span x-text="activityForm.id === null ? 'افزودن فعالیت' : 'ذخیره‌ی فعالیت'"></span>
                                            </button>
                                            <button class="button" type="button" x-show="activityForm.id !== null"
                                                    @click="cancelActivityEdit()">انصراف</button>
                                        </div>
                                    </div>
                                    <p class="help">
                                        فعالیت‌ها فقط در برنامه‌ی کلاسی می‌آیند و هیچ‌وقت در کارنامه نمره نمی‌گیرند
                                        (مثل ورزش یا نقاشی). فعالیت‌های «پیش‌فرض» را پلتفرم برای همه‌ی مدرسه‌ها نگه می‌دارد.
                                    </p>
                                </form>
                            </div>

                            @template.layout.empty-state(
                                show = "activities.length === 0",
                                art = "list",
                                message = @`هنوز فعالیتی ندارید. با فرمِ بالا اولین فعالیتِ فوق‌برنامه را بسازید.`)

                            <div class="table-container" x-show="activities.length > 0">
                                <table class="table is-fullwidth is-striped" id="ttActivitiesTable">
                                    <thead>
                                    <tr>
                                        <th scope="col">نام</th>
                                        <th scope="col">نوع</th>
                                        <th scope="col"><span class="is-sr-only">عملیات</span></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="activity in activities" :key="activity.id">
                                        <tr>
                                            <td x-text="activity.name"></td>
                                            <td>
                                                <span class="tag is-light" x-show="activity.platformDefault">پیش‌فرض پلتفرم</span>
                                                <span class="tag is-light" x-show="!activity.platformDefault">مدرسه</span>
                                            </td>
                                            <td>
                                                <div class="buttons" x-show="!activity.platformDefault">
                                                    <button class="button is-light" type="button"
                                                            :aria-label="'ویرایش ' + activity.name"
                                                            @click="editActivity(activity)">تغییر نام</button>
                                                    <button class="button is-danger is-light" type="button"
                                                            :aria-label="'حذف ' + activity.name"
                                                            @click="askDelete('activity', activity)">حذف</button>
                                                </div>
                                            </td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </template>

                <%-- ------------------------------------------------ ویرایشگر خانه --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="ttCellTitle"
                     tabindex="-1" x-ref="cellModal" :class="{ 'is-active': cell }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeCell">
                    <div class="modal-background" @click="closeCell"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="ttCellTitle">
                                <span x-text="cell ? cellDayLabel() + '، ' + cell.periodLabel : ''"></span>
                                <span class="is-size-7 has-text-grey" x-text="selectedClassName()"></span>
                            </p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeCell"></button>
                        </header>
                        <template x-if="cell">
                            <section class="modal-card-body">
                                <div class="notification is-danger" role="alert" x-show="cellError" x-text="cellError"></div>

                                <div class="field">
                                    <div class="tabs is-toggle is-toggle-rounded is-small">
                                        <ul>
                                            <li :class="{ 'is-active': cell.kind === 'course' }">
                                                <a href="#" @click.prevent="cell.kind = 'course'">درس</a>
                                            </li>
                                            <li :class="{ 'is-active': cell.kind === 'activity' }">
                                                <a href="#" @click.prevent="cell.kind = 'activity'">فعالیت فوق‌برنامه</a>
                                            </li>
                                        </ul>
                                    </div>
                                </div>

                                <div class="field" x-show="cell.kind === 'course'">
                                    <label class="label" for="ttCellCourse">درس</label>
                                    <div class="select is-fullwidth">
                                        <select id="ttCellCourse" x-model="cell.courseId">
                                            <option value="" disabled>یک درس را انتخاب کنید</option>
                                            <template x-for="course in courses" :key="course.id">
                                                <option :value="course.id" :selected="String(course.id) === cell.courseId" x-text="course.name"></option>
                                            </template>
                                        </select>
                                    </div>
                                    <p class="help" x-show="courses.length === 0">
                                        برای پایه‌ی این کلاس هنوز درسی تعریف نشده است؛ فقط فعالیت‌ها قابل انتخاب‌اند.
                                    </p>
                                </div>

                                <div class="field" x-show="cell.kind === 'activity'">
                                    <label class="label" for="ttCellActivity">فعالیت</label>
                                    <div class="select is-fullwidth">
                                        <select id="ttCellActivity" x-model="cell.activityId">
                                            <option value="" disabled>یک فعالیت را انتخاب کنید</option>
                                            <template x-for="activity in activities" :key="activity.id">
                                                <option :value="activity.id" :selected="String(activity.id) === cell.activityId"
                                                        x-text="activity.platformDefault ? activity.name + ' (پیش‌فرض)' : activity.name"></option>
                                            </template>
                                        </select>
                                    </div>
                                    <p class="help" x-show="activities.length === 0">
                                        هنوز فعالیتی ندارید؛ از تبِ «فعالیت‌ها» بسازید.
                                    </p>
                                </div>

                                <div class="field">
                                    <label class="label" for="ttCellTeacher">معلم (اختیاری)</label>
                                    <div class="select is-fullwidth">
                                        <select id="ttCellTeacher" x-model="cell.teacherId">
                                            <option value="">بدون معلم</option>
                                            <template x-for="teacher in teachers" :key="teacher.userId">
                                                <option :value="teacher.userId" :selected="String(teacher.userId) === cell.teacherId" x-text="teacher.fullName"></option>
                                            </template>
                                        </select>
                                    </div>
                                    <p class="help">یک معلم در یک روز و زنگ فقط در یک کلاس می‌تواند باشد.</p>
                                </div>
                            </section>
                        </template>
                        <footer class="modal-card-foot">
                            <button class="button is-link" type="button" id="ttCellSave"
                                    :class="{ 'is-loading': saving }" @click="saveCell">ذخیره</button>
                            <button class="button is-danger is-light" type="button" id="ttCellClear"
                                    x-show="cell && cell.hasEntry" @click="clearCell">خالی کردن خانه</button>
                            <button class="button" type="button" @click="closeCell">انصراف</button>
                        </footer>
                    </div>
                </div>

                <%-- ------------------------------------------------ تأیید حذف --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="ttConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="ttConfirmTitle">تأیید حذف</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="confirm && confirm.error" x-text="confirm ? confirm.error : ''"></div>
                            <p class="has-text-weight-semibold">
                                آیا از حذف «<span x-text="confirm ? confirm.name : ''"></span>» مطمئن هستید؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                زنگ یا فعالیتی که در برنامه‌ی کلاسی به کار رفته حذف نمی‌شود؛ اول خانه‌هایش را خالی کنید.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-danger" type="button" id="ttConfirmDelete"
                                    :class="{ 'is-loading': saving }" @click="runDelete">حذف قطعی</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
