@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "اعضای مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/people.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="peoplePage()">
    <div class="container">
        <h1 class="title is-4">اعضای مدرسه</h1>

        <div class="notification is-warning" x-show="!schoolId">
            برای مدیریت اعضا، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

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
                            <div class="control" style="align-self: flex-end">
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
                        <th>نام</th>
                        <th>ایمیل</th>
                        <th>نقش</th>
                        <th>وضعیت</th>
                        <th>عملیات</th>
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

                <div class="notification" x-show="!loading && members.length === 0">
                    هنوز عضوی در این مدرسه ثبت نشده است.
                </div>
            </div>
        </template>
    </div>
</section>
`)
