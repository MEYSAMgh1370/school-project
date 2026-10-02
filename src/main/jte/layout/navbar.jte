@import com.example.roshdeandishe.web.model.NavbarModel
@import com.example.roshdeandishe.web.model.RoleLabels

@param NavbarModel navbar

<%-- سوییچر مدرسه/نقش (Open Question 2 - حل‌شده): انتخاب فقط client-side
     است و در localStorage می‌ماند؛ نقش صرفاً نمایشی است (backend همه‌ی
     نقش‌های فعال را چک می‌کند - CLAUDE.md بند ۱۱ سؤال ۲). کاربر بدون
     عضویت (فرد B2C آینده) دراپ‌داون مدرسه را اصلاً نمی‌بیند. منطق در
     static/js/navbar.js (قاعده‌ی جانمایی کد ۲۰۲۶-۰۷-۰۵: fetch/regex در
     فایل استاتیک، نه inline).
     لینک‌های صفحه‌ها در سایدبار (aside.app-sidebar) زندگی می‌کنند، گروه‌بندی‌شده
     - هر صفحه‌ی مدیریتی جدید باید لینکش را همین‌جا به گروه مناسب اضافه کند
     (UX_ROADMAP.md §2: هیچ URL یتیمی وجود ندارد). نمایش لینک (و برچسبِ
     گروه) می‌تواند تابع نقشِ انتخاب‌شده باشد (فقط نمایش - کنترل دسترسی
     همیشه سمت سرور می‌ماند). نوار بالا فقط سوییچر مدرسه/نقش و منوی کاربر
     را دارد. wrapper با display: contents است تا nav و aside هر دو زیر
     همان x-data (selectedRole) باشند بی‌آنکه لایوت را تغییر دهد؛ چیدمانِ
     سایدبار در app.css («Sidebar layout»). هر گروه آکاردئون است: دکمه‌ی
     سرگروه (aria-expanded) لینک‌هایش را باز/بسته می‌کند؛ وضعیت در
     localStorage می‌ماند و گروهِ صفحه‌ی جاری همیشه باز است (navbar.js). --%>
<div class="app-shell-nav"
     x-data="schoolNavbar({@for(var school : navbar.schools())${school.id()}: [@for(var role : school.roles())'${role}',@endfor],@endfor})"
     @keydown.escape.window="schoolOpen = false; personOpen = false; menuOpen = false">
    <nav class="navbar app-topbar" role="navigation" aria-label="main navigation">
        <div class="navbar-brand">
            <span class="navbar-item has-text-weight-bold app-navbar-brand">رشدِاندیشه</span>
            <button type="button" class="navbar-burger" :class="{ 'is-active': menuOpen }"
                    aria-label="منو" aria-controls="app-sidebar" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
            </button>
        </div>
        <div class="navbar-menu">
            <div class="navbar-end">
                @if(!navbar.schools().isEmpty())
                    <div class="navbar-item has-dropdown" :class="{ 'is-active': schoolOpen }" @click.outside="schoolOpen = false">
                        <button type="button" class="navbar-link" aria-haspopup="true" :aria-expanded="schoolOpen" @click="schoolOpen = !schoolOpen; personOpen = false">
                            @for(var school : navbar.schools())
                                <span x-show="selectedSchoolId === ${school.id()}">${school.name()}</span>
                            @endfor
                        </button>
                        <div class="navbar-dropdown">
                            @for(var school : navbar.schools())
                                <button type="button" class="navbar-item" @click="selectSchool(${school.id()})">
                                    <span class="app-inline-gap" x-show="selectedSchoolId === ${school.id()}">✓</span>
                                    ${school.name()}
                                </button>
                            @endfor
                        </div>
                    </div>
                @endif
                <div class="navbar-item has-dropdown" :class="{ 'is-active': personOpen }" @click.outside="personOpen = false">
                    <button type="button" class="navbar-link" aria-haspopup="true" :aria-expanded="personOpen" @click="personOpen = !personOpen; schoolOpen = false">${navbar.fullName()}</button>
                    <div class="navbar-dropdown is-right">
                        @for(var school : navbar.schools())
                            @for(var role : school.roles())
                                <button type="button" class="navbar-item" x-show="selectedSchoolId === ${school.id()}" @click="selectRole('${role}')">
                                    <span class="app-inline-gap" x-show="selectedRole === '${role}'">✓</span>
                                    ${RoleLabels.persian(role)}
                                </button>
                            @endfor
                        @endfor
                        @if(!navbar.schools().isEmpty())
                            <hr class="navbar-divider">
                        @endif
                        <button type="button" class="navbar-item" @click="logout">خروج</button>
                    </div>
                </div>
            </div>
        </div>
    </nav>

    <aside class="app-sidebar menu" id="app-sidebar" :class="{ 'is-open': menuOpen }" aria-label="صفحه‌های مدرسه">
        <div class="app-sidebar-header">
            <a class="app-sidebar-brand" href="/dashboard">رشدِاندیشه</a>
            <button type="button" class="delete app-sidebar-close" aria-label="بستن منو" @click="menuOpen = false"></button>
        </div>
        <ul class="menu-list">
            <li><a href="/dashboard">داشبورد</a></li>
        </ul>
        @if(!navbar.schools().isEmpty())
            <div x-cloak x-show="['SCHOOL_ADMIN', 'TEACHER'].includes(selectedRole)" data-group="0">
                <button type="button" class="app-sidebar-toggle" aria-controls="sidebar-group-0-list" :aria-expanded="isGroupOpen(0)" @click="toggleGroup(0)">
                    <span>مدرسه</span>
                    <span class="app-sidebar-chevron" aria-hidden="true"></span>
                </button>
                <ul class="menu-list" id="sidebar-group-0-list" x-show="isGroupOpen(0)">
                    <li><a href="/people" x-show="selectedRole === 'SCHOOL_ADMIN'">اعضای مدرسه</a></li>
                    <li><a href="/students" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">دانش‌آموزان</a></li>
                    <li><a href="/roles" x-show="selectedRole === 'SCHOOL_ADMIN'">نقش‌ها و دسترسی‌ها</a></li>
                </ul>
            </div>
            <div x-cloak x-show="['SCHOOL_ADMIN', 'TEACHER', 'STUDENT'].includes(selectedRole)" data-group="1">
                <button type="button" class="app-sidebar-toggle" aria-controls="sidebar-group-1-list" :aria-expanded="isGroupOpen(1)" @click="toggleGroup(1)">
                    <span>آموزش</span>
                    <span class="app-sidebar-chevron" aria-hidden="true"></span>
                </button>
                <ul class="menu-list" id="sidebar-group-1-list" x-show="isGroupOpen(1)">
                    <li><a href="/classes" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">کلاس‌ها</a></li>
                    <li><a href="/curriculum" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">سرفصل‌ها و بانک سؤال</a></li>
                    <li><a href="/study-plans" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">برنامه‌های مطالعه</a></li>
                    <li><a href="/exams" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">آزمون‌ها</a></li>
                    <li><a href="/my-exams" x-show="selectedRole === 'STUDENT'">آزمون‌های من</a></li>
                    <li><a href="/my-study-plan" x-show="selectedRole === 'STUDENT'">برنامه‌ی مطالعه‌ی من</a></li>
                </ul>
            </div>
            <div x-cloak x-show="['SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT'].includes(selectedRole)" data-group="2">
                <button type="button" class="app-sidebar-toggle" aria-controls="sidebar-group-2-list" :aria-expanded="isGroupOpen(2)" @click="toggleGroup(2)">
                    <span>نمرات</span>
                    <span class="app-sidebar-chevron" aria-hidden="true"></span>
                </button>
                <ul class="menu-list" id="sidebar-group-2-list" x-show="isGroupOpen(2)">
                    <li><a href="/grades" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">دفتر نمرات</a></li>
                    <li><a href="/my-grades" x-show="selectedRole === 'STUDENT' || selectedRole === 'PARENT'">نمره‌های من</a></li>
                </ul>
            </div>
            <div x-cloak x-show="['SCHOOL_ADMIN', 'FINANCE_ADMIN', 'STUDENT', 'PARENT'].includes(selectedRole)" data-group="3">
                <button type="button" class="app-sidebar-toggle" aria-controls="sidebar-group-3-list" :aria-expanded="isGroupOpen(3)" @click="toggleGroup(3)">
                    <span>مالی</span>
                    <span class="app-sidebar-chevron" aria-hidden="true"></span>
                </button>
                <ul class="menu-list" id="sidebar-group-3-list" x-show="isGroupOpen(3)">
                    <li><a href="/finance" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'FINANCE_ADMIN'">امور مالی</a></li>
                    <li><a href="/expenses" x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'FINANCE_ADMIN'">هزینه‌ها</a></li>
                    <li><a href="/my-invoices" x-show="selectedRole === 'STUDENT' || selectedRole === 'PARENT'">فاکتورهای من</a></li>
                </ul>
            </div>
        @endif
        <div class="app-sidebar-account">
            <p class="app-sidebar-account-label">${navbar.fullName()}</p>
            @if(!navbar.schools().isEmpty())
                <p class="app-sidebar-account-sub">مدرسه</p>
                @for(var school : navbar.schools())
                    <button type="button" class="app-sidebar-account-item" @click="selectSchool(${school.id()})">
                        <span class="app-inline-gap" x-show="selectedSchoolId === ${school.id()}">✓</span>
                        ${school.name()}
                    </button>
                @endfor
                <p class="app-sidebar-account-sub">نقش</p>
                @for(var school : navbar.schools())
                    @for(var role : school.roles())
                        <button type="button" class="app-sidebar-account-item" x-show="selectedSchoolId === ${school.id()}" @click="selectRole('${role}')">
                            <span class="app-inline-gap" x-show="selectedRole === '${role}'">✓</span>
                            ${RoleLabels.persian(role)}
                        </button>
                    @endfor
                @endfor
                <hr class="navbar-divider">
            @endif
            <button type="button" class="app-sidebar-account-item" @click="logout">خروج</button>
        </div>
    </aside>
    <div class="app-sidebar-backdrop" x-cloak x-show="menuOpen" @click="menuOpen = false"></div>
</div>
