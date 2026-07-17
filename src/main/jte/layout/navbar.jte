@import com.example.roshdeandishe.web.NavbarModel
@import com.example.roshdeandishe.web.RoleLabels

@param NavbarModel navbar

<%-- سوییچر مدرسه/نقش (Open Question 2 - حل‌شده): انتخاب فقط client-side
     است و در localStorage می‌ماند؛ نقش صرفاً نمایشی است (backend همه‌ی
     نقش‌های فعال را چک می‌کند - CLAUDE.md بند ۱۱ سؤال ۲). کاربر بدون
     عضویت (فرد B2C آینده) دراپ‌داون مدرسه را اصلاً نمی‌بیند. منطق در
     static/js/navbar.js (قاعده‌ی جانمایی کد ۲۰۲۶-۰۷-۰۵: fetch/regex در
     فایل استاتیک، نه inline).
     لینک‌های صفحه‌ها در navbar-start زندگی می‌کنند - هر صفحه‌ی مدیریتی
     جدید باید لینکش را همین‌جا اضافه کند (UX_ROADMAP.md §2: هیچ URL
     یتیمی وجود ندارد). نمایش لینک می‌تواند تابع نقشِ انتخاب‌شده باشد
     (فقط نمایش - کنترل دسترسی همیشه سمت سرور می‌ماند). --%>
<nav class="navbar is-link" role="navigation" aria-label="main navigation"
     x-data="schoolNavbar({@for(var school : navbar.schools())${school.id()}: [@for(var role : school.roles())'${role}',@endfor],@endfor})"
     @keydown.escape.window="schoolOpen = false; personOpen = false; menuOpen = false">
    <div class="navbar-brand">
        <span class="navbar-item has-text-weight-bold">رشدِاندیشه</span>
        <a role="button" class="navbar-burger" :class="{ 'is-active': menuOpen }"
           aria-label="منو" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
        </a>
    </div>
    <div class="navbar-menu" :class="{ 'is-active': menuOpen }">
        <div class="navbar-start">
            <a class="navbar-item" href="/dashboard">داشبورد</a>
            @if(!navbar.schools().isEmpty())
                <a class="navbar-item" href="/people" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN'">اعضای مدرسه</a>
                <a class="navbar-item" href="/roles" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN'">نقش‌ها و دسترسی‌ها</a>
                <a class="navbar-item" href="/classes" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">کلاس‌ها</a>
                <a class="navbar-item" href="/curriculum" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">سرفصل‌ها و بانک سؤال</a>
                <a class="navbar-item" href="/study-plans" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">برنامه‌های مطالعه</a>
                <a class="navbar-item" href="/exams" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">آزمون‌ها</a>
                <a class="navbar-item" href="/my-exams" x-cloak x-show="selectedRole === 'STUDENT'">آزمون‌های من</a>
                <a class="navbar-item" href="/students" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'TEACHER'">دانش‌آموزان</a>
                <a class="navbar-item" href="/finance" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'FINANCE_ADMIN'">امور مالی</a>
                <a class="navbar-item" href="/expenses" x-cloak x-show="selectedRole === 'SCHOOL_ADMIN' || selectedRole === 'FINANCE_ADMIN'">هزینه‌ها</a>
                <a class="navbar-item" href="/my-invoices" x-cloak x-show="selectedRole === 'STUDENT' || selectedRole === 'PARENT'">فاکتورهای من</a>
            @endif
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
                                ${RoleLabels.persian(role)}
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
    </div>
</nav>
