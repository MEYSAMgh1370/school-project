@import com.example.roshdeandishe.web.dto.WardRef
@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar
@param java.util.List<WardRef> wards

@template.layout.main(title = "نمره‌های من", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/my-grades.js"),
        styles = java.util.List.of("/css/my-grades.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="myGradesPage()">
    <div class="container">
        @template.layout.page-header(
            title = "نمره‌های من",
            subtitle = "نمره‌های خودتان و فرزندانِ لینک‌شده‌ به شما در این مدرسه.")

        <%-- نامِ فرزندان از سمت سرور می‌رسد (API نمره‌ها نام نمی‌دهد)؛ به‌صورت
             attribute می‌آید تا escape درست HTML را JTE انجام دهد، نه یک رشته‌ی JSON
             دست‌ساز. my-grades.js آن‌ها را از DOM می‌خواند. عمداً *بیرون* از
             school-scope است: محتوای آن داخل <template x-if="schoolId"> می‌ماند و
             هنگام init در DOM نیست. --%>
        <div hidden id="my-grades-wards">
            @for(var ward : wards)
                <span data-student-id="${ward.studentId()}" data-school-id="${ward.schoolId()}"
                      data-name="${ward.name()}" data-own="${ward.own()}"></span>
            @endfor
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای مشاهده‌ی نمرات، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <%-- انتخابگرِ دانش‌آموز: یک کنترل که باز می‌شود و نامِ فرزندان داخلش است؛
                     با انتخابِ یکی، فقط نمره‌های همان دانش‌آموز زیرش نشان داده می‌شود.
                     با ?student=<id> (از کارت داشبورد) همان فرزند از قبل انتخاب است.
                     اگر فقط یک دانش‌آموز هست، انتخابگر بی‌معناست و فقط نامش می‌آید.
                     با خطای بارگذاری پنهان است: groups() از روی نام‌های سمت سرور پر می‌ماند،
                     و بدون !error شکستِ شبکه «بدون نمره» نشان داده می‌شد. --%>
                <div x-cloak x-show="!loading && !error && groups().length > 0">
                    <div class="app-picker" x-show="groups().length > 1"
                         @click.outside="pickerOpen = false" @keydown.escape="closePicker(true)">
                        <p class="is-size-7 has-text-grey mb-2" id="grades-picker-label">انتخاب دانش‌آموز</p>
                        <button type="button" class="app-picker-toggle" x-ref="pickerToggle" aria-controls="grades-picker-list"
                                aria-labelledby="grades-picker-label grades-picker-current"
                                :aria-expanded="pickerOpen" @click="pickerOpen = !pickerOpen">
                            <span id="grades-picker-current" x-text="selectedGroup() ? selectedGroup().label : ''"></span>
                            <span class="app-picker-chevron" aria-hidden="true"></span>
                        </button>
                        <ul class="app-picker-list" id="grades-picker-list" x-show="pickerOpen">
                            <template x-for="group in groups()" :key="group.studentId">
                                <li>
                                    <button type="button" class="app-picker-option"
                                            :class="{ 'is-selected': isSelected(group.studentId) }"
                                            :aria-current="isSelected(group.studentId) ? 'true' : null"
                                            @click="select(group.studentId)">
                                        <span x-text="group.label"></span>
                                        <span class="tag is-light" x-text="countLabel(group.grades.length)"></span>
                                    </button>
                                </li>
                            </template>
                        </ul>
                    </div>
                    <h2 class="title is-5 mb-4" x-show="groups().length === 1"
                        x-text="selectedGroup() ? selectedGroup().label : ''"></h2>

                    <div class="box app-picker-panel" x-show="selectedGroup()">
                        <p x-show="selectedGroup() && selectedGroup().grades.length === 0">نمره‌ای برای این دانش‌آموز ثبت نشده است.</p>
                        <div class="table-container" x-show="selectedGroup() && selectedGroup().grades.length > 0">
                            <table class="table is-fullwidth is-striped">
                                <thead>
                                <tr>
                                    <th scope="col">سال تحصیلی</th>
                                    <th scope="col">نوبت</th>
                                    <th scope="col">درس</th>
                                    <th scope="col">نمره</th>
                                    <th scope="col">نظر</th>
                                    <th scope="col">تاریخ ثبت</th>
                                </tr>
                                </thead>
                                <tbody>
                                <template x-for="grade in (selectedGroup() ? selectedGroup().grades : [])" :key="grade.id">
                                    <tr>
                                        <td dir="ltr" x-text="grade.academicYear"></td>
                                        <td x-text="termLabel(grade.term)"></td>
                                        <td x-text="grade.courseName || 'نمره‌ی کل'" :class="{ 'has-text-grey': !grade.courseName }"></td>
                                        <td x-text="gradeValue(grade)"></td>
                                        <td x-text="grade.comment || '—'"></td>
                                        <td dir="ltr" x-text="new Date(grade.recordedAt).toLocaleDateString('fa-IR')"></td>
                                    </tr>
                                </template>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                @template.layout.empty-state(
                    show = "!loading && !error && groups().length === 0",
                    art = "exam",
                    message = @`نمره‌ای برای شما یا فرزندانتان در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
