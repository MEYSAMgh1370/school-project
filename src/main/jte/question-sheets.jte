@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "برگه‌های سؤال", styles = java.util.List.of("/webjars/katex/0.16.22/dist/katex.min.css", "/css/question-box.css"), scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/js/list-search.js", "/js/modal-focus.js", "/webjars/katex/0.16.22/dist/katex.min.js", "/js/math.js", "/js/question-box-common.js", "/js/question-sheets.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="questionSheetsPage()">
    <div class="container">
        <div class="level">
            <div class="level-left">
                @template.layout.page-header(
                    title = "برگه‌های سؤال",
                    subtitle = "سؤال‌های باکس و بانک را در بخش‌های عنوان‌دار کنار هم بگذارید و با سربرگ مدرسه چاپ کنید.")
            </div>
            <div class="level-right">
                <a class="button is-light" href="/question-box" x-show="view === 'list'">باکس سؤالات</a>
                <button type="button" class="button is-light" x-show="view === 'builder'" @click="backToList">بازگشت به فهرست</button>
                <button type="button" class="button is-link" id="sheetNew" x-show="view === 'list' && tab === 'sheets'" @click="newSheet">برگه‌ی تازه</button>
                <button type="button" class="button is-link" id="headerNew" x-show="view === 'list' && tab === 'headers'" @click="openHeaderCreate($event)">سربرگ تازه</button>
                <button type="button" class="button is-link" id="sheetSave" x-show="view === 'builder'"
                        :class="{ 'is-loading': saving }" @click="saveSheet">ذخیره‌ی برگه</button>
            </div>
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای ساختن برگه‌ی سؤال، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`
                <template x-if="!blocked">
                    <div>
                        <%-- ------------------------------------------------ فهرست برگه‌ها و سربرگ‌ها --%>
                        <div x-show="view === 'list'">
                            <div class="tabs">
                                <ul>
                                    <li :class="{ 'is-active': tab === 'sheets' }"><a href="#" @click.prevent="tab = 'sheets'">برگه‌ها</a></li>
                                    <li :class="{ 'is-active': tab === 'headers' }"><a href="#" @click.prevent="tab = 'headers'">سربرگ‌ها</a></li>
                                </ul>
                            </div>

                            <div x-show="tab === 'sheets'">
                                <div class="app-list" role="list" x-show="sheets.length > 0">
                                    <template x-for="sheet in sheets" :key="sheet.id">
                                        <div class="app-question-row" role="listitem">
                                            <div class="app-question-row__main">
                                                <span class="app-question-row__name" x-text="sheet.title"></span>
                                                <span class="is-size-7 has-text-grey" x-text="QbApi.digits(sheet.itemCount) + ' سؤال'"></span>
                                            </div>
                                            <a class="button is-light" :href="'/question-sheets/' + sheet.id + '/print'">پیش‌نمایش و چاپ</a>
                                            <button type="button" class="button is-light" @click="openSheet(sheet.id)">ویرایش</button>
                                            <button type="button" class="button is-light is-danger" @click="askDeleteSheet(sheet, $event)">حذف</button>
                                        </div>
                                    </template>
                                </div>
                                @template.layout.empty-state(
                                    show = "!loading && sheets.length === 0",
                                    art = "exam",
                                    message = @`هنوز برگه‌ای نساخته‌اید. با «برگه‌ی تازه» شروع کنید؛ اگر سربرگ ندارید، اول در تب «سربرگ‌ها» یکی بسازید.`)
                            </div>

                            <div x-show="tab === 'headers'">
                                <div class="app-list" role="list" x-show="headers.length > 0">
                                    <template x-for="header in headers" :key="header.id">
                                        <div class="app-question-row" role="listitem">
                                            <div class="app-question-row__main">
                                                <span class="app-question-row__name" x-text="header.name"></span>
                                                <span class="is-size-7 has-text-grey" x-text="header.mode === 'IMAGE' ? 'سربرگ تصویری' : 'سربرگ قالبی: ' + layoutLabel(header.layout)"></span>
                                            </div>
                                            <span class="tag is-warning is-light" x-show="!header.complete">تصویر لازم دارد</span>
                                            <button type="button" class="button is-light" @click="openHeaderEdit(header, $event)">ویرایش</button>
                                            <button type="button" class="button is-light is-danger" @click="askDeleteHeader(header, $event)">حذف</button>
                                        </div>
                                    </template>
                                </div>
                                @template.layout.empty-state(
                                    show = "!loading && headers.length === 0",
                                    art = "list",
                                    message = @`هنوز سربرگی ندارید. با «سربرگ تازه» یک سربرگ قالبی بسازید یا عکس سربرگ خودتان را بارگذاری کنید.`)
                            </div>
                        </div>

                        <%-- ------------------------------------------------ سازنده‌ی برگه --%>
                        <div x-show="view === 'builder'" x-cloak>
                            <div class="box">
                                <div class="field">
                                    <label class="label" for="sheetTitle">عنوان برگه</label>
                                    <input class="input" id="sheetTitle" type="text" x-model="draft.title" :class="{ 'is-danger': errors.title }">
                                    <p class="help is-danger" x-show="errors.title" x-text="errors.title"></p>
                                </div>
                                <div class="field">
                                    <label class="label" for="sheetHeader">سربرگ</label>
                                    <div class="select is-fullwidth">
                                        <select id="sheetHeader" x-model="draft.headerId" :class="{ 'is-danger': errors.header }">
                                            <option value="">بدون سربرگ</option>
                                            <template x-for="header in usableHeaders()" :key="'sheet-header-' + header.id">
                                                <option :value="String(header.id)" x-text="header.name"></option>
                                            </template>
                                        </select>
                                    </div>
                                    <p class="help is-danger" x-show="errors.header" x-text="errors.header"></p>
                                </div>
                                <div class="field is-grouped is-grouped-multiline" x-show="headerValueFields().length > 0">
                                    <template x-for="field in headerValueFields()" :key="'hv-' + field">
                                        <div class="control is-expanded">
                                            <label class="label" :for="'hv-' + field" x-text="fieldLabel(field)"></label>
                                            <input class="input" :id="'hv-' + field" type="text" x-model="draft.headerValues[field]">
                                        </div>
                                    </template>
                                </div>
                                <div class="field">
                                    <label class="checkbox"><input type="checkbox" x-model="draft.showEssayAnswerSpace"> جای پاسخ زیر سؤال‌های تشریحی</label>
                                </div>
                                <div class="field">
                                    <label class="checkbox"><input type="checkbox" x-model="draft.includeAnswerKey"> چاپ پاسخنامه در صفحه‌ی جدا</label>
                                </div>
                                <p class="help is-danger" x-show="errors.sections" x-text="errors.sections"></p>
                            </div>

                            <template x-for="(section, si) in draft.sections" :key="section.key">
                                <div class="box">
                                    <div class="field is-grouped is-grouped-multiline">
                                        <div class="control is-expanded">
                                            <label class="label" :for="'secTitle' + si" x-text="'عنوان بخش ' + QbApi.digits(si + 1)"></label>
                                            <input class="input" :id="'secTitle' + si" type="text" x-model="section.title"
                                                   :class="{ 'is-danger': errors['section' + si] }">
                                            <p class="help is-danger" x-show="errors['section' + si]" x-text="errors['section' + si]"></p>
                                        </div>
                                        <div class="control is-expanded">
                                            <label class="label" :for="'secHint' + si">توضیح بخش (اختیاری)</label>
                                            <input class="input" :id="'secHint' + si" type="text" x-model="section.instruction">
                                        </div>
                                    </div>

                                    <div class="app-list" role="list" x-show="section.items.length > 0">
                                        <template x-for="(item, ii) in section.items" :key="item.source + '-' + item.questionId">
                                            <div class="app-question-row" role="listitem">
                                                <div class="app-question-row__main">
                                                    <span class="app-question-row__name">
                                                        <span x-show="previewText(item)" x-math="previewText(item)"></span>
                                                        <span class="has-text-grey" x-show="!previewText(item)" x-text="previewFallback(item)"></span>
                                                    </span>
                                                    <span class="is-size-7 has-text-grey" x-text="previewMeta(item)"></span>
                                                </div>
                                                <button type="button" class="button is-light" aria-label="بالا" @click="moveItem(si, ii, -1)" :disabled="ii === 0">▲</button>
                                                <button type="button" class="button is-light" aria-label="پایین" @click="moveItem(si, ii, 1)" :disabled="ii === section.items.length - 1">▼</button>
                                                <button type="button" class="button is-light is-danger" @click="removeItem(si, ii)">برداشتن</button>
                                            </div>
                                        </template>
                                    </div>
                                    <p class="has-text-grey" x-show="section.items.length === 0">این بخش هنوز سؤالی ندارد؛ با «افزودن سؤال» از باکس یا بانک بردارید.</p>

                                    <div class="buttons mt-3">
                                        <button type="button" class="button is-light" @click="openPicker(si, $event)">افزودن سؤال</button>
                                        <button type="button" class="button is-light" @click="moveSection(si, -1)" :disabled="si === 0">بخش را بالا ببر</button>
                                        <button type="button" class="button is-light" @click="moveSection(si, 1)" :disabled="si === draft.sections.length - 1">بخش را پایین ببر</button>
                                        <button type="button" class="button is-light is-danger" @click="removeSection(si)">حذف بخش</button>
                                    </div>
                                </div>
                            </template>

                            <div class="buttons">
                                <button type="button" class="button is-light" @click="addSection">افزودن بخش</button>
                                <a class="button is-light" x-show="draft.id" :href="'/question-sheets/' + draft.id + '/print'">پیش‌نمایش و چاپ</a>
                            </div>
                        </div>
                    </div>
                </template>
            `)
    </div>

    <%-- ------------------------------------------------ انتخاب سؤال برای یک بخش --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="pickerTitle"
         tabindex="-1" x-ref="pickerModal" :class="{ 'is-active': picker }" x-cloak
         @keydown.tab="window.ModalFocus && window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closePicker">
        <div class="modal-background" @click="closePicker"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="pickerTitle">افزودن سؤال به بخش</p>
                <button class="delete" type="button" aria-label="بستن" @click="closePicker"></button>
            </header>
            <template x-if="picker">
            <section class="modal-card-body">
                <div class="tabs is-small">
                    <ul>
                        <li :class="{ 'is-active': picker && picker.tab === 'BOX' }"><a href="#" @click.prevent="pickTab('BOX')">باکس سؤالات</a></li>
                        <li :class="{ 'is-active': picker && picker.tab === 'BANK' }"><a href="#" @click.prevent="pickTab('BANK')">بانک سؤال</a></li>
                    </ul>
                </div>
                <div class="notification is-warning" role="alert" x-show="picker && picker.error" x-text="picker ? picker.error : ''"></div>

                <div class="field" x-show="picker && picker.tab === 'BANK'">
                    <label class="label" for="pickerTopic">سرفصل</label>
                    <div class="select is-fullwidth">
                        <select id="pickerTopic" x-model="picker.topicId" @change="loadBankQuestions">
                            <option value="">— یک سرفصل انتخاب کنید —</option>
                            <template x-for="node in topicsFlat" :key="'pt-' + node.id">
                                <option :value="String(node.id)" x-text="'—'.repeat(node.depth) + ' ' + node.name"></option>
                            </template>
                        </select>
                    </div>
                </div>
                <div class="field">
                    <label class="label is-sr-only" for="pickerSearch">جست‌وجو</label>
                    <input class="input" id="pickerSearch" type="search" x-model="picker.search" placeholder="جست‌وجوی متن سؤال">
                </div>

                <div class="app-list" role="list" x-show="pickerRows().length > 0">
                    <template x-for="row in pickerRows()" :key="row.id">
                        <div class="app-question-row" role="listitem">
                            <div class="app-question-row__main">
                                <span class="app-question-row__name">
                                    <span x-show="row.text" x-math="row.text"></span>
                                    <span class="has-text-grey" x-show="!row.text">سؤال فقط‌تصویر</span>
                                </span>
                                <span class="is-size-7 has-text-grey" x-text="row.type === 'ESSAY' ? 'تشریحی' : 'تستی'"></span>
                            </div>
                            <span class="tag is-light" x-show="isPicked(row)">افزوده شد</span>
                            <button type="button" class="button is-light" :disabled="isPicked(row) || !row.complete" @click="pick(row)">افزودن</button>
                        </div>
                    </template>
                </div>
                <p class="has-text-grey" x-show="picker && !picker.loading && pickerRows().length === 0">سؤالی برای نمایش نیست.</p>
            </section>
            </template>
            <footer class="modal-card-foot">
                <button class="button is-link" type="button" @click="closePicker">پایان</button>
            </footer>
        </div>
    </div>

    <%-- ------------------------------------------------ سربرگ --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="headerFormTitle"
         tabindex="-1" x-ref="headerModal" :class="{ 'is-active': headerForm }" x-cloak
         @keydown.tab="window.ModalFocus && window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeHeaderForm">
        <div class="modal-background" @click="closeHeaderForm"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="headerFormTitle" x-text="headerForm && headerForm.id ? 'ویرایش سربرگ' : 'سربرگ تازه'"></p>
                <button class="delete" type="button" aria-label="بستن" @click="closeHeaderForm"></button>
            </header>
            <template x-if="headerForm">
            <section class="modal-card-body">
                <div class="notification is-danger" role="alert" x-show="headerForm && headerForm.error">
                    <span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="headerForm ? headerForm.error : ''"></span></span>
                </div>
                <div class="field">
                    <label class="label" for="headerName">نام سربرگ</label>
                    <input class="input" id="headerName" type="text" x-model="headerForm.name" :class="{ 'is-danger': headerForm.errors.name }">
                    <p class="help is-danger" x-show="headerForm.errors.name" x-text="headerForm.errors.name"></p>
                </div>
                <div class="field">
                    <label class="label" for="headerMode">نوع سربرگ</label>
                    <div class="select is-fullwidth">
                        <select id="headerMode" x-model="headerForm.mode" :disabled="headerForm.id">
                            <option value="LAYOUT">قالب آماده (فیلدهای قابل‌انتخاب)</option>
                            <option value="IMAGE">تصویر (سربرگ طراحی‌شده‌ی خودم)</option>
                        </select>
                    </div>
                </div>
                <div x-show="headerForm.mode === 'LAYOUT'">
                    <div class="field">
                        <label class="label" for="headerLayout">چیدمان</label>
                        <div class="select is-fullwidth">
                            <select id="headerLayout" x-model="headerForm.layout">
                                <template x-for="layout in layouts" :key="layout.value">
                                    <option :value="layout.value" x-text="layout.label"></option>
                                </template>
                            </select>
                        </div>
                    </div>
                    <div class="field">
                        <label class="checkbox"><input type="checkbox" x-model="headerForm.showSchoolName"> نام مدرسه بالای سربرگ</label>
                    </div>
                    <div class="field">
                        <span class="label">فیلدهای سربرگ</span>
                        <template x-for="field in allFields" :key="'hf-' + field.value">
                            <label class="checkbox qb-field-check">
                                <input type="checkbox" :value="field.value" x-model="headerForm.fields"> <span x-text="field.label"></span>
                            </label>
                        </template>
                    </div>
                </div>
                <div x-show="headerForm.mode === 'IMAGE'">
                    <p class="has-text-grey" x-show="!headerForm.id">برای بارگذاری تصویر، ابتدا سربرگ را ذخیره کنید؛ همین پنجره باز می‌ماند.</p>
                    <div x-show="headerForm.id">
                        <img class="qb-header-preview" x-show="headerForm.hasImage" x-qbimg="{ kind: 'header', id: headerForm.hasImage ? headerForm.id : null }" alt="پیش‌نمایش سربرگ">
                        <div class="field mt-3">
                            <label class="label" for="headerFile">فایل سربرگ (PNG، JPEG یا WebP، تا ۵ مگابایت)</label>
                            <input class="input" id="headerFile" type="file" accept="image/png,image/jpeg,image/webp"
                                   @change="uploadHeaderImage($event)" :disabled="uploading">
                        </div>
                    </div>
                </div>
            </section>
            </template>
            <footer class="modal-card-foot">
                <button class="button is-link" type="button" :class="{ 'is-loading': savingHeader }" @click="saveHeader">ذخیره</button>
                <button class="button is-light" type="button" @click="closeHeaderForm">بستن</button>
            </footer>
        </div>
    </div>

    <%-- ------------------------------------------------ تأیید حذف (برگه یا سربرگ) --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="sheetDeleteTitle"
         tabindex="-1" x-ref="deleteModal" :class="{ 'is-active': removal }" x-cloak
         @keydown.tab="window.ModalFocus && window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeRemoval">
        <div class="modal-background" @click="closeRemoval"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="sheetDeleteTitle" x-text="removal && removal.kind === 'header' ? 'حذف سربرگ' : 'حذف برگه'"></p>
                <button class="delete" type="button" aria-label="بستن" @click="closeRemoval"></button>
            </header>
            <section class="modal-card-body">
                <p x-text="removal ? '«' + removal.label + '» حذف می‌شود.' : ''"></p>
            </section>
            <footer class="modal-card-foot">
                <button class="button is-danger" type="button" :class="{ 'is-loading': deleting }" @click="confirmRemoval">حذف</button>
                <button class="button is-light" type="button" @click="closeRemoval">انصراف</button>
            </footer>
        </div>
    </div>
</section>
`)
