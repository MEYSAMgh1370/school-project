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
                      data-name="${ward.name()}"></span>
            @endfor
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای مشاهده‌ی نمرات، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <%-- آکاردئونِ دانش‌آموز: هر فرزند یک بخش دارد و فقط یک بخش باز است.
                     با ?student=<id> (از کارت داشبورد) بخشِ همان فرزند باز می‌شود. --%>
                <div class="app-accordion" x-cloak x-show="!loading && groups().length > 0">
                    <template x-for="group in groups()" :key="group.studentId">
                        <article class="box app-accordion-item">
                            <h2 class="title is-6 mb-0">
                                <button type="button" class="app-accordion-toggle"
                                        :id="'grades-toggle-' + group.studentId"
                                        :aria-expanded="isOpen(group.studentId)"
                                        :aria-controls="'grades-panel-' + group.studentId"
                                        @click="toggle(group.studentId)">
                                    <span x-text="group.label"></span>
                                    <span class="tag is-light" x-text="countLabel(group.grades.length)"></span>
                                    <span class="app-accordion-chevron" aria-hidden="true"></span>
                                </button>
                            </h2>
                            <div class="app-accordion-panel" role="region"
                                 :id="'grades-panel-' + group.studentId"
                                 :aria-labelledby="'grades-toggle-' + group.studentId"
                                 x-show="isOpen(group.studentId)">
                                <p x-show="group.grades.length === 0">نمره‌ای برای این دانش‌آموز ثبت نشده است.</p>
                                <div class="table-container" x-show="group.grades.length > 0">
                                    <table class="table is-fullwidth is-striped">
                                        <thead>
                                        <tr>
                                            <th scope="col">سال تحصیلی</th>
                                            <th scope="col">نوبت</th>
                                            <th scope="col">نمره</th>
                                            <th scope="col">نظر</th>
                                            <th scope="col">تاریخ ثبت</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <template x-for="grade in group.grades" :key="grade.id">
                                            <tr>
                                                <td dir="ltr" x-text="grade.academicYear"></td>
                                                <td x-text="termLabel(grade.term)"></td>
                                                <td x-text="gradeValue(grade)"></td>
                                                <td x-text="grade.comment || '—'"></td>
                                                <td dir="ltr" x-text="new Date(grade.recordedAt).toLocaleDateString('fa-IR')"></td>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </article>
                    </template>
                </div>

                @template.layout.empty-state(
                    show = "!loading && !error && groups().length === 0",
                    art = "exam",
                    message = @`نمره‌ای برای شما یا فرزندانتان در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
