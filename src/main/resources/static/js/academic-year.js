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
        // ارتقای خودکار هنگام تعویضِ سال (academic-year.md فاز ۳) و داده‌ی موجودی که هنوز به سالی نسبت داده نشده
        autoPromotion: false,
        autoSaving: false,
        unattributed: { classes: 0, students: 0 },
        attributeYear: '',
        attributing: false,
        // تأیید «نسبت دادن»: null یا { year, classes, students, error }
        confirm: null,
        // تأیید «ارتقای خودکار»: null یا { enabled, error }
        autoConfirm: null,
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
                this.confirm = null;
                this.autoConfirm = null;
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
            this.autoPromotion = !!settings.autoPromotion;
            this.loadUnattributed();
        },

        // سال‌هایی که داده‌ی موجود را می‌شود به آن‌ها نسبت داد: تا سالِ جاری (نه بعد از آن)
        get attributableYears() {
            if (!this.current) {
                return [];
            }
            const index = this.years.findIndex(y => y.id === this.current.id);
            return index < 0 ? [] : this.years.slice(0, index + 1);
        },

        hasUnattributed() {
            return !!this.current && (this.unattributed.classes > 0 || this.unattributed.students > 0);
        },

        // بهترین تلاش: مدرسه‌ای که ماژول آموزشی را ندارد 403 می‌گیرد و فقط بنر نمی‌آید
        async loadUnattributed() {
            const schoolId = this.schoolId;
            this.unattributed = { classes: 0, students: 0 };
            if (!this.current) {
                return;
            }
            try {
                const response = await fetch(`/api/admin/schools/${schoolId}/academic-year/unattributed`,
                    { headers: { 'X-School-ID': schoolId } });
                if (response.ok && schoolId === this.schoolId) {
                    this.unattributed = await response.json();
                }
            } catch (e) {
                // بنر اختیاری است
            }
        },

        // نسبت دادنِ داده‌ی قبلی به یک سال برگشت‌پذیر نیست: اول تأیید (modal) گرفته می‌شود
        askAttribute() {
            if (this.attributing) {
                return;
            }
            this.error = '';
            this.notice = '';
            if (!this.attributeYear) {
                this.error = 'یک سال تحصیلی را انتخاب کنید.';
                return;
            }
            this.confirm = {
                year: this.attributeYear,
                classes: this.unattributed.classes,
                students: this.unattributed.students,
                error: ''
            };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
        },

        async attribute() {
            const target = this.confirm;
            if (!target || this.attributing) {
                return;
            }
            const schoolId = this.schoolId;
            this.attributing = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`/api/admin/schools/${schoolId}/academic-year/attribution`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ academicYear: target.year })
                });
                if (schoolId !== this.schoolId) {
                    return;
                }
                if (!response.ok) {
                    // خطا داخل modal نشان داده می‌شود (پشتِ لایه‌ی modal دیده نمی‌شد)
                    target.error = (await this.classifyError(response)).message;
                    return;
                }
                const result = await response.json();
                this.confirm = null;
                this.notice = `کلاس‌ها و دانش‌آموزان قبلی به سال ${Calendar.digits(result.academicYear)} نسبت داده شد `
                    + `(${Calendar.digits(String(result.classes))} کلاس، ${Calendar.digits(String(result.students))} دانش‌آموز).`;
                this.attributeYear = '';
                await this.loadUnattributed();
            } catch (e) {
                target.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.attributing = false;
            }
        },

        // روشن/خاموش‌کردنِ ارتقای خودکار روی همه‌ی دانش‌آموزانِ تعویضِ بعدیِ سال اثر می‌گذارد: اول تأیید (modal)
        // گرفته می‌شود. چک‌باکس با کلیک می‌چرخد ولی وضعیتِ واقعی فقط بعد از تأیید عوض می‌شود، پس همان لحظه
        // به وضعیتِ واقعی برمی‌گردد (انصراف دیگر کاری لازم ندارد)
        askAutoPromotion(event) {
            const box = event.target;
            const enabled = box.checked;
            box.checked = this.autoPromotion;
            if (this.autoSaving) {
                return;
            }
            this.error = '';
            this.notice = '';
            this.autoConfirm = { enabled, error: '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.autoConfirmModal));
        },

        closeAutoConfirm() {
            this.autoConfirm = null;
        },

        async setAutoPromotion() {
            const target = this.autoConfirm;
            if (!target || this.autoSaving) {
                return;
            }
            const enabled = target.enabled;
            const schoolId = this.schoolId;
            this.autoSaving = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`/api/admin/schools/${schoolId}/academic-year/auto-promotion`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ enabled })
                });
                if (schoolId !== this.schoolId) {
                    return;
                }
                if (!response.ok) {
                    // سرور نپذیرفت؛ وضعیت همان قبلی می‌ماند و خطا داخل modal نشان داده می‌شود
                    target.error = (await this.classifyError(response)).message;
                    return;
                }
                const settings = await response.json();
                this.autoConfirm = null;
                this.autoPromotion = !!settings.autoPromotion;
                this.notice = this.autoPromotion
                    ? 'ارتقای خودکار روشن شد؛ از تعویض بعدیِ سال اثر می‌کند.'
                    : 'ارتقای خودکار خاموش شد.';
            } catch (e) {
                target.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.autoSaving = false;
            }
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
            this.autoPromotion = false;
            this.unattributed = { classes: 0, students: 0 };
            this.attributeYear = '';
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
            if (this.saving) {
                return;
            }
            if (this.selectedId === '') {
                this.notice = '';
                this.error = 'یک سال تحصیلی را انتخاب کنید.';
                return;
            }
            if (!this.canSave()) {
                this.notice = '';
                this.error = 'این سال هم‌اکنون سال تحصیلی جاری است؛ برای تغییر، سال دیگری را انتخاب کنید.';
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
