@template.layout.main(title = "آزمون‌دهی", scripts = java.util.List.of("/js/exam-take.js"), content = @`

<section class="section" id="main-content" role="main" tabindex="-1" x-data="examTakePage()">
    <div class="container">
        <template x-if="fatalError">
            <div class="notification is-danger" x-text="fatalError"></div>
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
                        <template x-for="section in exam.sections" :key="section.id">
                            <li :class="{ 'is-active': selectedSectionId === section.id }">
                                    <a href="#"
                                       @click.prevent="selectSection(section.id)">
                                        <span x-text="section.name || ('سرفصل #' + section.topicId)"></span>
                                        <span class="tag is-light app-inline-gap" x-text="answeredCount(section.id) + '/' + section.questionCount"></span>
                                    </a>
                            </li>
                        </template>
                    </ul>
                </div>

                <div class="notification" x-show="exam.sections.length === 0">
                    این آزمون هنوز بخشی ندارد.
                </div>

                <p role="status" x-cloak x-show="loadingQuestions">در حال بارگذاری سؤال‌ها...</p>

                <p class="has-text-info mb-4" role="status" aria-live="polite"
                   x-cloak x-show="saveStatusMessage()" x-text="saveStatusMessage()"></p>

                <div class="notification is-danger" role="alert" x-cloak x-show="saveErrorBySection[selectedSectionId]"
                     x-text="saveErrorBySection[selectedSectionId]"></div>

                <template x-for="(question, index) in currentQuestions()" :key="question.questionId">
                    <div class="box">
                        <p class="mb-3">
                            <strong x-text="(index + 1) + '. '"></strong>
                            <span x-text="question.text"></span>
                        </p>
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
</section>
`)
