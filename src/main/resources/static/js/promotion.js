// «ارتقای پایه» (openspec/changes/academic-year.md، فاز ۳): اجرای ارتقا، جا‌دادنِ دانش‌آموزانِ ارتقایافته در
// کلاس‌های سالِ جدید، و برگرداندنِ یک اجرا. همه‌ی اقدام‌ها سمتِ سرور با ACADEMIC_YEAR_MANAGE گیت شده‌اند.
document.addEventListener('alpine:init', () => {
    Alpine.data('promotionPage', () => ({
        schoolId: null,
        loading: false,
        // فقط بعد از یک بارگذاریِ موفق true است (با شکست، «ابتدا سال جاری را انتخاب کنید» گمراه‌کننده می‌شد)
        loaded: false,
        blocked: false,
        error: '',
        notice: '',
        current: null,
        years: [],
        unattributed: { classes: 0, students: 0 },
        pending: [],
        graduates: [],
        runs: [],
        plan: [],
        // نتیجه‌ی هر دانش‌آموز در پیش‌نمایش (شناسه ← PROMOTE/REPEAT/GRADUATE/LEAVE) و کلاسِ انتخابیِ هر جا‌نشده
        decisions: {},
        placements: {},
        fromYear: '',
        filterText: '',
        classes: [],
        allClasses: [],
        pupils: [],
        previewLoading: false,
        running: false,
        undoing: false,
        placing: false,
        confirm: null,
        opener: null,
        // فقط پاسخِ آخرین درخواست اثر می‌گذارد (عوض‌کردنِ سریعِ مدرسه/سال پاسخ کهنه را دور می‌ریزد)
        seq: 0,
        previewSeq: 0,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.confirm = null;
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

        base(path) {
            return `/api/admin/schools/${this.schoolId}${path}`;
        },

        // سال‌های قبل از سالِ جاری، نزدیک‌ترین اول (مبدأ ارتقا همیشه سالِ قبل از جاری است)
        get earlierYears() {
            const index = this.years.indexOf(this.current);
            return index <= 0 ? [] : this.years.slice(0, index).reverse();
        },

        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول آموزشی را نخریده است. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی ارتقای دانش‌آموزان را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        // خطا را به‌صورت شیء {blocking, message} می‌اندازد تا load() یک‌جا نشانش بدهد
        async getJson(path) {
            const response = await fetch(this.base(path), { headers: { 'X-School-ID': this.schoolId } });
            if (!response.ok) {
                throw await this.classifyError(response);
            }
            return response.json();
        },

        async showError(response) {
            this.error = (await this.classifyError(response)).message;
        },

        async load(quiet) {
            const seq = ++this.seq;
            const schoolId = this.schoolId;
            if (!quiet) {
                this.loading = true;
                this.loaded = false;
            }
            this.error = quiet ? this.error : '';
            this.blocked = false;
            try {
                const settings = await this.getJson('/academic-year');
                const current = settings.current ? settings.current.code : null;
                let data = { unattributed: { classes: 0, students: 0 }, pending: [], graduates: [], runs: [],
                    classes: [], allClasses: [], memberships: [] };
                if (current) {
                    const [unattributed, pending, graduates, runs, classes, allClasses, memberships] = await Promise.all([
                        this.getJson('/academic-year/unattributed'), this.getJson('/promotion/pending'),
                        this.getJson('/promotion/graduates'), this.getJson('/promotion/runs'),
                        this.getJson('/classes'), this.getJson('/classes?academicYear=all'),
                        this.getJson('/memberships')
                    ]);
                    data = { unattributed, pending, graduates, runs, classes, allClasses, memberships };
                }
                if (seq !== this.seq || schoolId !== this.schoolId) {
                    return;
                }
                this.current = current;
                this.years = settings.years.map(y => y.code);
                this.unattributed = data.unattributed;
                this.pending = data.pending;
                this.graduates = data.graduates;
                this.runs = data.runs;
                this.classes = data.classes;
                this.allClasses = data.allClasses;
                this.pupils = data.memberships.filter(m => m.roleCode === 'STUDENT');
                const placements = {};
                data.pending.forEach(item => {
                    placements[item.studentId] = '';
                });
                this.placements = placements;
                this.loaded = true;
                // سالِ مبدأ: همان سالِ قبل از جاری، مگر کاربر قبلاً سالِ دیگری را انتخاب کرده باشد
                if (!this.earlierYears.includes(this.fromYear)) {
                    this.fromYear = this.earlierYears.length > 0 ? this.earlierYears[0] : '';
                }
                await this.loadPreview();
            } catch (e) {
                if (seq === this.seq) {
                    this.blocked = !!e.blocking;
                    this.error = e.message !== undefined ? e.message : 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.seq) {
                    this.loading = false;
                }
            }
        },

        async loadPreview() {
            const seq = ++this.previewSeq;
            if (!this.fromYear) {
                this.plan = [];
                this.decisions = {};
                return;
            }
            this.previewLoading = true;
            this.plan = [];
            try {
                const plan = await this.getJson(`/promotion/preview?fromYear=${encodeURIComponent(this.fromYear)}`);
                if (seq !== this.previewSeq) {
                    return;
                }
                const decisions = {};
                plan.forEach(item => {
                    decisions[item.studentId] = item.defaultOutcome;
                });
                this.decisions = decisions;
                this.plan = plan;
            } catch (e) {
                if (seq === this.previewSeq) {
                    this.error = e.message !== undefined ? e.message : 'ارتباط با سرور برقرار نشد.';
                }
            } finally {
                if (seq === this.previewSeq) {
                    this.previewLoading = false;
                }
            }
        },

        // ---------------------------------------------------------------- نام‌ها

        studentName(userId) {
            const pupil = this.pupils.find(p => p.userId === userId);
            return pupil ? pupil.fullName : `دانش‌آموز #${Calendar.digits(String(userId))}`;
        },

        className(classId) {
            const classItem = this.allClasses.find(c => c.id === classId);
            return classItem ? classItem.name : '—';
        },

        visiblePlan() {
            const query = this.filterText.trim();
            return query ? this.plan.filter(item => ListSearch.matches(query, [this.studentName(item.userId)])) : this.plan;
        },

        // ---------------------------------------------------------------- ارتقا

        outcomeCounts() {
            const counts = { PROMOTE: 0, REPEAT: 0, GRADUATE: 0, LEAVE: 0 };
            this.plan.forEach(item => {
                const outcome = this.decisions[item.studentId] || item.defaultOutcome;
                counts[outcome] = (counts[outcome] || 0) + 1;
            });
            return counts;
        },

        countsText(counts) {
            const labels = { PROMOTE: 'ارتقا', REPEAT: 'تکرار پایه', GRADUATE: 'فارغ‌التحصیل', LEAVE: 'ترک مدرسه' };
            const parts = Object.keys(labels)
                .filter(key => counts && counts[key] > 0)
                .map(key => `${Calendar.digits(String(counts[key]))} ${labels[key]}`);
            return parts.length > 0 ? parts.join('، ') : '—';
        },

        summaryText() {
            return this.countsText(this.outcomeCounts());
        },

        runConfirmText() {
            const counts = this.outcomeCounts();
            let text = `از سال ${Calendar.digits(this.fromYear)} به سال ${Calendar.digits(this.current || '')}: ${this.summaryText()}. `
                + 'ارتقا فاکتوری صادر نمی‌کند و هر اجرا قابل برگرداندن است.';
            if (counts.LEAVE > 0) {
                text += ` دسترسیِ ${Calendar.digits(String(counts.LEAVE))} دانش‌آموزِ ترک‌کرده غیرفعال می‌شود (اطلاعاتشان حذف نمی‌شود).`;
            }
            return text;
        },

        openConfirm(kind, run, event) {
            this.error = '';
            this.notice = '';
            if (kind === 'run' && this.plan.length === 0) {
                this.error = this.fromYear
                    ? 'دانش‌آموزی برای ارتقا از این سال نمانده است.'
                    : 'یک سال را برای ارتقا انتخاب کنید.';
                return;
            }
            this.opener = event ? event.currentTarget : null;
            this.confirm = { kind, run };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.confirmModal));
        },

        closeConfirm() {
            this.confirm = null;
            window.ModalFocus.restore(this.opener, null);
        },

        async doConfirm() {
            const confirm = this.confirm;
            if (!confirm) {
                return;
            }
            if (confirm.kind === 'run') {
                await this.runPromotion();
            } else {
                await this.undo(confirm.run.id);
            }
        },

        async post(path, body) {
            return fetch(this.base(path), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-School-ID': this.schoolId,
                    'X-XSRF-TOKEN': this.csrfToken()
                },
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        },

        async runPromotion() {
            this.running = true;
            this.error = '';
            this.notice = '';
            try {
                // فقط نتیجه‌هایی که با پیش‌فرض فرق دارند می‌روند؛ بقیه قاعده‌ی پیش‌فرضِ سرور را می‌گیرند
                const decisions = this.plan
                    .filter(item => this.decisions[item.studentId] && this.decisions[item.studentId] !== item.defaultOutcome)
                    .map(item => ({ studentId: item.studentId, outcome: this.decisions[item.studentId] }));
                const response = await this.post('/promotion', { fromYear: this.fromYear, decisions });
                this.confirm = null;
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                const run = await response.json();
                this.notice = `ارتقا اجرا شد: ${this.countsText(run.counts)}.`;
                await this.load(true);
            } catch (e) {
                this.confirm = null;
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.running = false;
            }
        },

        async undo(runId) {
            this.undoing = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await this.post(`/promotion/runs/${runId}/undo`);
                this.confirm = null;
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                this.notice = 'اجرای ارتقا برگردانده شد.';
                await this.load(true);
            } catch (e) {
                this.confirm = null;
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.undoing = false;
            }
        },

        // ---------------------------------------------------------------- جا دادن

        classesOfGrade(gradeLevel) {
            return this.classes.filter(c => c.gradeLevel === gradeLevel);
        },

        classesOfGradeMissing() {
            return this.pending.some(item => this.classesOfGrade(item.gradeLevel).length === 0);
        },

        placementCount() {
            return this.pending.filter(item => this.placements[item.studentId]).length;
        },

        async place() {
            const placements = this.pending
                .filter(item => this.placements[item.studentId])
                .map(item => ({ studentId: item.studentId, classId: parseInt(this.placements[item.studentId], 10) }));
            if (this.placing) {
                return;
            }
            if (placements.length === 0) {
                this.error = 'برای دست‌کم یک دانش‌آموز کلاس را انتخاب کنید.';
                this.notice = '';
                return;
            }
            this.placing = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await this.post('/promotion/placements', { placements });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                const result = await response.json();
                this.notice = `${Calendar.digits(String(result.placed))} دانش‌آموز در کلاس جا داده شد.`;
                await this.load(true);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.placing = false;
            }
        },

        async changeOutcome(studentId, outcome) {
            this.error = '';
            this.notice = '';
            try {
                const response = await this.post('/promotion/outcome', { studentId, outcome });
                if (!response.ok) {
                    await this.showError(response);
                    return;
                }
                this.notice = outcome === 'REPEAT' ? 'پایه‌ی دانش‌آموز تکرار شد.' : 'دانش‌آموز به پایه‌ی بعد ارتقا یافت.';
                await this.load(true);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        }
    }));
});
