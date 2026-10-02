document.addEventListener('alpine:init', () => {
    Alpine.data('academicYearPage', () => ({
        schoolId: null,
        current: null,
        years: [],
        selectedId: '',
        loading: false,
        // فقط بعد از یک بارگذاریِ موفق true است: با شکست، کادرِ سال/فرم نشان داده
        // نمی‌شود (وگرنه «هنوز سالی انتخاب نشده» گمراه‌کننده می‌شد)
        loaded: false,
        saving: false,
        blocked: false,
        error: '',
        notice: '',
        // فقط پاسخ آخرین درخواست اثر می‌گذارد (عوض‌کردنِ سریعِ مدرسه پاسخ کهنه را دور می‌ریزد)
        seq: 0,
        // شمارنده‌ی ذخیره: ذخیره‌ی در جریانِ مدرسه‌ی قبلی نباید دکمه‌ی مدرسه‌ی تازه را
        // قفل کند، و finally آن نباید ذخیره‌ی جدیدتر را تمام‌شده نشان دهد
        saveSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                // ذخیره‌ی در جریان (مدرسه‌ی قبلی) دیگر به این مدرسه ربطی ندارد
                this.saveSeq++;
                this.saving = false;
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

        canSave() {
            return this.selectedId !== '' && !this.saving
                && !(this.current && String(this.current.id) === String(this.selectedId));
        },

        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی تعیین سال تحصیلی را ندارد.' };
                }
                // سالِ جاری در فاصله‌ی دیدن تا ذخیره عوض شده (درخواستِ کهنه یا مدیرِ دیگر)
                if (type.endsWith('academic-year-changed')) {
                    return { blocking: false, reload: true,
                        message: 'سال تحصیلی جاری در این فاصله عوض شده است؛ اطلاعات تازه بارگذاری شد، دوباره انتخاب کنید.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        apply(settings) {
            this.loaded = true;
            this.current = settings.current;
            this.years = settings.years;
            this.selectedId = settings.current ? settings.current.id : '';
        },

        // فهرستِ همه‌ی سال‌ها فقط برای مدیر است (ACADEMIC_YEAR_MANAGE)؛ بقیه‌ی
        // نقش‌ها فقط سال جاری را می‌بینند (GET /api/me/.../academic-year) و این
        // صفحه را ندارند - 403 اینجا به‌صورت پیام «اجازه ندارید» نشان داده می‌شود
        async load() {
            const seq = ++this.seq;
            this.loading = true;
            this.error = '';
            this.blocked = false;
            // وضعیتِ مدرسه‌ی قبلی (سال جاری، فهرست، انتخاب) کنار گذاشته می‌شود: اگر
            // این بارگذاری شکست بخورد، صفحه نباید سالِ مدرسه‌ی قبلی را زیرِ مدرسه‌ی
            // تازه نشان دهد (و ذخیره‌ی بر پایه‌ی اطلاعات کهنه ممکن نشود).
            this.loaded = false;
            this.current = null;
            this.years = [];
            this.selectedId = '';
            try {
                const response = await fetch(`/api/admin/schools/${this.schoolId}/academic-year`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    if (seq === this.seq) {
                        this.error = classified.message;
                        this.blocked = classified.blocking;
                    }
                    return;
                }
                const settings = await response.json();
                if (seq === this.seq) {
                    this.apply(settings);
                }
            } catch (e) {
                if (seq === this.seq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.seq) {
                    this.loading = false;
                }
            }
        },

        async save() {
            if (!this.canSave()) {
                return;
            }
            const schoolId = this.schoolId;
            const saveSeq = ++this.saveSeq;
            this.saving = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`/api/admin/schools/${schoolId}/academic-year`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    // سالِ جاریِ دیده‌شده هم می‌رود تا سرور فقط وقتی بنویسد که هنوز همان
                    // است - ذخیره‌ی کهنه (تأخیری یا مدیرِ دیگر) انتخابِ جدیدتر را برنمی‌گرداند
                    body: JSON.stringify({
                        academicYearId: parseInt(this.selectedId, 10),
                        expectedCurrentAcademicYearId: this.current ? this.current.id : null
                    })
                });
                // هر پاسخ (موفق یا خطا) فقط اگر هنوز آخرین ذخیره است نشان داده می‌شود.
                // saveSeq با هر ذخیره‌ی تازه و با هر عوض‌شدنِ مدرسه بالا می‌رود، پس
                // هم ذخیره‌ی مدرسه‌ی قبلی و هم ذخیره‌ی کهنه‌ترِ همین مدرسه (A←B←A)
                // دور ریخته می‌شود - فقط چک‌کردنِ شناسه‌ی مدرسه برای دومی کافی نبود.
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    if (saveSeq === this.saveSeq) {
                        if (classified.reload) {
                            // وضعیتِ تازه را بگیر، بعد پیام را نشان بده (load خطا را پاک می‌کند)
                            await this.load();
                        }
                        if (saveSeq === this.saveSeq) {
                            this.error = classified.message;
                        }
                    }
                    return;
                }
                const settings = await response.json();
                if (saveSeq === this.saveSeq) {
                    this.apply(settings);
                    this.notice = 'سال تحصیلی جاری ثبت شد.';
                }
            } catch (e) {
                if (saveSeq === this.saveSeq) {
                    this.error = 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (saveSeq === this.saveSeq) {
                    this.saving = false;
                }
            }
        }
    }));
});
