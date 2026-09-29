@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- ثبت مدرسه‌ی self-service (CLAUDE.md §1): سازنده بلافاصله SCHOOL_ADMIN
     می‌شود. صفحه‌ی اختصاصی به‌جای فرم روی داشبورد (تصمیم ۲۰۲۶-۰۷-۰۶)؛
     کامپوننت createSchoolForm در /js/school-new.js است - قانون جای‌گذاری
     کد §2 (fetch → فایل استاتیک). --%>
@template.layout.main(title = "ثبت مدرسه", scripts = java.util.List.of("/js/navbar.js", "/js/school-new.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1">
    <div class="container app-narrow-container">
        <div class="box" x-data="createSchoolForm">
            <h1 class="title is-4">ثبت مدرسه‌ی جدید</h1>
            <p class="subtitle is-6">شما بلافاصله مدیر این مدرسه می‌شوید.</p>

            <form @submit.prevent="submit">
                <div class="field">
                    <label class="label" for="schoolName">نام مدرسه</label>
                    <div class="control">
                        <input class="input" id="schoolName" type="text" x-model="name" required>
                    </div>
                </div>

                <div class="field">
                    <label class="label" for="schoolLevel">مقطع تحصیلی</label>
                    <div class="control">
                        <div class="select is-fullwidth">
                            <select id="schoolLevel" x-model="level" required>
                                <template x-for="option in levelOptions" :key="option.value">
                                    <option :value="option.value" x-text="option.label"></option>
                                </template>
                            </select>
                        </div>
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

                <div class="field">
                    <label class="label" for="schoolSlug">نشانی اختصاصی (اختیاری)</label>
                    <div class="control">
                        <input class="input" id="schoolSlug" type="text" x-model="slug"
                               :class="{ 'is-danger': slug.trim() && !slugValid }"
                               dir="ltr" placeholder="tizhooshan" autocomplete="off">
                    </div>
                    <p class="help" x-show="!slug.trim() || slugValid">
                        حروف کوچک انگلیسی، رقم و خط تیره (۳ تا ۶۳ نویسه). درِ ورودی مدرسه‌تان می‌شود:
                        <span dir="ltr" x-text="slugPreview"></span>
                    </p>
                    <p class="help is-danger" x-show="slug.trim() && !slugValid">
                        فقط حروف کوچک انگلیسی، رقم و خط تیره (نه در ابتدا/انتها)، بین ۳ تا ۶۳ نویسه.
                    </p>
                </div>

                <div class="field is-grouped">
                    <div class="control">
                        <button class="button is-link" type="submit" :disabled="!canSubmit" :class="{ 'is-loading': submitting }">
                            ثبت مدرسه
                        </button>
                    </div>
                    <div class="control">
                        <a class="button" href="/dashboard">انصراف</a>
                    </div>
                </div>
            </form>

            <div class="notification is-danger mt-4" x-show="errorMessage" x-text="errorMessage"></div>
        </div>
    </div>
</section>
`)
