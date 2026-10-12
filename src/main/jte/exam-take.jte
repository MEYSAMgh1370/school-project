@template.layout.main(title = "آزمون‌دهی", styles = java.util.List.of("/webjars/katex/0.16.22/dist/katex.min.css"), scripts = java.util.List.of("/js/modal-focus.js", "/webjars/katex/0.16.22/dist/katex.min.js", "/js/math.js", "/js/exam-take.js"), content = @`

<section class="section" id="main-content" role="main" tabindex="-1" x-data="examTakePage()">
    <div class="container">
        <template x-if="fatalError">
            <div class="notification is-danger" role="alert"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="fatalError"></span></span></div>
        </template>

        <template x-if="!fatalError && loading">
            <p>در حال بارگذاری آزمون...</p>
        </template>

        <template x-if="!fatalError && !loading && exam">
            <div>
                <div class="level">
                    <div class="level-left">
                        <div>
                            <h1 class="title is-4" x-text="exam.title || 'آزمون هفتگی'"></h1>
                            <p class="is-size-7 has-text-grey">پاسخ‌های شما بلافاصله پس از انتخاب ذخیره می‌شوند.</p>
                        </div>
                    </div>
                    <div class="level-right">
                        <div class="tag is-medium" :class="expired ? 'is-danger' : 'is-info'" x-show="remainingLabel">
                                زمان باقی‌مانده: <span dir="ltr" class="app-inline-gap-xs" x-text="remainingLabel"></span>
                        </div>
                    </div>
                </div>

                <div class="notification is-warning" role="alert" x-cloak x-show="expired">
                    زمان این آزمون به پایان رسیده یا جلسه‌ی شما منقضی شده است. برای ادامه به «آزمون‌های من» بازگردید.
                </div>

                    <div class="tabs" x-show="exam.sections.length > 0">
                    <ul>
                        <template x-for="(section, sectionIndex) in exam.sections" :key="section.id">
                            <li :class="{ 'is-active': selectedSectionId === section.id }">
                                    <a href="#"
                                       @click.prevent="selectSection(section.id)">
                                        <span x-text="section.name || ('بخش ' + toPersianDigits(sectionIndex + 1))"></span>
                                        <span class="tag is-light app-inline-gap" x-text="toPersianDigits(answeredCount(section.id) + '/' + section.questionCount)"></span>
                                    </a>
                            </li>
                        </template>
                    </ul>
                </div>

                @template.layout.empty-state(
                    show = "exam.sections.length === 0",
                    art = "exam",
                    message = @`این آزمون هنوز بخشی ندارد.`)

                <p role="status" x-cloak x-show="loadingQuestions">در حال بارگذاری سؤال‌ها...</p>

                <p class="has-text-info-on-scheme mb-4" role="status" aria-live="polite"
                   x-cloak x-show="saveStatusMessage()" x-text="saveStatusMessage()"></p>

                <div class="notification is-danger" role="alert" x-cloak x-show="saveErrorBySection[selectedSectionId]"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="saveErrorBySection[selectedSectionId]"></span></span></div>

                <div class="notification is-success" role="status" x-cloak x-show="reportNotice" x-text="reportNotice"></div>
                <div class="notification is-danger" role="alert" x-cloak x-show="reportPageError"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="reportPageError"></span></span></div>

                <template x-for="(question, index) in currentQuestions()" :key="question.questionId">
                    <div class="box">
                        <div class="is-flex is-justify-content-space-between is-align-items-flex-start mb-3">
                            <p>
                                <strong x-text="toPersianDigits(index + 1) + '. '"></strong>
                                <span x-text="question.text"></span>
                            </p>
                            <button class="button is-light" type="button" @click="openReport(question, $event)">گزارش مشکل</button>
                        </div>
                        <template x-for="n in [1, 2, 3, 4]" :key="'choice-' + question.questionId + '-' + n">
                            <label class="radio is-block mb-2">
                                <input type="radio" :name="'question-' + question.questionId"
                                       :checked="question.selectedChoice === n"
                                       :disabled="expired || isQuestionSaving(question.questionId)"
                                       @change="selectChoice(question, n)">
                                <span x-text="' ' + question['choice' + n]"></span>
                            </label>
                        </template>
                    </div>
                </template>

                <a class="button" href="/my-exams">
                    پایان و بازگشت به آزمون‌های من
                </a>
            </div>
        </template>
    </div>

    <%-- گزارش مشکل یک سؤال (تنها modal صفحه؛ مستقل از پاسخ‌ها و زمان‌سنج آزمون) --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="reportModalTitle"
         tabindex="-1" x-ref="reportModal" :class="{ 'is-active': report }" x-cloak
         @keydown.tab="window.ModalFocus && window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeReport">
        <div class="modal-background" @click="closeReport"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="reportModalTitle">گزارش مشکل سؤال</p>
                <button class="delete" type="button" aria-label="بستن" @click="closeReport"></button>
            </header>
            <section class="modal-card-body">
                <div class="notification is-danger" role="alert" x-show="report && report.error">
                    <span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="report ? report.error : ''"></span></span>
                </div>
                <div class="notification is-light">
                    <span x-math="report ? report.question.text : ''"></span>
                </div>
                <div class="field">
                    <label class="label" for="reportType">نوع مشکل</label>
                    <div class="select is-fullwidth" :class="{ 'is-danger': report && report.errors.reportType }">
                        <select id="reportType" x-model="reportForm.reportType" required
                                :aria-invalid="report && report.errors.reportType ? 'true' : 'false'">
                            <option value="">— انتخاب کنید —</option>
                            <template x-for="type in reportTypes" :key="type.value">
                                <option :value="type.value" x-text="type.label"></option>
                            </template>
                        </select>
                    </div>
                    <p class="help is-danger" x-show="report && report.errors.reportType" x-text="report ? report.errors.reportType : ''"></p>
                </div>
                <div class="field">
                    <label class="label" for="reportComment">توضیح (اختیاری)</label>
                    <textarea class="textarea" id="reportComment" maxlength="1000" x-model="reportForm.comment"
                              :class="{ 'is-danger': report && report.errors.comment }"></textarea>
                    <p class="help" x-text="toPersianDigits(reportForm.comment.length) + '/' + toPersianDigits(1000)"></p>
                    <p class="help is-danger" x-show="report && report.errors.comment" x-text="report ? report.errors.comment : ''"></p>
                </div>
            </section>
            <footer class="modal-card-foot">
                <button class="button is-link" type="button" :class="{ 'is-loading': submittingReport }"
                        @click="submitReport">ثبت گزارش</button>
                <button class="button is-light" type="button" @click="closeReport">انصراف</button>
            </footer>
        </div>
    </div>
</section>
`)
