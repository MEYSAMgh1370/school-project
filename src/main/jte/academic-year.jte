@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "سال تحصیلی", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/academic-year.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1" x-data="academicYearPage()">
    <div class="container">
        @template.layout.page-header(
            title = "سال تحصیلی",
            subtitle = "سال تحصیلیِ جاریِ این مدرسه؛ صفحه‌های دیگر آن را به‌عنوان سال پیش‌فرض می‌گیرند.")

        @template.layout.school-scope(
            noSchoolMessage = "برای تنظیم سال تحصیلی، یک مدرسه از سوییچر بالا انتخاب کنید.",
            content = @`

                <p x-show="loading">در حال بارگذاری...</p>

                <template x-if="!blocked">
                    <div class="box" x-cloak x-show="!loading && loaded">
                        <p class="is-size-7 has-text-grey mb-1">سال تحصیلی جاری</p>
                        <p class="title is-3 mb-4" dir="ltr" id="academicYearCurrent" x-show="current"
                           x-text="current ? Calendar.digits(current.code) : ''"></p>
                        <p class="mb-4" id="academicYearNone" x-show="!current">
                            هنوز سال تحصیلی‌ای برای این مدرسه انتخاب نشده است.
                        </p>
                        <%-- اولین انتخابِ سالِ جاری دیدِ معلم/دانش‌آموز/ولی را به همان سال محدود می‌کند
                             (academic-year.md فاز ۱)؛ مدیر باید پیش از ثبت بداند --%>
                        <div class="notification is-warning is-light" id="academicYearFirstChoiceWarning" x-show="!current">
                            تا وقتی سال جاری انتخاب نشود، همه‌ی کاربران همه‌ی سال‌ها را می‌بینند. با اولین انتخاب،
                            معلم، دانش‌آموز و ولی فقط اطلاعاتِ همان سال را می‌بینند؛ مثلاً نمره‌های سال‌های دیگر
                            (و نمره‌هایی که سالشان در فهرست نیست) فقط برای مدیر مدرسه قابل مشاهده می‌ماند و
                            کارنامه‌ی سال‌های قبل را مدیر تحویل می‌دهد.
                        </div>

                        <form @submit.prevent="save">
                            <div class="field is-grouped is-grouped-multiline">
                                <div class="control">
                                    <label class="label" for="academicYearSelect">انتخاب سال تحصیلی</label>
                                    <div class="select">
                                        <select id="academicYearSelect" x-model="selectedId" required>
                                            <option value="" disabled>یک سال را انتخاب کنید</option>
                                            <template x-for="year in years" :key="year.id">
                                                <option :value="year.id" x-text="Calendar.digits(year.code)"></option>
                                            </template>
                                        </select>
                                    </div>
                                </div>
                                <div class="control app-control-align-end">
                                    <button class="button is-link" type="submit"
                                            :disabled="!canSave()" :class="{ 'is-loading': saving }">
                                        ثبت سال تحصیلی
                                    </button>
                                </div>
                            </div>
                            <p class="help">
                                شروع و پایانِ هر سال با انتخابِ شما تعیین می‌شود. با تغییرِ سال جاری هیچ داده‌ای
                                حذف یا تغییر نمی‌کند؛ سالِ پیش‌فرضِ صفحه‌ها عوض می‌شود و معلم، دانش‌آموز و ولی
                                فقط اطلاعاتِ سالِ جاری را می‌بینند (شما هر سالی را می‌بینید). هر سالِ تحصیلیِ تازه
                                خودکار، در تاریخِ تعیین‌شده در تنظیمات سیستم، به این فهرست اضافه می‌شود.
                            </p>
                        </form>
                    </div>
                </template>
            `)
    </div>
</section>
`)
