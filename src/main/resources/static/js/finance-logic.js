// منطقِ خالصِ صفحه‌ی مالی (تخفیف و اقساط شهریه، openspec/changes/tuition-installments.md): مبلغِ قابل‌پرداخت،
// مانده و بررسیِ «جمع اقساط». جدا از finance.js است تا بدون مرورگر و Alpine تست‌پذیر باشد. مبلغ‌ها ریال‌اند و
// ورودیِ کاربر ممکن است ارقام فارسی/عربی داشته باشد؛ حسابِ ریالی روی صدم (۲ رقم اعشار، مثل سرور) انجام می‌شود تا
// خطای اعشاریِ ممیز شناور جمع را نشکند. سرور مرجعِ نهایی است؛ این فقط راهنمای زنده‌ی فرم است.
//
// وابستگی: /js/digits.js و /js/money.js پیش از این فایل.
window.FinanceLogic = (function () {
    // مبلغِ تایپ‌شده یا عددی → صدم (عدد صحیح)؛ null اگر خالی یا نامعتبر یا منفی باشد
    function cents(value) {
        const text = window.Money.normalizeAmount(value == null ? '' : value);
        if (!/^\d+(\.\d{1,2})?$/.test(text)) {
            return null;
        }
        return Math.round(Number(text) * 100);
    }

    function centsOfNumber(value) {
        return Math.round((Number(value) || 0) * 100);
    }

    // قابل‌پرداخت = مبلغ - مجموع تخفیف (فاکتورِ قدیمی بدون discountTotal هم کار می‌کند)
    function payable(invoice) {
        return (centsOfNumber(invoice.amount) - centsOfNumber(invoice.discountTotal)) / 100;
    }

    function remaining(invoice) {
        return (centsOfNumber(payable(invoice)) - centsOfNumber(invoice.amountPaid)) / 100;
    }

    // قسطِ کاملاً پرداخت‌شده ویرایش‌پذیر نیست
    function isEditable(installment) {
        return installment.status !== 'PAID';
    }

    // جمعی که اقساطِ ویرایش‌پذیر باید داشته باشند = قابل‌پرداخت - مبلغِ اقساطِ کاملاً پرداخت‌شده
    function expectedSum(invoice, installments) {
        const paid = installments.filter((i) => !isEditable(i)).reduce((sum, i) => sum + centsOfNumber(i.amount), 0);
        return (centsOfNumber(payable(invoice)) - paid) / 100;
    }

    // جمعِ مبلغ‌های تایپ‌شده‌ی ردیف‌های ویرایش؛ ردیفِ خالی/نامعتبر صفر حساب می‌شود
    function editedSum(rows) {
        return rows.reduce((sum, row) => sum + (cents(row.amount) || 0), 0) / 100;
    }

    // همه‌ی ردیف‌ها مبلغِ مثبتِ معتبر دارند و جمعشان دقیقاً برابرِ expected است
    function sumMatches(rows, expected) {
        if (rows.length === 0) {
            return false;
        }
        const values = rows.map((row) => cents(row.amount));
        if (values.some((v) => v === null || v <= 0)) {
            return false;
        }
        return values.reduce((a, b) => a + b, 0) === centsOfNumber(expected);
    }

    // تخفیف‌های مشروطِ هنوز باز (PENDING)؛ ساقط‌شده و حاصل‌شده در این فهرست نیست
    function pendingDiscounts(discounts) {
        return (discounts || []).filter((d) => d.status === 'PENDING');
    }

    // برچسب وضعیتِ تخفیف؛ برای PENDING تاریخِ نمایشیِ آماده (formatDate) را می‌گیرد
    function discountStatusLabel(discount, formattedDeadline) {
        switch (discount.status) {
            case 'PENDING':
                return formattedDeadline ? 'مشروط تا ' + formattedDeadline : 'مشروط';
            case 'EARNED':
                return 'حاصل شد';
            case 'LAPSED':
                return 'ساقط شد';
            default:
                return 'دائمی';
        }
    }

    // کلاسِ Bulma ی برچسبِ وضعیت
    function discountStatusClass(discount) {
        switch (discount.status) {
            case 'PENDING':
                return 'is-warning';
            case 'EARNED':
                return 'is-success';
            case 'LAPSED':
                return 'is-light';
            default:
                return 'is-info';
        }
    }

    return { cents, payable, remaining, isEditable, expectedSum, editedSum, sumMatches,
        pendingDiscounts, discountStatusLabel, discountStatusClass };
})();
