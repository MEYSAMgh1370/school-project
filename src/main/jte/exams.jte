@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "آزمون‌ها", scripts = java.util.List.of("/js/navbar.js", "/js/exams.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="examsPage()">
    <div class="container">
        <h1 class="title is-4">آزمون‌ها</h1>

        <div class="notification is-warning" x-show="!schoolId">
            برای مدیریت آزمون‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                <template x-if="!blocked">
                    <div>
                        <div class="box">
                            <h2 class="title is-6">آزمون‌های تألیفی</h2>

                            <div class="notification is-light" x-show="!loading && exams.length === 0">
                                این مدرسه هنوز آزمون تألیفی‌ای ندارد. اولین آزمون را از فرم زیر بسازید.
                            </div>

                            <div class="table-container" x-show="exams.length > 0">
                            <table class="table is-fullwidth is-striped">
                                <thead>
                                <tr>
                                    <th>عنوان</th>
                                    <th>شروع</th>
                                    <th>پایان</th>
                                    <th>مدت (دقیقه)</th>
                                    <th></th>
                                </tr>
                                </thead>
                                <tbody>
                                <template x-for="exam in exams" :key="exam.id">
                                    <tr>
                                        <td x-text="exam.title"></td>
                                        <td dir="ltr" x-text="exam.start.replace('T', ' ')"></td>
                                        <td dir="ltr" x-text="exam.end.replace('T', ' ')"></td>
                                        <td dir="ltr" x-text="exam.durationMinutes"></td>
                                        <td>
                                            <button type="button" class="button is-small"
                                                    :class="{ 'is-link': expandedExamId === exam.id }"
                                                    @click="toggleExam(exam.id)">
                                                مدیریت
                                            </button>
                                        </td>
                                    </tr>
                                </template>
                                </tbody>
                            </table>
                            </div>

                            <template x-for="exam in exams" :key="'panel-' + exam.id">
                                <div class="box" x-show="expandedExamId === exam.id" x-cloak>
                                    <h3 class="title is-6" x-text="'مدیریت آزمون: ' + exam.title"></h3>

                                    <div class="tabs">
                                        <ul>
                                            <li :class="{ 'is-active': examTab === 'sections' }">
                                                <a @click="examTab = 'sections'">بخش‌ها</a>
                                            </li>
                                            <li :class="{ 'is-active': examTab === 'assign' }">
                                                <a @click="examTab = 'assign'">تخصیص دانش‌آموز</a>
                                            </li>
                                            <li :class="{ 'is-active': examTab === 'results' }">
                                                <a @click="examTab = 'results'">نتایج</a>
                                            </li>
                                        </ul>
                                    </div>

                                    <div x-show="examTab === 'sections'">
                                        <div class="table-container" x-show="sections.length > 0">
                                        <table class="table is-fullwidth is-narrow">
                                            <thead>
                                            <tr><th>سرفصل</th><th>نام بخش</th><th>تعداد سؤال</th></tr>
                                            </thead>
                                            <tbody>
                                            <template x-for="section in sections" :key="section.id">
                                                <tr>
                                                    <td x-text="topicName(section.topicId)"></td>
                                                    <td x-text="section.name || '—'"></td>
                                                    <td dir="ltr" x-text="section.questionCount"></td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
                                        </div>
                                        <p class="has-text-grey mb-3" x-show="sections.length === 0">
                                            هنوز بخشی برای این آزمون ساخته نشده است.
                                        </p>

                                        <h4 class="title is-6">افزودن بخش با انتخاب دستیِ سؤال</h4>
                                        <form @submit.prevent="createManualSection(exam.id)">
                                            <div class="field is-grouped is-grouped-multiline">
                                                <div class="control is-expanded">
                                                    <div class="select is-fullwidth">
                                                        <select x-model="manualSectionForm.topicId" @change="onManualTopicChange" required>
                                                            <option value="">— سرفصل —</option>
                                                            <template x-for="node in flatTopics" :key="'ms-' + node.id">
                                                                <option :value="node.id" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                                                            </template>
                                                        </select>
                                                    </div>
                                                </div>
                                                <div class="control is-expanded">
                                                    <input class="input" type="text" placeholder="نام بخش (اختیاری)"
                                                           x-model="manualSectionForm.name">
                                                </div>
                                            </div>
                                            <div class="box" style="max-height: 12rem; overflow-y: auto"
                                                 x-show="manualSectionForm.topicId">
                                                <p class="has-text-grey" x-show="questionsForTopic.length === 0">
                                                    این سرفصل سؤالی ندارد.
                                                </p>
                                                <template x-for="question in questionsForTopic" :key="question.id">
                                                    <label class="checkbox is-block mb-2">
                                                        <input type="checkbox"
                                                               :checked="manualSectionForm.questionIds.includes(question.id)"
                                                               @change="toggleQuestionSelection(question.id)">
                                                        <span x-text="' ' + question.text"></span>
                                                    </label>
                                                </template>
                                            </div>
                                            <div class="field">
                                                <button class="button is-link" type="submit"
                                                        :disabled="!manualSectionForm.topicId || manualSectionForm.questionIds.length === 0"
                                                        :class="{ 'is-loading': submittingManualSection }">
                                                    افزودن بخش
                                                </button>
                                            </div>
                                        </form>

                                        <hr>

                                        <h4 class="title is-6">افزودن بخش با انتخاب خودکار سؤال</h4>
                                        <form @submit.prevent="createAutoSection(exam.id)">
                                            <div class="field is-grouped is-grouped-multiline">
                                                <div class="control is-expanded">
                                                    <div class="select is-fullwidth">
                                                        <select x-model="autoSectionForm.topicId" required>
                                                            <option value="">— سرفصل (شامل زیرسرفصل‌ها) —</option>
                                                            <template x-for="node in flatTopics" :key="'as-' + node.id">
                                                                <option :value="node.id" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                                                            </template>
                                                        </select>
                                                    </div>
                                                </div>
                                                <div class="control">
                                                    <input class="input" type="text" placeholder="نام بخش (اختیاری)"
                                                           x-model="autoSectionForm.name" style="width: 12rem">
                                                </div>
                                                <div class="control">
                                                    <input class="input" dir="ltr" type="number" min="1" placeholder="تعداد سؤال"
                                                           x-model="autoSectionForm.count" style="width: 8rem">
                                                </div>
                                                <div class="control" style="align-self: center">
                                                    <label class="checkbox">
                                                        <input type="checkbox" x-model="autoSectionForm.lazy">
                                                        در صورت کمبود سؤال، همان تعداد موجود انتخاب شود
                                                    </label>
                                                </div>
                                                <div class="control">
                                                    <button class="button is-link" type="submit"
                                                            :class="{ 'is-loading': submittingAutoSection }">
                                                        افزودن بخش
                                                    </button>
                                                </div>
                                            </div>
                                        </form>
                                    </div>

                                    <div x-show="examTab === 'assign'">
                                        <div class="table-container" x-show="assignedStudentIds.length > 0">
                                        <table class="table is-fullwidth is-narrow">
                                            <tbody>
                                            <template x-for="studentId in assignedStudentIds" :key="studentId">
                                                <tr><td x-text="studentLabel(studentId)"></td></tr>
                                            </template>
                                            </tbody>
                                        </table>
                                        </div>
                                        <p class="has-text-grey mb-3" x-show="assignedStudentIds.length === 0">
                                            هنوز دانش‌آموزی به این آزمون تخصیص داده نشده است.
                                        </p>
                                        <div class="field has-addons">
                                            <div class="control is-expanded">
                                                <div class="select is-fullwidth">
                                                    <select x-model="assignStudentIdInput">
                                                        <option value="">— انتخاب دانش‌آموز —</option>
                                                        <template x-for="student in students" :key="'as-opt-' + student.studentId">
                                                            <option :value="student.studentId" x-text="studentLabel(student.studentId)"></option>
                                                        </template>
                                                    </select>
                                                </div>
                                            </div>
                                            <div class="control">
                                                <button class="button is-link" type="button"
                                                        :disabled="!assignStudentIdInput"
                                                        :class="{ 'is-loading': submittingAssignment }"
                                                        @click="assignStudent(exam.id)">
                                                    تخصیص
                                                </button>
                                            </div>
                                        </div>
                                    </div>

                                    <div x-show="examTab === 'results'">
                                        <div class="table-container" x-show="attemptedStudents.length > 0">
                                        <table class="table is-fullwidth is-narrow">
                                            <thead><tr><th>دانش‌آموز</th><th>زمان شروع</th><th></th></tr></thead>
                                            <tbody>
                                            <template x-for="attempt in attemptedStudents" :key="attempt.studentId">
                                                <tr>
                                                    <td x-text="studentLabel(attempt.studentId)"></td>
                                                    <td dir="ltr" x-text="attempt.startedAt"></td>
                                                    <td>
                                                        <button type="button" class="button is-small"
                                                                :class="{ 'is-link': expandedResultStudentId === attempt.studentId }"
                                                                @click="toggleResult(exam.id, attempt.studentId)">
                                                            مشاهده نتیجه
                                                        </button>
                                                    </td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
                                        </div>
                                        <p class="has-text-grey" x-show="attemptedStudents.length === 0">
                                            هنوز دانش‌آموزی برای این آزمون تلاشی ثبت نکرده است.
                                        </p>

                                        <template x-if="studentResult">
                                            <div class="box">
                                                <p class="mb-3">
                                                    نمره‌ی کل:
                                                    <strong dir="ltr" x-text="studentResult.overallScore"></strong>
                                                </p>
                                                <div class="table-container">
                                                <table class="table is-fullwidth is-narrow">
                                                    <thead>
                                                    <tr>
                                                        <th>بخش</th><th>درست</th><th>غلط</th><th>بی‌پاسخ</th><th>نمره</th>
                                                    </tr>
                                                    </thead>
                                                    <tbody>
                                                    <template x-for="section in studentResult.sections" :key="section.sectionId">
                                                        <tr>
                                                            <td x-text="section.name || topicName(section.topicId)"></td>
                                                            <td dir="ltr" x-text="section.correctCount"></td>
                                                            <td dir="ltr" x-text="section.incorrectCount"></td>
                                                            <td dir="ltr" x-text="section.unansweredCount"></td>
                                                            <td dir="ltr" x-text="section.score"></td>
                                                        </tr>
                                                    </template>
                                                    </tbody>
                                                </table>
                                                </div>
                                            </div>
                                        </template>
                                    </div>
                                </div>
                            </template>

                            <hr>
                            <h3 class="title is-6">ساخت آزمون تألیفی تازه</h3>
                            <form @submit.prevent="createExam">
                                <div class="field is-grouped is-grouped-multiline">
                                    <div class="control is-expanded">
                                        <label class="label" for="examTitle">عنوان</label>
                                        <input class="input" id="examTitle" type="text" x-model="examForm.title" required>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="examStart">شروع</label>
                                        <input class="input" dir="ltr" id="examStart" type="datetime-local" x-model="examForm.start" required>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="examEnd">پایان</label>
                                        <input class="input" dir="ltr" id="examEnd" type="datetime-local" x-model="examForm.end" required>
                                    </div>
                                    <div class="control">
                                        <label class="label" for="examDuration">مدت (دقیقه)</label>
                                        <input class="input" dir="ltr" id="examDuration" type="number" min="1"
                                               x-model="examForm.durationMinutes" style="width: 8rem" required>
                                    </div>
                                    <div class="control" style="align-self: flex-end">
                                        <button class="button is-link" type="submit" :class="{ 'is-loading': submittingExam }">
                                            ساخت آزمون
                                        </button>
                                    </div>
                                </div>
                            </form>
                        </div>

                        <div class="box">
                            <h2 class="title is-6">ساخت آزمون هفتگیِ خودکار</h2>
                            <p class="has-text-grey mb-3">
                                از سرفصل‌های مطالعه‌شده‌ی اخیر دانش‌آموز، یک آزمون شخصی برای هفته‌ی انتخابی می‌سازد.
                            </p>
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control is-expanded">
                                    <label class="label" for="weeklyStudent">دانش‌آموز</label>
                                    <div class="select is-fullwidth">
                                        <select id="weeklyStudent" x-model="weeklyForm.studentId">
                                            <option value="">— انتخاب کنید —</option>
                                            <template x-for="student in students" :key="'wk-' + student.studentId">
                                                <option :value="student.studentId" x-text="studentLabel(student.studentId)"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control">
                                    <label class="label" for="weeklyDate">یک روز از هفته‌ی مورد نظر</label>
                                    <input class="input" dir="ltr" id="weeklyDate" type="date" x-model="weeklyForm.date">
                                </div>
                                <div class="control" style="align-self: flex-end">
                                    <button class="button is-link" type="button"
                                            :disabled="!weeklyForm.studentId"
                                            :class="{ 'is-loading': generatingWeekly }" @click="generateWeekly">
                                        ساخت آزمون هفتگی
                                    </button>
                                </div>
                            </div>

                            <template x-if="weeklyResult">
                                <div class="box">
                                    <p class="mb-2">آزمون هفتگی برای هفته‌ی <span dir="ltr" x-text="weeklyResult.weekStart"></span> ساخته شد:</p>
                                    <div class="table-container">
                                    <table class="table is-fullwidth is-narrow">
                                        <thead><tr><th>سرفصل</th><th>تعداد سؤال</th></tr></thead>
                                        <tbody>
                                        <template x-for="section in weeklyResult.sections" :key="section.id">
                                            <tr>
                                                <td x-text="topicName(section.topicId)"></td>
                                                <td dir="ltr" x-text="section.questionCount"></td>
                                            </tr>
                                        </template>
                                        </tbody>
                                    </table>
                                    </div>
                                </div>
                            </template>
                        </div>
                    </div>
                </template>
            </div>
        </template>
    </div>
</section>
`)
