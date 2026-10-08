window.Money = (function () {
    const amountFormatter = new Intl.NumberFormat('fa-IR-u-nu-arabext', {
        useGrouping: true,
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
    });

    function normalizeDigits(value) {
        return window.Digits.toAscii(value);
    }

    function normalizeAmount(value) {
        return normalizeDigits(value)
            .replace(/[٬,\s\u00a0\u202f]/g, '')
            .replace(/٫/g, '.');
    }

    function normalizeAmountInput(event) {
        const normalized = normalizeAmount(event.target.value);
        event.target.value = normalized;
        return normalized;
    }

    function formatAmount(value) {
        if (value === null || value === undefined || value === '') {
            return '—';
        }

        const numericValue = typeof value === 'number' ? value : Number(value);
        if (!Number.isFinite(numericValue)) {
            return '—';
        }

        return amountFormatter.format(numericValue);
    }

    // مبلغ ریالی را به تومان و به حروف می‌نویسد: ۲۰۰۰۰۰۰ ریال → «دویست هزار تومان».
    // ده ریال یک تومان است؛ باقیِ ریال جدا نوشته می‌شود («یک تومان و پنج ریال»).
    // ورودی باید عدد صحیح و نامنفی باشد؛ در غیر این صورت رشته‌ی خالی برمی‌گردد.
    const ONES = ['', 'یک', 'دو', 'سه', 'چهار', 'پنج', 'شش', 'هفت', 'هشت', 'نه'];
    const TEENS = ['ده', 'یازده', 'دوازده', 'سیزده', 'چهارده', 'پانزده', 'شانزده', 'هفده', 'هجده', 'نوزده'];
    const TENS = ['', '', 'بیست', 'سی', 'چهل', 'پنجاه', 'شصت', 'هفتاد', 'هشتاد', 'نود'];
    const HUNDREDS = ['', 'صد', 'دویست', 'سیصد', 'چهارصد', 'پانصد', 'ششصد', 'هفتصد', 'هشتصد', 'نهصد'];
    const SCALES = ['', 'هزار', 'میلیون', 'میلیارد', 'تریلیون'];
    const MAX_WORDED = 1e15;

    // ۰ تا ۹۹۹
    function groupWords(n) {
        const parts = [];
        const hundreds = Math.floor(n / 100);
        const rest = n % 100;
        if (hundreds) {
            parts.push(HUNDREDS[hundreds]);
        }
        if (rest >= 10 && rest < 20) {
            parts.push(TEENS[rest - 10]);
        } else {
            if (Math.floor(rest / 10)) {
                parts.push(TENS[Math.floor(rest / 10)]);
            }
            if (rest % 10) {
                parts.push(ONES[rest % 10]);
            }
        }
        return parts.join(' و ');
    }

    // عدد صحیحِ نامنفیِ کمتر از MAX_WORDED به حروف فارسی
    function integerWords(n) {
        if (n === 0) {
            return 'صفر';
        }
        const parts = [];
        let rest = n;
        let scale = 0;
        while (rest > 0) {
            const group = rest % 1000;
            if (group) {
                const words = groupWords(group);
                // «هزار» تنها، نه «یک هزار»؛ ولی «دو میلیون»
                parts.unshift(scale === 0 ? words : (group === 1 ? '' : words + ' ') + SCALES[scale]);
            }
            rest = Math.floor(rest / 1000);
            scale++;
        }
        return parts.filter(Boolean).join(' و ');
    }

    function tomanWords(value) {
        const normalized = normalizeAmount(value == null ? '' : value);
        if (normalized === '') {
            return '';
        }
        const amount = Number(normalized);
        if (!Number.isInteger(amount) || amount < 0 || amount >= MAX_WORDED) {
            return '';
        }
        const toman = Math.floor(amount / 10);
        const rial = amount % 10;
        if (amount === 0) {
            return 'صفر تومان';
        }
        const parts = [];
        if (toman) {
            parts.push(integerWords(toman) + ' تومان');
        }
        if (rial) {
            parts.push(integerWords(rial) + ' ریال');
        }
        return parts.join(' و ');
    }

    return {
        formatAmount: formatAmount,
        normalizeAmount: normalizeAmount,
        normalizeAmountInput: normalizeAmountInput,
        tomanWords: tomanWords
    };
})();
