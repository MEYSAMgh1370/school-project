@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "باکس سؤالات", styles = java.util.List.of("/webjars/katex/0.16.22/dist/katex.min.css", "/css/question-box.css"), scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/jalali.js", "/js/calendar.js", "/js/list-search.js", "/js/modal-focus.js", "/webjars/katex/0.16.22/dist/katex.min.js", "/js/math.js", "/js/question-box-common.js", "/js/question-box.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="questionBoxPage()">
    <div class="container">
        <div class="qb-page-head">
            <div class="qb-page-head__title">
                <div>
                    @template.layout.page-header(
                    title = "باکس سؤالات",
                    subtitle = "سؤال‌های تستی و تشریحی معلم‌های مدرسه؛ برای ساختن برگه‌ی چاپی از «برگه‌های سؤال» استفاده کنید.")
                </div>
            </div>
            <div class="qb-page-head__actions">
                <div class="buttons">
                <a class="button is-light" href="/question-sheets">برگه‌های سؤال</a>
                <button type="button" class="button is-link" id="questionAdd" @click="openCreate($event)">افزودن سؤال</button>
                </div>
            </div>
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای دیدن باکس سؤالات، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`
                <template x-if="!blocked">
                    <div class="box">
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control is-expanded">
                                <label class="label is-sr-only" for="qbSearch">جست‌وجو در سؤال‌ها</label>
                                <input class="input" id="qbSearch" type="search" x-model="filterText" placeholder="جست‌وجوی متن سؤال یا گزینه‌ها">
                            </div>
                            <div class="control">
                                <label class="label is-sr-only" for="qbType">نوع سؤال</label>
                                <div class="select">
                                    <select id="qbType" x-model="typeFilter">
                                        <option value="">همه‌ی نوع‌ها</option>
                                        <option value="MULTIPLE_CHOICE">تستی</option>
                                        <option value="ESSAY">تشریحی</option>
                                    </select>
                                </div>
                            </div>
                        </div>

                        <div class="app-list" role="list" x-show="visibleQuestions().length > 0">
                            <template x-for="question in visibleQuestions()" :key="question.id">
                                <div class="app-question-row" role="listitem">
                                    <div class="app-question-row__main">
                                        <span class="app-question-row__name">
                                            <span x-show="question.text" x-math="question.text"></span>
                                            <span class="has-text-grey" x-show="!question.text">سؤال فقط‌تصویر</span>
                                        </span>
                                        <span class="is-size-7 has-text-grey">
                                            <span x-text="typeLabel(question)"></span>
                                            <span x-show="question.images.length > 0" x-text="' · ' + QbApi.digits(question.images.length) + ' تصویر'"></span>
                                            <span x-show="!question.editable"> · سؤال همکار</span>
                                        </span>
                                    </div>
                                    <span class="tag is-warning is-light" x-show="!question.complete">تصویر لازم دارد</span>
                                    <button type="button" class="button is-light" @click="openEdit(question, $event)">ویرایش</button>
                                    <button type="button" class="button is-light is-danger" @click="askDelete(question, $event)">حذف</button>
                                </div>
                            </template>
                        </div>
                        <p class="has-text-grey mt-3" x-show="questions.length > 0 && filteredQuestions().length === 0">
                            سؤالی با این جست‌وجو پیدا نشد.
                        </p>
                        <button type="button" class="button is-light mt-3"
                                x-show="filteredQuestions().length > shown" @click="showMore()"
                                x-text="'نمایش بیشتر (' + remainingLabel() + ' مورد دیگر)'"></button>

                        @template.layout.empty-state(
                            show = "!loading && questions.length === 0",
                            art = "exam",
                            message = @`باکس هنوز سؤالی ندارد. با «افزودن سؤال» اولین سؤال تستی یا تشریحی را بسازید؛ سؤال‌ها برای همه‌ی معلم‌های مدرسه دیده می‌شوند.`)
                    </div>
                </template>
            `)
    </div>

    <%-- ------------------------------------------------ افزودن / ویرایش سؤال --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="qbFormTitle"
         tabindex="-1" x-ref="formModal" :class="{ 'is-active': form }" x-cloak
         @keydown.tab="window.ModalFocus && window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeForm">
        <div class="modal-background" @click="closeForm"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="qbFormTitle" x-text="form && form.id ? 'ویرایش سؤال' : 'افزودن سؤال'"></p>
                <button class="delete" type="button" aria-label="بستن" @click="closeForm"></button>
            </header>
            <template x-if="form">
            <section class="modal-card-body">
                <div class="notification is-danger" role="alert" x-show="form && form.error">
                    <span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="form ? form.error : ''"></span></span>
                </div>

                <div class="field is-grouped is-grouped-multiline">
                    <div class="control">
                        <label class="label" for="qbFormType">نوع سؤال</label>
                        <div class="select">
                            <select id="qbFormType" x-model="form.type">
                                <option value="MULTIPLE_CHOICE">تستی (چهارگزینه‌ای)</option>
                                <option value="ESSAY">تشریحی</option>
                            </select>
                        </div>
                    </div>
                    <div class="control">
                        <label class="label" for="qbFormMode">شکل سؤال</label>
                        <div class="select">
                            <select id="qbFormMode" x-model="form.contentMode">
                                <option value="TEXT">متنی (با امکان پیوست تصویر)</option>
                                <option value="IMAGE_ONLY">فقط تصویر (عکس کل سؤال)</option>
                            </select>
                        </div>
                    </div>
                </div>

                <div class="field" x-show="form.contentMode === 'TEXT'">
                    <label class="label" for="qbFormText">متن سؤال</label>
                    <textarea class="textarea" id="qbFormText" x-model="form.text"
                              :class="{ 'is-danger': form.errors.text }"></textarea>
                    <p class="help">فرمول را میان دو علامت دلار بنویسید، مثل $x^2$.</p>
                    <p class="help is-danger" x-show="form.errors.text" x-text="form.errors.text"></p>
                </div>

                <div x-show="form.type === 'MULTIPLE_CHOICE' && form.contentMode === 'TEXT'">
                    <template x-for="n in [1,2,3,4]" :key="'qb-choice-' + n">
                        <div class="field">
                            <label class="label" :for="'qbChoice' + n" x-text="'گزینه ' + QbApi.digits(n)"></label>
                            <input class="input" :id="'qbChoice' + n" type="text" x-model="form.choices[n - 1]"
                                   :class="{ 'is-danger': form.errors.choices }">
                        </div>
                    </template>
                    <p class="help is-danger" x-show="form.errors.choices" x-text="form.errors.choices"></p>
                </div>

                <div class="field" x-show="form.type === 'MULTIPLE_CHOICE'">
                    <label class="label" for="qbCorrect">گزینه‌ی درست</label>
                    <div class="select">
                        <select id="qbCorrect" x-model="form.correctChoice">
                            <option value="1">گزینه ۱</option>
                            <option value="2">گزینه ۲</option>
                            <option value="3">گزینه ۳</option>
                            <option value="4">گزینه ۴</option>
                        </select>
                    </div>
                </div>

                <div class="field">
                    <label class="label" for="qbAnswer">پاسخ یا راه‌حل (اختیاری)</label>
                    <textarea class="textarea" id="qbAnswer" x-model="form.answer"></textarea>
                </div>

                <div class="field">
                    <label class="label" for="qbScore">نمره‌ی پیشنهادی (اختیاری)</label>
                    <input class="input" id="qbScore" type="text" inputmode="numeric" dir="ltr" x-model="form.suggestedScore"
                           :class="{ 'is-danger': form.errors.suggestedScore }">
                    <p class="help is-danger" x-show="form.errors.suggestedScore" x-text="form.errors.suggestedScore"></p>
                </div>

                <hr>
                <h3 class="title is-6">تصویرها</h3>
                <p class="has-text-grey" x-show="!form.id">
                    برای افزودن تصویر، ابتدا سؤال را ذخیره کنید؛ همین پنجره باز می‌ماند.
                </p>
                <div x-show="form.id">
                    <div class="qb-image-grid" x-show="form.images.length > 0">
                        <template x-for="image in form.images" :key="image.id">
                            <figure class="qb-image-tile">
                                <img x-qbimg="{ kind: 'question', id: image.id }" :alt="roleLabel(image.role)">
                                <figcaption class="is-size-7" x-text="roleLabel(image.role)"></figcaption>
                                <button type="button" class="button is-light is-danger is-small" @click="removeImage(image)">حذف تصویر</button>
                            </figure>
                        </template>
                    </div>
                    <div class="field is-grouped is-grouped-multiline mt-3">
                        <div class="control">
                            <label class="label" for="qbImageRole">این تصویر مربوط به</label>
                            <div class="select">
                                <select id="qbImageRole" x-model="imageRole">
                                    <template x-for="role in availableRoles()" :key="role.value">
                                        <option :value="role.value" x-text="role.label"></option>
                                    </template>
                                </select>
                            </div>
                        </div>
                        <div class="control">
                            <label class="label" for="qbImageFile">فایل (PNG، JPEG یا WebP، تا ۵ مگابایت)</label>
                            <input class="input" id="qbImageFile" type="file" accept="image/png,image/jpeg,image/webp"
                                   @change="uploadImage($event)" :disabled="uploading">
                        </div>
                    </div>
                    <p class="help is-danger" x-show="form.imageError" x-text="form.imageError"></p>
                </div>
            </section>
            </template>
            <footer class="modal-card-foot">
                <button class="button is-link" type="button" :class="{ 'is-loading': saving }" @click="save">ذخیره</button>
                <button class="button is-light" type="button" @click="closeForm">بستن</button>
            </footer>
        </div>
    </div>

    <%-- ------------------------------------------------ تأیید حذف --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="qbDeleteTitle"
         tabindex="-1" x-ref="deleteModal" :class="{ 'is-active': removal }" x-cloak
         @keydown.tab="window.ModalFocus && window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeDelete">
        <div class="modal-background" @click="closeDelete"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="qbDeleteTitle">حذف سؤال</p>
                <button class="delete" type="button" aria-label="بستن" @click="closeDelete"></button>
            </header>
            <section class="modal-card-body">
                <p>این سؤال از باکس حذف می‌شود. برگه‌هایی که قبلاً آن را داشته‌اند همچنان آن را چاپ می‌کنند.</p>
            </section>
            <footer class="modal-card-foot">
                <button class="button is-danger" type="button" :class="{ 'is-loading': deleting }" @click="confirmDelete">حذف</button>
                <button class="button is-light" type="button" @click="closeDelete">انصراف</button>
            </footer>
        </div>
    </div>
</section>
`)
