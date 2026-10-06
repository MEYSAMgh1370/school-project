@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «برنامه‌ی سالانه» (openspec/changes/yearly-plan.md): رویدادهای مدرسه و تعطیلی‌های سراسریِ سال تحصیلی،
     گروه‌بندی‌شده به ماهِ جلالی. هر عضو می‌خواند؛ اقدامِ اصلیِ «افزودن رویداد» در inline-end هدر است و بر اساس
     Permission پنهان نمی‌شود (UX_ROADMAP §3). حذف با modal تأیید (بدون confirm مرورگر)؛ تعطیلیِ سراسری فقط‌خواندنی است.
     روز در ورودی جلالی با ارقام فارسی است و روی سیم ISO می‌رود (date-input.js). --%>
@template.layout.main(title = "برنامه‌ی سالانه",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/date-input.js", "/js/school-api.js", "/js/modal-focus.js", "/js/yearly-plan-logic.js", "/js/yearly-plan.js"),
        content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="yearlyPlanPage()">
    <div class="container">
        <div class="level is-mobile">
            <div class="level-left">
                <div>
                    @template.layout.page-header(
                        title = "برنامه‌ی سالانه",
                        subtitle = "رویدادهای مدرسه و تعطیلی‌های سراسری سال تحصیلی")
                </div>
            </div>
            <div class="level-right" x-cloak x-show="schoolId">
                <button class="button is-link" type="button" id="yearlyPlanAdd" @click="openAdd">افزودن رویداد</button>
            </div>
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای دیدن برنامه‌ی سالانه، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <div class="level is-mobile" x-show="year">
                    <div class="level-left">
                        <p class="has-text-weight-semibold" id="yearlyPlanYearLine" x-text="yearLine()"></p>
                    </div>
                    <div class="level-right" x-show="years.length > 1">
                        <div class="field">
                            <label class="label is-sr-only" for="yearlyPlanYear">سال تحصیلی</label>
                            <div class="control">
                                <div class="select" :class="{ 'is-loading': loading }">
                                    <select id="yearlyPlanYear" x-model.number="selectedYearId" @change="onYearChange()">
                                        <template x-for="y in years" :key="y.id">
                                            <option :value="y.id" :selected="y.id === selectedYearId" x-text="yearOptionLabel(y)"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <p x-show="loading && !loaded">در حال بارگذاری...</p>

                <%-- هنوز سالِ جاری انتخاب نشده --%>
                @template.layout.empty-state(
                    show = "loaded && !loading && !year",
                    art = "calendar",
                    message = @`برای این مدرسه هنوز سال تحصیلی جاری انتخاب نشده است. مدیر مدرسه می‌تواند آن را در صفحه‌ی <a href="/academic-year">سال تحصیلی</a> تعیین کند.`)

                <%-- سالِ جاری هست ولی چیزی ثبت نشده --%>
                @template.layout.empty-state(
                    show = "loaded && !loading && year && items.length === 0",
                    art = "calendar",
                    message = @`برای این سال هنوز رویدادی ثبت نشده است. هفته‌های امتحان، مراسم، جلسه‌های اولیا و اردوها را اینجا ببینید؛ مدیر با «افزودن رویداد» آن‌ها را ثبت می‌کند. تعطیلی‌های سراسری هم خودکار در همین فهرست می‌آیند.`)

                <div x-show="groups.length > 0" id="yearlyPlanList">
                    <template x-for="group in groups" :key="group.key">
                        <div class="mb-5">
                            <h2 class="title is-5" x-text="group.title"></h2>
                            <div class="table-container">
                                <table class="table is-fullwidth is-striped">
                                    <caption class="is-sr-only" x-text="'رویدادهای ' + group.title"></caption>
                                    <thead>
                                    <tr>
                                        <th scope="col" class="is-hidden-mobile">روز</th>
                                        <th scope="col">تاریخ</th>
                                        <th scope="col">عنوان</th>
                                        <th scope="col"><span class="is-sr-only">عملیات</span></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <template x-for="item in group.items" :key="itemKey(item)">
                                        <tr>
                                            <td class="is-hidden-mobile" x-text="dayName(item.day)"></td>
                                            <td x-text="dayShort(item.day)"></td>
                                            <td>
                                                <span x-text="item.title"></span>
                                                <span class="tag is-info is-light" x-show="item.source === 'PLATFORM'">تعطیلی سراسری</span>
                                            </td>
                                            <td>
                                                <template x-if="item.source === 'SCHOOL'">
                                                    <button class="button is-danger is-light" type="button"
                                                            :aria-label="'حذف رویداد ' + item.title"
                                                            @click="askRemove(item)">حذف</button>
                                                </template>
                                            </td>
                                        </tr>
                                    </template>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </template>
                </div>

                <%-- ------------------------------------------------ افزودن رویداد --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="yearlyPlanAddTitle"
                     tabindex="-1" x-ref="addModal" :class="{ 'is-active': add }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeAdd">
                    <div class="modal-background" @click="closeAdd"></div>
                    <template x-if="add">
                        <form class="modal-card" @submit.prevent="submitAdd" novalidate>
                            <header class="modal-card-head">
                                <p class="modal-card-title" id="yearlyPlanAddTitle">افزودن رویداد</p>
                                <button class="delete" type="button" aria-label="بستن" @click="closeAdd"></button>
                            </header>
                            <section class="modal-card-body">
                                <div class="notification is-danger" role="alert" x-show="add.error" x-text="add.error"></div>

                                <div class="field">
                                    <label class="label" for="yearlyPlanDate">تاریخ</label>
                                    <div class="control">
                                        <input class="input" id="yearlyPlanDate" type="text" dir="ltr" inputmode="numeric"
                                               autocomplete="off" :placeholder="exampleDate()"
                                               x-model="add.date" :class="{ 'is-danger': add.dateError }">
                                    </div>
                                    <p class="help is-danger" x-show="add.dateError" x-text="add.dateError"></p>
                                    <p class="help" x-show="!add.dateError" x-text="rangeHelp()"></p>
                                </div>

                                <div class="field">
                                    <label class="label" for="yearlyPlanTitleInput">عنوان</label>
                                    <div class="control">
                                        <input class="input" id="yearlyPlanTitleInput" type="text" maxlength="128"
                                               x-model="add.title" :class="{ 'is-danger': add.titleError }">
                                    </div>
                                    <p class="help is-danger" x-show="add.titleError" x-text="add.titleError"></p>
                                </div>
                            </section>
                            <footer class="modal-card-foot">
                                <button class="button is-link" type="submit" id="yearlyPlanAddSubmit"
                                        :class="{ 'is-loading': saving }">افزودن</button>
                                <button class="button" type="button" @click="closeAdd">انصراف</button>
                            </footer>
                        </form>
                    </template>
                </div>

                <%-- ------------------------------------------------ تأیید حذف --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="yearlyPlanConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="yearlyPlanConfirmTitle">تأیید حذف</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="confirm && confirm.error" x-text="confirm ? confirm.error : ''"></div>
                            <p class="has-text-weight-semibold">
                                آیا از حذف رویدادِ «<span x-text="confirm ? confirm.title : ''"></span>»
                                (<span x-text="confirm ? dayShort(confirm.day) : ''"></span>) مطمئن هستید؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                بعد از حذف، این رویداد از برنامه‌ی سالانه‌ی همه‌ی اعضای مدرسه برداشته می‌شود.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-danger" type="button" id="yearlyPlanConfirmDelete"
                                    :class="{ 'is-loading': saving }" @click="runRemove">حذف قطعی</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
