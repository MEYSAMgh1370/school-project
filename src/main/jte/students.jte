@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "دانش‌آموزان مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/member-names.js", "/js/students.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1"
         x-data="studentsPage()" @keydown.escape.window="selectedStudentId = null">
    <div class="container">
        <h1 class="title is-4">دانش‌آموزان مدرسه</h1>

        <div class="notification is-warning" x-cloak x-show="!schoolId">
            برای مدیریت دانش‌آموزان، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                <%-- میان‌بر دعوت درست بعد از ثبت‌نام موفق (UX_ROADMAP.md §6.3) -
                     تا ادمین برای دعوت‌کردنِ دانش‌آموزِ تازه‌ثبت‌نام‌شده مجبور
                     نباشد به /people برود. همان endpoint موجودِ
                     iam.MembershipController که people.js هم استفاده می‌کند. --%>
                <div class="box" x-show="pendingInviteStudent" x-cloak>
                    <p class="mb-2">
                        دانش‌آموز <strong x-text="pendingInviteStudent && pendingInviteStudent.fullName"></strong>
                        ثبت‌نام شد و هنوز رمز عبور ندارد.
                    </p>
                    <button class="button is-small is-link" type="button" x-show="!pendingInviteResult"
                            @click="sendInviteForPendingStudent" :class="{ 'is-loading': invitingPending }">
                        ارسال دعوت‌نامه
                    </button>
                    <p class="has-text-success" x-show="pendingInviteResult && pendingInviteResult.alreadyActivated">
                        این کاربر قبلاً حسابش را فعال کرده است.
                    </p>
                    <p class="has-text-success" x-show="pendingInviteResult && pendingInviteResult.emailSent">
                        ایمیل دعوت ارسال شد.
                    </p>
                    <div class="field has-addons mt-2"
                         x-show="pendingInviteResult && pendingInviteResult.emailSent === false">
                        <div class="control is-expanded">
                            <input class="input" type="text" dir="ltr" readonly x-ref="pendingInviteLink"
                                   :value="pendingInviteResult && pendingInviteResult.link">
                        </div>
                        <div class="control">
                            <button class="button" type="button" @click="copyPendingInviteLink">کپی</button>
                        </div>
                    </div>
                </div>

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
                            <div class="control app-control-align-end">
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
                        <th scope="col">نام دانش‌آموز</th>
                        <th scope="col">ایمیل</th>
                        <th scope="col">کلاس</th>
                        <th scope="col">والدین</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="student in students" :key="student.studentId">
                        <tr>
                            <td x-text="studentName(student.userId)"></td>
                            <td dir="ltr" x-text="studentEmail(student.userId)"></td>
                            <td x-text="className(student.classId)"></td>
                            <td>
                                <button class="button is-small" type="button"
                                        @click="toggleGuardianPanel(student.studentId)"
                                        :aria-expanded="selectedStudentId === student.studentId ? 'true' : 'false'">
                                    <span x-text="selectedStudentId === student.studentId ? 'بستن' : 'مدیریت والدین'"></span>
                                    <span class="tag is-light app-inline-gap" x-show="guardiansByStudent[student.studentId]"
                                          x-text="(guardiansByStudent[student.studentId] || []).length"></span>
                                </button>
                            </td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                </div>

                <div class="notification" x-show="!loading && students.length === 0">
                    هنوز دانش‌آموزی در این مدرسه ثبت‌نام نشده است.
                </div>

                <div class="box" x-show="selectedStudentId" x-cloak>
                    <div class="level">
                        <div class="level-left">
                            <h2 class="title is-6" x-text="'والدین دانش‌آموز: ' + studentName(selectedStudentUserId())"></h2>
                        </div>
                        <div class="level-right">
                            <button class="delete" type="button" aria-label="بستن مدیریت والدین"
                                    @click="selectedStudentId = null"></button>
                        </div>
                    </div>

                    <div class="table-container" x-show="guardiansForSelected().length > 0">
                    <table class="table is-fullwidth is-narrow">
                        <thead>
                        <tr>
                            <th scope="col">نام والد</th>
                            <th scope="col">ایمیل</th>
                        </tr>
                        </thead>
                        <tbody>
                        <template x-for="guardianUserId in guardiansForSelected()" :key="guardianUserId">
                            <tr>
                                <td x-text="guardianName(guardianUserId)"></td>
                                <td dir="ltr" x-text="guardianEmail(guardianUserId)"></td>
                            </tr>
                        </template>
                        </tbody>
                    </table>
                    </div>
                    <p class="has-text-grey" x-show="!guardiansLoading && guardiansForSelected().length === 0">
                        هنوز ولی‌ای برای این دانش‌آموز لینک نشده است.
                    </p>

                    <hr>

                    <p class="has-text-grey" x-show="!loading && parents.length === 0">
                        هیچ عضوی با نقش والد در این مدرسه ثبت نشده است. ابتدا از صفحه‌ی
                        <a href="/people">افراد</a> یک والد اضافه کنید.
                    </p>
                    <form @submit.prevent="linkGuardian" x-show="availableParentsForSelected().length > 0">
                        <div class="field is-grouped">
                            <div class="control is-expanded">
                                <label class="label" for="guardianPicker">افزودن والد</label>
                                <div class="select is-fullwidth">
                                    <select id="guardianPicker" x-model="guardianForm.guardianUserId" required>
                                        <option value="">انتخاب والد</option>
                                        <template x-for="parent in availableParentsForSelected()" :key="parent.userId">
                                            <option :value="parent.userId" x-text="parent.fullName"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>
                            <div class="control app-control-align-end">
                                <button class="button is-link" type="submit" :class="{ 'is-loading': guardianSubmitting }">
                                    لینک کردن
                                </button>
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        </template>
    </div>
</section>
`)
