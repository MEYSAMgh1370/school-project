document.addEventListener('alpine:init', () => {
    Alpine.data('rolesPage', () => ({
        schoolId: null,
        roles: [],
        permissions: [],
        blocked: false,
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        form: { code: '', displayName: '', permissionCodes: [] },

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        rolesBase() {
            return `/api/admin/schools/${this.schoolId}/roles`;
        },

        permissionsBase() {
            return `/api/admin/schools/${this.schoolId}/permissions`;
        },

        permissionLabel(code) {
            const permission = this.permissions.find(p => p.code === code);
            return permission ? permission.displayName : code;
        },

        // همان تشخیصِ دولایه‌ی §5 که finance.js معرفی کرد - ROLE_MANAGE
        // ماژول ندارد، پس در عمل همیشه permission-denied است، نه module-not-entitled
        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی مدیریت نقش‌ها را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        async load() {
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                const permissionsResponse = await fetch(this.permissionsBase(), { headers: { 'X-School-ID': this.schoolId } });
                if (!permissionsResponse.ok) {
                    const classified = await this.classifyError(permissionsResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.permissions = await permissionsResponse.json();

                const rolesResponse = await fetch(this.rolesBase(), { headers: { 'X-School-ID': this.schoolId } });
                if (!rolesResponse.ok) {
                    const classified = await this.classifyError(rolesResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.roles = await rolesResponse.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async createRole() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.rolesBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify(this.form)
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                this.notice = 'نقش جدید ساخته شد.';
                this.form = { code: '', displayName: '', permissionCodes: [] };
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        }
    }));
});
