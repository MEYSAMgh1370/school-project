document.addEventListener('alpine:init', () => {
    Alpine.data('peoplePage', () => ({
        schoolId: null,
        members: [],
        loading: false,
        submitting: false,
        error: '',
        notice: '',
        invite: null,
        // جست‌وجوی آزاد روی نام، ایمیل و نقش (فهرست ردیفی؛ DESIGN.md §6)
        filterText: '',
        form: { email: '', fullName: '', roleCode: 'STUDENT' },
        roleLabels: {
            STUDENT: 'دانش‌آموز',
            TEACHER: 'معلم',
            PARENT: 'والد',
            SCHOOL_ADMIN: 'مدیر مدرسه',
            FINANCE_ADMIN: 'مدیر مالی'
        },
        // نقش‌های سفارشیِ همین مدرسه (از /roles، ROLE_MANAGE) - بدون این،
        // یک نقش سفارشیِ تازه‌ساخته‌شده هرگز در دراپ‌داون این صفحه قابل‌تخصیص
        // نبود. فقط SCHOOL_ADMIN هم MEMBERSHIP_MANAGE و هم ROLE_MANAGE را
        // دارد، پس این فراخوانی هرگز برای کاربری که اصلاً نمی‌تواند این
        // صفحه را ببیند 403 نمی‌شود.
        customRoles: [],
        get roleCodes() {
            return [...Object.keys(this.roleLabels), ...this.customRoles.map(r => r.code)];
        },
        labelFor(code) {
            if (this.roleLabels[code]) {
                return this.roleLabels[code];
            }
            const custom = this.customRoles.find(r => r.code === code);
            return custom ? custom.displayName : code;
        },

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.invite = null;
                this.filterText = '';
                this.error = '';
                this.notice = '';
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        visibleMembers() {
            const query = this.filterText.trim();
            if (!query) {
                return this.members;
            }
            return this.members.filter(member => ListSearch.matches(query, [
                member.fullName,
                member.email,
                this.labelFor(member.roleCode)
            ]));
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        base() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        rolesBase() {
            return `/api/admin/schools/${this.schoolId}/roles`;
        },

        async showError(response) {
            try {
                const body = await response.json();
                this.error = body.detail || body.title || 'خطایی رخ داد.';
            } catch (e) {
                this.error = 'خطایی رخ داد. (' + response.status + ')';
            }
        },

        async load() {
            this.loading = true;
            this.error = '';
            try {
                const response = await fetch(this.base(), {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    await this.showError(response);
                    this.members = [];
                    return;
                }
                this.members = await response.json();

                // best-effort: اگر به هر دلیلی 403/خطا شد، فقط نقش‌های
                // سفارشی در دراپ‌داون/برچسب‌ها دیده نمی‌شوند - صفحه نمی‌شکند
                const rolesResponse = await fetch(this.rolesBase(), { headers: { 'X-School-ID': this.schoolId } });
                this.customRoles = rolesResponse.ok
                    ? (await rolesResponse.json()).filter(r => r.custom)
                    : [];
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async addMember() {
            this.submitting = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.base(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify(this.form)
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                this.notice = 'عضو جدید اضافه شد.';
                this.form = { email: '', fullName: '', roleCode: 'STUDENT' };
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        },

        async toggleActive(member) {
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.base()}/${member.membershipId}/active`, {
                    method: 'PATCH',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ active: !member.active })
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                // member.active هنوز مقدار قبل از تغییر است
                this.notice = member.active ? 'عضویت غیرفعال شد.' : 'عضویت فعال شد.';
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        async sendInvite(member) {
            this.error = '';
            this.notice = '';
            this.invite = null;
            try {
                const response = await fetch(`${this.base()}/${member.membershipId}/invite-token`, {
                    method: 'POST',
                    headers: {
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    }
                });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                const body = await response.json();
                if (!body.issued) {
                    this.notice = 'این کاربر قبلاً حسابش را فعال کرده است.';
                } else if (body.emailSent) {
                    this.notice = 'ایمیل دعوت ارسال شد.';
                } else {
                    this.invite = {
                        fullName: member.fullName,
                        emailSent: false,
                        link: `${location.origin}/activate?token=${body.inviteToken}`
                    };
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        copyInviteLink() {
            const input = this.$refs.inviteLink;
            input.select();
            navigator.clipboard.writeText(input.value).catch(() => document.execCommand('copy'));
        }
    }));
});
