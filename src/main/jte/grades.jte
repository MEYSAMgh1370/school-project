@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "نمرات", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/member-names.js", "/js/grades.js"),
        styles = java.util.List.of("/css/grades.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="gradesPage()">
    <div class="container">
        @template.layout.page-header(
            title = "نمرات")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت نمرات، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <template x-if="!blocked">
                    <div>
                        <div class="box">
                            <h2 class="title is-6">انتخاب کلاس و بازه‌ی تحصیلی</h2>
                            <form @submit.prevent="load">
                                <div class="field is-grouped is-grouped-multiline">
                                    <div class="control is-expanded">
                                        <label class="label" for="gradeClass">کلاس</label>
                                        <div class="select is-fullwidth">
                                            <select id="gradeClass" x-model="filter.classId" required>
                                                <option value="" disabled>یک کلاس را انتخاب کنید</option>
                                                <template x-for="classItem in classes" :key="classItem.id">
                                                    <option :value="classItem.id" x-text="classItem.name"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <%-- درس: «نمرهٔ کل» (پیش‌فرض، رفتار پیشین) یا یکی از درس‌های سراسریِ
                                         هم‌پایه‌ی کلاس. با تغییرِ درس پس از بارگذاری، جدول همان‌جا دوباره
                                         بارگذاری می‌شود تا ردیف‌ها همیشه با درسِ نشان‌داده‌شده یکی باشند. --%>
                                    <div class="control">
                                        <label class="label" for="gradeCourse">درس</label>
                                        <div class="select">
                                            <select id="gradeCourse" x-model="filter.courseId" :disabled="!filter.classId"
                                                    aria-describedby="gradeCourseHelp" @change="if (loaded || loading) load()">
                                                <option value="">نمره‌ی کل</option>
                                                <template x-for="course in courses" :key="course.id">
                                                    <option :value="course.id" x-text="course.name"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="gradeYear">سال تحصیلی</label>
                                        <%-- مدیر هر سالِ فهرست را می‌بیند؛ بقیه فقط سالِ جاری؛ بدونِ هیچ سالی (مدرسه سال
                                             جاری ندارد و کاربر مدیر نیست) فیلدِ متنی می‌ماند و سرور سالِ ناشناخته را رد می‌کند --%>
                                        <div class="select is-fullwidth" x-cloak x-show="years.length > 0">
                                            <select id="gradeYear" dir="ltr" x-model="filter.academicYear" :disabled="years.length === 0">
                                                <template x-for="year in years" :key="year">
                                                    <option :value="year" x-text="year"></option>
                                                </template>
                                            </select>
                                        </div>
                                        <input class="input" dir="ltr" id="gradeYearText" type="text" aria-label="سال تحصیلی"
                                               placeholder="۱۴۰۴-۱۴۰۵" x-model="filter.academicYear"
                                               x-cloak x-show="years.length === 0" :required="years.length === 0">
                                    </div>
                                    <div class="control">
                                        <label class="label" for="gradeTerm">نوبت</label>
                                        <div class="select">
                                            <select id="gradeTerm" x-model="filter.term">
                                                <option value="FIRST">نوبت اول</option>
                                                <option value="SECOND">نوبت دوم</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="control app-control-align-end">
                                        <%-- یک دکمه‌ی پُر در هر نما (DESIGN.md §4.2): «بارگذاری» تا وقتی جدول نیامده اقدامِ
                                             اصلی است؛ بعد از آن «ثبت همه» اصلی می‌شود و این دکمه فرعی --%>
                                        <button class="button" type="submit"
                                                :class="[loading ? 'is-loading' : '', loaded ? 'is-light' : 'is-primary']">
                                            بارگذاری
                                        </button>
                                    </div>
                                </div>
                                <p class="help" id="gradeCourseHelp" x-show="filter.classId && courses.length === 0">
                                    برای پایه‌ی این کلاس هنوز درسی تعریف نشده است؛ فقط «نمره‌ی کل» قابل ثبت است.
                                </p>
                            </form>
                        </div>

                        <template x-if="loaded">
                            <div>
                                @template.layout.empty-state(
                                    show = "rosterStudents().length === 0",
                                    art = "people",
                                    message = @`این کلاس هنوز دانش‌آموزی ندارد. از صفحه‌ی
<a href="/students">دانش‌آموزان</a> دانش‌آموز اضافه کنید.`)

                                <template x-if="rosterStudents().length > 0">
                                    <div class="app-grades-entry">
                                        <p class="mb-3">
                                            <span class="is-size-7 has-text-grey">نمره‌های:</span>
                                            <span class="tag is-info is-light" id="gradeCourseLabel" x-text="courseLabel()"></span>
                                        </p>

                                        <%-- مرور پس از ثبتِ ناموفق (DESIGN.md §5a): خلاصه با شمارش، چه اشتباهی شده و چه باید کرد --%>
                                        <div class="notification is-danger" role="alert" x-show="reviewSummary"
                                             x-text="reviewSummary"></div>

                                        <%-- نمای فهرست --%>
                                        <div x-show="view === 'list'">
                                            <div class="field">
                                                <label class="label is-sr-only" for="gradeStudentSearch">جست‌وجوی نام دانش‌آموز</label>
                                                <div class="control has-icons-start">
                                                    <input class="input" id="gradeStudentSearch" type="search" x-model="filterText"
                                                           placeholder="جست‌وجوی نام دانش‌آموز">
                                                </div>
                                            </div>

                                            <%-- chip ها «کجا بروم» را جواب می‌دهند، نه فقط «کجا هستم» (§5a). برای فهرست‌های
                                                 بلند (بیش از ۲۵) یا وقتی موردِ نیازمند اصلاح هست --%>
                                            <div class="app-chips" role="group" aria-label="فیلتر فهرست" x-show="showChips()">
                                                <template x-for="item in chips()" :key="item.key">
                                                    <button class="app-chip" type="button" :class="{ 'is-selected': chip === item.key }"
                                                            :aria-pressed="chip === item.key ? 'true' : 'false'" @click="selectChip(item.key)">
                                                        <span x-text="item.label"></span>
                                                        (<span x-text="fa(item.count)"></span>)
                                                    </button>
                                                </template>
                                            </div>

                                            <p class="help mb-2">نام دانش‌آموز را بزنید تا ورود نمره یکی‌یکی (و با افزودن توضیح) باز شود.</p>

                                            <div class="app-list" role="list">
                                                <template x-for="row in shownRoster()" :key="row.studentId">
                                                    <div class="app-grade-row" role="listitem">
                                                        <span class="app-avatar" aria-hidden="true" x-text="row.name.charAt(0)"></span>
                                                        <div class="app-grade-row__main">
                                                            <button class="app-grade-row__name" type="button" @click="startFocus(row)"
                                                                    x-text="row.name"></button>
                                                            <span class="tag" :class="rowStatus(row).cls" x-text="rowStatus(row).label"></span>
                                                            <span class="tag is-light" x-show="absenceLabel(row)" x-text="absenceLabel(row)"></span>
                                                            <p class="is-size-7 has-text-grey" x-show="row.previous">
                                                                نوبت اول: <span x-text="row.previous"></span>
                                                            </p>
                                                            <p class="help is-danger" x-show="row.validationError" x-text="row.validationError"></p>
                                                            <p class="help" x-show="row.suggestion">
                                                                شاید منظورتان <span x-text="faNumber(row.suggestion)"></span> بوده؟
                                                                <button class="button is-light is-small" type="button"
                                                                        @click="acceptSuggestion(row)">بله، اصلاح کن</button>
                                                            </p>
                                                        </div>
                                                        <div class="app-grade-row__value">
                                                            <div class="select" :class="{ 'is-danger': row.validationError }"
                                                                 x-show="selectedScheme() === 'DESCRIPTIVE'">
                                                                <select x-model="row.descriptiveLevel" @change="row.validationError = ''"
                                                                        :aria-label="'سطح ارزشیابی ' + row.name">
                                                                    <option value="" disabled>انتخاب کنید</option>
                                                                    <option value="EXCELLENT">خیلی خوب</option>
                                                                    <option value="GOOD">خوب</option>
                                                                    <option value="ACCEPTABLE">قابل قبول</option>
                                                                    <option value="NEEDS_EFFORT">نیازمند تلاش</option>
                                                                </select>
                                                            </div>
                                                            <%-- ورودیِ متنی با inputmode اعشاری: کنترلِ number رقم‌های فارسی را قبل از JS رد می‌کرد --%>
                                                            <input class="input app-value" dir="ltr" type="text" inputmode="decimal"
                                                                   autocomplete="off" x-show="selectedScheme() === 'NUMERIC'"
                                                                   :aria-label="'نمره‌ی ' + row.name"
                                                                   :class="{ 'is-danger': row.validationError }"
                                                                   x-model="row.numericScore" @input="row.validationError = ''; row.suggestion = ''">
                                                        </div>
                                                        <button class="button is-light" type="button" @click="saveGrade(row)"
                                                                :class="{ 'is-loading': row.saving }">
                                                            ثبت
                                                        </button>
                                                    </div>
                                                </template>
                                            </div>

                                            <%-- نوارِ اقدام در پایین (انگشت می‌رسد): تنها دکمه‌ی پُرِ این نما --%>
                                            <div class="app-action-bar">
                                                <button class="button is-primary" type="button" @click="primaryAction()"
                                                        :class="{ 'is-loading': savingAll }" x-text="reviewLabel()"></button>
                                            </div>
                                        </div>

                                        <%-- نمای تمرکز: یک دانش‌آموز، مقدارِ درشت، نوارِ پایین درست بالای کیبورد --%>
                                        <div x-show="view === 'focus'" x-cloak>
                                            <template x-if="focusRow()">
                                                <div class="app-focus">
                                                    <button class="button is-light is-small mb-3" type="button" @click="exitFocus()">
                                                        <span class="app-dir-glyph">›</span>&nbsp;بازگشت به فهرست
                                                    </button>
                                                    <div class="app-focus__who">
                                                        <span class="app-avatar app-avatar--lg" aria-hidden="true" x-text="focusRow().name.charAt(0)"></span>
                                                        <div>
                                                            <h3 class="title is-6 mb-1" x-text="focusRow().name"></h3>
                                                            <span class="tag" :class="rowStatus(focusRow()).cls"
                                                                  x-text="rowStatus(focusRow()).label"></span>
                                                            <span class="tag is-light" x-show="absenceLabel(focusRow())"
                                                                  x-text="absenceLabel(focusRow())"></span>
                                                        </div>
                                                    </div>

                                                    <div class="field mt-4" x-show="selectedScheme() === 'NUMERIC'">
                                                        <label class="label" for="gradeFocusField">نمره (از ۲۰)</label>
                                                        <input class="input app-value is-focused" id="gradeFocusField" dir="ltr" type="text"
                                                               inputmode="decimal" autocomplete="off"
                                                               :class="{ 'is-danger': focusRow().validationError }"
                                                               x-model="focusRow().numericScore"
                                                               @input="focusRow().validationError = ''; focusRow().suggestion = ''"
                                                               @keydown.enter.prevent="goNext()">
                                                    </div>
                                                    <div class="field mt-4" x-show="selectedScheme() === 'DESCRIPTIVE'">
                                                        <label class="label" for="gradeFocusLevel">سطح ارزشیابی</label>
                                                        <div class="select is-fullwidth" :class="{ 'is-danger': focusRow().validationError }">
                                                            <select id="gradeFocusLevel" x-model="focusRow().descriptiveLevel"
                                                                    @change="focusRow().validationError = ''">
                                                                <option value="" disabled>انتخاب کنید</option>
                                                                <option value="EXCELLENT">خیلی خوب</option>
                                                                <option value="GOOD">خوب</option>
                                                                <option value="ACCEPTABLE">قابل قبول</option>
                                                                <option value="NEEDS_EFFORT">نیازمند تلاش</option>
                                                            </select>
                                                        </div>
                                                    </div>
                                                    <p class="help is-danger" x-show="focusRow().validationError"
                                                       x-text="focusRow().validationError"></p>
                                                    <p class="help" x-show="focusRow().suggestion">
                                                        شاید منظورتان <span x-text="faNumber(focusRow().suggestion)"></span> بوده؟
                                                        <button class="button is-light is-small" type="button"
                                                                @click="acceptSuggestion(focusRow())">بله، اصلاح کن</button>
                                                    </p>

                                                    <div class="field mt-4">
                                                        <label class="label" for="gradeFocusComment">نظر (اختیاری)</label>
                                                        <input class="input" id="gradeFocusComment" type="text" x-model="focusRow().comment">
                                                    </div>
                                                    <p class="is-size-7 has-text-grey" x-show="focusRow().previous">
                                                        نوبت اول: <span x-text="focusRow().previous"></span>
                                                    </p>
                                                    <p class="is-size-7 has-text-grey">
                                                        آخرین ثبت:
                                                        <span dir="ltr" x-text="focusRow().recordedAt ? Calendar.formatDate(focusRow().recordedAt) : '—'"></span>
                                                    </p>
                                                </div>
                                            </template>

                                            <div class="app-action-bar app-action-bar--keyboard"
                                                 :style="'inset-block-end: ' + keyboardInset + 'px'">
                                                <button class="button is-light" type="button" x-show="prevRow()" @click="goPrev()">قبلی</button>
                                                <button class="button is-light" type="button" @click="skip()">رد کردن</button>
                                                <button class="button is-primary" type="button" @click="goNext()"
                                                        :class="{ 'is-loading': savingAll }" x-text="nextLabel()"></button>
                                            </div>
                                        </div>
                                    </div>
                                </template>
                            </div>
                        </template>

                        <div class="notification" x-show="!loading && !loaded">
                            یک کلاس، سال تحصیلی و نوبت را انتخاب کنید و «بارگذاری» را بزنید.
                        </div>
                    </div>
                </template>
            `)
    </div>
</section>
`)
