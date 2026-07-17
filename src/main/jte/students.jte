@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "دانش‌آموزان مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/students.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="studentsPage()">
    <div class="container">
        <h1 class="title is-4">دانش‌آموزان مدرسه</h1>

        <div class="notification is-warning" x-show="!schoolId">
            برای مدیریت دانش‌آموزان، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                <div class="notification is-warning" x-show="!loading && classes.length === 0">
                    ابتدا باید حداقل یک کلاس بسازید (صفحه‌ی کلاس‌ها).
                </div>

                <div class="box" x-show="classes.length > 0">
                    <h2 class="title is-6">ثبت‌نام دانش‌آموز جدید</h2>
                    <form @submit.prevent="enrollStudent">
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control is-expanded">
                                <label class="label" for="studentEmail">ایمیل</label>
                                <input class="input" dir="ltr" id="studentEmail" type="email" x-model="form.email" required>
                            </div>
                            <div class="control is-expanded">
                                <label class="label" for="studentFullName">نام کامل</label>
                                <input class="input" id="studentFullName" type="text" x-model="form.fullName" required>
                            </div>
                            <div class="control">
                                <label class="label" for="studentClass">کلاس</label>
                                <div class="select">
                                    <select id="studentClass" x-model="form.classId" required>
                                        <option value="">انتخاب کلاس</option>
                                        <template x-for="classItem in classes" :key="classItem.id">
                                            <option :value="classItem.id" x-text="classItem.name"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>
                            <div class="control" style="align-self: flex-end">
                                <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                    ثبت‌نام
                                </button>
                            </div>
                        </div>
                    </form>
                </div>

                <div class="table-container">
                <table class="table is-fullwidth is-striped">
                    <thead>
                    <tr>
                        <th>نام دانش‌آموز</th>
                        <th>ایمیل</th>
                        <th>کلاس</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="student in students" :key="student.studentId">
                        <tr>
                            <td x-text="studentName(student.userId)"></td>
                            <td dir="ltr" x-text="studentEmail(student.userId)"></td>
                            <td x-text="className(student.classId)"></td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                </div>

                <div class="notification" x-show="!loading && students.length === 0">
                    هنوز دانش‌آموزی در این مدرسه ثبت‌نام نشده است.
                </div>
            </div>
        </template>
    </div>
</section>
`)
