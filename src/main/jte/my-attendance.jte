@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «غیبت‌های من» (openspec/changes/attendance.md «Excuses»): دانش‌آموز غیبت و تأخیرِ خودش را می‌بیند و والد
     غیبت و تأخیرِ فرزندانش را و برای هر روزِ غایب عذر می‌فرستد. اقدامِ اصلیِ صفحه «نمایش» در inline-end هدر است؛
     «ارسال عذر» اقدامِ اصلیِ modal است (یک modal در هر لحظه). بازه در ورودی جلالی با ارقام فارسی است و ISO به
     سرور می‌رود (date-input.js). خودسرویس است: هیچ کنترلی با مجوز پنهان نمی‌شود؛ دکمه‌ی عذر فقط روی روزِ غایبِ
     بدونِ عذرِ تصمیم‌گرفته‌شده در ردیفِ فرزند می‌آید (وضعیت، نه مجوز) و سرور هر ارسالِ نابه‌جا را رد می‌کند. --%>
@template.layout.main(title = "غیبت‌های من",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/date-input.js", "/js/school-api.js", "/js/modal-focus.js", "/js/my-attendance.js"),
        content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="myAttendancePage()">
    <div class="container">
        <div class="level is-mobile">
            <div class="level-left">
                <div>
                    @template.layout.page-header(
                        title = "غیبت‌های من",
                        subtitle = "غیبت و تأخیرِ شما یا فرزندانتان در بازه‌ی انتخابی؛ والد می‌تواند برای هر غیبت عذر بفرستد.")
                </div>
            </div>
            <div class="level-right" x-cloak x-show="schoolId">
                <button class="button is-link" type="button" id="myAttendanceRun"
                        :class="{ 'is-loading': loading }" @click="load">نمایش</button>
            </div>
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای دیدنِ غیبت‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <div x-show="!blocked">
                    <div class="box">
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control">
                                <label class="label" for="myAttFrom">از تاریخ</label>
                                <input class="input app-input-w-8" id="myAttFrom" type="text" dir="ltr" inputmode="numeric"
                                       autocomplete="off" :placeholder="exampleDate()" x-model="fromText"
                                       :class="{ 'is-danger': fromError }" @keydown.enter.prevent="load()">
                                <p class="help is-danger" id="myAttFromError" x-show="fromError" x-text="fromError"></p>
                            </div>
                            <div class="control">
                                <label class="label" for="myAttTo">تا تاریخ</label>
                                <input class="input app-input-w-8" id="myAttTo" type="text" dir="ltr" inputmode="numeric"
                                       autocomplete="off" :placeholder="exampleDate()" x-model="toText"
                                       :class="{ 'is-danger': toError }" @keydown.enter.prevent="load()">
                                <p class="help is-danger" id="myAttToError" x-show="toError" x-text="toError"></p>
                            </div>
                        </div>
                        <p class="help">بازه حداکثر ۳۶۶ روز است؛ ارقام فارسی هم پذیرفته می‌شود.</p>
                    </div>

                    <p x-show="loading && !loaded">در حال بارگذاری...</p>

                    @template.layout.empty-state(
                        show = "loaded && !loading && !hasAnyDay()",
                        art = "calendar",
                        message = @`در این بازه غیبت یا تأخیری ثبت نشده است. بازه را بزرگ‌تر کنید؛ غیبت و تأخیر را معلم‌ها در حضور و غیابِ روزانه ثبت می‌کنند.`)

                    <div id="myAttendanceList" x-show="loaded && hasAnyDay()">
                        <template x-for="student in students" :key="student.studentId">
                            <article class="box">
                                <h2 class="title is-5 mb-3">
                                    <span x-text="student.studentName"></span>
                                    <span class="tag is-light" x-show="student.own">خودم</span>
                                </h2>
                                <p class="has-text-grey" x-show="student.days.length === 0">
                                    در این بازه برای این دانش‌آموز غیبت یا تأخیری ثبت نشده است.
                                </p>
                                <div class="table-container" x-show="student.days.length > 0">
                                    <table class="table is-fullwidth is-striped">
                                        <caption class="is-sr-only" x-text="'غیبت و تأخیرِ ' + student.studentName"></caption>
                                        <thead>
                                        <tr>
                                            <th scope="col">روز</th>
                                            <th scope="col">وضعیت</th>
                                            <th scope="col">عذر</th>
                                            <th scope="col"><span class="is-sr-only">عملیات</span></th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <template x-for="day in student.days" :key="day.day">
                                            <tr>
                                                <td x-text="dayLabel(day.day)"></td>
                                                <td>
                                                    <span class="tag is-light" x-text="statusLabel(day.status)"></span>
                                                    <span class="tag is-success is-light" x-show="day.excused">موجه</span>
                                                </td>
                                                <td>
                                                    <template x-if="day.excuse !== null">
                                                        <span>
                                                            <span class="tag" :class="excuseTag(day.excuse.status)"
                                                                  x-text="excuseLabel(day.excuse.status)"></span>
                                                            <span class="is-size-7 has-text-grey" x-show="day.excuse.note"
                                                                  x-text="day.excuse.note"></span>
                                                        </span>
                                                    </template>
                                                    <template x-if="day.excuse === null">
                                                        <span class="has-text-grey" aria-label="بدون عذر">—</span>
                                                    </template>
                                                </td>
                                                <td>
                                                    <button class="button is-light" type="button"
                                                            x-show="canExcuse(student, day)"
                                                            :aria-label="excuseActionLabel(day) + ' برای ' + student.studentName + '، ' + dayLabel(day.day)"
                                                            @click="openExcuse(student, day)" x-text="excuseActionLabel(day)"></button>
                                                </td>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                </div>
                            </article>
                        </template>
                    </div>
                </div>

                <%-- ------------------------------------------------ ارسال عذر --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="myAttExcuseTitle"
                     tabindex="-1" x-ref="excuseModal" :class="{ 'is-active': excuse }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeExcuse">
                    <div class="modal-background" @click="closeExcuse"></div>
                    <template x-if="excuse">
                        <form class="modal-card" @submit.prevent="submitExcuse" novalidate>
                            <header class="modal-card-head">
                                <p class="modal-card-title" id="myAttExcuseTitle">عذر غیبت</p>
                                <button class="delete" type="button" aria-label="بستن" @click="closeExcuse"></button>
                            </header>
                            <section class="modal-card-body">
                                <div class="notification is-danger" role="alert" x-show="excuse.error" x-text="excuse.error"></div>

                                <p class="mb-3">
                                    <span x-text="excuse.studentName"></span>
                                    ·
                                    <span x-text="dayLabel(excuse.day)"></span>
                                </p>

                                <div class="field">
                                    <label class="label" for="myAttNote">دلیلِ غیبت</label>
                                    <div class="control">
                                        <textarea class="textarea" id="myAttNote" rows="4" x-model="excuse.note"
                                                  :class="{ 'is-danger': excuse.noteError }"></textarea>
                                    </div>
                                    <p class="help is-danger" x-show="excuse.noteError" x-text="excuse.noteError"></p>
                                    <p class="help" id="myAttNoteCounter">
                                        <span x-text="Calendar.digits(noteLength())"></span> از <span x-text="Calendar.digits(maxNote)"></span> نویسه
                                    </p>
                                </div>
                            </section>
                            <footer class="modal-card-foot">
                                <button class="button is-link" type="submit" id="myAttExcuseSubmit"
                                        :class="{ 'is-loading': saving }">ارسال عذر</button>
                                <button class="button" type="button" @click="closeExcuse">انصراف</button>
                            </footer>
                        </form>
                    </template>
                </div>
            `)
    </div>
</section>
`)
