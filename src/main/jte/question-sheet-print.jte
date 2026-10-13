@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar
@param Long sheetId

<%-- صفحه‌ی چاپ برگه (openspec/changes/question-box.md): عمداً بدون ناوبار تا فقط خودِ برگه روی کاغذ بیاید.
     مرجع چاپ Chrome است (@media print در /css/question-box.css). نام مدرسه‌ها از data-attribute خوانده می‌شود. --%>
@template.layout.main(title = "چاپ برگه‌ی سؤال", styles = java.util.List.of("/webjars/katex/0.16.22/dist/katex.min.css", "/css/question-box.css"), scripts = java.util.List.of("/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/webjars/katex/0.16.22/dist/katex.min.js", "/js/math.js", "/js/question-box-common.js", "/js/question-sheet-print.js"), content = @`
<main id="main-content" role="main" tabindex="-1" x-data="questionSheetPrintPage()" data-sheet-id="${sheetId}">
    @for(var school : navbar.schools())
        <span hidden data-school-id="${school.id()}" data-school-name="${school.name()}"></span>
    @endfor

    <div class="container qb-print-toolbar no-print">
        <div class="level">
            <div class="level-left">
                <h1 class="title is-5">پیش‌نمایش چاپ</h1>
            </div>
            <div class="level-right">
                <a class="button is-light" href="/question-sheets">بازگشت به برگه‌ها</a>
                <button type="button" class="button is-link" id="sheetPrint" @click="printSheet">چاپ</button>
            </div>
        </div>
        <div class="notification is-danger" role="alert" x-show="error" x-text="error"></div>
        <p class="help" x-show="sheet">برای ذخیره به‌صورت PDF، در پنجره‌ی چاپ مقصد را «Save as PDF» بگذارید.</p>
    </div>

    <article class="qb-sheet" x-show="sheet" x-cloak>
        <%-- سربرگ تصویری --%>
        <template x-if="sheet && sheet.header && sheet.header.mode === 'IMAGE'">
            <img class="qb-sheet-header-image" x-qbimg="{ kind: 'header', id: sheet.header.id }" alt="سربرگ">
        </template>

        <%-- سربرگ قالبی --%>
        <template x-if="sheet && sheet.header && sheet.header.mode === 'LAYOUT'">
            <header class="qb-sheet-header" :class="'qb-sheet-header--' + sheet.header.layout.toLowerCase()">
                <p class="qb-sheet-school" x-show="sheet.header.showSchoolName && schoolName" x-text="schoolName"></p>
                <div class="qb-sheet-fields">
                    <template x-for="field in sheet.header.fields" :key="field">
                        <div class="qb-sheet-field" :class="{ 'qb-sheet-field--score': field === 'SCORE_BOX' }">
                            <span class="qb-sheet-field-label" x-text="fieldLabel(field)"></span>
                            <span class="qb-sheet-field-value" x-text="fieldValue(field)"></span>
                        </div>
                    </template>
                </div>
            </header>
        </template>

        <h2 class="qb-sheet-title" x-text="sheet ? sheet.title : ''"></h2>

        <template x-for="(section, si) in (sheet ? sheet.sections : [])" :key="'s' + si">
            <section class="qb-section">
                <h3 class="qb-section-title" x-text="section.title"></h3>
                <p class="qb-section-instruction" x-show="section.instruction" x-text="section.instruction"></p>

                <template x-for="item in section.items" :key="'q' + item.number">
                    <div class="qb-question">
                        <template x-if="!item.available">
                            <p class="qb-question-missing" x-text="num(item.number) + '. (این سؤال دیگر در دسترس نیست)'"></p>
                        </template>
                        <template x-if="item.available">
                            <div>
                                <div class="qb-question-head">
                                    <span class="qb-question-number" x-text="num(item.number) + '.'"></span>
                                    <span class="qb-question-text" x-show="item.text" x-math="item.text"></span>
                                    <span class="qb-question-score" x-show="item.suggestedScore" x-text="'(' + num(item.suggestedScore) + ' نمره)'"></span>
                                </div>
                                <template x-for="image in imagesOf(item, 'STEM')" :key="'st' + image.id">
                                    <img class="qb-question-image" x-qbimg="{ kind: 'question', id: image.id }" alt="">
                                </template>
                                <ol class="qb-choices" x-show="item.type === 'MULTIPLE_CHOICE' && item.choices.length === 4">
                                    <template x-for="(choice, ci) in item.choices" :key="'c' + ci">
                                        <li>
                                            <span class="qb-choice-label" x-text="choiceLetter(ci) + ')'"></span>
                                            <span x-math="choice"></span>
                                            <template x-for="image in imagesOf(item, 'CHOICE_' + (ci + 1))" :key="'ci' + image.id">
                                                <img class="qb-choice-image" x-qbimg="{ kind: 'question', id: image.id }" alt="">
                                            </template>
                                        </li>
                                    </template>
                                </ol>
                                <ol class="qb-choices" x-show="item.type === 'MULTIPLE_CHOICE' && item.choices.length === 0 && choiceImageCount(item) > 0">
                                    <template x-for="n in [1,2,3,4]" :key="'ic' + n">
                                        <li x-show="imagesOf(item, 'CHOICE_' + n).length > 0">
                                            <span class="qb-choice-label" x-text="choiceLetter(n - 1) + ')'"></span>
                                            <template x-for="image in imagesOf(item, 'CHOICE_' + n)" :key="'ici' + image.id">
                                                <img class="qb-choice-image" x-qbimg="{ kind: 'question', id: image.id }" alt="">
                                            </template>
                                        </li>
                                    </template>
                                </ol>
                                <div class="qb-answer-space" x-show="item.type === 'ESSAY' && sheet.showEssayAnswerSpace" aria-hidden="true"></div>
                            </div>
                        </template>
                    </div>
                </template>
            </section>
        </template>

        <%-- پاسخنامه در صفحه‌ی جدا --%>
        <section class="qb-answer-key" x-show="sheet && sheet.includeAnswerKey">
            <h2 class="qb-sheet-title">پاسخنامه — <span x-text="sheet ? sheet.title : ''"></span></h2>
            <template x-for="item in answerKeyItems()" :key="'a' + item.number">
                <div class="qb-key-row">
                    <span class="qb-question-number" x-text="num(item.number) + '.'"></span>
                    <span x-show="item.type === 'MULTIPLE_CHOICE' && item.correctChoice" x-text="'گزینه ' + choiceLetter(item.correctChoice - 1)"></span>
                    <span x-show="item.answer" x-math="item.answer"></span>
                    <template x-for="image in imagesOf(item, 'ANSWER')" :key="'ak' + image.id">
                        <img class="qb-question-image" x-qbimg="{ kind: 'question', id: image.id }" alt="">
                    </template>
                </div>
            </template>
        </section>
    </article>
</main>
`)
