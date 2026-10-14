// منطقِ خالصِ صفحه‌ی «سابقه‌ی تغییرات مالی» (GET /api/admin/schools/{id}/finance/audit): برچسب فارسیِ نوعِ تغییر،
// خلاصه‌ی یک‌خطی و ردیف‌های جزئیات از روی details، اعتبارسنجی فیلترها و ساختِ query string. جدا از
// finance-audit.js است تا بدون مرورگر و Alpine تست‌پذیر باشد. تاریخ، مبلغ و رقم فارسی را خودِ صفحه می‌دهد
// (fmt = { amount, date, number }) تا این فایل به Calendar/Money وابسته نباشد؛ روی سیم فقط ISO و رقم ASCII است.
window.FinanceAuditLogic = (function () {
    const ACTION_LABELS = {
        INVOICE_ISSUED: 'صدور فاکتور',
        PAYMENT_RECORDED: 'ثبت پرداخت',
        INVOICE_DISCOUNTED: 'ثبت تخفیف',
        DISCOUNT_EARNED: 'تخفیف مشروط قطعی شد',
        DISCOUNT_LAPSED: 'تخفیف مشروط ساقط شد',
        INSTALLMENTS_CHANGED: 'تغییر اقساط',
        INSTALLMENTS_SETTING_CHANGED: 'تغییر تنظیم اقساط',
        EXPENSE_RECORDED: 'ثبت هزینه',
        EXPENSE_APPROVED: 'تأیید هزینه',
        EXPENSE_PAID: 'پرداخت هزینه',
        EXPENSE_CANCELLED: 'لغو هزینه'
    };

    const METHOD_SUMMARY = {
        CASH: 'پرداخت نقدی',
        CARD: 'پرداخت با کارت',
        BANK_TRANSFER: 'پرداخت با انتقال بانکی',
        CHEQUE: 'پرداخت با چک',
        OTHER: 'پرداخت به روشی دیگر'
    };

    const METHOD_LABEL = {
        CASH: 'نقدی',
        CARD: 'کارت',
        BANK_TRANSFER: 'انتقال بانکی',
        CHEQUE: 'چک',
        OTHER: 'سایر'
    };

    const CAUSE_LABEL = {
        GENERATED: 'تعریف اقساط',
        EDITED: 'ویرایش دستی',
        DISCOUNT_RESPLIT: 'تقسیم دوباره پس از تخفیف',
        DISCOUNT_LAPSE_RESPLIT: 'تقسیم دوباره پس از ساقط‌شدن تخفیف'
    };

    // محرکِ گذار در DiscountEarned/Lapsed (FinanceFacadeImpl)
    const TRIGGER_LABEL = {
        payment: 'با ثبت پرداخت',
        discount: 'هنگام ثبت تخفیف',
        sweep: 'بررسی خودکار',
        installments: 'هنگام تغییر اقساط'
    };

    const DISCOUNT_STATUS_LABEL = {
        APPLIED: 'دائمی',
        PENDING: 'مشروط',
        EARNED: 'قطعی‌شده',
        LAPSED: 'ساقط‌شده'
    };

    const UNDATED = 'بدون سررسید';

    // گزینه‌های فیلتر نوعِ تغییر، به ترتیبِ نمایش
    function actionOptions() {
        return Object.keys(ACTION_LABELS).map((code) => ({ code, label: ACTION_LABELS[code] }));
    }

    // کدِ ناشناخته همان‌طور که هست نمایش داده می‌شود
    function actionLabel(code) {
        return ACTION_LABELS[code] || code || '—';
    }

    // هیچ مجری = تغییرِ خودکارِ سیستم
    function actorLabel(item) {
        if (item.actorUserId == null) {
            return 'سیستم';
        }
        return item.actorName || '—';
    }

    // details ممکن است شیء، رشته‌ی JSON یا null باشد؛ همیشه شیء (یا null) برمی‌گردد
    function detailsOf(item) {
        let details = item ? item.details : null;
        if (typeof details === 'string') {
            try {
                details = JSON.parse(details);
            } catch (e) {
                return null;
            }
        }
        return details && typeof details === 'object' && !Array.isArray(details) ? details : null;
    }

    function isPresent(value) {
        return value !== null && value !== undefined && value !== '';
    }

    function onOff(value) {
        return value === true || value === 'true' ? 'روشن' : 'خاموش';
    }

    function triggerLabel(trigger) {
        return TRIGGER_LABEL[trigger] || trigger;
    }

    // خلاصه‌ی یک‌خطی؛ برای کدِ ناشناخته یا بدون details رشته‌ی خالی
    function summarize(item, fmt) {
        const d = detailsOf(item) || {};
        switch (item.action) {
            case 'INVOICE_ISSUED':
                return 'فاکتور جدید صادر شد';
            case 'PAYMENT_RECORDED': {
                const base = METHOD_SUMMARY[d.method] || (isPresent(d.method) ? 'پرداخت (' + d.method + ')' : 'پرداخت');
                return isPresent(d.note) ? base + ' — ' + d.note : base;
            }
            case 'INVOICE_DISCOUNTED': {
                let text = 'تخفیف';
                if (isPresent(item.amount)) {
                    text += ' ' + fmt.amount(item.amount);
                }
                if (isPresent(d.deadline)) {
                    text += ' تا ' + fmt.date(d.deadline);
                }
                if (d.status === 'PENDING' || isPresent(d.deadline)) {
                    text += ' مشروط';
                }
                return isPresent(d.reason) ? text + ' — ' + d.reason : text;
            }
            case 'DISCOUNT_EARNED':
            case 'DISCOUNT_LAPSED': {
                const verb = item.action === 'DISCOUNT_EARNED' ? 'قطعی شد' : 'ساقط شد';
                let text = 'تخفیف' + (isPresent(item.amount) ? ' ' + fmt.amount(item.amount) : '') + ' ' + verb;
                if (isPresent(d.trigger)) {
                    text += ' (' + triggerLabel(d.trigger) + ')';
                }
                return text;
            }
            case 'INSTALLMENTS_CHANGED':
                return isPresent(d.cause) ? 'اقساط: ' + (CAUSE_LABEL[d.cause] || d.cause) : 'اقساط تغییر کرد';
            case 'INSTALLMENTS_SETTING_CHANGED':
                return 'تنظیم اقساط: ' + onOff(d.from) + ' ← ' + onOff(d.to);
            case 'EXPENSE_RECORDED':
            case 'EXPENSE_APPROVED':
            case 'EXPENSE_PAID':
            case 'EXPENSE_CANCELLED':
                return isPresent(item.expenseId) ? 'هزینه‌ی شماره‌ی ' + fmt.number(item.expenseId) : '';
            default:
                return '';
        }
    }

    // ردیف‌های قبل/بعدِ اقساط برای INSTALLMENTS_CHANGED؛ در بقیه‌ی نوع‌ها null
    function installmentTables(item) {
        const d = detailsOf(item);
        if (item.action !== 'INSTALLMENTS_CHANGED' || !d) {
            return null;
        }
        const rows = (list) => (Array.isArray(list) ? list : []).map((row) => ({
            amount: row.amount,
            amountPaid: row.amountPaid,
            dueDate: row.dueDate == null || row.dueDate === '' ? null : row.dueDate
        }));
        return { cause: d.cause, before: rows(d.before), after: rows(d.after) };
    }

    // متنِ سررسیدِ یک ردیف؛ format برای تاریخِ خالی صدا زده نمی‌شود
    function dueLabel(row, format) {
        return row.dueDate == null ? UNDATED : format(row.dueDate);
    }

    // فهرستِ کلید/مقدار برای نوع‌هایی که جدولِ قبل/بعد ندارند؛ مقدارِ خالی نمی‌آید
    function detailEntries(item, fmt) {
        const d = detailsOf(item);
        if (!d || item.action === 'INSTALLMENTS_CHANGED') {
            return [];
        }
        const entries = [];
        const add = (label, value) => {
            if (isPresent(value)) {
                entries.push({ label, value: String(value) });
            }
        };
        switch (item.action) {
            case 'PAYMENT_RECORDED':
                add('شناسه‌ی پرداخت', isPresent(d.paymentId) ? fmt.number(d.paymentId) : null);
                add('روش پرداخت', METHOD_LABEL[d.method] || d.method);
                add('توضیح', d.note);
                break;
            case 'INVOICE_DISCOUNTED':
                add('شناسه‌ی تخفیف', isPresent(d.discountId) ? fmt.number(d.discountId) : null);
                add('دلیل', d.reason);
                add('مهلت تسویه', isPresent(d.deadline) ? fmt.date(d.deadline) : null);
                add('وضعیت', DISCOUNT_STATUS_LABEL[d.status] || d.status);
                break;
            case 'DISCOUNT_EARNED':
            case 'DISCOUNT_LAPSED':
                add('شناسه‌ی تخفیف', isPresent(d.discountId) ? fmt.number(d.discountId) : null);
                add('مهلت تسویه', isPresent(d.deadline) ? fmt.date(d.deadline) : null);
                add('محرک', isPresent(d.trigger) ? triggerLabel(d.trigger) : null);
                break;
            case 'INSTALLMENTS_SETTING_CHANGED':
                add('پیش از تغییر', isPresent(d.from) ? onOff(d.from) : null);
                add('پس از تغییر', isPresent(d.to) ? onOff(d.to) : null);
                break;
            default:
                Object.keys(d).forEach((key) => {
                    const value = d[key];
                    add(key, value !== null && typeof value === 'object' ? JSON.stringify(value) : value);
                });
        }
        return entries;
    }

    function hasDetails(item, fmt) {
        return installmentTables(item) !== null || detailEntries(item, fmt).length > 0;
    }

    // «۱۴:۰۵» از یک Date؛ digits تابعِ رقم فارسی است
    function clock(date, digits) {
        const pad = (n) => String(n).padStart(2, '0');
        return digits(pad(date.getHours()) + ':' + pad(date.getMinutes()));
    }

    function pageCount(totalItems, size) {
        return Math.max(1, Math.ceil((Number(totalItems) || 0) / size));
    }

    // شماره‌ی مثبتِ ASCII یا ''؛ برای فیلتر فاکتور/دانش‌آموز
    function positiveId(value, toAscii) {
        const text = toAscii(value == null ? '' : value).trim();
        return /^\d{1,15}$/.test(text) && Number(text) > 0 ? text.replace(/^0+/, '') : '';
    }

    // invoiceId/studentId از query string صفحه (لینک «سابقه‌ی این فاکتور»)
    function parseInitialQuery(search, toAscii) {
        const params = new URLSearchParams(search || '');
        return {
            invoiceId: positiveId(params.get('invoiceId'), toAscii),
            studentId: positiveId(params.get('studentId'), toAscii)
        };
    }

    // فرمِ فیلتر → { ok, errors, value }؛ value روی سیم است (ISO میلادی، ارقام ASCII). تاریخِ خالی = بدون محدودیت
    function validateFilters(form, helpers) {
        const errors = { invoiceId: '', from: '', to: '' };
        const value = { invoiceId: '', action: form.action || '', from: '', to: '' };
        const invoiceText = helpers.toAscii(form.invoiceId == null ? '' : form.invoiceId).trim();
        if (invoiceText !== '') {
            value.invoiceId = positiveId(invoiceText, helpers.toAscii);
            if (value.invoiceId === '') {
                errors.invoiceId = 'شماره‌ی فاکتور را فقط با رقم و بزرگ‌تر از صفر بنویسید.';
            }
        }
        const dateError = 'تاریخ را به شکل سال/ماه/روز بنویسید، مثل ' + helpers.example + '.';
        ['from', 'to'].forEach((key) => {
            const text = String(form[key] == null ? '' : form[key]).trim();
            if (text === '') {
                return;
            }
            const iso = helpers.parseDate(text);
            if (iso) {
                value[key] = iso;
            } else {
                errors[key] = dateError;
            }
        });
        if (!errors.from && !errors.to && value.from && value.to && value.from > value.to) {
            errors.to = 'تاریخ پایان نباید پیش از تاریخ شروع باشد.';
        }
        return { ok: !errors.invoiceId && !errors.from && !errors.to, errors, value };
    }

    // query string درخواست؛ فقط فیلترهای پرشده. page بر مبنای صفر است (قرارداد API)
    function buildQuery(filters, page, size) {
        const params = new URLSearchParams();
        ['invoiceId', 'studentId', 'action', 'from', 'to'].forEach((key) => {
            if (isPresent(filters[key])) {
                params.set(key, String(filters[key]));
            }
        });
        params.set('page', String(page));
        params.set('size', String(size));
        return params.toString();
    }

    return {
        actionOptions, actionLabel, actorLabel, detailsOf, summarize, installmentTables, dueLabel,
        detailEntries, hasDetails, clock, pageCount, parseInitialQuery, validateFilters, buildQuery
    };
})();
