@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «گزارش‌های سؤال پلتفرم»: صفِ گزارش‌های مشکلِ سؤال‌های پلتفرم از همه‌ی مدرسه‌ها برای مدیر پلتفرم (که معمولاً هیچ
     مدرسه‌ای ندارد). مثل «تعطیلی‌های سراسری» نه school-scope دارد و نه X-School-ID؛ مجوز فقط روی API پلتفرم سنجیده
     می‌شود و کنترل‌ها بر اساس Permission پنهان نمی‌شوند (UX_ROADMAP §3). این صفحه یک «صف» است، پس اقدامِ اصلیِ
     هدر ندارد؛ اقدام‌ها روی هر ردیف‌اند و هر کدام یک modal برای یادداشتِ اختیاری باز می‌کنند. D10: گزارش هرگز خودِ
     سؤال را ویرایش نمی‌کند. موبایل: ستون‌های ثانویه می‌افتند و جدول در table-container اسکرول می‌شود. --%>
@template.layout.main(title = "گزارش‌های سؤال پلتفرم",
        styles = java.util.List.of("/webjars/katex/0.16.22/dist/katex.min.css"),
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/modal-focus.js", "/webjars/katex/0.16.22/dist/katex.min.js", "/js/math.js", "/js/platform-question-reports.js"),
        content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="platformQuestionReportsPage()">
    <div class="container">
        @template.layout.page-header(
            title = "گزارش‌های سؤال پلتفرم",
            subtitle = "مشکل‌هایی که مدرسه‌ها در سؤال‌های پلتفرم گزارش کرده‌اند. اصلاحِ خودِ سؤال جداگانه انجام می‌شود؛ گزارش سؤال را تغییر نمی‌دهد.")

        <%-- ناحیه‌ی مشترکِ خطا و پیام موفقیت --%>
        <div class="notification is-danger" role="alert" x-cloak x-show="error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="error"></span></span></div>
        <div class="notification is-success" role="status" x-cloak x-show="notice" x-text="notice"></div>

        <div class="columns is-mobile is-multiline" x-cloak x-show="loaded && items.length > 0">
            <div class="column is-12-mobile is-8-tablet">
                <div class="field">
                    <label class="label is-sr-only" for="platformReportsSearch">جست‌وجو در گزارش‌ها</label>
                    <div class="control">
                        <input class="input" id="platformReportsSearch" type="search" x-model="filterText"
                               placeholder="جست‌وجو در متن سؤال، توضیح یا یادداشت">
                    </div>
                </div>
            </div>
            <div class="column is-12-mobile is-4-tablet">
                <div class="field">
                    <label class="label is-sr-only" for="platformReportsStatus">وضعیت</label>
                    <div class="control is-expanded">
                        <div class="select is-fullwidth">
                            <select id="platformReportsStatus" x-model="filterStatus">
                                <option value="">همه‌ی وضعیت‌ها</option>
                                <option value="OPEN">باز</option>
                                <option value="REVIEWED">بررسی شد</option>
                                <option value="FIXED">رفع شد</option>
                                <option value="CLOSED">بسته</option>
                            </select>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <p x-show="loading && !loaded && !error">در حال بارگذاری...</p>

        @template.layout.empty-state(
            show = "loaded && !loading && items.length === 0",
            art = "list",
            message = @`هنوز هیچ مدرسه‌ای مشکلی در سؤال‌های پلتفرم گزارش نکرده است. هر گزارش تازه اینجا در صف می‌آید تا بررسی و پیگیری شود.`)

        <p class="has-text-grey" x-cloak x-show="loaded && items.length > 0 && filtered().length === 0">گزارشی با این جست‌وجو پیدا نشد.</p>

        <div class="table-container" id="platformReportsList" x-cloak x-show="filtered().length > 0">
            <table class="table is-fullwidth is-striped">
                <caption class="is-sr-only">گزارش‌های سؤال پلتفرم</caption>
                <thead>
                <tr>
                    <th scope="col">سؤال</th>
                    <th scope="col">نوع</th>
                    <th scope="col" class="is-hidden-mobile">توضیح</th>
                    <th scope="col" class="is-hidden-mobile">مدرسه</th>
                    <th scope="col">وضعیت</th>
                    <th scope="col" class="is-hidden-mobile">تاریخ</th>
                    <th scope="col" class="is-hidden-mobile">یادداشت</th>
                    <th scope="col"><span class="is-sr-only">عملیات</span></th>
                </tr>
                </thead>
                <tbody>
                <template x-for="item in filtered()" :key="item.id">
                    <tr>
                        <td><span x-math="item.questionText"></span></td>
                        <td x-text="typeLabel(item.reportType)"></td>
                        <td class="is-hidden-mobile" x-text="item.comment || '—'"></td>
                        <td class="is-hidden-mobile" x-text="digits(item.schoolId)"></td>
                        <td><span class="tag" :class="statusTag(item.status)" x-text="statusLabel(item.status)"></span></td>
                        <td class="is-hidden-mobile" x-text="dateOf(item.createdAt)"></td>
                        <td class="is-hidden-mobile" x-text="item.resolutionNote || '—'"></td>
                        <td>
                            <div class="buttons">
                                <template x-if="item.status === 'OPEN'">
                                    <button class="button is-light" type="button"
                                            :aria-label="'بررسی شد: گزارش شماره‌ی ' + digits(item.id)"
                                            @click="openEdit(item, 'REVIEWED')">بررسی شد</button>
                                </template>
                                <template x-if="item.status === 'REVIEWED'">
                                    <button class="button is-light" type="button"
                                            :aria-label="'رفع شد: گزارش شماره‌ی ' + digits(item.id)"
                                            @click="openEdit(item, 'FIXED')">رفع شد</button>
                                </template>
                                <template x-if="item.status === 'REVIEWED'">
                                    <button class="button is-light" type="button"
                                            :aria-label="'بستن: گزارش شماره‌ی ' + digits(item.id)"
                                            @click="openEdit(item, 'CLOSED')">بستن</button>
                                </template>
                            </div>
                        </td>
                    </tr>
                </template>
                </tbody>
            </table>
        </div>

        <%-- ------------------------------------------------ تغییر وضعیت (یک modalِ مشترک) --%>
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="platformReportsEditTitle"
             tabindex="-1" x-ref="editModal" :class="{ 'is-active': edit }" x-cloak
             @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeEdit">
            <div class="modal-background" @click="closeEdit"></div>
            <template x-if="edit">
                <form class="modal-card" @submit.prevent="submitEdit" novalidate>
                    <header class="modal-card-head">
                        <p class="modal-card-title" id="platformReportsEditTitle" x-text="'وضعیت گزارش: ' + actionLabel(edit.status)"></p>
                        <button class="delete" type="button" aria-label="بستن" @click="closeEdit"></button>
                    </header>
                    <section class="modal-card-body">
                        <div class="notification is-danger" role="alert" x-show="edit.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="edit.error"></span></span></div>

                        <p class="mb-3" x-math="edit.questionText"></p>

                        <div class="field">
                            <label class="label" for="platformReportsNote">یادداشت (اختیاری)</label>
                            <div class="control">
                                <textarea class="textarea" id="platformReportsNote" rows="3" maxlength="1000"
                                          x-model="edit.note"></textarea>
                            </div>
                            <p class="help" x-text="noteCount()"></p>
                            <p class="help">اصلاحِ خودِ سؤال جداگانه انجام می‌شود: اول سؤال را ویرایش کنید، بعد گزارش را «رفع شد» کنید. ثبتِ وضعیت سؤال را تغییر نمی‌دهد.</p>
                        </div>
                    </section>
                    <footer class="modal-card-foot">
                        <button class="button is-link" type="submit" id="platformReportsEditSubmit"
                                :class="{ 'is-loading': saving }">ثبت</button>
                        <button class="button" type="button" @click="closeEdit">انصراف</button>
                    </footer>
                </form>
            </template>
        </div>
    </div>
</section>
`)
