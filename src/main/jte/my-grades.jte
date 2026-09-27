@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "نمره‌های من", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/my-grades.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="myGradesPage()">
    <div class="container">
        @template.layout.page-header(
            title = "نمره‌های من",
            subtitle = "نمره‌های خودتان و فرزندانِ لینک‌شده‌ به شما در این مدرسه.")

        @template.layout.school-scope(
            noSchoolMessage = "برای مشاهده‌ی نمرات، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <div class="table-container" x-show="!loading && grades.length > 0">
                <table class="table is-fullwidth is-striped">
                    <thead>
                    <tr>
                        <th scope="col">دانش‌آموز</th>
                        <th scope="col">سال تحصیلی</th>
                        <th scope="col">نوبت</th>
                        <th scope="col">نمره</th>
                        <th scope="col">نظر</th>
                        <th scope="col">تاریخ ثبت</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="grade in grades" :key="grade.id">
                        <tr>
                            <td>دانش‌آموز #<span x-text="grade.studentId"></span></td>
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

                @template.layout.empty-state(
                    show = "!loading && !error && grades.length === 0",
                    art = "exam",
                    message = @`نمره‌ای برای شما یا فرزندانتان در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
