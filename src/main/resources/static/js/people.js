document.addEventListener('alpine:init', () => {
    Alpine.data('peoplePage', () => ({
        schoolId: null,
        members: [],
        loading: false,
        submitting: false,
        // یک تغییرِ فعال/غیرفعال در حال انجام (دوبار کلیک نباید دو درخواست بفرستد)
        toggling: false,
        // تأیید «غیرفعال کردن»: null یا { member, error }
        confirm: null,
        error: '',
        notice: '',
        invite: null,
        // جست‌وجوی آزاد روی نام، ایمیل و نقش (فهرست ردیفی؛ DESIGN.md §6)
        filterText: '',
        // صفحه‌بندی «نمایش بیشتر» (DESIGN.md §6): ۲۵ ردیف در هر نوبت
        pageSize: 25,
        shown: 25,
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
            this.$watch('filterText', () => { this.shown = this.pageSize; });
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.invite = null;
                this.confirm = null;
                this.filterText = '';
                this.shown = this.pageSize;
                this.error = '';
                this.notice = '';
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        filteredMembers() {
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

        visibleMembers() {
            return this.filteredMembers().slice(0, this.shown);
        },

        remainingCount() {
            return this.filteredMembers().length - this.shown;
        },

        remainingLabel() {
            return this.remainingCount().toLocaleString('fa-IR-u-nu-arabext');
        },

        showMore() {
            this.shown += this.pageSize;
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

        // confirmTarget: modal تأیید که درخواست از آن آمده (یا null). پیام بعد از خواندنِ بدنه
        // نوشته می‌شود؛ اگر modal در این فاصله بسته شده باشد، به خطای صفحه می‌رود.
        async showError(response, confirmTarget = null) {
            let message;
            try {
                const body = await response.json();
                message = body.detail || body.title || 'خطایی رخ داد.';
            } catch (e) {
                message = 'خطایی رخ داد. (' + response.status + ')';
            }
            if (confirmTarget && this.confirm === confirmTarget) {
                confirmTarget.error = message;
            } else {
                this.error = message;
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
                const created = await response.json();
                // نقشِ انتخاب‌شده می‌ماند، تا افزودنِ چند معلمِ پشت‌سرهم هر بار نقش را دوباره انتخاب نخواهد.
                this.form = { email: '', fullName: '', roleCode: this.form.roleCode };
                await this.load();
                // دعوت همان لحظه فرستاده می‌شود؛ مدیر مجبور نیست یادش بماند «ارسال دعوت» را جدا بزند.
                await this.sendInvite({ membershipId: created.membershipId, fullName: created.fullName }, true);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submitting = false;
            }
        },

        // غیرفعال کردن دسترسیِ یک عضو اول تأیید (modal) می‌گیرد؛ فعال کردن دوباره بی‌خطر است و مستقیم انجام می‌شود
        askDeactivate(member) {
            if (this.toggling) {
                return;
            }
            this.error = '';
            this.notice = '';
            this.confirm = { member, error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
        },

        deactivate() {
            const target = this.confirm;
            if (!target || this.toggling) {
                return;
            }
            return this.toggleActive(target.member, target);
        },

        // confirmTarget: وقتی از modal تأیید می‌آید، خطا داخل همان modal نشان داده می‌شود
        async toggleActive(member, confirmTarget = null) {
            if (this.toggling) {
                return;
            }
            this.toggling = true;
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
                    await this.showError(response, confirmTarget);
                    return;
                }
                this.confirm = null;
                // member.active هنوز مقدار قبل از تغییر است
                this.notice = member.active ? 'عضویت غیرفعال شد.' : 'عضویت فعال شد.';
                await this.load();
            } catch (e) {
                // اگر modal بسته شده باشد، خطا بالای صفحه نشان داده می‌شود
                if (confirmTarget && this.confirm === confirmTarget) {
                    confirmTarget.error = 'ارتباط با سرور برقرار نشد.';
                } else {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                this.toggling = false;
            }
        },

        // added=true یعنی همین الان با «افزودن» ساخته شده؛ فقط متنِ پیام فرق می‌کند.
        async sendInvite(member, added = false) {
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
                    if (added) {
                        this.notice = member.fullName + ' اضافه شد، ولی دعوت فرستاده نشد؛ از «ارسال/تکرار دعوت» دوباره امتحان کنید.';
                    }
                    return;
                }
                const body = await response.json();
                if (!body.issued) {
                    this.notice = added
                        ? member.fullName + ' اضافه شد؛ حسابش از قبل فعال است و به دعوت نیاز ندارد.'
                        : 'این کاربر قبلاً حسابش را فعال کرده است.';
                } else if (body.emailSent) {
                    this.notice = added
                        ? member.fullName + ' اضافه شد و ایمیل دعوت برایش فرستاده شد.'
                        : 'ایمیل دعوت ارسال شد.';
                } else {
                    if (added) {
                        this.notice = member.fullName + ' اضافه شد.';
                    }
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
