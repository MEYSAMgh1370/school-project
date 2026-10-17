@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- «بات بله‌ی مدرسه» (openspec/changes/bale-school-bots.md، بخش Pages). هدف: مدیر مدرسه بات اختصاصی مدرسه را
     در بله ثبت می‌کند تا اولیا و کارکنان با بات هم‌نام مدرسه حرف بزنند. اقدامِ اصلی «ثبت بات» در inline-end هدر
     است و دکمه‌ی submit فرمِ توکن است (form="baleBotForm"). حالت‌ها: بدون بات (سه گام + فرم)، فعال (نام، لینک،
     اعضای متصل، آخرین دریافت، «تعویض توکن»، «حذف بات» با modal)، غیرفعال (هشدار + فرم) و خطا (ناحیه‌ی مشترک
     school-scope). توکن فقط در ورودیِ password است و هرگز به صفحه برنمی‌گردد. سوییچ API کسب‌وکار در PR 1 نیست.
     API: GET/PUT/DELETE /api/admin/schools/{id}/bale-bot (مجوز BALE_BOT_MANAGE، بدون ماژول). --%>
@template.layout.main(title = "بات بله‌ی مدرسه",
        scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/school-api.js", "/js/modal-focus.js", "/js/bale-bot-logic.js", "/js/bale-bot.js"),
        content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="baleBotPage()">
    <div class="container">
        <div class="level is-mobile">
            <div class="level-left">
                <div>
                    @template.layout.page-header(
                        title = "بات بله‌ی مدرسه",
                        subtitle = "با ثبت بات اختصاصی، اولیا و کارکنان مدرسه با باتی در بله گفتگو می‌کنند که نام و تصویر مدرسه را دارد. ثبت بات اختیاری است.")
                </div>
            </div>
            <div class="level-right" x-cloak x-show="schoolId && showForm">
                <button class="button is-link" type="submit" form="baleBotForm" id="baleBotRegister"
                        :class="{ 'is-loading': saving }">ثبت بات</button>
            </div>
        </div>

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت بات بله، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <p role="status" x-show="loading && !loaded">در حال بارگذاری...</p>

                <div x-show="!blocked">
                    <%-- ------------------------------------------------ بدون بات --%>
                    <div class="box" id="baleBotNone" x-cloak x-show="loaded && state === 'none'">
                        <h2 class="title is-5">بات اختصاصی مدرسه را راه بیندازید</h2>
                        <p class="has-text-grey mb-3">تا وقتی بات ثبت نکنید، مدرسه از بات کلیِ سرویس استفاده می‌کند و همه‌چیز مثل قبل کار می‌کند.</p>
                        <ol class="mb-2">
                            <li>در بله با <span dir="ltr">@botfather</span> گفتگو کنید و با نام و تصویر مدرسه یک بات بسازید.</li>
                            <li>توکنی را که BotFather می‌دهد کپی کنید.</li>
                            <li>توکن را در کادر زیر بچسبانید و «ثبت بات» را بزنید.</li>
                        </ol>
                    </div>

                    <%-- ------------------------------------------------ غیرفعال --%>
                    <div class="notification is-warning" role="alert" id="baleBotDisabled" x-cloak x-show="loaded && state === 'disabled'">
                        <span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span>توکن بات نامعتبر شده است؛ تا اصلاح آن، اولیا از بات کلی سرویس می‌گیرند. توکن تازه را از BotFather بگیرید و در کادر زیر وارد کنید.</span></span>
                    </div>

                    <%-- ------------------------------------------------ فعال --%>
                    <div class="box" id="baleBotActive" x-cloak x-show="loaded && state === 'active'">
                        <template x-if="bot">
                            <div>
                                <h2 class="title is-5">
                                    <span dir="ltr" x-text="'@' + bot.username"></span>
                                    <span class="tag is-light">فعال</span>
                                </h2>
                                <dl>
                                    <div class="py-1">
                                        <dt class="has-text-grey">لینک بات</dt>
                                        <dd><a dir="ltr" target="_blank" rel="noopener noreferrer" :href="bot.link" x-text="bot.link.replace('https://', '')"></a></dd>
                                    </div>
                                    <div class="py-1">
                                        <dt class="has-text-grey">اعضای متصل به بات</dt>
                                        <dd x-text="membersText()"></dd>
                                    </div>
                                    <div class="py-1">
                                        <dt class="has-text-grey">آخرین دریافت از بات</dt>
                                        <dd x-text="lastUpdateText()"></dd>
                                    </div>
                                </dl>
                                <div class="buttons mt-4">
                                    <button class="button is-light" type="button" id="baleBotReplace" @click="startReplace()">تعویض توکن</button>
                                    <button class="button is-light" type="button" id="baleBotDelete" @click="askRemove()">حذف بات</button>
                                </div>
                            </div>
                        </template>
                    </div>

                    <%-- ------------------------------------------------ فرم توکن (بدون بات، غیرفعال یا تعویض) --%>
                    <form class="box" id="baleBotForm" novalidate autocomplete="off" x-cloak x-show="showForm" @submit.prevent="submit()">
                        <div class="field">
                            <label class="label" for="baleBotToken">توکن بات</label>
                            <div class="control">
                                <input class="input" dir="ltr" id="baleBotToken" type="password" autocomplete="off"
                                       autocapitalize="off" spellcheck="false" x-model="token"
                                       :class="{ 'is-danger': tokenError }" :aria-invalid="tokenError ? 'true' : 'false'"
                                       aria-describedby="baleBotTokenHelp">
                            </div>
                            <p class="help is-danger" role="alert" id="baleBotTokenError" x-show="tokenError"><span class="app-notice">@template.layout.icon(name = "alert", size = 16)<span x-text="tokenError"></span></span></p>
                            <p class="help" id="baleBotTokenHelp">توکن بعد از ثبت دیگر نمایش داده نمی‌شود، حتی بخشی از آن.</p>
                        </div>
                        <div class="field" x-show="replacing">
                            <button class="button is-light" type="button" id="baleBotCancelReplace" @click="cancelReplace()">انصراف</button>
                        </div>
                    </form>
                </div>

                <%-- ------------------------------------------------ تأیید حذف --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="baleBotDeleteTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirming }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="baleBotDeleteTitle">حذف بات مدرسه</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert" x-show="confirmError"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="confirmError"></span></span></div>
                            <p class="has-text-weight-semibold">آیا از حذف بات مدرسه مطمئن هستید؟</p>
                            <ul class="mt-2">
                                <li>از این به بعد اولیا و کارکنان پیام‌ها را از بات کلی سرویس می‌گیرند.</li>
                                <li>بات مدرسه، تا وقتی توکنش کار کند، به اعضای متصل خبر می‌دهد که به بات کلی بروند.</li>
                                <li>توکن از سرویس پاک می‌شود؛ برای ثبت دوباره باید توکن را دوباره وارد کنید.</li>
                            </ul>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-danger" type="button" id="baleBotConfirmDelete"
                                    :class="{ 'is-loading': saving }" @click="runRemove()">حذف قطعی</button>
                            <button class="button" type="button" @click="closeConfirm()">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
