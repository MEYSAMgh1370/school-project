@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "ارتقای پایه", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/modal-focus.js", "/js/promotion.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="promotionPage()">
    <div class="container">
        @template.layout.page-header(
            title = "ارتقای پایه",
            subtitle = "دانش‌آموزان سال قبل را به سال تحصیلی جاری ارتقا بدهید و آن‌ها را در کلاس‌های جدید بگذارید. هیچ اطلاعاتی حذف نمی‌شود و هر اجرا قابل برگرداندن است.")

        @template.layout.school-scope(
            noSchoolMessage = "برای ارتقای دانش‌آموزان، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <template x-if="!blocked && loaded">
                    <div>
                        <%-- بدونِ سالِ جاری ارتقا معنا ندارد (هیچ سالی حدس زده نمی‌شود) --%>
                        <div class="notification is-warning is-light" id="promotionNoYear" x-show="!current">
                            ابتدا در صفحه‌ی <a href="/academic-year">سال تحصیلی</a> سالِ جاریِ مدرسه را انتخاب کنید.
                        </div>

                        <div class="notification is-warning is-light" id="promotionUnattributed"
                             x-show="current && (unattributed.classes > 0 || unattributed.students > 0)">
                            <span x-text="Calendar.digits(String(unattributed.classes))"></span> کلاس و
                            <span x-text="Calendar.digits(String(unattributed.students))"></span> دانش‌آموز هنوز به هیچ سالی
                            نسبت داده نشده‌اند و در ارتقا نمی‌آیند. در صفحه‌ی <a href="/academic-year">سال تحصیلی</a>
                            آن‌ها را به یک سال نسبت دهید.
                        </div>

                        <template x-if="current">
                            <div>
                                <%-- ۱) دانش‌آموزانِ ارتقایافته که کلاسشان هنوز مشخص نیست --%>
                                <div class="box" id="promotionPending" x-show="pending.length > 0">
                                    <h2 class="title is-6">در انتظار جا دادن در کلاس
                                        <span class="tag is-light app-inline-gap" x-text="Calendar.digits(String(pending.length))"></span>
                                    </h2>
                                    <p class="help mb-3">
                                        برای هر دانش‌آموز کلاسی از سال <span dir="ltr" x-text="Calendar.digits(current)"></span>
                                        و هم‌پایه‌ی او را انتخاب کنید. اگر کلاسِ سالِ جدید هنوز ساخته نشده،
                                        ابتدا از صفحه‌ی <a href="/classes">کلاس‌ها</a> بسازید.
                                    </p>
                                    <div class="table-container">
                                        <table class="table is-fullwidth is-striped">
                                            <thead>
                                            <tr>
                                                <th scope="col">نام دانش‌آموز</th>
                                                <th scope="col">پایه‌ی سال جدید</th>
                                                <th scope="col">کلاس</th>
                                                <th scope="col">نتیجه</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <template x-for="item in pending" :key="item.studentId">
                                                <tr>
                                                    <td x-text="studentName(item.userId)"></td>
                                                    <td>
                                                        <span x-text="Calendar.digits(String(item.gradeLevel))"></span>
                                                        <span class="tag is-light" x-show="item.previousGradeLevel === item.gradeLevel">تکرار پایه</span>
                                                    </td>
                                                    <td>
                                                        <div class="select">
                                                            <select :aria-label="'کلاس ' + studentName(item.userId)"
                                                                    @change="placements[item.studentId] = $event.target.value">
                                                                <option value="">انتخاب کلاس</option>
                                                                <template x-for="classItem in classesOfGrade(item.gradeLevel)" :key="classItem.id">
                                                                    <option :value="String(classItem.id)" x-text="classItem.name"
                                                                            :selected="placements[item.studentId] === String(classItem.id)"></option>
                                                                </template>
                                                            </select>
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <button class="button is-small is-light" type="button"
                                                                @click="changeOutcome(item.studentId, item.previousGradeLevel === item.gradeLevel ? 'PROMOTE' : 'REPEAT')">
                                                            <span x-text="item.previousGradeLevel === item.gradeLevel ? 'ارتقا به پایه‌ی بعد' : 'تکرار پایه'"></span>
                                                        </button>
                                                    </td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
                                    </div>
                                    <p class="help is-danger mb-2" x-show="classesOfGradeMissing()">
                                        برای بعضی پایه‌ها کلاسی در این سال نیست.
                                    </p>
                                    <button class="button is-link" type="button" id="promotionPlaceButton"
                                            @click="place()" :disabled="placementCount() === 0 || placing"
                                            :class="{ 'is-loading': placing }">
                                        جا دادن در کلاس‌ها
                                    </button>
                                </div>

                                <%-- ۲) فارغ‌التحصیل‌شدگانِ اجرای امسال: می‌توان به‌جای آن تکرار پایه گذاشت --%>
                                <div class="box" id="promotionGraduates" x-show="graduates.length > 0">
                                    <h2 class="title is-6">فارغ‌التحصیل‌شدگانِ این اجرا
                                        <span class="tag is-light app-inline-gap" x-text="Calendar.digits(String(graduates.length))"></span>
                                    </h2>
                                    <p class="help mb-3">آخرین پایه‌ی مدرسه را تمام کرده‌اند. اگر کسی باید پایه را تکرار کند، «تکرار پایه» را بزنید.</p>
                                    <div class="table-container">
                                        <table class="table is-fullwidth is-narrow">
                                            <tbody>
                                            <template x-for="item in graduates" :key="item.studentId">
                                                <tr>
                                                    <td x-text="studentName(item.userId)"></td>
                                                    <td>پایه‌ی <span x-text="Calendar.digits(String(item.gradeLevel))"></span></td>
                                                    <td>
                                                        <button class="button is-small is-light" type="button"
                                                                @click="changeOutcome(item.studentId, 'REPEAT')">تکرار پایه</button>
                                                    </td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>

                                <%-- ۳) اجرای ارتقا --%>
                                <div class="box" id="promotionRun">
                                    <h2 class="title is-6">اجرای ارتقا</h2>
                                    <div class="field is-grouped is-grouped-multiline">
                                        <div class="control">
                                            <label class="label" for="promotionFromYear">دانش‌آموزان سالِ</label>
                                            <div class="select">
                                                <select id="promotionFromYear" dir="ltr"
                                                        @change="fromYear = $event.target.value; loadPreview()">
                                                    <option value="" disabled :selected="!fromYear">یک سال را انتخاب کنید</option>
                                                    <template x-for="y in earlierYears" :key="y">
                                                        <option :value="y" x-text="Calendar.digits(y)" :selected="y === fromYear"></option>
                                                    </template>
                                                </select>
                                            </div>
                                        </div>
                                        <div class="control is-expanded">
                                            <label class="label" for="promotionFilter">جستجو</label>
                                            <input class="input" id="promotionFilter" type="search" x-model="filterText"
                                                   placeholder="نام دانش‌آموز">
                                        </div>
                                    </div>
                                    <p class="help mb-3">
                                        پیش‌فرض: همه به پایه‌ی بعد می‌روند و کسانی که در آخرین پایه‌ی مدرسه‌اند فارغ‌التحصیل می‌شوند.
                                        برای کسی که پایه را تکرار می‌کند یا مدرسه را ترک می‌کند، نتیجه‌اش را عوض کنید. ترک مدرسه
                                        دسترسیِ دانش‌آموز را غیرفعال می‌کند ولی اطلاعاتش حذف نمی‌شود.
                                    </p>

                                    <p x-show="previewLoading">در حال بارگذاری...</p>

                                    <div class="table-container" x-show="plan.length > 0">
                                        <table class="table is-fullwidth is-striped">
                                            <thead>
                                            <tr>
                                                <th scope="col">نام دانش‌آموز</th>
                                                <th scope="col">کلاس سال قبل</th>
                                                <th scope="col">پایه</th>
                                                <th scope="col">نتیجه</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <template x-for="item in visiblePlan()" :key="item.studentId">
                                                <tr>
                                                    <td x-text="studentName(item.userId)"></td>
                                                    <td x-text="className(item.classId)"></td>
                                                    <td x-text="Calendar.digits(String(item.gradeLevel))"></td>
                                                    <td>
                                                        <div class="select">
                                                            <select :aria-label="'نتیجه ' + studentName(item.userId)"
                                                                    x-model="decisions[item.studentId]">
                                                                <option value="PROMOTE" :disabled="item.defaultOutcome === 'GRADUATE'">ارتقا به پایه‌ی بعد</option>
                                                                <option value="REPEAT">تکرار پایه</option>
                                                                <option value="GRADUATE" :disabled="item.defaultOutcome !== 'GRADUATE'">فارغ‌التحصیل</option>
                                                                <option value="LEAVE">ترک مدرسه</option>
                                                            </select>
                                                        </div>
                                                    </td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
                                    </div>

                                    @template.layout.empty-state(
                                        show = "fromYear && !previewLoading && plan.length === 0",
                                        art = "people",
                                        message = @`دانش‌آموزی برای ارتقا از این سال نمانده است (یا قبلاً ارتقا یافته‌اند).`)

                                    <p class="mb-3" id="promotionSummary" x-show="plan.length > 0" x-text="summaryText()"></p>
                                    <button class="button is-link" type="button" id="promotionRunButton"
                                            @click="openConfirm('run', null, $event)"
                                            :disabled="plan.length === 0 || running" :class="{ 'is-loading': running }">
                                        اجرای ارتقا
                                    </button>
                                </div>

                                <%-- ۴) اجراهای قبلی --%>
                                <div class="box" id="promotionRuns" x-show="runs.length > 0">
                                    <h2 class="title is-6">اجراهای ارتقا</h2>
                                    <div class="table-container">
                                        <table class="table is-fullwidth is-narrow">
                                            <thead>
                                            <tr>
                                                <th scope="col">تاریخ</th>
                                                <th scope="col">از سال تا سال</th>
                                                <th scope="col">نوع</th>
                                                <th scope="col">نتیجه</th>
                                                <th scope="col">وضعیت</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <template x-for="run in runs" :key="run.id">
                                                <tr>
                                                    <td x-text="Calendar.formatDate(run.createdAt)"></td>
                                                    <td dir="ltr" x-text="Calendar.digits(run.fromYear) + ' ← ' + Calendar.digits(run.toYear)"></td>
                                                    <td x-text="run.mode === 'AUTO' ? 'خودکار' : 'دستی'"></td>
                                                    <td x-text="countsText(run.counts)"></td>
                                                    <td>
                                                        <span class="tag is-light" x-show="run.undoneAt">برگردانده شد</span>
                                                        <button class="button is-small is-light" type="button"
                                                                x-show="!run.undoneAt && run.undoable"
                                                                @click="openConfirm('undo', run, $event)">برگرداندن</button>
                                                        <span class="help" x-show="!run.undoneAt && !run.undoable">
                                                            قابل برگرداندن نیست (کسی از آن در کلاس جا شده یا اجرای جدیدتری هست)
                                                        </span>
                                                    </td>
                                                </tr>
                                            </template>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </div>
                        </template>
                    </div>
                </template>

                <%-- تأیید اجرا / برگرداندن: یک modal --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="promotionConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="promotionConfirmTitle"
                               x-text="confirm && confirm.kind === 'run' ? 'اجرای ارتقا' : 'برگرداندن اجرای ارتقا'"></p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body" x-show="confirm">
                            <p x-show="confirm && confirm.kind === 'run'" x-text="runConfirmText()"></p>
                            <p x-show="confirm && confirm.kind === 'undo'">
                                وضعیت و کلاسِ همه‌ی دانش‌آموزانِ این اجرا به پیش از اجرا برمی‌گردد و ثبت‌نام‌های ساخته‌شده‌ی
                                این اجرا پاک می‌شود؛ دسترسیِ کسانی که ترک مدرسه کرده بودند دوباره فعال می‌شود.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-link" type="button" id="promotionConfirmButton" @click="doConfirm()"
                                    :class="{ 'is-loading': running || undoing }">
                                <span x-text="confirm && confirm.kind === 'run' ? 'اجرا' : 'برگرداندن'"></span>
                            </button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
