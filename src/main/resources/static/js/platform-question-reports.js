// «گزارش‌های سؤال پلتفرم» (/platform/question-reports): صفِ گزارش‌های مشکلِ سؤال‌های پلتفرم از همه‌ی مدرسه‌ها برای
// مدیر پلتفرم. مسیرهای پلتفرم‌اند: هدرِ مدرسه نمی‌فرستند، ولی CSRF روی نوشتن دقیقاً مثل صفحه‌ی تعطیلی‌های سراسری
// می‌رود. کنترل‌ها بر اساس Permission پنهان نمی‌شوند (UX_ROADMAP §3): کاربرِ دیگر 403 می‌گیرد و پیامِ مشترک را می‌بیند.
// API: GET /api/platform/question-reports، PATCH /api/platform/question-reports/{id}/status.
// D10: گزارش هرگز خودِ سؤال را ویرایش نمی‌کند؛ مدیر اول سؤال را جدا اصلاح می‌کند، بعد گزارش را «رفع شد» می‌کند.
document.addEventListener('alpine:init', () => {
    const DENIED = 'فقط مدیر پلتفرم می‌تواند گزارش‌های سؤال پلتفرم را مدیریت کند.';
    const NETWORK = 'ارتباط با سرور برقرار نشد.';
    const BASE = '/api/platform/question-reports';
    const NOTE_MAX = 1000;

    const TYPE_LABELS = {
        WRONG_ANSWER: 'پاسخ نادرست',
        TYPO: 'غلط املایی',
        UNCLEAR_TEXT: 'متن نامفهوم',
        OTHER: 'سایر'
    };
    const STATUS_LABELS = { OPEN: 'باز', REVIEWED: 'بررسی شد', FIXED: 'رفع شد', CLOSED: 'بسته' };
    const STATUS_TAGS = { OPEN: 'is-warning', REVIEWED: 'is-light', FIXED: 'is-success', CLOSED: 'is-light' };
    // فعل روی دکمه و عنوان modal؛ هر گذار یک modalِ مشترک دارد
    const ACTION_LABELS = { REVIEWED: 'بررسی شد', FIXED: 'رفع شد', CLOSED: 'بستن' };

    function csrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    // ProblemDetail → { blocking, message }؛ parse دفاعی چون بدنه شاید JSON نباشد
    async function problemOf(response) {
        let body = null;
        try {
            body = await response.json();
        } catch (e) {
            body = null;
        }
        const type = body && typeof body.type === 'string' ? body.type : '';
        if (type.endsWith('permission-denied') || response.status === 403) {
            return { blocking: true, message: DENIED };
        }
        if (type.endsWith('invalid-report-transition')) {
            return { blocking: false, message: 'این گذارِ وضعیت مجاز نیست؛ فهرست به‌روز شد.', stale: true };
        }
        const detail = body && (body.detail || body.title);
        return { blocking: false, message: detail || 'خطایی رخ داد. (' + response.status + ')' };
    }

    async function request(method, url, body) {
        const headers = {};
        if (method !== 'GET') {
            headers['X-XSRF-TOKEN'] = csrfToken();
        }
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (!response.ok) {
            const problem = await problemOf(response);
            const error = new Error(problem.message);
            error.blocking = problem.blocking;
            error.stale = problem.stale === true;
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    // نبودِ blocking یعنی fetch خودش پرتاب کرده (خطای شبکه)
    function messageOf(error) {
        return error.blocking !== undefined ? error.message : NETWORK;
    }

    Alpine.data('platformQuestionReportsPage', () => ({
        items: [],
        loading: false,
        // false = هنوز خواندنِ موفقی نبوده؛ شکست «فهرست خالی» نشان نمی‌دهد
        loaded: false,
        error: '',
        notice: '',
        saving: false,
        filterText: '',
        filterStatus: '',
        // modal: null = بسته؛ وگرنه { id, status, note, error, questionText }
        edit: null,
        noteMax: NOTE_MAX,
        listSeq: 0,
        mutateSeq: 0,

        init() {
            this.load();
        },

        async load() {
            const seq = ++this.listSeq;
            this.loading = true;
            this.error = '';
            try {
                const items = await request('GET', BASE);
                if (seq !== this.listSeq) {
                    return;
                }
                this.items = Array.isArray(items) ? items : [];
                this.loaded = true;
            } catch (e) {
                if (seq === this.listSeq) {
                    this.items = [];
                    this.loaded = false;
                    this.error = messageOf(e);
                }
            } finally {
                if (seq === this.listSeq) {
                    this.loading = false;
                }
            }
        },

        // ---------------------------------------------------------------- نمایش

        typeLabel(type) {
            return TYPE_LABELS[type] || TYPE_LABELS.OTHER;
        },

        statusLabel(status) {
            return STATUS_LABELS[status] || status;
        },

        statusTag(status) {
            return STATUS_TAGS[status] || 'is-light';
        },

        actionLabel(status) {
            return ACTION_LABELS[status] || '';
        },

        digits(value) {
            return Calendar.digits(value);
        },

        // Instant → تاریخ جلالیِ روزِ محلی با ارقام فارسی؛ روی سیم ISO می‌ماند
        dateOf(value) {
            const date = new Date(value);
            if (!value || Number.isNaN(date.getTime())) {
                return '';
            }
            const c = Calendar.fromDate(date);
            const pad = (n) => String(n).padStart(2, '0');
            return Calendar.digits(c.y + '/' + pad(c.m) + '/' + pad(c.d));
        },

        filtered() {
            const needle = this.filterText.trim().toLowerCase();
            return this.items.filter((item) => {
                if (this.filterStatus && item.status !== this.filterStatus) {
                    return false;
                }
                if (!needle) {
                    return true;
                }
                return [item.questionText, item.comment, item.resolutionNote, this.typeLabel(item.reportType)]
                    .some((part) => (part || '').toLowerCase().includes(needle));
            });
        },

        // ---------------------------------------------------------------- گذار وضعیت (یک modalِ مشترک)

        openEdit(item, status) {
            this.notice = '';
            this.edit = { id: item.id, status, note: '', error: '', questionText: item.questionText || '' };
            this.$nextTick(() => window.ModalFocus.focusFirst(this.$refs.editModal));
        },

        closeEdit() {
            this.edit = null;
        },

        noteCount() {
            return this.edit ? Calendar.digits(this.edit.note.length) + ' از ' + Calendar.digits(NOTE_MAX) : '';
        },

        async submitEdit() {
            const form = this.edit;
            if (!form || this.saving) {
                return;
            }
            const note = form.note.trim();
            if (note.length > NOTE_MAX) {
                form.error = 'یادداشت نباید بیشتر از ' + Calendar.digits(NOTE_MAX) + ' نویسه باشد.';
                return;
            }
            form.error = '';
            const body = { status: form.status };
            if (note) {
                body.resolutionNote = note;
            }
            await this.mutate(async () => {
                await request('PATCH', `${BASE}/${form.id}/status`, body);
            }, 'گزارش به‌روزرسانی شد.', () => {
                this.edit = null;
                this.load();
            }, (message, stale) => {
                if (stale) {
                    this.edit = null;
                    this.error = message;
                    this.load();
                } else if (this.edit) {
                    this.edit.error = message;
                }
            });
        },

        // یک نوشتن: is-loading (CSS) + این محافظ، نه disabled (دکمه‌ی disabled فوکوس را از modal می‌گیرد)
        async mutate(run, successMessage, onSuccess, onFailure) {
            if (this.saving) {
                return;
            }
            const seq = ++this.mutateSeq;
            this.saving = true;
            this.error = '';
            try {
                await run();
                if (seq !== this.mutateSeq) {
                    return;
                }
                this.notice = successMessage;
                onSuccess();
            } catch (e) {
                if (seq === this.mutateSeq) {
                    const message = messageOf(e);
                    if (e.blocking) {
                        // مجوز نداشتن: modal بسته و دلیل در ناحیه‌ی خطای مشترک نشان داده می‌شود
                        this.edit = null;
                        this.error = message;
                    } else {
                        onFailure(message, e.stale === true);
                    }
                }
            } finally {
                if (seq === this.mutateSeq) {
                    this.saving = false;
                }
            }
        }
    }));
});
