document.addEventListener('alpine:init', () => {
    // نقشِ انتخاب‌شده برای بخش‌هایی خارج از navbar (کارت‌های «دسترسی سریع»
    // داشبورد) - منبع حقیقت همچنان state خودِ سوییچر است و این store فقط
    // آینه‌ی آن است.
    Alpine.store('nav', {
        selectedRole: '',
        // Permissionهای نقشِ انتخاب‌شده در مدرسه‌ی انتخاب‌شده (برای نمایشِ لینک به نقش‌های سفارشی)
        permissions: []
    });

    // سوییچر مدرسه/نقش (Open Question 2 - حل‌شده): انتخاب فقط client-side
    // است و در localStorage می‌ماند؛ نقش صرفاً نمایشی است (backend همه‌ی
    // نقش‌های فعال را چک می‌کند - CLAUDE.md بند ۱۱ سؤال ۲).
    Alpine.data('schoolNavbar', (schoolRoles, rolePermissions) => ({
        menuOpen: false,
        schoolOpen: false,
        personOpen: false,
        selectedSchoolId: null,
        selectedRole: '',
        schoolRoles,
        rolePermissions: rolePermissions || {},
        // Permissionهای نقشِ انتخاب‌شده؛ سایدبار با آن لینکِ مدیریتی را به نقشِ سفارشی هم نشان می‌دهد
        permissions: [],
        openGroups: {},
        init() {
            const ids = Object.keys(this.schoolRoles).map(Number);
            // ?school=<id> (لینک‌های فرزندانِ داشبورد) بر مقدار ذخیره‌شده مقدم است و
            // فقط اگر عضویتِ کاربر باشد پذیرفته می‌شود؛ چون در خودِ URL است، با
            // کلیک میانی/«باز کردن در تب جدید» هم کار می‌کند. navbar قبل از
            // کامپوننت صفحه init می‌شود، پس صفحه همین مدرسه را از localStorage می‌خواند.
            const rawSchool = new URLSearchParams(window.location.search).get('school') || '';
            const urlId = /^\d+$/.test(rawSchool) ? parseInt(rawSchool, 10) : NaN;
            const savedId = ids.includes(urlId) ? urlId : parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.selectedSchoolId = ids.includes(savedId) ? savedId : (ids.length ? ids[0] : null);
            const roles = this.selectedSchoolId ? this.schoolRoles[this.selectedSchoolId] : [];
            const savedRole = localStorage.getItem('selectedRole') || '';
            this.selectedRole = roles.includes(savedRole) ? savedRole : (roles.length ? roles[0] : '');
            // انتخابِ پیش‌فرضِ حل‌شده هم باید ذخیره شود، نه فقط کلیک کاربر:
            // وگرنه ادمینِ تک‌مدرسه‌ای که هرگز دراپ‌داون را باز نکرده، در
            // /people پیام «مدرسه انتخاب کنید» می‌گیرد در حالی که navbar
            // همان مدرسه را انتخاب‌شده نشان می‌دهد. (navbar در ترتیب سند
            // قبل از کامپوننت صفحه init می‌شود، پس صفحه مقدار را می‌بیند.)
            if (this.selectedSchoolId !== null) {
                localStorage.setItem('selectedSchoolId', this.selectedSchoolId);
            }
            if (this.selectedRole) {
                localStorage.setItem('selectedRole', this.selectedRole);
            }
            try {
                this.openGroups = JSON.parse(localStorage.getItem('sidebarOpenGroups') || '{}') || {};
            } catch (e) {
                this.openGroups = {};
            }
            this.syncStore();
            this.markCurrentPage();
            // موبایل: کشوی منو صفحه را قفل می‌کند (Bulma .is-clipped) و با
            // رسیدن به عرض دسکتاپ بسته می‌شود تا قفل جا نماند.
            this.$watch('menuOpen', (open) => {
                document.documentElement.classList.toggle('is-clipped', open);
            });
            window.matchMedia('(min-width: 1024px)').addEventListener('change', (event) => {
                if (event.matches) {
                    this.menuOpen = false;
                }
            });
        },
        syncStore() {
            const schoolPermissions = this.rolePermissions[this.selectedSchoolId] || {};
            this.permissions = schoolPermissions[this.selectedRole] || [];
            Alpine.store('nav').selectedRole = this.selectedRole;
            Alpine.store('nav').permissions = this.permissions;
        },
        isGroupOpen(index) {
            return this.openGroups[index] === true;
        },
        toggleGroup(index) {
            this.openGroups = { ...this.openGroups, [index]: !this.isGroupOpen(index) };
            try {
                localStorage.setItem('sidebarOpenGroups', JSON.stringify(this.openGroups));
            } catch (e) {
                // حالت خصوصی/مسدود: فقط حالتِ همین صفحه می‌ماند.
            }
        },
        // صفحه‌ی جاری در سایدبار: aria-current + is-active (Bulma .menu-list).
        // سمت سرور مسیر جاری را نمی‌داند، پس از location می‌خوانیم؛ لینک‌ها
        // همه‌ی صفحه‌های مدیریتی را پوشش می‌دهند و مسیرها تخت‌اند.
        markCurrentPage() {
            const path = window.location.pathname;
            this.$root.querySelectorAll('.app-sidebar .menu-list a[href]').forEach((link) => {
                const current = link.getAttribute('href') === path;
                link.classList.toggle('is-active', current);
                if (current) {
                    link.setAttribute('aria-current', 'page');
                    // گروهِ صفحه‌ی جاری باز می‌ماند تا کاربر جایش را گم نکند.
                    const group = link.closest('[data-group]');
                    if (group) {
                        this.openGroups = { ...this.openGroups, [group.dataset.group]: true };
                    }
                } else {
                    link.removeAttribute('aria-current');
                }
            });
        },
        selectSchool(id) {
            this.selectedSchoolId = id;
            localStorage.setItem('selectedSchoolId', id);
            const roles = this.schoolRoles[id] || [];
            if (!roles.includes(this.selectedRole)) {
                this.selectRole(roles.length ? roles[0] : '');
            }
            // نقشِ همنامِ مدرسه‌ی دیگر (نقش سفارشی) Permissionهای دیگری دارد
            this.syncStore();
            this.schoolOpen = false;
            // صفحه‌های مدیریتی (مثلاً /people) با گوش‌دادن به این رویداد
            // داده‌ی خودشان را برای مدرسه‌ی تازه‌انتخاب‌شده دوباره می‌گیرند.
            window.dispatchEvent(new CustomEvent('school-changed', { detail: { id } }));
        },
        selectRole(role) {
            this.selectedRole = role;
            localStorage.setItem('selectedRole', role);
            this.syncStore();
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
    }));
});
