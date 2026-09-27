@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "سرفصل‌ها و بانک سؤال", scripts = java.util.List.of("/js/navbar.js", "/js/curriculum.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="curriculumPage()">
    <div class="container">
        @template.layout.page-header(
            title = "سرفصل‌ها و بانک سؤال")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت سرفصل‌ها و سؤال‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <template x-if="!blocked">
                    <div class="columns">
                        <div class="column is-one-third">
                            <div class="box">
                                <h2 class="title is-6">سرفصل‌ها</h2>

                                @template.layout.empty-state(
                                    show = "!loading && topics.length === 0",
                                    art = "list",
                                    tone = "is-light",
                                    message = @`این مدرسه هنوز سرفصلی ندارد. اولین سرفصل را از فرم زیر اضافه کنید.`)

                                <ul class="mb-4" x-show="topics.length > 0">
                                    <li>
                                        <button type="button" class="button is-fullwidth is-small mb-1"
                                                :class="selectedTopicId === null ? 'is-link' : 'is-light'"
                                                @click="selectTopic(null)">
                                            همه‌ی سؤال‌های مدرسه
                                        </button>
                                    </li>
                                    <template x-for="node in flatTopics" :key="node.id">
                                        <li :style="'padding-inline-start: calc(var(--space-3) * ' + node.depth + ')'">
                                            <button type="button" class="button is-fullwidth is-small mb-1"
                                                    :class="selectedTopicId === node.id ? 'is-link' : 'is-light'"
                                                    @click="selectTopic(node.id)">
                                                <span x-text="node.name"></span>
                                            </button>
                                        </li>
                                    </template>
                                </ul>

                                <form @submit.prevent="createTopic">
                                    <div class="field">
                                        <label class="label" for="topicName">نام سرفصل تازه</label>
                                        <input class="input" id="topicName" type="text" x-model="topicForm.name" required>
                                    </div>
                                    <div class="field">
                                        <label class="label" for="topicParent">زیرمجموعه‌ی</label>
                                        <div class="select is-fullwidth">
                                            <select id="topicParent" x-model="topicForm.parentId">
                                                <option value="">— سرفصل ریشه —</option>
                                                <template x-for="node in flatTopics" :key="'parent-' + node.id">
                                                    <option :value="node.id" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="field">
                                        <button class="button is-link is-fullwidth" type="submit" :class="{ 'is-loading': submittingTopic }">
                                            افزودن سرفصل
                                        </button>
                                    </div>
                                </form>
                            </div>
                        </div>

                        <div class="column">
                            <div class="box">
                                <h2 class="title is-6" x-text="selectedTopicId ? 'سؤال‌های سرفصل: ' + selectedTopicName() : 'همه‌ی سؤال‌های مدرسه'"></h2>

                                <div class="field is-grouped is-grouped-multiline mb-4" x-show="topics.length > 0">
                                    <template x-for="level in difficultyLevels" :key="level.value">
                                        <div class="control">
                                            <button type="button" class="button is-small"
                                                    :class="difficultyFilter.includes(level.value) ? 'is-link' : 'is-light'"
                                                    @click="toggleDifficulty(level.value)">
                                                <span x-text="level.label"></span>
                                            </button>
                                        </div>
                                    </template>
                                </div>

                                <div class="table-container" x-show="questions.length > 0">
                                <table class="table is-fullwidth is-striped">
                                    <thead>
                                    <tr>
                                        <th scope="col">متن سؤال</th>
                                        <th scope="col">گزینه‌ی درست</th>
                                        <th scope="col">سطح دشواری</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="question in questions" :key="question.id">
                                        <tr>
                                            <td x-text="question.text"></td>
                                            <td x-text="'گزینه ' + question.correctChoice"></td>
                                            <td>
                                                <span class="tag is-light" x-text="difficultyLabel(question.difficulty)"></span>
                                            </td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                                </div>

                                @template.layout.empty-state(
                                    show = "!loading && questions.length === 0",
                                    art = "exam",
                                    message = @`<span x-show="selectedTopicId">برای این سرفصل هنوز سؤالی ثبت نشده است. از فرم زیر اولین سؤال را اضافه کنید.</span>
<span x-show="!selectedTopicId">هنوز سؤالی در این مدرسه ثبت نشده است.</span>`)

                                <hr>

                                <h3 class="title is-6">افزودن سؤال تازه</h3>
                                <div class="notification is-warning" x-show="topics.length === 0">
                                    برای افزودن سؤال، ابتدا یک سرفصل بسازید.
                                </div>
                                <form @submit.prevent="createQuestion" x-show="topics.length > 0">
                                    <div class="field">
                                        <label class="label" for="questionTopic">سرفصل</label>
                                        <div class="select is-fullwidth">
                                            <select id="questionTopic" x-model="questionForm.topicId" required>
                                                <option value="">— انتخاب کنید —</option>
                                                <template x-for="node in flatTopics" :key="'q-topic-' + node.id">
                                                    <option :value="node.id" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                                                </template>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="field">
                                        <label class="label" for="questionText">متن سؤال</label>
                                        <textarea class="textarea" id="questionText" x-model="questionForm.text" required></textarea>
                                    </div>
                                    <div class="field is-grouped is-grouped-multiline">
                                        <template x-for="n in [1,2,3,4]" :key="'choice-' + n">
                                            <div class="control is-expanded">
                                                <label class="label" :for="'choice' + n" x-text="'گزینه ' + n"></label>
                                                <input class="input" :id="'choice' + n" type="text"
                                                       x-model="questionForm['choice' + n]" required>
                                            </div>
                                        </template>
                                    </div>
                                    <div class="field is-grouped is-grouped-multiline">
                                        <div class="control">
                                            <label class="label" for="correctChoice">گزینه‌ی درست</label>
                                            <div class="select">
                                                <select id="correctChoice" x-model="questionForm.correctChoice" required>
                                                    <option value="1">گزینه ۱</option>
                                                    <option value="2">گزینه ۲</option>
                                                    <option value="3">گزینه ۳</option>
                                                    <option value="4">گزینه ۴</option>
                                                </select>
                                            </div>
                                        </div>
                                        <div class="control">
                                            <label class="label" for="questionDifficulty">سطح دشواری (اختیاری)</label>
                                            <div class="select">
                                                <select id="questionDifficulty" x-model="questionForm.difficulty">
                                                    <option value="">— نامشخص —</option>
                                                    <template x-for="level in difficultyLevels" :key="'opt-' + level.value">
                                                        <option :value="level.value" x-text="level.label"></option>
                                                    </template>
                                                </select>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="field">
                                        <label class="label" for="questionAnswer">توضیح پاسخ (اختیاری)</label>
                                        <textarea class="textarea" id="questionAnswer" x-model="questionForm.answer"></textarea>
                                    </div>
                                    <div class="field">
                                        <button class="button is-link" type="submit" :class="{ 'is-loading': submittingQuestion }">
                                            افزودن سؤال
                                        </button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </template>
            `)
    </div>
</section>
`)
