@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "نمرات", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/member-names.js", "/js/grades.js"), content = @`
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
                                    <div class="control">
                                        <label class="label" for="gradeYear">سال تحصیلی</label>
                                        <input class="input" dir="ltr" id="gradeYear" type="text"
                                               placeholder="۱۴۰۴-۱۴۰۵" x-model="filter.academicYear" required>
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
                                        <button class="button is-link" type="submit" :class="{ 'is-loading': loading }">
                                            بارگذاری
                                        </button>
                                    </div>
                                </div>
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
                                    <div>
                                        <div class="field">
                                            <label class="label is-sr-only" for="gradeStudentSearch">جست‌وجوی نام دانش‌آموز</label>
                                            <div class="control has-icons-start">
                                                <input class="input" id="gradeStudentSearch" type="search" x-model="filterText"
                                                       placeholder="جست‌وجوی نام دانش‌آموز">
                                            </div>
                                        </div>

                                        <div class="table-container">
                                        <table class="table is-fullwidth is-striped">
                                            <thead>
                                            <tr>
                                                <th scope="col">دانش‌آموز</th>
                                                <th scope="col" x-show="selectedScheme() === 'DESCRIPTIVE'">سطح ارزشیابی</th>
                                                <th scope="col" x-show="selectedScheme() === 'NUMERIC'">نمره (از ۲۰)</th>
                                                <th scope="col">نظر (اختیاری)</th>
                                                <th scope="col">آخرین ثبت</th>
                                                <th scope="col"><span class="is-sr-only">عملیات</span></th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <template x-for="row in visibleRoster()" :key="row.studentId">
                                                <tr>
                                                    <td x-text="row.name"></td>
                                                    <td x-show="selectedScheme() === 'DESCRIPTIVE'">
                                                        <div class="select is-small" :class="{ 'is-danger': row.validationError }">
                                                            <select x-model="row.descriptiveLevel"
                                                                    :aria-label="'سطح ارزشیابی ' + row.name">
                                                                <option value="" disabled>انتخاب کنید</option>
                                                                <option value="EXCELLENT">خیلی خوب</option>
                                                                <option value="GOOD">خوب</option>
                                                                <option value="ACCEPTABLE">قابل قبول</option>
                                                                <option value="NEEDS_EFFORT">نیازمند تلاش</option>
                                                            </select>
                                                        </div>
                                                        <p class="help is-danger" x-show="row.validationError" x-text="row.validationError"></p>
                                                    </td>
                                                    <td x-show="selectedScheme() === 'NUMERIC'">
                                                        <input class="input is-small" dir="ltr" type="number"
                                                               min="0" max="20" step="0.25"
                                                               :aria-label="'نمره‌ی ' + row.name"
                                                               :class="{ 'is-danger': row.validationError }"
                                                               x-model="row.numericScore">
                                                        <p class="help is-danger" x-show="row.validationError" x-text="row.validationError"></p>
                                                    </td>
                                                    <td>
                                                        <input class="input is-small" type="text" x-model="row.comment"
                                                               :aria-label="'نظر برای ' + row.name">
                                                    </td>
                                                    <td dir="ltr" x-text="row.recordedAt ? new Date(row.recordedAt).toLocaleDateString('fa-IR') : '—'"></td>
                                                    <td>
                                                        <button class="button is-small is-link" type="button" @click="saveGrade(row)"
                                                                :class="{ 'is-loading': row.saving }">
                                                            ثبت نمره
                                                        </button>
                                                    </td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
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
