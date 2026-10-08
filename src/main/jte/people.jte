@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "اعضای مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/digits.js", "/js/list-search.js", "/js/modal-focus.js", "/js/people.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="peoplePage()">
    <div class="container">
        @template.layout.page-header(
            title = "اعضای مدرسه")

        @template.layout.school-scope(
            noSchoolMessage = "برای مدیریت اعضا، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <div class="box" x-show="invite && !invite.emailSent">
                    <p class="mb-2">
                        ارسال ایمیل دعوت برای <strong x-text="invite && invite.fullName"></strong> ناموفق بود.
                        لینک فعال‌سازی را دستی به او بدهید:
                    </p>
                    <div class="field has-addons">
                        <div class="control is-expanded">
                            <input class="input" type="text" dir="ltr" readonly x-ref="inviteLink" :value="invite && invite.link">
                        </div>
                        <div class="control">
                            <button class="button" type="button" @click="copyInviteLink">کپی</button>
                        </div>
                    </div>
                </div>

                <div class="box">
                    <h2 class="title is-6">افزودن عضو جدید</h2>
                    <form @submit.prevent="addMember">
                        <div class="field is-grouped is-grouped-multiline">
                            <div class="control is-expanded">
                                <label class="label" for="memberEmail">ایمیل</label>
                                <input class="input" id="memberEmail" type="email" dir="ltr" x-model="form.email" required>
                            </div>
                            <div class="control is-expanded">
                                <label class="label" for="memberFullName">نام و نام خانوادگی</label>
                                <input class="input" id="memberFullName" type="text" x-model="form.fullName" required>
                            </div>
                            <div class="control">
                                <label class="label" for="memberRole">نقش</label>
                                <div class="select">
                                    <select id="memberRole" x-model="form.roleCode">
                                        <template x-for="code in roleCodes" :key="code">
                                            <option :value="code" x-text="labelFor(code)"></option>
                                        </template>
                                    </select>
                                </div>
                            </div>
                            <div class="control app-control-align-end">
                                <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                    افزودن
                                </button>
                            </div>
                        </div>
                    </form>
                </div>

                <%-- فهرست ردیفی (DESIGN.md §5a): جست‌وجوی نام/ایمیل/نقش، هر عضو یک ردیف با آواتار، نقش و وضعیت به
                     کلمه (نه فقط رنگ)، و دو اقدام کم‌حجم. --%>
                <template x-if="members.length > 0">
                    <div>
                        <div class="field">
                            <label class="label is-sr-only" for="memberSearch">جست‌وجو در اعضا</label>
                            <div class="control">
                                <input class="input" id="memberSearch" type="search" x-model="filterText"
                                       placeholder="جست‌وجوی نام، ایمیل یا نقش">
                            </div>
                        </div>

                        <div class="app-list" role="list">
                            <template x-for="member in visibleMembers()" :key="member.membershipId">
                                <div class="app-member-row" role="listitem">
                                    <span class="app-avatar" aria-hidden="true" x-text="member.fullName.charAt(0)"></span>
                                    <div class="app-member-row__main">
                                        <span class="app-member-row__name" x-text="member.fullName"></span>
                                        <span class="is-size-7 has-text-grey" dir="ltr" x-text="member.email"></span>
                                        <span class="is-size-7 has-text-grey" x-text="labelFor(member.roleCode)"></span>
                                    </div>
                                    <div class="app-member-row__status">
                                        <span class="tag is-success" x-show="member.active">فعال</span>
                                        <span class="tag is-danger" x-show="!member.active">غیرفعال</span>
                                        <span class="tag is-light" x-show="member.hasPassword">فعال‌سازی شده</span>
                                        <span class="tag is-warning" x-show="!member.hasPassword">در انتظار فعال‌سازی</span>
                                    </div>
                                    <div class="app-member-row__actions">
                                        <button class="button is-light" type="button" @click="member.active ? askDeactivate(member) : toggleActive(member)">
                                            <span x-show="member.active">غیرفعال کردن</span>
                                            <span x-show="!member.active">فعال کردن</span>
                                        </button>
                                        <button class="button is-light" type="button" @click="sendInvite(member)">
                                            ارسال/تکرار دعوت
                                        </button>
                                    </div>
                                </div>
                            </template>
                        </div>
                        <div class="mt-3" x-show="remainingCount() > 0">
                            <button class="button is-light" type="button" @click="showMore">
                                نمایش بیشتر (<span x-text="remainingLabel()"></span> مورد دیگر)
                            </button>
                        </div>
                        <p class="has-text-grey mt-3" x-show="visibleMembers().length === 0">
                            عضوی با این جست‌وجو پیدا نشد.
                        </p>
                    </div>
                </template>

                @template.layout.empty-state(
                    show = "!loading && members.length === 0",
                    art = "people",
                    message = @`هنوز عضوی در این مدرسه ثبت نشده است.`)

                <%-- ------------------------------------------------ تأیید غیرفعال کردن عضو --%>
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="peopleConfirmTitle"
                     tabindex="-1" x-ref="confirmModal" :class="{ 'is-active': confirm }" x-cloak
                     @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeConfirm">
                    <div class="modal-background" @click="closeConfirm"></div>
                    <div class="modal-card">
                        <header class="modal-card-head">
                            <p class="modal-card-title" id="peopleConfirmTitle">تأیید غیرفعال کردن عضو</p>
                            <button class="delete" type="button" aria-label="بستن" @click="closeConfirm"></button>
                        </header>
                        <section class="modal-card-body">
                            <div class="notification is-danger" role="alert"
                                 x-show="confirm && confirm.error"><span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="confirm ? confirm.error : ''"></span></span></div>
                            <p class="has-text-weight-semibold">
                                عضویتِ <span x-text="confirm ? confirm.member.fullName : ''"></span> در این مدرسه غیرفعال شود؟
                            </p>
                            <p class="has-text-grey is-size-7">
                                دسترسیِ این عضو با این نقش به مدرسه بسته می‌شود. حساب کاربری و اطلاعاتش حذف نمی‌شود و هر وقت خواستید می‌توانید دوباره فعالش کنید.
                            </p>
                        </section>
                        <footer class="modal-card-foot">
                            <button class="button is-danger" type="button" id="peopleConfirmDeactivate"
                                    :class="{ 'is-loading': toggling }" @click="deactivate">غیرفعال کردن</button>
                            <button class="button" type="button" @click="closeConfirm">انصراف</button>
                        </footer>
                    </div>
                </div>
            `)
    </div>
</section>
`)
