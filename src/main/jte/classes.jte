@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "کلاس‌های مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/member-names.js", "/js/classes.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1"
         x-data="classesPage()" @keydown.escape.window="selectedClassId = null">
    <div class="container">
        @template.layout.page-header(
            title = "کلاس‌های مدرسه")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت کلاس‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <%-- فرمِ ساخت کلاس فقط برای نقشی با CLASS_MANAGE دیده می‌شود؛ به معلم نشان‌دادنِ فرمی که ثبتش همیشه ۴۰۳
                     می‌دهد گمراه‌کننده بود. این فقط نمایش است؛ مرزِ امنیتی خودِ API است. --%>
                <div class="box" x-cloak x-show="$store.nav.permissions.includes('CLASS_MANAGE')">
                    <h2 class="title is-6">افزودن کلاس جدید</h2>
                    <p class="help" x-show="school" x-text="gradeRangeHelp()"></p>
                    <form @submit.prevent="createClass">
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control is-expanded">
                                <label class="label" for="className">نام کلاس</label>
                                <input class="input" id="className" type="text" x-model="form.name" required>
                            </div>
                            <div class="control">
                                <label class="label" for="classGradeLevel">پایه</label>
                                <input class="input" id="classGradeLevel" type="number" x-model="form.gradeLevel"
                                       :min="school ? school.minGrade : 1" :max="school ? school.maxGrade : 12" required>
                            </div>
                            <div class="control is-expanded">
                                <label class="label" for="classSection">بخش</label>
                                <input class="input" id="classSection" type="text" x-model="form.section">
                            </div>
                            <div class="control">
                                <label class="label" for="classTeacher">معلم</label>
                                <div class="select">
                                    <select id="classTeacher" x-model="form.teacherId">
                                        <option value="">بدون معلم</option>
                                        <template x-for="teacher in teachers" :key="teacher.userId">
                                            <option :value="teacher.userId" x-text="teacher.fullName"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>
                            <div class="control app-control-align-end">
                                <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                    افزودن
                                </button>
                            </div>
                        </div>
                    </form>
                </div>

                <%-- فهرست ردیفی (DESIGN.md §5a): هر کلاس یک ردیف با نام، سال، پایه، بخش و معلم، و دکمه‌ی فهرست
                     دانش‌آموزان با شمارش. --%>
                <template x-if="classes.length > 0">
                    <div class="app-list" role="list">
                        <template x-for="classItem in classes" :key="classItem.id">
                            <div class="app-class-row" role="listitem">
                                <span class="app-avatar" aria-hidden="true" x-text="classItem.name.charAt(0)"></span>
                                <div class="app-class-row__main">
                                    <span class="app-class-row__name" x-text="classItem.name"></span>
                                    <span class="is-size-7 has-text-grey">
                                        <span x-text="yearLabel(classItem.academicYear)"></span>
                                        · پایه <span x-text="Calendar.digits(classItem.gradeLevel)"></span>
                                        <span x-show="classItem.section">· بخش <span x-text="Calendar.digits(classItem.section)"></span></span>
                                    </span>
                                    <span class="is-size-7 has-text-grey" x-text="'معلم: ' + teacherName(classItem.teacherId)"></span>
                                </div>
                                <button class="button is-light" type="button"
                                        @click="toggleRoster(classItem.id)"
                                        :aria-expanded="selectedClassId === classItem.id ? 'true' : 'false'">
                                    <span x-text="selectedClassId === classItem.id ? 'بستن فهرست' : 'مشاهده دانش‌آموزان'"></span>
                                    <span class="tag is-light app-inline-gap" x-text="Calendar.digits(rosterFor(classItem.id).length)"></span>
                                </button>
                            </div>
                        </template>
                    </div>
                </template>

                @template.layout.empty-state(
                    show = "!loading && classes.length === 0",
                    art = "list",
                    message = @`هنوز کلاسی در این مدرسه ثبت نشده است.`)

                <div class="box" x-show="selectedClassId" x-cloak>
                    <div class="level">
                        <div class="level-left">
                            <h2 class="title is-6" x-text="'دانش‌آموزان کلاس: ' + selectedClassName()"></h2>
                        </div>
                        <div class="level-right">
                            <button class="delete" type="button" aria-label="بستن فهرست دانش‌آموزان"
                                    @click="selectedClassId = null"></button>
                        </div>
                    </div>
                    <div class="table-container" x-show="rosterForSelected().length > 0">
                    <table class="table is-fullwidth is-narrow">
                        <thead>
                        <tr>
                            <th scope="col">نام دانش‌آموز</th>
                            <th scope="col">ایمیل</th>
                        </tr>
                        </thead>
                        <tbody>
                        <template x-for="student in rosterForSelected()" :key="student.userId">
                            <tr>
                                <td x-text="student.fullName"></td>
                                <td dir="ltr" x-text="student.email"></td>
                            </tr>
                        </template>
                        </tbody>
                    </table>
                    </div>
                    <p class="has-text-grey" x-show="rosterForSelected().length === 0">
                        دانش‌آموزی در این کلاس ثبت‌نام نشده است.
                    </p>
                </div>
            `)
    </div>
</section>
`)
