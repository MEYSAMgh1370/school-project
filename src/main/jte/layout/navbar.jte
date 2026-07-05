@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

<%-- سوییچر مدرسه/نقش (Open Question 2 - حل‌شده): انتخاب فقط client-side
     است و در localStorage می‌ماند؛ نقش صرفاً نمایشی است (backend همه‌ی
     نقش‌های فعال را چک می‌کند - CLAUDE.md بند ۱۱ سؤال ۲). کاربر بدون
     عضویت (فرد B2C آینده) دراپ‌داون مدرسه را اصلاً نمی‌بیند. منطق در
     static/js/navbar.js (قاعده‌ی جانمایی کد ۲۰۲۶-۰۷-۰۵: fetch/regex در
     فایل استاتیک، نه inline). --%>
<nav class="navbar is-link" role="navigation" aria-label="main navigation"
     x-data="schoolNavbar({@for(var school : navbar.schools())${school.id()}: [@for(var role : school.roles())'${role}',@endfor],@endfor})"
     @keydown.escape.window="schoolOpen = false; personOpen = false">
    <div class="navbar-brand">
        <span class="navbar-item has-text-weight-bold">رشدِاندیشه</span>
    </div>
    <div class="navbar-end">
        @if(!navbar.schools().isEmpty())
            <div class="navbar-item has-dropdown" :class="{ 'is-active': schoolOpen }" @click.outside="schoolOpen = false">
                <a class="navbar-link" aria-haspopup="true" :aria-expanded="schoolOpen" @click="schoolOpen = !schoolOpen; personOpen = false">
                    @for(var school : navbar.schools())
                        <span x-show="selectedSchoolId === ${school.id()}">${school.name()}</span>
                    @endfor
                </a>
                <div class="navbar-dropdown">
                    @for(var school : navbar.schools())
                        <a class="navbar-item" @click="selectSchool(${school.id()})">
                            <span class="ml-2" x-show="selectedSchoolId === ${school.id()}">✓</span>
                            ${school.name()}
                        </a>
                    @endfor
                </div>
            </div>
        @endif
        <div class="navbar-item has-dropdown" :class="{ 'is-active': personOpen }" @click.outside="personOpen = false">
            <a class="navbar-link" aria-haspopup="true" :aria-expanded="personOpen" @click="personOpen = !personOpen; schoolOpen = false">${navbar.fullName()}</a>
            <div class="navbar-dropdown is-right">
                @for(var school : navbar.schools())
                    @for(var role : school.roles())
                        <a class="navbar-item" x-show="selectedSchoolId === ${school.id()}" @click="selectRole('${role}')">
                            <span class="ml-2" x-show="selectedRole === '${role}'">✓</span>
                            ${role}
                        </a>
                    @endfor
                @endfor
                @if(!navbar.schools().isEmpty())
                    <hr class="navbar-divider">
                @endif
                <a class="navbar-item" @click="logout">خروج</a>
            </div>
        </div>
    </div>
</nav>
