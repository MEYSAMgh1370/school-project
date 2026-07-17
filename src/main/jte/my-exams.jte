@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "آزمون‌های من", scripts = java.util.List.of("/js/navbar.js", "/js/my-exams.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="myExamsPage()">
    <div class="container">
        <h1 class="title is-4">آزمون‌های من</h1>
        <p class="subtitle is-6">آزمون‌های هفتگیِ خودتان و آزمون‌های تألیفی‌ای که به شما تخصیص داده شده.</p>

        <div class="notification is-warning" x-show="!schoolId">
            برای مشاهده‌ی آزمون‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>

                <p x-show="loading">در حال بارگذاری...</p>

                <div x-show="!loading">
                    <template x-for="exam in exams" :key="exam.id">
                        <div class="box">
                            <div class="level">
                                <div class="level-left">
                                    <div>
                                        <p class="title is-6" x-text="examLabel(exam)"></p>
                                        <p class="is-size-7 has-text-grey">
                                            <span dir="ltr" x-text="exam.start.replace('T', ' ')"></span>
                                            تا
                                            <span dir="ltr" x-text="exam.end.replace('T', ' ')"></span>
                                            — مدت <span dir="ltr" x-text="exam.durationMinutes"></span> دقیقه
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
                                        نمره‌ی کل: <strong dir="ltr" x-text="results[exam.id].overallScore"></strong>
                                    </p>
                                    <table class="table is-fullwidth is-narrow">
                                        <thead>
                                        <tr><th>بخش</th><th>درست</th><th>غلط</th><th>بی‌پاسخ</th><th>نمره</th></tr>
                                        </thead>
                                        <tbody>
                                        <template x-for="section in results[exam.id].sections" :key="section.sectionId">
                                            <tr>
                                                <td x-text="section.name || 'سرفصل #' + section.topicId"></td>
                                                <td dir="ltr" x-text="section.correctCount"></td>
                                                <td dir="ltr" x-text="section.incorrectCount"></td>
                                                <td dir="ltr" x-text="section.unansweredCount"></td>
                                                <td dir="ltr" x-text="section.score"></td>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                </div>
                            </template>
                        </div>
                    </template>
                </div>

                <div class="notification" x-show="!loading && !error && exams.length === 0">
                    آزمونی برای شما ثبت نشده است.
                </div>
            </div>
        </template>
    </div>
</section>
`)
