@import com.example.roshdeandishe.web.model.NavCatalog
@import com.example.roshdeandishe.web.model.NavbarModel

@param NavbarModel navbar

<%-- سوییچر مدرسه/نقش (Open Question 2 - حل‌شده): انتخاب فقط client-side
     است و در localStorage می‌ماند؛ نقش صرفاً نمایشی است (backend همه‌ی
     نقش‌های فعال را چک می‌کند - CLAUDE.md بند ۱۱ سؤال ۲). کاربر بدون
     عضویت (فرد B2C آینده) دراپ‌داون مدرسه را اصلاً نمی‌بیند. منطق در
     static/js/navbar.js (قاعده‌ی جانمایی کد ۲۰۲۶-۰۷-۰۵: fetch/regex در
     فایل استاتیک، نه inline).
     لینک‌های صفحه‌ها در سایدبار (aside.app-sidebar) زندگی می‌کنند و از منبع
     واحدِ NavCatalog رندر می‌شوند (کارت‌های «دسترسی سریع» داشبورد هم از
     همان) - هر صفحه‌ی مدیریتی جدید باید لینکش را در NavCatalog به گروه
     مناسب اضافه کند، نه اینجا (UX_ROADMAP.md §2: هیچ URL یتیمی وجود ندارد). نمایش لینک (و برچسبِ
     گروه) می‌تواند تابع نقشِ انتخاب‌شده باشد (فقط نمایش - کنترل دسترسی
     همیشه سمت سرور می‌ماند). نوار بالا فقط سوییچر مدرسه/نقش و منوی کاربر
     را دارد. wrapper با display: contents است تا nav و aside هر دو زیر
     همان x-data (selectedRole) باشند بی‌آنکه لایوت را تغییر دهد؛ چیدمانِ
     سایدبار در app.css («Sidebar layout»). هر گروه آکاردئون است: دکمه‌ی
     سرگروه (aria-expanded) لینک‌هایش را باز/بسته می‌کند؛ وضعیت در
     localStorage می‌ماند و گروهِ صفحه‌ی جاری همیشه باز است (navbar.js). --%>
<div class="app-shell-nav" data-week-starts="${navbar.weekStartsJson()}"
     x-data="schoolNavbar({@for(var school : navbar.schools())${school.id()}: [@for(var role : school.roles())'${role}',@endfor],@endfor}, {@for(var school : navbar.schools())${school.id()}: {@for(var role : school.roles())'${role}': [@for(var code : school.rolePermissions().getOrDefault(role, java.util.List.of()))'${code}',@endfor],@endfor},@endfor})"
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
                                <span x-show="selectedSchoolId === ${school.id()}">${school.name()}@if(school.currentYear() != null)
                                    <span class="tag is-light app-year-tag" dir="ltr" title="سال تحصیلی جاری">${school.currentYearDisplay()}</span>@endif</span>
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
                                    ${navbar.roleLabel(school.id(), role)}
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
            @for(var group : NavCatalog.groups())
                <div x-cloak x-show="${group.visibleWhen("selectedRole", "permissions")}" data-group="${group.id()}">
                    <button type="button" class="app-sidebar-toggle" aria-controls="sidebar-group-${group.id()}-list"
                            :aria-expanded="isGroupOpen('${group.id()}')" @click="toggleGroup('${group.id()}')">
                        <span>${group.label()}</span>
                        <span class="app-sidebar-chevron" aria-hidden="true"></span>
                    </button>
                    <ul class="menu-list" id="sidebar-group-${group.id()}-list" x-show="isGroupOpen('${group.id()}')">
                        @for(var link : group.links())
                            <li><a href="${link.href()}" x-show="${link.visibleWhen("selectedRole", "permissions")}">${link.label()}</a></li>
                        @endfor
                    </ul>
                </div>
            @endfor
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
                            ${navbar.roleLabel(school.id(), role)}
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
