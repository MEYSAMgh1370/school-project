@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "دانش‌آموزان مدرسه", styles = java.util.List.of("/css/grades.css"), scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/member-names.js", "/js/list-search.js", "/js/students.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1"
         x-data="studentsPage()" @keydown.escape.window="selectedStudentId = null">
    <div class="container">
        @template.layout.page-header(
            title = "دانش‌آموزان مدرسه")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت دانش‌آموزان، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

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
                    <p class="has-text-success-on-scheme" x-show="pendingInviteResult && pendingInviteResult.alreadyActivated">
                        این کاربر قبلاً حسابش را فعال کرده است.
                    </p>
                    <p class="has-text-success-on-scheme" x-show="pendingInviteResult && pendingInviteResult.emailSent">
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
                    برای ثبت‌نام باید حداقل یک کلاس (در سال تحصیلی جاری) داشته باشید؛ ابتدا از صفحه‌ی کلاس‌ها بسازید.
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

                <%-- فهرست ردیفی (DESIGN.md §5a، همان الگوی دفتر نمرات): جست‌وجوی نام/ایمیل/کلاس بالای فهرست،
                     هر دانش‌آموز یک ردیف با آواتار، کلاس و تعداد ولی، و دکمه‌ی مدیریت والدین. --%>
                <template x-if="students.length > 0">
                    <div>
                        <div class="field">
                            <label class="label is-sr-only" for="studentSearch">جست‌وجو در دانش‌آموزان</label>
                            <div class="control">
                                <input class="input" id="studentSearch" type="search" x-model="filterText"
                                       placeholder="جست‌وجوی نام، ایمیل یا کلاس">
                            </div>
                        </div>

                        <div class="app-chips" role="group" aria-label="فیلتر فهرست">
                            <template x-for="item in chips()" :key="item.key">
                                <button class="app-chip" type="button" :class="{ 'is-selected': statusChip === item.key }"
                                        :aria-pressed="statusChip === item.key ? 'true' : 'false'" @click="selectChip(item.key)">
                                    <span x-text="item.label"></span>
                                    (<span x-text="fa(item.count)"></span>)
                                </button>
                            </template>
                        </div>

                        <div class="app-list" role="list">
                            <template x-for="student in visibleStudents()" :key="student.studentId">
                                <div class="app-student-row" role="listitem">
                                    <span class="app-avatar" aria-hidden="true" x-text="studentName(student.userId).charAt(0)"></span>
                                    <div class="app-student-row__main">
                                        <span class="app-student-row__name" x-text="studentName(student.userId)"></span>
                                        <span class="is-size-7 has-text-grey" dir="ltr" x-text="studentEmail(student.userId)"></span>
                                    </div>
                                    <span class="tag is-light" x-text="classLabel(student)"></span>
                                    <button class="button is-light" type="button"
                                            @click="toggleGuardianPanel(student.studentId)"
                                            :aria-expanded="selectedStudentId === student.studentId ? 'true' : 'false'">
                                        <span x-text="selectedStudentId === student.studentId ? 'بستن' : 'مدیریت والدین'"></span>
                                        <span class="tag is-light app-inline-gap" x-show="guardiansByStudent[student.studentId]"
                                              x-text="fa((guardiansByStudent[student.studentId] || []).length)"></span>
                                    </button>
                                </div>
                            </template>
                        </div>
                        <button class="button is-light mt-3" type="button" x-show="filteredStudents().length > shown"
                                @click="showMore()"
                                x-text="'نمایش بیشتر (' + remainingCount().toLocaleString('fa-IR-u-nu-arabext') + ' مورد دیگر)'"></button>
                        <p class="has-text-grey mt-3" x-show="filteredStudents().length === 0">
                            دانش‌آموزی با این جست‌وجو پیدا نشد.
                        </p>
                    </div>
                </template>

                @template.layout.empty-state(
                    show = "!loading && students.length === 0",
                    art = "people",
                    message = @`هنوز دانش‌آموزی در این مدرسه ثبت‌نام نشده است.`)

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
            `)
    </div>
</section>
`)
