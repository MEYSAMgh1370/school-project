@import com.example.roshdeandishe.web.DashboardPageController.DashboardModel
@import com.example.roshdeandishe.web.NavbarModel
@import com.example.roshdeandishe.web.RoleLabels

@param DashboardModel model
@param NavbarModel navbar

@template.layout.main(title = "داشبورد", scripts = java.util.List.of("/js/navbar.js", "/js/dashboard.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section">
    <div class="container">
        <h1 class="title is-4">عضویت‌های من</h1>
        <p class="subtitle is-6" dir="ltr">${model.email()}</p>

        @if(model.memberships().isEmpty())
            <div class="notification">
                هنوز در هیچ مدرسه‌ای عضویتی ندارید. می‌توانید همین حالا مدرسه‌ی
                خودتان را بسازید، یا منتظر دعوت‌نامه‌ی مدرسه‌تان بمانید.
            </div>
        @else
            <table class="table is-fullwidth is-striped">
                <thead>
                <tr>
                    <th>مدرسه</th>
                    <th>نقش</th>
                    <th>وضعیت</th>
                </tr>
                </thead>
                <tbody>
                @for(var membership : model.memberships())
                    <tr>
                        <td>${membership.schoolName()}</td>
                        <td>${RoleLabels.persian(membership.roleCode())}</td>
                        <td>
                            @if(membership.active())
                                <span class="tag is-success">فعال</span>
                            @else
                                <span class="tag is-danger">غیرفعال</span>
                            @endif
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif

        <!-- ساخت مدرسه‌ی self-service (CLAUDE.md §1): سازنده بلافاصله
             SCHOOL_ADMIN می‌شود. کامپوننت createSchoolForm در
             /js/dashboard.js است - قانون جای‌گذاری کد §2 (fetch → فایل استاتیک). -->
        <div class="box mt-5" x-data="createSchoolForm">
            <button class="button is-link" x-show="!open" @click="open = true">
                + ساخت مدرسه‌ی جدید
            </button>
            <div x-show="open" x-cloak>
                <h2 class="title is-5">ساخت مدرسه‌ی جدید</h2>
                <p class="subtitle is-6">شما بلافاصله مدیر (SCHOOL_ADMIN) این مدرسه می‌شوید.</p>

                <form @submit.prevent="submit">
                    <div class="field">
                        <label class="label" for="schoolName">نام مدرسه</label>
                        <div class="control">
                            <input class="input" id="schoolName" type="text" x-model="name" required>
                        </div>
                    </div>

                    <div class="field is-grouped">
                        <div class="control is-expanded">
                            <label class="label" for="schoolTimezone">منطقه‌ی زمانی</label>
                            <input class="input" id="schoolTimezone" type="text" x-model="timezone" required dir="ltr">
                        </div>
                        <div class="control is-expanded">
                            <label class="label" for="schoolLocale">زبان/منطقه</label>
                            <input class="input" id="schoolLocale" type="text" x-model="locale" required dir="ltr">
                        </div>
                    </div>

                    <div class="field is-grouped">
                        <div class="control">
                            <button class="button is-link" type="submit" :disabled="!canSubmit" :class="{ 'is-loading': submitting }">
                                ساخت مدرسه
                            </button>
                        </div>
                        <div class="control">
                            <button class="button" type="button" @click="open = false">انصراف</button>
                        </div>
                    </div>
                </form>

                <div class="notification is-danger mt-4" x-show="errorMessage" x-text="errorMessage"></div>
            </div>
        </div>
    </div>
</section>
`)
