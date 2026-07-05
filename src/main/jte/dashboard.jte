@import com.example.roshdeandishe.web.DashboardPageController.DashboardModel

@param DashboardModel model

@template.layout.main(title = "داشبورد", pageScript = "/js/dashboard.js", content = @`
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
