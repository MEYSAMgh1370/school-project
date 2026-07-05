@import com.example.roshdeandishe.web.DashboardPageController.DashboardModel

@param DashboardModel model

@template.layout.main(title = "داشبورد", content = @`
<!-- سوییچر مدرسه/نقش (Open Question 2 - حل‌شده): انتخاب فقط client-side
     است و در localStorage می‌ماند؛ نقش صرفاً نمایشی است (backend همه‌ی
     نقش‌های فعال را چک می‌کند - CLAUDE.md بند ۱۱ سؤال ۲). کاربر بدون
     عضویت (فرد B2C آینده) دراپ‌داون مدرسه را اصلاً نمی‌بیند. -->
<nav class="navbar is-link" role="navigation" aria-label="main navigation"
     x-data="{
        schoolOpen: false,
        personOpen: false,
        selectedSchoolId: null,
        selectedRole: '',
        schoolRoles: {@for(var school : model.schools())${school.id()}: [@for(var role : school.roles())'${role}',@endfor],@endfor},
        init() {
            const ids = Object.keys(this.schoolRoles).map(Number);
            const savedId = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.selectedSchoolId = ids.includes(savedId) ? savedId : (ids.length ? ids[0] : null);
            const roles = this.selectedSchoolId ? this.schoolRoles[this.selectedSchoolId] : [];
            const savedRole = localStorage.getItem('selectedRole') || '';
            this.selectedRole = roles.includes(savedRole) ? savedRole : (roles.length ? roles[0] : '');
        },
        selectSchool(id) {
            this.selectedSchoolId = id;
            localStorage.setItem('selectedSchoolId', id);
            const roles = this.schoolRoles[id] || [];
            if (!roles.includes(this.selectedRole)) {
                this.selectRole(roles.length ? roles[0] : '');
            }
            this.schoolOpen = false;
        },
        selectRole(role) {
            this.selectedRole = role;
            localStorage.setItem('selectedRole', role);
            this.personOpen = false;
        },
        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },
        async logout() {
            try {
                await fetch('/api/auth/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': this.csrfToken() } });
            } finally {
                window.location.href = '/login';
            }
        }
     }">
    <div class="navbar-brand">
        <span class="navbar-item has-text-weight-bold">رشدِاندیشه</span>
    </div>
    <div class="navbar-end">
        @if(!model.schools().isEmpty())
            <div class="navbar-item has-dropdown" :class="{ 'is-active': schoolOpen }">
                <a class="navbar-link" @click="schoolOpen = !schoolOpen; personOpen = false">
                    @for(var school : model.schools())
                        <span x-show="selectedSchoolId === ${school.id()}">${school.name()}</span>
                    @endfor
                </a>
                <div class="navbar-dropdown">
                    @for(var school : model.schools())
                        <a class="navbar-item" @click="selectSchool(${school.id()})">
                            <span class="ml-2" x-show="selectedSchoolId === ${school.id()}">✓</span>
                            ${school.name()}
                        </a>
                    @endfor
                </div>
            </div>
        @endif
        <div class="navbar-item has-dropdown" :class="{ 'is-active': personOpen }">
            <a class="navbar-link" @click="personOpen = !personOpen; schoolOpen = false">${model.fullName()}</a>
            <div class="navbar-dropdown is-right">
                @for(var school : model.schools())
                    @for(var role : school.roles())
                        <a class="navbar-item" x-show="selectedSchoolId === ${school.id()}" @click="selectRole('${role}')">
                            <span class="ml-2" x-show="selectedRole === '${role}'">✓</span>
                            ${role}
                        </a>
                    @endfor
                @endfor
                @if(!model.schools().isEmpty())
                    <hr class="navbar-divider">
                @endif
                <a class="navbar-item" @click="logout">خروج</a>
            </div>
        </div>
    </div>
</nav>

<section class="section">
    <div class="container">
        <h1 class="title is-4">عضویت‌های من</h1>
        <p class="subtitle is-6" dir="ltr">${model.email()}</p>

        @if(model.memberships().isEmpty())
            <div class="notification">
                هنوز در هیچ مدرسه‌ای عضویتی ندارید.
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
                        <td>${membership.roleCode()}</td>
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
    </div>
</section>
`)
