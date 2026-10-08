@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "آزمون‌های من", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/my-exams.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="myExamsPage()">
    <div class="container">
        @template.layout.page-header(
            title = "آزمون‌های من",
            subtitle = "آزمون‌های هفتگیِ خودتان و آزمون‌های تألیفی‌ای که به شما تخصیص داده شده.")

        @template.layout.school-scope(
            noSchoolMessage = "برای مشاهده‌ی آزمون‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.",
            successNotice = false,
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <div x-show="!loading">
                    <template x-for="exam in exams" :key="exam.id">
                        <div class="box">
                            <div class="level">
                                <div class="level-left">
                                    <div>
                                        <p class="title is-6" x-text="examLabel(exam)"></p>
                                        <p class="is-size-7 has-text-grey">
                                            <span x-text="Calendar.formatDateTimeRange(exam.start, exam.end)"></span>
                                            — مدت <span x-text="Calendar.digits(exam.durationMinutes)"></span> دقیقه
                                        </p>
                                    </div>
                                </div>
                                <div class="level-right">
                                    <button class="button is-link" type="button"
                                            x-show="hasStarted(exam) && !hasEnded(exam)"
                                            :class="{ 'is-loading': starting === exam.id }"
                                            @click="startAttempt(exam)">
                                        شروع آزمون
                                    </button>
                                    <span class="tag is-warning" x-show="!hasStarted(exam)">هنوز شروع نشده</span>
                                    <button class="button is-small" type="button" x-show="hasEnded(exam)"
                                            @click="viewResult(exam)">
                                        مشاهده نتیجه
                                    </button>
                                </div>
                            </div>

                            <template x-if="results[exam.id]">
                                <div class="mt-3">
                                    <p class="mb-2">
                                        نمره‌ی کل: <strong dir="ltr" x-text="Calendar.digits(results[exam.id].overallScore)"></strong>
                                    </p>
                                    <div class="table-container">
                                    <table class="table is-fullwidth is-narrow">
                                        <thead>
                                        <tr><th scope="col">بخش</th><th scope="col">درست</th><th scope="col">غلط</th><th scope="col">بی‌پاسخ</th><th scope="col">نمره</th></tr>
                                        </thead>
                                        <tbody>
                                        <template x-for="(section, sectionIndex) in results[exam.id].sections" :key="section.sectionId">
                                            <tr>
                                                <td x-text="section.name || 'بخش ' + Calendar.digits(sectionIndex + 1)"></td>
                                                <td dir="ltr" x-text="Calendar.digits(section.correctCount)"></td>
                                                <td dir="ltr" x-text="Calendar.digits(section.incorrectCount)"></td>
                                                <td dir="ltr" x-text="Calendar.digits(section.unansweredCount)"></td>
                                                <td dir="ltr" x-text="Calendar.digits(section.score)"></td>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                    </div>
                                </div>
                            </template>
                        </div>
                    </template>
                </div>

                @template.layout.empty-state(
                    show = "!loading && !error && exams.length === 0",
                    art = "exam",
                    message = @`آزمونی برای شما ثبت نشده است.`)
            `)
    </div>
</section>
`)
