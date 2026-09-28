@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "اعضای مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/people.js"), content = @`
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

                <div class="table-container">
                <table class="table is-fullwidth is-striped">
                    <thead>
                    <tr>
                        <th scope="col">نام</th>
                        <th scope="col">ایمیل</th>
                        <th scope="col">نقش</th>
                        <th scope="col">وضعیت</th>
                        <th scope="col">عملیات</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template x-for="member in members" :key="member.membershipId">
                        <tr>
                            <td x-text="member.fullName"></td>
                            <td dir="ltr" x-text="member.email"></td>
                            <td x-text="labelFor(member.roleCode)"></td>
                            <td>
                                <span class="tag is-success" x-show="member.active">فعال</span>
                                <span class="tag is-danger" x-show="!member.active">غیرفعال</span>
                                <span class="tag is-light" x-show="member.hasPassword">فعال‌سازی شده</span>
                                <span class="tag is-warning" x-show="!member.hasPassword">در انتظار فعال‌سازی</span>
                            </td>
                            <td>
                                <button class="button is-small" type="button" @click="toggleActive(member)">
                                    <span x-show="member.active">غیرفعال کردن</span>
                                    <span x-show="!member.active">فعال کردن</span>
                                </button>
                                <button class="button is-small" type="button" @click="sendInvite(member)">
                                    ارسال/تکرار دعوت
                                </button>
                            </td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                </div>

                @template.layout.empty-state(
                    show = "!loading && members.length === 0",
                    art = "people",
                    message = @`هنوز عضوی در این مدرسه ثبت نشده است.`)
            `)
    </div>
</section>
`)
