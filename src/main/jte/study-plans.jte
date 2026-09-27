@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "برنامه‌های مطالعه", styles = java.util.List.of("/css/my-study-plan.css"), scripts = java.util.List.of("/js/navbar.js", "/js/member-names.js", "/js/modal-focus.js", "/js/study-plans.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1"
         x-data="studyPlansPage()" @keydown.escape.window="closeGridBlock()">
    <div class="container">
        @template.layout.page-header(title = "برنامه‌های مطالعه")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت برنامه‌های مطالعه، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <template x-if="!blocked">
                    <div>
                        <div class="box">
                            <h2 class="title is-6">برنامه‌ها</h2>

                            @template.layout.empty-state(
                                show = "!loading && plans.length === 0",
                                art = "calendar",
                                tone = "is-light",
                                message = @`این مدرسه هنوز برنامه‌ی مطالعه‌ای ندارد. اولین برنامه را از فرم زیر بسازید.`)

                            <div class="table-container" x-show="plans.length > 0">
                            <table class="table is-fullwidth is-striped">
                                <thead>
                                <tr>
                                    <th scope="col">عنوان</th>
                                    <th scope="col">تعداد درس وزن‌دهی‌شده</th>
                                    <th scope="col"><span class="is-sr-only">جزئیات</span></th>
                                </tr>
                                </thead>
                                <tbody>
                                <template x-for="plan in plans" :key="plan.id">
                                    <tr>
                                        <td x-text="plan.title"></td>
                                        <td x-text="plan.weights.length"></td>
                                        <td>
                                            <button type="button" class="button is-small"
                                                    :class="{ 'is-link': expandedPlanId === plan.id }"
                                                    @click="togglePlanDetails(plan.id)">
                                                جزئیات برنامه
                                            </button>
                                        </td>
                                    </tr>
                                </template>
                                </tbody>
                            </table>
                            </div>

                            <template x-for="plan in plans" :key="'detail-' + plan.id">
                                <div class="box" x-show="expandedPlanId === plan.id" x-cloak>
                                        <div class="tabs is-toggle is-toggle-rounded is-small">
                                        <ul>
                                            <li :class="{ 'is-active': planDetailTab === 'assignments' }">
                                                <a href="#" @click.prevent="planDetailTab = 'assignments'">تخصیص به دانش‌آموز</a>
                                            </li>
                                            <li :class="{ 'is-active': planDetailTab === 'weekCourses' }">
                                                <a href="#" @click.prevent="selectWeekCoursesTab(plan.id)">سرفصل‌های هفتگی</a>
                                            </li>
                                        </ul>
                                    </div>

                                    <template x-if="planDetailTab === 'assignments'">
                                        <div>
                                            <h3 class="title is-6" x-text="'دانش‌آموزان تخصیص‌یافته به: ' + plan.title"></h3>
                                            <div class="table-container" x-show="assignments.length > 0">
                                            <table class="table is-fullwidth is-narrow">
                                                <tbody>
                                                <template x-for="assignment in assignments" :key="assignment.studentId">
                                                    <tr>
                                                        <td x-text="studentLabel(assignment.studentId)"></td>
                                                    </tr>
                                                </template>
                                                </tbody>
                                            </table>
                                            </div>
                                            <p class="has-text-grey mb-3" x-show="assignments.length === 0">
                                                هنوز دانش‌آموزی به این برنامه تخصیص داده نشده است.
                                            </p>
                                            <div class="field has-addons">
                                                <div class="control is-expanded">
                                                    <div class="select is-fullwidth">
                                                        <select x-model="assignStudentIdInput">
                                                            <option value="">— انتخاب دانش‌آموز —</option>
                                                            <template x-for="student in students" :key="'opt-' + student.studentId">
                                                                <option :value="student.studentId" x-text="studentLabel(student.studentId)"></option>
                                                            </template>
                                                        </select>
                                                    </div>
                                                </div>
                                                <div class="control">
                                                    <button class="button is-link" type="button"
                                                            :disabled="!assignStudentIdInput"
                                                            :class="{ 'is-loading': submittingAssignment }"
                                                            @click="assignStudent(plan.id)">
                                                        تخصیص
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </template>

                                    <template x-if="planDetailTab === 'weekCourses'">
                                        <div>
                                            <h3 class="title is-6" x-text="'سرفصل‌های هفتگیِ: ' + plan.title"></h3>
                                            <div class="table-container" x-show="weekCourses.length > 0">
                                            <table class="table is-fullwidth is-narrow">
                                                <thead>
                                                <tr>
                                                    <th scope="col">شروع هفته</th>
                                                    <th scope="col">سرفصل</th>
                                                    <th scope="col">زیرسرفصل‌ها</th>
                                                </tr>
                                                </thead>
                                                <tbody>
                                                <template x-for="weekCourse in weekCourses" :key="weekCourse.id">
                                                    <tr>
                                                        <td dir="ltr" x-text="weekCourse.weekStart"></td>
                                                        <td x-text="topicName(weekCourse.courseTopicId)"></td>
                                                        <td x-text="subTopicNames(weekCourse.subTopicIds) || '—'"></td>
                                                    </tr>
                                                </template>
                                                </tbody>
                                            </table>
                                            </div>
                                            <p class="has-text-grey mb-3" x-show="!loadingWeekCourses && weekCourses.length === 0">
                                                هنوز سرفصل هفتگی‌ای برای این برنامه ثبت نشده است.
                                            </p>

                                            <div class="field is-grouped is-grouped-multiline">
                                                <div class="control">
                                                    <label class="label" for="weekCourseStart">یک روز از هفته‌ی مورد نظر</label>
                                                    <input class="input" dir="ltr" id="weekCourseStart" type="date"
                                                           x-model="weekCourseForm.weekStart">
                                                </div>
                                                <div class="control is-expanded">
                                                    <label class="label" for="weekCourseTopic">سرفصل هفته</label>
                                                    <div class="select is-fullwidth">
                                                        <select id="weekCourseTopic" x-model="weekCourseForm.courseTopicId">
                                                            <option value="">— انتخاب کنید —</option>
                                                            <template x-for="node in flatTopics" :key="'wc-topic-' + node.id">
                                                                <option :value="node.id" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                                                            </template>
                                                        </select>
                                                    </div>
                                                </div>
                                            </div>

                                            <label class="label">زیرسرفصل‌ها (حداکثر ۱۰ مورد)</label>
                                            <div class="field is-grouped is-grouped-multiline mb-3">
                                                <template x-for="node in flatTopics" :key="'wc-sub-' + node.id">
                                                    <label class="checkbox app-checkbox-inline" x-show="String(node.id) !== String(weekCourseForm.courseTopicId)">
                                                        <input type="checkbox"
                                                               :checked="weekCourseForm.subTopicIds.includes(node.id)"
                                                               :disabled="!weekCourseForm.subTopicIds.includes(node.id) && weekCourseForm.subTopicIds.length >= 10"
                                                               @change="toggleWeekCourseSubTopic(node.id)">
                                                        <span x-text="node.name"></span>
                                                    </label>
                                                </template>
                                            </div>

                                            <div class="field">
                                                <button class="button is-link" type="button"
                                                        :disabled="!weekCourseForm.weekStart || !weekCourseForm.courseTopicId"
                                                        :class="{ 'is-loading': submittingWeekCourse }"
                                                        @click="createWeekCourse(plan.id)">
                                                    افزودن سرفصل هفتگی
                                                </button>
                                            </div>
                                        </div>
                                    </template>
                                </div>
                            </template>

                            <hr>
                            <h3 class="title is-6">ساخت برنامه‌ی تازه</h3>
                            <form @submit.prevent="createPlan">
                                <div class="field">
                                    <label class="label" for="planTitle">عنوان برنامه</label>
                                    <input class="input" id="planTitle" type="text" x-model="planForm.title" required>
                                </div>
                                <label class="label">وزن‌دهی درس‌ها</label>
                                <template x-for="(row, index) in planForm.weights" :key="index">
                                    <div class="field is-grouped is-grouped-multiline">
                                        <div class="control is-expanded">
                                            <div class="select is-fullwidth">
                                                <select x-model="row.topicId"
                                                        :aria-label="'سرفصل ردیف ' + (index + 1)">
                                                    <option value="">— سرفصل —</option>
                                                    <template x-for="node in flatTopics" :key="'w-' + index + '-' + node.id">
                                                        <option :value="node.id" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                                                    </template>
                                                </select>
                                            </div>
                                        </div>
                                        <div class="control">
                                                <input class="input app-input-w-6" type="number" min="0" step="0.1" placeholder="وزن"
                                                       :aria-label="'وزن ردیف ' + (index + 1)"
                                                       x-model="row.weight">
                                        </div>
                                        <div class="control">
                                                <input class="input app-input-w-6" type="number" min="1" placeholder="اولویت"
                                                       :aria-label="'اولویت ردیف ' + (index + 1)"
                                                       x-model="row.priority">
                                        </div>
                                        <div class="control">
                                            <button class="button is-danger is-light" type="button"
                                                    @click="removeWeightRow(index)">حذف</button>
                                        </div>
                                    </div>
                                </template>
                                <div class="field">
                                    <button class="button is-light is-small" type="button" @click="addWeightRow">
                                        افزودن درس
                                    </button>
                                </div>
                                <div class="field">
                                    <button class="button is-link" type="submit" :class="{ 'is-loading': submittingPlan }">
                                        ساخت برنامه
                                    </button>
                                </div>
                            </form>
                        </div>

                        <div class="box">
                            <h2 class="title is-6">برنامه‌ی زمانی و بازه‌های مطالعه‌ی دانش‌آموز</h2>
                            <div class="field">
                                <label class="label" for="studentPicker">دانش‌آموز</label>
                                <div class="select is-fullwidth">
                                    <select id="studentPicker" x-model="selectedStudentId" @change="selectStudent(selectedStudentId)">
                                        <option value="">— انتخاب کنید —</option>
                                        <template x-for="student in students" :key="'sel-' + student.studentId">
                                            <option :value="student.studentId" x-text="studentLabel(student.studentId)"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>

                            <template x-if="selectedStudentId">
                                <div>
                                    <h3 class="title is-6">برنامه‌ی زمانی آزاد هفتگی (پیش‌فرض)</h3>
                                    <template x-for="day in weekdays" :key="day.code">
                                        <div class="field is-grouped is-grouped-multiline mb-2">
                                            <div class="control">
                                                <span class="tag is-medium" x-text="day.label"></span>
                                            </div>
                                            <template x-for="(range, index) in scheduleForm[day.code]" :key="day.code + '-' + index">
                                                <div class="control">
                                                    <div class="field has-addons">
                                                        <div class="control">
                                                            <input class="input" dir="ltr" type="time" x-model="range.start"
                                                                   :aria-label="'شروع بازه‌ی ' + day.label + ' شماره ' + (index + 1)">
                                                        </div>
                                                        <div class="control">
                                                            <input class="input" dir="ltr" type="time" x-model="range.end"
                                                                   :aria-label="'پایان بازه‌ی ' + day.label + ' شماره ' + (index + 1)">
                                                        </div>
                                                        <div class="control">
                                                            <button class="button is-danger is-light" type="button"
                                                                    :aria-label="'حذف بازه‌ی ' + day.label + ' شماره ' + (index + 1)"
                                                                    @click="removeTimeRange(day.code, index)">حذف</button>
                                                        </div>
                                                    </div>
                                                </div>
                                            </template>
                                            <div class="control">
                                                <button class="button is-light is-small" type="button" @click="addTimeRange(day.code)">
                                                    افزودن بازه
                                                </button>
                                            </div>
                                        </div>
                                    </template>
                                    <div class="field">
                                        <button class="button is-link" type="button"
                                                :class="{ 'is-loading': savingSchedule }" @click="saveDefaultSchedule">
                                            ذخیره‌ی برنامه‌ی زمانی
                                        </button>
                                    </div>

                                    <hr>

                                    <h3 class="title is-6">بازه‌های مطالعه</h3>
                                    <div class="field is-grouped mb-3">
                                        <div class="control">
                                            <label class="label" for="weekDate">یک روز از هفته‌ی مورد نظر</label>
                                            <input class="input" dir="ltr" id="weekDate" type="date" x-model="weekDate" @change="loadWeek">
                                        </div>
                                        <div class="control app-control-align-end">
                                            <button class="button" type="button" :class="{ 'is-loading': suggesting }" @click="suggestWeek">
                                                پیشنهاد خودکار این هفته
                                            </button>
                                        </div>
                                        <div class="control app-control-align-end">
                                            <button class="button is-danger is-light" type="button"
                                                    :class="{ 'is-loading': clearing }" @click="clearWeek">
                                                خالی‌کردن این هفته
                                            </button>
                                        </div>
                                        <div class="control app-control-align-end">
                                            <button class="button" type="button" @click="copyPanelOpen = !copyPanelOpen">
                                                کپی این هفته برای دانش‌آموزان دیگر
                                            </button>
                                        </div>
                                    </div>

                                    <div class="box" x-show="copyPanelOpen" x-cloak>
                                        <h4 class="title is-6">کپی بازه‌های این هفته به دانش‌آموزان دیگر</h4>
                                        <p class="has-text-grey" x-show="otherStudents().length === 0">
                                            دانش‌آموز دیگری در این مدرسه برای کپی وجود ندارد.
                                        </p>
                                        <div class="field is-grouped is-grouped-multiline mb-3">
                                            <template x-for="student in otherStudents()" :key="'copy-target-' + student.studentId">
                                                <label class="checkbox app-checkbox-inline">
                                                    <input type="checkbox"
                                                           :checked="copyTargetIds.includes(student.studentId)"
                                                           @change="toggleCopyTarget(student.studentId)">
                                                    <span x-text="studentLabel(student.studentId)"></span>
                                                </label>
                                            </template>
                                        </div>
                                        <div class="field">
                                            <button class="button is-link" type="button"
                                                    :disabled="copyTargetIds.length === 0"
                                                    :class="{ 'is-loading': copyingWeek }" @click="copyWeekToOthers">
                                                کپی کردن
                                            </button>
                                        </div>
                                        <div class="table-container mt-3" x-show="copyResults.length > 0">
                                        <table class="table is-fullwidth is-narrow">
                                            <thead>
                                            <tr>
                                                <th scope="col">دانش‌آموز</th>
                                                <th scope="col">نتیجه</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <template x-for="result in copyResults" :key="'copy-result-' + result.studentId">
                                                <tr>
                                                    <td x-text="studentLabel(result.studentId)"></td>
                                                    <td>
                                                        <span class="tag" :class="result.ok ? 'is-success' : 'is-danger'"
                                                              x-text="result.ok ? 'موفق' : (result.message || 'ناموفق')"></span>
                                                    </td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
                                        </div>
                                    </div>

                                    <div class="sp-legend" x-show="!loadingWeek && intervals.length > 0">
                                        <span class="sp-legend-item"><span class="sp-swatch sp-swatch--pending"></span> در انتظار ثبت</span>
                                        <span class="sp-legend-item"><span class="sp-swatch sp-swatch--done"></span> مطالعه انجام‌شده</span>
                                    </div>

                                    <div class="box sp-grid-box" x-show="!loadingWeek && intervals.length > 0">
                                        <div class="sp-grid">
                                            <div class="sp-axis">
                                                <template x-for="h in hourMarks" :key="h">
                                                    <div class="sp-hour" :style="'top:' + hourMarkTop(h) + 'px'" x-text="hourMarkLabel(h)"></div>
                                                </template>
                                            </div>
                                            <template x-for="day in weekDays" :key="day.iso">
                                                <div class="sp-day" :class="{ 'sp-day--today': day.isToday }">
                                                    <div class="sp-day-head">
                                                        <div class="sp-day-name" x-text="day.name"></div>
                                                        <div :class="day.isToday ? 'sp-day-num sp-day-num--today' : 'sp-day-num'" x-text="day.dateNum"></div>
                                                    </div>
                                                    <div class="sp-day-body">
                                                        <template x-for="block in day.blocks" :key="block.id">
                                                            <button type="button" class="sp-block"
                                                                    :class="block.done ? 'sp-block--done' : 'sp-block--pending'"
                                                                    :style="'top:' + block.top + 'px; height:' + block.height + 'px'"
                                                                    @click="openGridBlock(block)">
                                                                <span class="sp-block-title" x-text="block.topicName"></span>
                                                                <span class="sp-block-time" x-show="block.showTime" x-text="block.timeLabel"></span>
                                                                <span class="sp-block-check" x-show="block.done">✓</span>
                                                            </button>
                                                        </template>
                                                    </div>
                                                </div>
                                            </template>
                                        </div>
                                    </div>

                                    @template.layout.empty-state(
                                        show = "!loadingWeek && intervals.length === 0",
                                        art = "calendar",
                                        message = @`برای این هفته هنوز بازه‌ی مطالعه‌ای ثبت نشده است. از «پیشنهاد خودکار» یا فرم زیر استفاده کنید.`)

                                    <h4 class="title is-6">افزودن بازه‌ی مطالعه‌ی دستی</h4>
                                    <form @submit.prevent="createInterval">
                                        <div class="field is-grouped is-grouped-multiline">
                                            <div class="control is-expanded">
                                                <label class="label" for="intervalTopic">سرفصل</label>
                                                <div class="select is-fullwidth">
                                                    <select id="intervalTopic" x-model="intervalForm.topicId" required>
                                                        <option value="">— انتخاب کنید —</option>
                                                        <template x-for="node in flatTopics" :key="'iv-' + node.id">
                                                            <option :value="node.id" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                                                        </template>
                                                    </select>
                                                </div>
                                            </div>
                                            <div class="control">
                                                <label class="label" for="intervalStart">شروع</label>
                                                <input class="input" dir="ltr" id="intervalStart" type="datetime-local"
                                                       x-model="intervalForm.start" required>
                                            </div>
                                            <div class="control">
                                                <label class="label" for="intervalEnd">پایان</label>
                                                <input class="input" dir="ltr" id="intervalEnd" type="datetime-local"
                                                       x-model="intervalForm.end" required>
                                            </div>
                                            <div class="control app-control-align-end">
                                                <button class="button is-link" type="submit" :class="{ 'is-loading': submittingInterval }">
                                                    افزودن بازه
                                                </button>
                                            </div>
                                        </div>
                                    </form>
                                </div>
                            </template>
                        </div>
                    </div>
                </template>
            `)
    </div>

    <%-- ------------------------------------------------------- بازه‌ی انتخاب‌شده --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="gridBlockTitle"
         tabindex="-1" x-ref="gridBlockModal" :class="{ 'is-active': gridBlock }" x-cloak
         @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeGridBlock">
        <div class="modal-background" @click="closeGridBlock"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="gridBlockTitle" x-text="gridBlock ? gridBlock.topicName : ''"></p>
                <button class="delete" type="button" aria-label="بستن" @click="closeGridBlock"></button>
            </header>
            <template x-if="gridBlock && gridBlockStep === 'detail'">
                <section class="modal-card-body">
                    <p class="has-text-grey mb-4" x-text="gridBlock ? gridBlock.timeLabel : ''"></p>
                    <p>
                        وضعیت:
                        <span class="tag" :class="gridBlock && gridBlock.done ? 'is-success' : 'is-light'"
                              x-text="gridBlock && gridBlock.done ? 'انجام‌شده' : 'انجام‌نشده'"></span>
                    </p>
                </section>
            </template>
            <template x-if="gridBlock && gridBlockStep === 'confirm-delete'">
                <section class="modal-card-body">
                    <p class="has-text-weight-semibold">آیا از حذف این بازه‌ی مطالعه مطمئن هستید؟</p>
                    <p class="has-text-grey" x-text="gridBlock ? gridBlock.topicName + ' — ' + gridBlock.timeLabel : ''"></p>
                </section>
            </template>
            <footer class="modal-card-foot">
                <template x-if="gridBlockStep === 'detail'">
                    <div class="app-display-contents">
                        <button class="button is-link" type="button" data-detail-action @click="toggleGridBlockDone">
                            <span x-text="gridBlock && gridBlock.done ? 'علامت‌گذاری به‌عنوان انجام‌نشده' : 'علامت‌گذاری به‌عنوان انجام‌شده'"></span>
                        </button>
                        <button class="button is-danger is-light" type="button" @click="showGridBlockDeleteConfirmation">حذف</button>
                        <button class="button" type="button" @click="closeGridBlock">بستن</button>
                    </div>
                </template>
                <template x-if="gridBlockStep === 'confirm-delete'">
                    <div class="app-display-contents">
                        <button class="button is-danger" type="button" data-confirm-delete @click="deleteGridBlock">حذف قطعی</button>
                        <button class="button" type="button" @click="cancelGridBlockDelete">انصراف</button>
                    </div>
                </template>
            </footer>
        </div>
    </div>
</section>
`)
