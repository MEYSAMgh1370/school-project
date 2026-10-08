@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "آزمون‌های من", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/modal-focus.js", "/js/my-exams.js"), content = @`
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
                                            @click="askStart(exam)">
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

                <%-- ------------------------------------------------ تأیید شروع آزمون --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="myExamsConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="myExamsConfirmTitle">تأیید شروع آزمون</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="confirm && confirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="confirm ? confirm.error : ''"></span></span></div>
                            <p class="has-text-weight-semibold">
                                «<span x-text="confirm ? examLabel(confirm.exam) : ''"></span>» همین حالا شروع شود؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                با شروع آزمون، زمان‌سنج هم شروع می‌شود؛ مدت آزمون
                                <span x-text="confirm ? Calendar.digits(confirm.exam.durationMinutes) : ''"></span> دقیقه است.
                                وقتی آماده‌اید شروع کنید.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-link" type="button" id="myExamsConfirmStart"
                                    :class="{ 'is-loading': starting !== null }"
                                    @click="startAttempt(confirm.exam)">شروع آزمون</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
