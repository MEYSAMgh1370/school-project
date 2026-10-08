@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «تقویم مدرسه» (openspec/changes/attendance.md فاز ۰): روزهای تعطیلِ مدرسه. اقدامِ اصلیِ صفحه
     «افزودن روز تعطیل» در inline-end هدر است؛ حذف با modal تأیید (بدون confirm مرورگر). روز در ورودی جلالی
     با ارقام فارسی است و روی سیم ISO می‌رود (date-input.js). --%>
@template.layout.main(title = "تقویم مدرسه",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/date-input.js", "/js/school-api.js", "/js/modal-focus.js", "/js/school-calendar.js"),
        content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="schoolCalendarPage()">
    <div class="container">
        <div class="level is-mobile">
            <div class="level-left">
                <div>
                    @template.layout.page-header(
                        title = "تقویم مدرسه",
                        subtitle = "روزهای تعطیلِ مدرسه؛ در این روزها حضور و غیاب گرفته نمی‌شود.")
                </div>
            </div>
            <div class="level-right" x-cloak x-show="schoolId">
                <button class="button is-link" type="button" id="calendarAdd" @click="openAdd">افزودن روز تعطیل</button>
            </div>
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت تقویم، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <div x-show="!blocked">
                    <div class="box">
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control app-control-align-end">
                                <button class="button is-light" type="button" id="calendarPrev" @click="previousMonth">ماه قبل</button>
                            </div>
                            <div class="control">
                                <label class="label" for="calendarMonth">ماه</label>
                                <div class="select">
                                    <select id="calendarMonth" x-model.number="month" @change="onMonthChange()">
                                        <template x-for="(name, index) in monthNames" :key="index">
                                            <option :value="index + 1" :selected="index + 1 === month" x-text="name"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>
                            <div class="control">
                                <label class="label" for="calendarYear">سال</label>
                                <input class="input app-input-w-6" id="calendarYear" type="text" inputmode="numeric" maxlength="4"
                                       x-model="yearText" @change="onYearChange()" @keydown.enter.prevent="onYearChange()">
                            </div>
                            <div class="control app-control-align-end">
                                <button class="button is-light" type="button" id="calendarNext" @click="nextMonth">ماه بعد</button>
                            </div>
                        </div>
                    </div>

                    <h2 class="title is-5" id="calendarMonthTitle" x-text="monthTitle()"></h2>

                    <p x-show="loading && !loaded">در حال بارگذاری...</p>

                    @template.layout.empty-state(
                        show = "loaded && !loading && entries.length === 0",
                        art = "calendar",
                        message = @`در این ماه روز تعطیلی ثبت نشده است. با «افزودن روز تعطیل» تعطیلی‌های رسمی و روزهایی را که مدرسه بسته است ثبت کنید تا در آن روزها حضور و غیاب گرفته نشود. آخرِ هفته‌ی مدرسه خودکار تعطیل است و نیازی به ثبت ندارد.`)

                    <div class="table-container" x-show="entries.length > 0">
                        <table class="table is-fullwidth is-striped" id="calendarTable">
                            <caption class="is-sr-only">روزهای تعطیلِ ماه</caption>
                            <thead>
                            <tr>
                                <th scope="col">روز</th>
                                <th scope="col">تاریخ</th>
                                <th scope="col">عنوان</th>
                                <th scope="col"><span class="is-sr-only">عملیات</span></th>
                            </tr>
                            </thead>
                            <tbody>
                            <template x-for="entry in entries" :key="entry.id">
                                <tr>
                                    <td x-text="dayName(entry.day)"></td>
                                    <td x-text="dayShort(entry.day)"></td>
                                    <td x-text="entry.title"></td>
                                    <td>
                                        <button class="button is-danger is-light" type="button"
                                                :aria-label="'حذف روز تعطیل ' + entry.title"
                                                @click="askRemove(entry)">حذف</button>
                                    </td>
                                </tr>
                            </template>
                            </tbody>
                        </table>
                    </div>
                </div>

                <%-- ------------------------------------------------ افزودن روز تعطیل --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="calendarAddTitle"
                     tabindex="-1" x-ref="addModal" :class="{ 'is-active': add }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeAdd">
                    <div class="modal-background" @click="closeAdd"></div>
                    <template x-if="add">
                        <form class="modal-card" @submit.prevent="submitAdd" novalidate>
                            <header class="modal-card-head">
                                <p class="modal-card-title" id="calendarAddTitle">افزودن روز تعطیل</p>
                                <button class="delete" type="button" aria-label="بستن" @click="closeAdd"></button>
                            </header>
                            <section class="modal-card-body">
                                <div class="notification is-danger" role="alert" x-show="add.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="add.error"></span></span></div>

                                <div class="field">
                                    <label class="label" for="calendarDate">تاریخ</label>
                                    <div class="control">
                                        <input class="input" id="calendarDate" type="text" dir="ltr" inputmode="numeric"
                                               autocomplete="off" :placeholder="exampleDate()"
                                               x-model="add.date" :class="{ 'is-danger': add.dateError }">
                                    </div>
                                    <p class="help is-danger" role="alert" x-show="add.dateError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="add.dateError"></span></span></p>
                                    <p class="help" x-show="!add.dateError">تاریخ را به شکل سال/ماه/روز بنویسید؛ ارقام فارسی هم پذیرفته می‌شود.</p>
                                </div>

                                <div class="field">
                                    <label class="label" for="calendarTitleInput">عنوان</label>
                                    <div class="control">
                                        <input class="input" id="calendarTitleInput" type="text" maxlength="128"
                                               x-model="add.title" :class="{ 'is-danger': add.titleError }">
                                    </div>
                                    <p class="help is-danger" role="alert" x-show="add.titleError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="add.titleError"></span></span></p>
                                </div>
                            </section>
                            <footer class="modal-card-foot">
                                <button class="button is-link" type="submit" id="calendarAddSubmit"
                                        :class="{ 'is-loading': saving }">افزودن</button>
                                <button class="button" type="button" @click="closeAdd">انصراف</button>
                            </footer>
                        </form>
                    </template>
                </div>

                <%-- ------------------------------------------------ تأیید حذف --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="calendarConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="calendarConfirmTitle">تأیید حذف</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="confirm && confirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="confirm ? confirm.error : ''"></span></span></div>
                            <p class="has-text-weight-semibold">
                                آیا از حذف روز تعطیلِ «<span x-text="confirm ? confirm.title : ''"></span>»
                                (<span x-text="confirm ? dayShort(confirm.day) : ''"></span>) مطمئن هستید؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                بعد از حذف، در آن روز دوباره می‌توان حضور و غیاب گرفت.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-danger" type="button" id="calendarConfirmDelete"
                                    :class="{ 'is-loading': saving }" @click="runRemove">حذف قطعی</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
