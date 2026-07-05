document.addEventListener('alpine:init', () => {
    // سوییچر مدرسه/نقش (Open Question 2 - حل‌شده): انتخاب فقط client-side
    // است و در localStorage می‌ماند؛ نقش صرفاً نمایشی است (backend همه‌ی
    // نقش‌های فعال را چک می‌کند - CLAUDE.md بند ۱۱ سؤال ۲).
    Alpine.data('schoolNavbar', (schoolRoles) => ({
        schoolOpen: false,
        personOpen: false,
        selectedSchoolId: null,
        selectedRole: '',
        schoolRoles,
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
            // صفحه‌های مدیریتی (مثلاً /people) با گوش‌دادن به این رویداد
            // داده‌ی خودشان را برای مدرسه‌ی تازه‌انتخاب‌شده دوباره می‌گیرند.
            window.dispatchEvent(new CustomEvent('school-changed', { detail: { id } }));
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
    }));
});
