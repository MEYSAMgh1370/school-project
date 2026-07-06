@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "کلاس‌های مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/classes.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="classesPage()">
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

                <table class="table is-fullwidth is-striped">
                    <thead>
                    <tr>
                        <th>نام کلاس</th>
                        <th>بخش</th>
                        <th>معلم</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="classItem in classes" :key="classItem.id">
                        <tr>
                            <td x-text="classItem.name"></td>
                            <td x-text="classItem.section || '—'"></td>
                            <td x-text="teacherName(classItem.teacherId)"></td>
                        </tr>
                    </template>
                    </tbody>
                </table>

                <div class="notification" x-show="!loading && classes.length === 0">
                    هنوز کلاسی در این مدرسه ثبت نشده است.
                </div>
            </div>
        </template>
    </div>
</section>
`)
