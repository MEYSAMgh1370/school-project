@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "کلاس‌های مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/classes.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="classesPage()" @keydown.escape.window="selectedClassId = null">
    <div class="container">
        <h1 class="title is-4">کلاس‌های مدرسه</h1>

        <div class="notification is-warning" x-show="!schoolId">
            برای مدیریت کلاس‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                <div class="box">
                    <h2 class="title is-6">افزودن کلاس جدید</h2>
                    <form @submit.prevent="createClass">
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control is-expanded">
                                <label class="label" for="className">نام کلاس</label>
                                <input class="input" id="className" type="text" x-model="form.name" required>
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
                            <div class="control" style="align-self: flex-end">
                                <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                    افزودن
                                </button>
                            </div>
                        </div>
                    </form>
                </div>

                <div class="table-container">
                <table class="table is-fullwidth is-striped">
                    <thead>
                    <tr>
                        <th>نام کلاس</th>
                        <th>بخش</th>
                        <th>معلم</th>
                        <th>دانش‌آموزان کلاس</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="classItem in classes" :key="classItem.id">
                        <tr>
                            <td x-text="classItem.name"></td>
                            <td x-text="classItem.section || '—'"></td>
                            <td x-text="teacherName(classItem.teacherId)"></td>
                            <td>
                                <button class="button is-small" type="button"
                                        @click="toggleRoster(classItem.id)"
                                        :aria-expanded="selectedClassId === classItem.id ? 'true' : 'false'">
                                    <span x-text="selectedClassId === classItem.id ? 'بستن فهرست' : 'مشاهده دانش‌آموزان'"></span>
                                    <span class="tag is-light ml-2" x-text="rosterFor(classItem.id).length"></span>
                                </button>
                            </td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                </div>

                <div class="notification" x-show="!loading && classes.length === 0">
                    هنوز کلاسی در این مدرسه ثبت نشده است.
                </div>

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
                            <th>نام دانش‌آموز</th>
                            <th>ایمیل</th>
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
            </div>
        </template>
    </div>
</section>
`)
