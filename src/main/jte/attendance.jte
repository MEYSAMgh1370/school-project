@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «حضور و غیاب» (openspec/changes/attendance.md): معلم/مدیر کلاس و روز را انتخاب می‌کند و غایب‌ها و
     دیرآمدگان را علامت می‌زند؛ اقدامِ اصلی «ثبت حضور و غیاب» در نوارِ چسبیدهٔ پایینِ صفحه است (نه هدر)، تا با
     فهرستِ بلند و روی گوشی نیازی به برگشتن به بالا نباشد، و وقتی هیچ استثنایی علامت نخورده «همه حاضرند - ثبت»
     می‌شود. کنترل‌ها هیچ‌وقت به‌خاطرِ
     مجوز پنهان/غیرفعال نمی‌شوند: نبودِ مجوز یا ویرایشِ روزِ گذشته 403 می‌گیرد و پیامِ سرور در ناحیه‌ی خطا
     می‌آید. روز جلالی با ارقام فارسی نمایش داده می‌شود و ISO به سرور می‌رود (date-input.js). --%>
@template.layout.main(title = "حضور و غیاب",
        scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/list-search.js", "/js/jalali.js", "/js/calendar.js", "/js/date-input.js", "/js/school-api.js", "/js/attendance.js"),
        styles = java.util.List.of("/css/attendance.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="attendancePage()">
    <div class="container">
        @template.layout.page-header(
            title = "حضور و غیاب",
            subtitle = "کلاس و روز را انتخاب کنید و فقط غایب‌ها و دیرآمدگان را علامت بزنید؛ دانش‌آموزِ بدونِ علامت حاضر است.")

        @template.layout.school-scope(
            noSchoolMessage = "برای ثبت حضور و غیاب، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <div x-show="!blocked">
                    <p x-show="loading && !loaded">در حال بارگذاری...</p>

                    @template.layout.empty-state(
                        show = "loaded && !loading && classes.length === 0",
                        art = "people",
                        message = @`کلاسی برای ثبت پیدا نشد. معلم فقط کلاس‌هایی را می‌بیند که در آن‌ها تدریس می‌کند؛ اگر کلاسی به شما نرسیده، مدیر مدرسه باید شما را به کلاس یا برنامه‌ی کلاسی اضافه کند. مدیر می‌تواند از صفحه‌ی <a href="/classes">کلاس‌ها</a> کلاس بسازد.`)

                    <div x-show="classes.length > 0">
                        <div class="box">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="attClass">کلاس</label>
                                    <div class="select">
                                        <select id="attClass" x-model="classId" @change="onClassChange()">
                                            <template x-for="item in classes" :key="item.classId">
                                                <option :value="String(item.classId)" :selected="String(item.classId) === String(classId)" x-text="classLabel(item)"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control">
                                    <label class="label" for="attDay">روز</label>
                                    <input class="input app-input-w-8" id="attDay" type="text" dir="ltr" inputmode="numeric"
                                           autocomplete="off" :placeholder="exampleDate()" x-model="dayText"
                                           :class="{ 'is-danger': dayError }"
                                           @change="onDayInput()" @keydown.enter.prevent="onDayInput()">
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-light" type="button" id="attToday" @click="goToday">امروز</button>
                                </div>
                            </div>
                            <p class="help is-danger" role="alert" id="attDayError" x-show="dayError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="dayError"></span></span></p>
                            <p class="is-size-7 has-text-grey" id="attDayTitle" x-show="!dayError" x-text="dayTitle()"></p>
                        </div>

                        <%-- وضعیتِ همین کلاس-روز: روزِ آینده، روزِ غیردرسی، ثبت‌شده یا هنوز نه --%>
                        <div class="notification is-warning is-light" id="attFutureDay" x-show="roll && futureDay()">
                            روزِ آینده را نمی‌توان ثبت کرد. روز را به امروز یا یکی از روزهای گذشته برگردانید.
                        </div>
                        <div class="notification is-light" id="attNotSchoolDay" x-show="roll && !roll.recordable && !futureDay()">
                            این روز، روز درسیِ مدرسه نیست (آخرِ هفته یا تعطیلیِ ثبت‌شده در تقویم مدرسه)، پس حضور و غیابی برایش
                            ثبت نمی‌شود. روزِ دیگری را انتخاب کنید.
                        </div>
                        <div class="tags" x-show="roll && roll.recordable">
                            <span class="tag is-success is-light" id="attTaken" x-show="roll && roll.taken">حضور و غیابِ این کلاس در این روز ثبت شده است</span>
                            <span class="tag is-warning is-light" id="attNotTaken" x-show="roll && !roll.taken">هنوز ثبت نشده؛ تا غایب‌ها را علامت نزنید همه حاضر ثبت می‌شوند</span>
                        </div>

                        <p x-show="rollLoading">در حال بارگذاری فهرستِ کلاس...</p>

                        @template.layout.empty-state(
                            show = "roll && roll.students.length === 0",
                            art = "people",
                            message = @`در این کلاس هنوز دانش‌آموزی ثبت‌نام نشده است. از صفحه‌ی <a href="/students">دانش‌آموزان</a> دانش‌آموز را در کلاس ثبت‌نام کنید.`)

                        <div class="box" id="attRosterBox" x-show="rows.length > 0">
                            <h2 class="title is-6">
                                دانش‌آموزان
                                <span class="tag is-light" x-text="Calendar.digits(rows.length)"></span>
                            </h2>
                            <%-- جست‌وجوی نام، فقط برای کلاسِ بلند: غایبِ یک کلاسِ ۲۰ نفره بدونِ اسکرول پیدا شود. --%>
                            <div class="field" x-show="rows.length > 8">
                                <label class="label is-sr-only" for="attSearch">جست‌وجوی دانش‌آموز</label>
                                <input class="input" id="attSearch" type="search" x-model="query"
                                       placeholder="جست‌وجوی نام دانش‌آموز" autocomplete="off">
                            </div>
                            <p class="is-size-7 has-text-grey mb-2" id="attSummary">
                                غایب: <span x-text="Calendar.digits(countOf('ABSENT'))"></span>
                                · موجه: <span x-text="Calendar.digits(excusedCount())"></span>
                                · تأخیر: <span x-text="Calendar.digits(countOf('LATE'))"></span>
                            </p>
                            <ul class="app-att-list" id="attRoster">
                                <template x-for="item in visibleRows()" :key="item.row.studentId">
                                    <li class="app-att-row" x-data="{ row: item.row, index: item.index }">
                                        <span class="app-att-name">
                                            <span class="is-size-7 has-text-grey" x-text="Calendar.digits(index + 1)"></span>
                                            <span x-text="row.studentName"></span>
                                        </span>
                                        <div class="buttons has-addons app-att-status" role="group" :aria-label="'وضعیت ' + row.studentName">
                                            <template x-for="status in statuses" :key="status.code">
                                                <button type="button" class="button"
                                                        :class="{ 'is-link is-selected': row.status === status.code }"
                                                        :aria-pressed="row.status === status.code ? 'true' : 'false'"
                                                        @click="setStatus(row, status.code)" x-text="status.label"></button>
                                            </template>
                                        </div>
                                        <div class="app-att-extra" x-show="row.status !== 'PRESENT'">
                                            <label class="checkbox" x-show="row.status === 'ABSENT'">
                                                <input class="app-inline-gap-xs" type="checkbox" x-model="row.excused">
                                                موجه
                                            </label>
                                            <div class="app-att-note">
                                                <input class="input" type="text" maxlength="255"
                                                       placeholder="یادداشت (اختیاری)" :aria-label="'یادداشت ' + row.studentName"
                                                       x-model="row.note" :class="{ 'is-danger': noteErrors[row.studentId] }">
                                                <p class="help is-danger" role="alert" x-show="noteErrors[row.studentId]"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="noteErrors[row.studentId]"></span></span></p>
                                            </div>
                                        </div>
                                    </li>
                                </template>
                            </ul>
                            <p class="mt-3" id="attNoMatch" x-show="query.trim() !== '' && visibleRows().length === 0">
                                دانش‌آموزی با این نام پیدا نشد.
                            </p>
                        </div>

                        <%-- نوارِ چسبیده: همیشه در دسترس و تنها دکمه‌ی اصلیِ صفحه. بدونِ استثنا «همه حاضرند - ثبت». --%>
                        <div class="app-att-bar" id="attBar" x-cloak x-show="rows.length > 0 && rollRecordable()">
                            <p class="app-att-bar-summary" id="attBarSummary">
                                غایب: <span x-text="Calendar.digits(countOf('ABSENT'))"></span>
                                · تأخیر: <span x-text="Calendar.digits(countOf('LATE'))"></span>
                            </p>
                            <button class="button is-link" type="button" id="attendanceSave"
                                    :class="{ 'is-loading': saving }" :disabled="saving" @click="save"><span x-text="saveLabel()">ثبت حضور و غیاب</span></button>
                        </div>
                    </div>
                </div>
            `)
    </div>
</section>
`)
