@import com.example.roshdeandishe.web.model.DashboardModel
@import com.example.roshdeandishe.web.model.NavCatalog
@import com.example.roshdeandishe.web.model.NavbarModel

@param DashboardModel model
@param NavbarModel navbar

@template.layout.main(title = "پیشخوان", scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/calendar.js", "/js/timetable-grid.js", "/js/date-input.js", "/js/school-api.js", "/js/upcoming-events-logic.js", "/js/dashboard.js", "/js/dashboard-timetable.js", "/js/dashboard-upcoming.js"),
        styles = java.util.List.of("/css/dashboard.css", "/css/timetable.css"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1">
    <div class="container">
        <h1 class="title is-4">عضویت‌های من</h1>
        <p class="subtitle is-6" dir="ltr">${model.email()}</p>

        <%-- سالِ تحصیلیِ جاریِ مدرسه‌ی انتخاب‌شده (academic-year.md فاز ۱)؛ مدرسه‌ی بدونِ سالِ جاری چیزی نشان نمی‌دهد --%>
        @for(var school : navbar.schools())
            @if(school.currentYear() != null)
                <p class="is-size-7 has-text-grey mb-4" x-data x-cloak x-show="$store.nav.selectedSchoolId === ${school.id()}">
                    سال تحصیلی جاری: <span dir="ltr">${school.currentYearDisplay()}</span>
                </p>
            @endif
        @endfor

        <%-- «دسترسی سریع»: همان لینک‌های سایدبار، از منبع واحد NavCatalog (فقط
             نمایش - کنترل دسترسی سمت سرور می‌ماند). نقشِ انتخاب‌شده از
             $store.nav (آینه‌ی سوییچر navbar.js) می‌آید؛ کاربر بدون عضویت نقشی
             ندارد و کل بخش پنهان می‌ماند. x-data خالی لازم است: Alpine فقط داخل
             یک x-data ریشه را راه‌اندازی می‌کند و این بخش ریشه‌ی دیگری ندارد. --%>
        <section class="mb-5" aria-labelledby="dashboard-shortcuts-title" x-data
                 x-cloak x-show="${NavCatalog.anyShortcutVisibleWhen("$store.nav.selectedRole", "$store.nav.permissions")}">
            <h2 class="title is-5" id="dashboard-shortcuts-title">دسترسی سریع</h2>
            <%-- چیدمان (DESIGN.md §5a): کارتِ تمام‌عرض ← کارت‌های نیم‌عرض ← ردیف‌های فهرست به تفکیک
                 گروهِ NavCatalog. جایگاهِ هر لینک (Tier) ثابت است، چون نقشِ انتخاب‌شده client-side
                 عوض می‌شود و هر زیرمجموعه‌ای از کارت‌ها باید درست چیده شود. همه‌ی لینک‌ها
                 data-shortcut دارند (تست‌ها با همین نشانه می‌شمارند). --%>
            <div class="columns is-multiline is-mobile is-variable is-3">
                @for(var link : NavCatalog.dailyShortcuts())
                    <div class="column ${link.isFeatured() ? "is-12" : "is-6 is-6-mobile"}"
                         x-cloak x-show="${link.shortcutVisibleWhen("$store.nav.selectedRole", "$store.nav.permissions")}">
                        <a class="box app-shortcut${link.isFeatured() ? " app-shortcut--featured" : ""}" href="${link.href()}" data-shortcut>
                            <span class="app-tile">@template.layout.icon(name = link.icon(), size = 24)</span>
                            <span class="app-shortcut__text">
                                <span class="title ${link.isFeatured() ? "is-5" : "is-6"} mb-1">${link.label()}</span>
                                <span class="is-size-7 has-text-grey">${link.description()}</span>
                            </span>
                            @if(link.isFeatured())
                                @template.layout.icon(name = "chevron", size = 20)
                            @endif
                        </a>
                    </div>
                @endfor
            </div>
            @for(var group : NavCatalog.groups())
                @if(!group.rowShortcuts().isEmpty())
                    <div class="mb-4" x-cloak
                         x-show="${group.rowsVisibleWhen("$store.nav.selectedRole", "$store.nav.permissions")}">
                        <h3 class="title is-6 mb-2">${group.label()}</h3>
                        <div class="app-list">
                            @for(var link : group.rowShortcuts())
                                <a class="app-row" href="${link.href()}" data-shortcut
                                   x-cloak x-show="${link.shortcutVisibleWhen("$store.nav.selectedRole", "$store.nav.permissions")}">
                                    @template.layout.icon(name = link.icon(), size = 20)
                                    <span class="app-row__label">${link.label()}</span>
                                    @if("/academic-year".equals(link.href()))
                                        @for(var school : navbar.schools())
                                            @if(school.currentYear() != null)
                                                <span class="is-size-7 has-text-grey" dir="ltr"
                                                      x-show="$store.nav.selectedSchoolId === ${school.id()}">${school.currentYearDisplay()}</span>
                                            @endif
                                        @endfor
                                    @endif
                                    @template.layout.icon(name = "chevron", size = 16)
                                </a>
                            @endfor
                        </div>
                    </div>
                @endif
            @endfor
        </section>

        <%-- «وضعیت شهریه»: فقط برای مدیرِ مدرسه (چکِ literal SCHOOL_ADMIN در کنترلر) و فقط
             وقتی نقشِ انتخاب‌شده «مدیر» و مدرسه‌ی انتخاب‌شده همین مدرسه است (نمایشی، مثل
             بقیه‌ی بخش‌ها). فقط درصدِ پرداخت به صورت‌حساب است، نه مبلغ (واحد پولی
             تصمیم‌نگرفته). --%>
        @for(var row : model.tuition())
            <section class="mb-5" aria-labelledby="dashboard-tuition-title-${row.schoolId()}" x-data
                     x-cloak x-show="$store.nav.selectedRole === 'SCHOOL_ADMIN' && $store.nav.selectedSchoolId === ${row.schoolId()}">
                <div class="box">
                    <h2 class="title is-5 mb-1" id="dashboard-tuition-title-${row.schoolId()}">وضعیت شهریه</h2>
                    <p class="is-size-7 has-text-grey mb-3">${row.schoolName()}</p>
                    @if(row.hasInvoices())
                        <p class="mb-2">
                            <strong><span x-text="Calendar.digits('${row.percentPaid()}')">${row.percentPaid()}</span>٪</strong>
                            از شهریه‌ی صادرشده پرداخت شده است.
                        </p>
                        <progress class="progress is-primary" value="${row.percentPaid()}" max="100"
                                  aria-label="درصد پرداخت شهریه">${row.percentPaid()}٪</progress>
                    @else
                        <p class="mb-0">هنوز فاکتور شهریه‌ای صادر نشده است؛ بعد از صدور، درصد پرداخت اینجا می‌آید.</p>
                    @endif
                    <p class="mt-3 mb-0"><a href="/finance">امور مالی</a></p>
                </div>
            </section>
        @endfor

        <%-- «حضور و غیاب امروز»: کنارِ «وضعیت شهریه»، با همان الگو: فقط برای مدیرِ مدرسه (چکِ literal
             SCHOOL_ADMIN در کنترلر) و فقط مدرسه‌ای که ماژول حضور و غیاب را دارد (AttendanceTodayAssembler)؛
             نمایشِ سمتِ کلاینت هم به نقشِ انتخاب‌شده و مدرسه‌ی انتخاب‌شده وابسته است. فقط رقم‌های خلاصه
             (غایب، درصدِ حضور، کلاسِ بدونِ ثبت)، نه نامِ دانش‌آموزان؛ نام‌ها در /attendance/report می‌مانند. --%>
        @for(var attendance : model.attendanceToday())
            <section class="mb-5" aria-labelledby="dashboard-attendance-title-${attendance.schoolId()}" x-data
                     x-cloak x-show="$store.nav.selectedRole === 'SCHOOL_ADMIN' && $store.nav.selectedSchoolId === ${attendance.schoolId()}">
                <div class="box">
                    <h2 class="title is-5 mb-1" id="dashboard-attendance-title-${attendance.schoolId()}">حضور و غیاب امروز</h2>
                    <p class="is-size-7 has-text-grey mb-3">${attendance.schoolName()}</p>
                    @if(!attendance.schoolDay())
                        <p class="mb-0">امروز روز درسیِ مدرسه نیست (آخرِ هفته یا تعطیلیِ ثبت‌شده در تقویم مدرسه).</p>
                    @elseif(!attendance.hasRoll())
                        <p class="mb-0">هنوز حضور و غیابی ثبت نشده است؛ بعد از ثبتِ معلم‌ها، رقم اینجا می‌آید.</p>
                    @else
                        <p class="mb-2">
                            <strong><span x-text="Calendar.digits('${attendance.absent()}')">${attendance.absent()}</span> غایب</strong>
                            ·
                            <strong><span x-text="Calendar.digits('${attendance.percentValue()}')">${attendance.percentValue()}</span>٪ حضور</strong>
                        </p>
                        <progress class="progress is-primary" value="${attendance.percentValue()}" max="100"
                                  aria-label="درصد حضور امروز">${attendance.percentValue()}٪</progress>
                        @if(attendance.classesWithoutRoll() > 0)
                            <p class="is-size-7 has-text-grey mb-0">
                                <span x-text="Calendar.digits('${attendance.classesWithoutRoll()}')">${attendance.classesWithoutRoll()}</span> کلاس بدون ثبت
                            </p>
                        @endif
                    @endif
                    <p class="mt-3 mb-0"><a href="/attendance/report">غایبین امروز</a></p>
                </div>
            </section>
        @endfor

        <%-- «رویدادهای نزدیک»: رویدادهای برنامه‌ی سالانه‌ی مدرسه‌ی انتخاب‌شده که پنجره‌ی یادآوری‌شان شروع شده
             (openspec/changes/event-reminders.md). کاملاً client-side از /api/me/schools/{id}/upcoming-events؛ برای
             غیرگیرنده لیست خالی می‌آید. بخش تا وقتی رویدادی نباشد (بارگذاری، لیست خالی، مدرسه‌ی انتخاب‌نشده، خطا)
             پنهان می‌ماند. --%>
        <section class="mb-5" aria-labelledby="dashboard-upcoming-title" x-data="dashboardUpcoming()"
                 x-cloak x-show="rows.length > 0" id="dashboardUpcoming">
            <div class="box">
                <h2 class="title is-5 mb-3" id="dashboard-upcoming-title">رویدادهای نزدیک</h2>
                <ul id="dashboardUpcomingList">
                    <template x-for="row in rows" :key="row.id">
                        <li class="mb-2">
                            <span class="has-text-weight-semibold" x-text="row.title"></span>
                            <span class="is-block is-size-7 has-text-grey" x-text="row.date + '، ' + row.relative"></span>
                        </li>
                    </template>
                </ul>
                <p class="mt-3 mb-0"><a href="/yearly-plan">برنامه‌ی سالانه</a></p>
            </div>
        </section>

        <%-- «برنامه‌ی امروز»: فقط برای نقشِ انتخاب‌شده‌ی «دانش‌آموز» (تصمیمِ محصول: دانش‌آموز
             برنامه را در صفحه‌ی اول می‌بیند؛ معلم و والد در /my-timetable). داده کاملاً
             client-side از /api/me/schools/{id}/timetable می‌آید و برای نقش‌های دیگر هیچ
             درخواستی نمی‌رود. --%>
        <section class="mb-5" aria-labelledby="dashboard-timetable-title" x-data="dashboardTimetable()"
                 x-cloak x-show="isStudent()" id="dashboardTimetable">
            <div class="box">
                <h2 class="title is-5 mb-1" id="dashboard-timetable-title">برنامه‌ی امروز</h2>
                <p class="is-size-7 has-text-grey mb-3" x-show="isSchoolDay()">
                    <span id="dashboardTimetableDay" x-text="dayTitle()"></span>
                    <span x-show="className" x-text="'، کلاس ' + className"></span>
                </p>

                <p x-show="loading">در حال بارگذاری...</p>
                <div class="notification is-danger" role="alert" x-show="error" x-text="error"></div>

                <div x-show="loaded && !error">
                    <p id="dashboardTimetableNotEnrolled" x-show="!enrolled">
                        هنوز در هیچ کلاسی ثبت‌نام نشده‌اید؛ بعد از ثبت‌نام، برنامه‌ی کلاس شما اینجا می‌آید.
                    </p>
                    <p id="dashboardTimetableNoYear" x-show="enrolled && academicYear === null">
                        مدیر مدرسه هنوز سال تحصیلیِ جاری را انتخاب نکرده است.
                    </p>
                    <p id="dashboardTimetableHoliday" x-show="enrolled && academicYear !== null && !isSchoolDay()">
                        امروز روز کلاسی نیست (روزهای کلاسی <span x-text="schoolDaysLabel()"></span> است).
                    </p>
                    <p id="dashboardTimetableNone"
                       x-show="enrolled && academicYear !== null && isSchoolDay() && todayRows().length === 0">
                        برای امروز درسی در برنامه‌ی کلاس شما ثبت نشده است.
                    </p>
                    <ul class="app-tt-today-list" id="dashboardTimetableList" x-show="enrolled && isSchoolDay() && todayRows().length > 0">
                        <template x-for="row in todayRows()" :key="row.period.id">
                            <li>
                                <span class="app-tt-time" x-text="rangeLabel(row.period)"></span>
                                <span>
                                    <span class="app-tt-subject" x-text="subjectOf(row.entry)"></span>
                                    <span class="app-tt-meta" x-show="row.entry.teacherName" x-text="row.entry.teacherName"></span>
                                </span>
                            </li>
                        </template>
                    </ul>
                </div>

                <p class="mt-3 mb-0"><a href="/my-timetable">برنامه‌ی کل هفته</a></p>
            </div>
        </section>

        <%-- «فرزندان من»: فقط برای کاربری که در حداقل یک مدرسه والدِ فعال است و فقط
             وقتی نقشِ انتخاب‌شده «والد» است (همان مدل کارت‌های دسترسی سریع،
             $store.nav). داده فقط فرزندانِ واقعاً لینک‌شده به همین کاربر است
             (WardsAssembler). وضعیت مالی فقط شمارش فاکتورها بر اساس وضعیتشان است،
             نه مبلغ (واحد پولی هنوز تصمیم‌نگرفته). عددها اینجا ASCII رندر می‌شوند و
             با Calendar.digits به رقم فارسی درمی‌آیند (DESIGN.md §8).
             x-data خالی لازم است: Alpine بدون ریشه‌ی x-data x-show را اجرا نمی‌کند. --%>
        @if(model.parent())
            <section class="mb-5" aria-labelledby="dashboard-wards-title" x-data
                     x-cloak x-show="['PARENT'].includes($store.nav.selectedRole)">
                <h2 class="title is-5" id="dashboard-wards-title">فرزندان من</h2>
                @if(model.wards().isEmpty())
                    <div class="notification is-light">
                        هنوز دانش‌آموزی به حساب شما لینک نشده است. مدیر مدرسه می‌تواند از صفحه‌ی
                        «دانش‌آموزان» فرزندتان را به حساب شما متصل کند.
                    </div>
                @else
                    <div class="columns is-multiline is-variable is-3">
                        @for(var ward : model.wards())
                            <div class="column is-6-desktop is-12-tablet is-12-mobile">
                                <article class="box app-ward">
                                    <h3 class="title is-6 mb-2">${ward.name()}</h3>
                                    <p class="is-size-7 has-text-grey mb-2">${ward.schoolName()}</p>
                                    <div class="tags mb-2">
                                        @if(ward.hasClass())
                                            <span class="tag is-light">کلاس ${ward.className()}</span>
                                        @endif
                                        @if(ward.hasSection())
                                            <span class="tag is-light">بخش ${ward.section()}</span>
                                        @endif
                                    </div>
                                    <p class="mb-0">
                                        <span class="is-size-7 has-text-grey">وضعیت مالی:</span>
                                        @if(ward.hasNoInvoices())
                                            <span class="tag is-light">فاکتوری صادر نشده</span>
                                        @elseif(ward.isOverdue())
                                            <span class="tag is-danger"><span x-text="Calendar.digits('${ward.overdueCount()}')">${ward.overdueCount()}</span>&nbsp;فاکتور دیرکرد</span>
                                        @elseif(ward.isOpen())
                                            <span class="tag is-warning"><span x-text="Calendar.digits('${ward.openCount()}')">${ward.openCount()}</span>&nbsp;فاکتور در انتظار پرداخت</span>
                                        @else
                                            <span class="tag is-success">همه‌ی فاکتورها تسویه‌شده</span>
                                        @endif
                                    </p>
                                    <%-- لینک‌های هر فرزند: فقط شناسه‌ی او و شناسه‌ی مدرسه در URL می‌رود
                                         (نه نام - اطلاعات کودک نباید در تاریخچه/لاگ نشت کند). navbar
                                         مقصد، ?school را به‌عنوان مدرسه‌ی انتخاب‌شده می‌پذیرد، پس لینک
                                         بدون وابستگی به کلیک (تب جدید، کلیک میانی) درست کار می‌کند.
                                         کنترل‌ها اندازه‌ی عادی دارند (۴۴px). --%>
                                    <div class="buttons mt-3 mb-0">
                                        <a class="button is-light" href="/my-grades?student=${ward.studentId()}&school=${ward.schoolId()}">نمره‌ها</a>
                                        <a class="button is-light" href="/my-invoices?student=${ward.studentId()}&school=${ward.schoolId()}">فاکتورها</a>
                                        <a class="button is-light" href="/my-attendance?school=${ward.schoolId()}">غیبت‌ها</a>
                                    </div>
                                </article>
                            </div>
                        @endfor
                    </div>
                @endif
            </section>
        @endif

        <%-- CTA ثبت مدرسه فقط در حالت بدون عضویت (جریان بنیان‌گذار):
             به دانش‌آموز/والدِ دعوت‌شده پیشنهاد «مدرسه بسازید» نمی‌دهیم
             (تصمیم ۲۰۲۶-۰۷-۰۶؛ فرم به /schools/new منتقل شد). --%>
        @if(model.memberships().isEmpty())
            <div class="notification">
                هنوز در هیچ مدرسه‌ای عضویتی ندارید. می‌توانید همین حالا
                <a href="/schools/new">مدرسه‌ی خودتان را ثبت کنید</a>،
                یا منتظر دعوت‌نامه‌ی مدرسه‌تان بمانید.
            </div>
        @else
            <div class="table-container">
            <table class="table is-fullwidth is-striped">
                <thead>
                <tr>
                    <th scope="col">مدرسه</th>
                    <th scope="col">نقش</th>
                    <th scope="col">وضعیت</th>
                </tr>
                </thead>
                <tbody>
                @for(var membership : model.memberships())
                    <tr>
                        <td>${membership.schoolName()}</td>
                        <td>${navbar.roleLabel(membership.schoolId(), membership.roleCode())}</td>
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
            </div>
        @endif

        <%-- نشانی ورود مدرسه: فقط برای مدرسه‌هایی که کاربر در آن‌ها مدیرِ
             فعال است. داده سمت سرور رندر می‌شود (بدون fetch، بدون
             X-School-ID) و به سوییچرِ ناوبار واکنش نشان نمی‌دهد چون هر
             مدرسه‌ی مدیریت‌شده هم‌زمان فهرست می‌شود، نه فقط مدرسه‌ی انتخابی. --%>
        @if(!model.schoolEntries().isEmpty())
            <div class="box" x-data="schoolEntryLinks()">
                <h2 class="title is-5">نشانی ورود مدرسه</h2>
                <p class="is-size-7 has-text-grey mb-3">
                    این نشانی را بین معلم‌ها، دانش‌آموزان و والدین پخش کنید تا
                    از درِ اختصاصیِ مدرسه‌ی شما وارد شوند.
                </p>
                @for(var entry : model.schoolEntries())
                    <div class="mb-4">
                        <h3 class="title is-6 mb-2">${entry.schoolName()}</h3>
                        @if(entry.hasSlug())
                            <%-- slug از طریق data-attribute به Alpine می‌رسد، نه
                                 با درج مستقیم در عبارت JS - همان قاعده‌ای که
                                 باکس شماره تلفن بالاتر رعایت می‌کند. اشتراک‌گذاری
                                 اقدامِ اصلی است (برگه‌ی اشتراکِ گوشی)؛ «کپی نشانی»
                                 دومی (DESIGN.md §5a). --%>
                            <p class="app-url mb-3">
                                <a href="/s/${entry.slug()}" dir="ltr" data-slug="${entry.slug()}"
                                   x-text="entryUrl($el.dataset.slug)"></a>
                            </p>
                            <div class="buttons">
                                <button class="button is-primary" type="button" data-slug="${entry.slug()}"
                                        @click="share($el.dataset.slug)">
                                    @template.layout.icon(name = "share", size = 20)
                                    <span class="app-inline-gap-xs"></span>اشتراک‌گذاری
                                </button>
                                <button class="button is-light" type="button" data-slug="${entry.slug()}"
                                        @click="copy($el.dataset.slug)">
                                    کپی نشانی
                                </button>
                            </div>
                        @else
                            <p class="is-size-7 has-text-grey">
                                این مدرسه نشانی اختصاصی ندارد. نشانی فقط هنگام
                                ثبت مدرسه انتخاب می‌شود.
                            </p>
                        @endif
                    </div>
                @endfor
                <div class="notification is-danger mt-3" role="alert"
                     x-cloak x-show="errorMessage" x-text="errorMessage"></div>
                <div class="notification is-success mt-3" role="status" aria-live="polite"
                     x-cloak x-show="successMessage" x-text="successMessage"></div>
            </div>
        @endif

        <%-- شماره تلفن روی data-attribute (نه داخل عبارت x-data) قرار می‌گیرد
             تا از escape شدنِ صحیحِ HTML مطمئن باشیم - setPhoneNumber فقط طول
             را اعتبارسنجی می‌کند، نه نویسه‌ها، پس درج مستقیم داخل یک عبارت JS
             می‌توانست با یک تکِ‌کوتیشن در مقدار بشکند. --%>
        <div class="box" data-phone-number="${model.phoneNumber() == null ? "" : model.phoneNumber()}"
             x-data="phoneNumberField()" x-init="phoneNumber = $el.dataset.phoneNumber">
            <h2 class="title is-5">شماره تلفن</h2>
            <p class="is-size-7 has-text-grey mb-3">
                با ثبت شماره تلفن می‌توانید ربات بله را به حساب خود متصل کنید و
                با کدِ پیامکی (به‌جای رمز عبور) وارد شوید.
            </p>
            <form @submit.prevent="save">
                <label class="label" for="dashboardPhone">شماره تلفن همراه</label>
                <div class="field has-addons">
                    <div class="control is-expanded">
                        <input class="input" id="dashboardPhone" type="tel" dir="ltr" x-model="phoneNumber"
                               autocomplete="tel" inputmode="tel" placeholder="۰۹۱۲۳۴۵۶۷۸۹" required>
                    </div>
                    <div class="control">
                        <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                            ذخیره
                        </button>
                    </div>
                </div>
            </form>
            <div class="notification is-danger mt-3" role="alert"
                 x-cloak x-show="errorMessage" x-text="errorMessage"></div>
            <div class="notification is-success mt-3" role="status" aria-live="polite"
                 x-cloak x-show="successMessage" x-text="successMessage"></div>
        </div>
    </div>
</section>
`)
