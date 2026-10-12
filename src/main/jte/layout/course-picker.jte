<%-- انتخابگر پایه و درس (course-picker.js). سه صفحه‌ی سرفصل، آزمون و برنامه‌ی مطالعه آن را
     استفاده می‌کنند؛ پس از سومین بار تکرار، به partial رفت (design-page، گام ۴).

     این partial داخل x-data همان صفحه نوشته می‌شود و فقط این فیلدها را می‌خواند:
     pickerGrade، pickerCourses، pickerCourseId، pickerCoursesLoading، pickerTopicsLoading، pickerError
     و متدهای pickerLoadCourses() و pickerLoadTopics() از CoursePicker.

     هیچ پارامتری ندارد؛ هر صفحه فقط همین را include می‌کند. --%>
<div class="box mb-4" role="group" aria-labelledby="coursePickerTitle">
    <h2 class="title is-6" id="coursePickerTitle">پایه و درس</h2>

    <div class="columns is-mobile is-multiline">
        <div class="column is-one-third-tablet is-full-mobile">
            <div class="field">
                <label class="label" for="coursePickerGrade">پایه</label>
                <div class="select is-fullwidth">
                    <select id="coursePickerGrade" x-model="pickerGrade" @change="pickerLoadCourses()">
                        <option value="">— انتخاب پایه —</option>
                        <template x-for="grade in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]" :key="'grade-' + grade">
                            <option :value="grade" x-text="grade.toLocaleString('fa-IR-u-nu-arabext')"></option>
                        </template>
                    </select>
                </div>
            </div>
        </div>

        <div class="column is-two-thirds-tablet is-full-mobile">
            <div class="field">
                <label class="label" for="coursePickerCourse">درس</label>
                <div class="select is-fullwidth" :class="{ 'is-loading': pickerCoursesLoading || pickerTopicsLoading }">
                    <select id="coursePickerCourse" x-model="pickerCourseId"
                            :disabled="!pickerGrade || pickerCourses.length === 0"
                            @change="pickerLoadTopics()">
                        <option value="">— انتخاب درس —</option>
                        <template x-for="course in pickerCourses" :key="'course-' + course.id">
                            <option :value="course.id"
                                    x-text="course.name + (course.track ? '' : ' (مشترک)')"></option>
                        </template>
                    </select>
                </div>
            </div>
        </div>
    </div>

    <p class="help is-danger" x-show="pickerError" x-text="pickerError"></p>

    <p class="has-text-grey" x-show="!pickerGrade">
        یک پایه و درس را انتخاب کنید تا سرفصل‌های همان درس نشان داده شود.
    </p>
    <p class="has-text-grey" x-show="pickerGrade && !pickerCoursesLoading && pickerCourses.length === 0 && !pickerError">
        برای این پایه درسی که به سرفصل وصل باشد پیدا نشد.
    </p>
</div>
