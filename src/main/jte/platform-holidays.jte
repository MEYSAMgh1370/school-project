@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «تعطیلی‌های سراسری» (openspec/changes/yearly-plan.md فاز C): تعطیلی‌های همه‌ی مدرسه‌ها در هر سال تحصیلی،
     گروه‌بندی‌شده به ماهِ جلالی، برای مدیر پلتفرم که معمولاً هیچ مدرسه‌ای ندارد. پس این صفحه نه layout/school-scope
     دارد و نه X-School-ID می‌فرستد؛ API زیر /api/platform است و اجازه (PLATFORM_HOLIDAY_MANAGE) فقط آنجا سنجیده
     می‌شود - کنترل‌ها بر اساس Permission پنهان نمی‌شوند (UX_ROADMAP §3). اقدامِ اصلیِ «افزودن تعطیلی» در inline-end
     هدر است؛ حذف با modal تأیید (بدون confirm مرورگر). روز در ورودی جلالی با ارقام فارسی است و روی سیم ISO می‌رود
     (date-input.js). موبایل: ستونِ روزِ هفته می‌افتد و جدول در table-container اسکرول می‌شود. --%>
@template.layout.main(title = "تعطیلی‌های سراسری",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/date-input.js", "/js/modal-focus.js", "/js/yearly-plan-logic.js", "/js/platform-holidays.js"),
        content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="platformHolidaysPage()">
    <div class="container">
        <div class="level is-mobile">
            <div class="level-left">
                <div>
                    @template.layout.page-header(
                        title = "تعطیلی‌های سراسری",
                        subtitle = "این روزها در برنامه‌ی سالانه‌ی همه‌ی مدرسه‌ها می‌آیند و روز را در همه‌ی مدرسه‌ها تعطیل می‌کنند")
                </div>
            </div>
            <div class="level-right">
                <button class="button is-link" type="button" id="platformHolidaysAdd" @click="openAdd">افزودن تعطیلی</button>
            </div>
        </div>

        <%-- ناحیه‌ی مشترکِ خطا و پیام موفقیت --%>
        <div class="notification is-danger" role="alert" x-cloak x-show="error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="error"></span></span></div>
        <div class="notification is-success" role="status" x-cloak x-show="notice" x-text="notice"></div>

        <div class="level is-mobile is-flex-wrap-wrap is-gap-2" x-cloak x-show="years.length > 0">
            <div class="level-left">
                <p class="has-text-weight-semibold" id="platformHolidaysYearLine" x-text="yearLine()"></p>
            </div>
            <div class="level-right">
                <div class="field">
                    <label class="label is-sr-only" for="platformHolidaysYear">سال تحصیلی</label>
                    <div class="control">
                        <div class="select" :class="{ 'is-loading': loading }">
                            <select id="platformHolidaysYear" x-model.number="selectedYearId" @change="onYearChange()">
                                <template x-for="y in years" :key="y.id">
                                    <option :value="y.id" :selected="y.id === selectedYearId" x-text="yearOptionLabel(y)"></option>
                                </template>
                            </select>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <p x-show="loading && !loaded && !error">در حال بارگذاری...</p>

        <%-- هیچ سال تحصیلی‌ای تعریف نشده --%>
        @template.layout.empty-state(
            show = "yearsLoaded && !loading && !year",
            art = "calendar",
            message = @`هنوز هیچ سال تحصیلی‌ای تعریف نشده است. تعطیلی‌های سراسری به یک سال تحصیلی وصل می‌شوند؛ پس از تعریف سال می‌توانید با «افزودن تعطیلی» آن‌ها را ثبت کنید.`)

        <%-- سال هست ولی تعطیلی‌ای ثبت نشده --%>
        @template.layout.empty-state(
            show = "loaded && !loading && year && items.length === 0",
            art = "calendar",
            message = @`برای این سال هنوز تعطیلی سراسری‌ای ثبت نشده است. تعطیلی‌های ملی و مذهبی را اینجا با «افزودن تعطیلی» ثبت کنید؛ هر مدرسه آن‌ها را خودکار در برنامه‌ی سالانه‌اش می‌بیند و روز در همه‌ی مدرسه‌ها تعطیل می‌شود.`)

        <div x-show="groups.length > 0" id="platformHolidaysList">
            <template x-for="group in groups" :key="group.key">
                <div class="mb-5">
                    <h2 class="title is-5" x-text="group.title"></h2>
                    <div class="table-container">
                        <table class="table is-fullwidth is-striped">
                            <caption class="is-sr-only" x-text="'تعطیلی‌های ' + group.title"></caption>
                            <thead>
                            <tr>
                                <th scope="col" class="is-hidden-mobile">روز</th>
                                <th scope="col">تاریخ</th>
                                <th scope="col">عنوان</th>
                                <th scope="col"><span class="is-sr-only">عملیات</span></th>
                            </tr>
                            </thead>
                            <tbody>
                            <template x-for="item in group.items" :key="item.id">
                                <tr>
                                    <td class="is-hidden-mobile" x-text="dayName(item.day)"></td>
                                    <td x-text="dayShort(item.day)"></td>
                                    <td x-text="item.title"></td>
                                    <td>
                                        <button class="button is-danger is-light" type="button"
                                                :aria-label="'حذف تعطیلی ' + item.title"
                                                @click="askRemove(item)">حذف</button>
                                    </td>
                                </tr>
                            </template>
                            </tbody>
                        </table>
                    </div>
                </div>
            </template>
        </div>

        <%-- ------------------------------------------------ افزودن تعطیلی --%>
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="platformHolidaysAddTitle"
             tabindex="-1" x-ref="addModal" :class="{ 'is-active': add }" x-cloak
             @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeAdd">
            <div class="modal-background" @click="closeAdd"></div>
            <template x-if="add">
                <form class="modal-card" @submit.prevent="submitAdd" novalidate>
                    <header class="modal-card-head">
                        <p class="modal-card-title" id="platformHolidaysAddTitle">افزودن تعطیلی</p>
                        <button class="delete" type="button" aria-label="بستن" @click="closeAdd"></button>
                    </header>
                    <section class="modal-card-body">
                        <div class="notification is-danger" role="alert" x-show="add.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="add.error"></span></span></div>

                        <div class="field">
                            <label class="label" for="platformHolidaysDate">تاریخ</label>
                            <div class="control">
                                <input class="input" id="platformHolidaysDate" type="text" dir="ltr" inputmode="numeric"
                                       autocomplete="off" :placeholder="exampleDate()"
                                       x-model="add.date" :class="{ 'is-danger': add.dateError }">
                            </div>
                            <p class="help is-danger" role="alert" x-show="add.dateError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="add.dateError"></span></span></p>
                            <p class="help" x-show="!add.dateError" x-text="rangeHelp()"></p>
                        </div>

                        <div class="field">
                            <label class="label" for="platformHolidaysTitleInput">عنوان</label>
                            <div class="control">
                                <input class="input" id="platformHolidaysTitleInput" type="text" maxlength="128"
                                       x-model="add.title" :class="{ 'is-danger': add.titleError }">
                            </div>
                            <p class="help is-danger" role="alert" x-show="add.titleError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="add.titleError"></span></span></p>
                        </div>
                    </section>
                    <footer class="modal-card-foot">
                        <button class="button is-link" type="submit" id="platformHolidaysAddSubmit"
                                :class="{ 'is-loading': saving }">افزودن</button>
                        <button class="button" type="button" @click="closeAdd">انصراف</button>
                    </footer>
                </form>
            </template>
        </div>

        <%-- ------------------------------------------------ تأیید حذف --%>
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="platformHolidaysConfirmTitle"
             tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
             @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
            <div class="modal-background" @click="closeConfirm"></div>
            <div class="modal-card">
                <header class="modal-card-head">
                    <p class="modal-card-title" id="platformHolidaysConfirmTitle">تأیید حذف</p>
                    <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                </header>
                <section class="modal-card-body">
                    <div class="notification is-danger" role="alert"
                         x-show="confirm && confirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="confirm ? confirm.error : ''"></span></span></div>
                    <p class="has-text-weight-semibold">
                        آیا از حذف تعطیلیِ «<span x-text="confirm ? confirm.title : ''"></span>»
                        (<span x-text="confirm ? dayShort(confirm.day) : ''"></span>) مطمئن هستید؟
                    </p>
                    <p class="has-text-grey is-size-7">
                        بعد از حذف، این روز از برنامه‌ی سالانه‌ی همه‌ی مدرسه‌ها برداشته می‌شود و دیگر در همه‌ی مدرسه‌ها تعطیل نیست.
                    </p>
                </section>
                <footer class="modal-card-foot">
                    <button class="button is-danger" type="button" id="platformHolidaysConfirmDelete"
                            :class="{ 'is-loading': saving }" @click="runRemove">حذف قطعی</button>
                    <button class="button" type="button" @click="closeConfirm">انصراف</button>
                </footer>
            </div>
        </div>
    </div>
</section>
`)
