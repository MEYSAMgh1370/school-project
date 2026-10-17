// «بات بله‌ی مدرسه» (/bale-bot، openspec/changes/bale-school-bots.md): ثبت، تعویض و حذفِ رباتِ اختصاصیِ مدرسه.
// API: GET/PUT/DELETE /api/admin/schools/{id}/bale-bot (مجوز BALE_BOT_MANAGE؛ بدون @RequiresModule).
// توکن فقط در همان ورودیِ password است: بعد از PUT موفق خالی می‌شود و هرگز در state دیگری نگه‌داری یا
// از پاسخ GET دوباره نمایش داده نمی‌شود.
document.addEventListener('alpine:init', () => {
    const MESSAGES = {
        entitled: 'این مدرسه ماژول مورد نیاز را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.',
        denied: 'نقش انتخاب‌شده‌ی شما اجازه‌ی مدیریت بات بله‌ی مدرسه را ندارد.'
    };
    const URL = (schoolId) => `/api/admin/schools/${schoolId}/bale-bot`;

    Alpine.data('baleBotPage', () => ({
        schoolId: null,
        // فقط username/link/status/linkedMembers/lastUpdateAt؛ null = بات ندارد
        bot: null,
        loaded: false,
        loading: false,
        saving: false,
        blocked: false,
        error: '',
        notice: '',
        // فقط مقدارِ ورودیِ فرم
        token: '',
        tokenError: '',
        // حالت «تعویض توکن» روی بات فعال
        replacing: false,
        // modal تأیید حذف
        confirming: false,
        confirmError: '',
        seq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.reset();
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        reset() {
            this.seq++;
            this.bot = null;
            this.loaded = false;
            this.loading = false;
            this.saving = false;
            this.blocked = false;
            this.error = '';
            this.notice = '';
            this.token = '';
            this.tokenError = '';
            this.replacing = false;
            this.confirming = false;
            this.confirmError = '';
        },

        get state() {
            return BaleBotLogic.viewState(this.bot);
        },

        // فرمِ توکن: بدون بات، بات غیرفعال، یا حالتِ تعویض
        get showForm() {
            return this.loaded && !this.blocked && (this.state !== 'active' || this.replacing);
        },

        async load() {
            if (!this.schoolId) {
                return;
            }
            const seq = ++this.seq;
            this.loading = true;
            this.error = '';
            try {
                const bot = await SchoolApi.request(this.schoolId, 'GET', URL(this.schoolId), undefined, MESSAGES);
                if (seq !== this.seq) {
                    return;
                }
                this.bot = bot;
                this.loaded = true;
            } catch (e) {
                if (seq === this.seq) {
                    this.loaded = false;
                    this.blocked = !!e.blocking;
                    this.error = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.seq) {
                    this.loading = false;
                }
            }
        },

        startReplace() {
            this.replacing = true;
            this.notice = '';
            this.error = '';
            this.token = '';
            this.tokenError = '';
        },

        cancelReplace() {
            this.replacing = false;
            this.token = '';
            this.tokenError = '';
            this.error = '';
        },

        async submit() {
            if (this.saving) {
                return;
            }
            this.notice = '';
            this.error = '';
            this.tokenError = BaleBotLogic.validateToken(this.token);
            if (this.tokenError) {
                this.error = this.tokenError;
                return;
            }
            const schoolId = this.schoolId;
            const seq = this.seq;
            const wasReplacing = this.state !== 'none';
            this.saving = true;
            try {
                const bot = await SchoolApi.request(schoolId, 'PUT', URL(schoolId), { token: this.token.trim() }, MESSAGES);
                if (seq !== this.seq) {
                    return;
                }
                this.token = '';
                this.bot = bot;
                this.replacing = false;
                this.notice = wasReplacing ? 'توکن بات تعویض شد.' : 'بات ثبت شد.';
            } catch (e) {
                if (seq === this.seq) {
                    this.blocked = !!e.blocking;
                    this.error = e.blocking === undefined
                        ? SchoolApi.messageOf(e)
                        : (e.blocking ? e.message : BaleBotLogic.errorMessage({ status: e.status, type: e.type, detail: e.message }));
                }
            } finally {
                if (seq === this.seq) {
                    this.saving = false;
                }
            }
        },

        askRemove() {
            this.confirmError = '';
            this.confirming = true;
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            if (!this.saving) {
                this.confirming = false;
            }
        },

        async runRemove() {
            if (this.saving) {
                return;
            }
            const schoolId = this.schoolId;
            const seq = this.seq;
            this.saving = true;
            this.confirmError = '';
            try {
                await SchoolApi.request(schoolId, 'DELETE', URL(schoolId), undefined, MESSAGES);
                if (seq !== this.seq) {
                    return;
                }
                this.bot = null;
                this.replacing = false;
                this.confirming = false;
                this.notice = 'بات حذف شد.';
            } catch (e) {
                if (seq === this.seq) {
                    this.confirmError = SchoolApi.messageOf(e);
                }
            } finally {
                if (seq === this.seq) {
                    this.saving = false;
                }
            }
        },

        lastUpdateText() {
            const date = this.bot && this.bot.lastUpdateAt ? new Date(this.bot.lastUpdateAt) : null;
            if (!date || Number.isNaN(date.getTime())) {
                return 'هنوز پیامی دریافت نشده است';
            }
            const pad = (n) => String(n).padStart(2, '0');
            return Calendar.formatDate(date) + ' ' + Calendar.digits(pad(date.getHours()) + ':' + pad(date.getMinutes()));
        },

        membersText() {
            return Calendar.digits(this.bot ? this.bot.linkedMembers : 0) + ' نفر';
        }
    }));
});
