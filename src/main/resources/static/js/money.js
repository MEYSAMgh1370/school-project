window.Money = (function () {
    const amountFormatter = new Intl.NumberFormat('fa-IR-u-nu-arabext', {
        useGrouping: true,
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
    });

    function normalizeDigits(value) {
        return String(value ?? '')
            .replace(/[۰-۹]/g, digit => String(digit.charCodeAt(0) - '۰'.charCodeAt(0)))
            .replace(/[٠-٩]/g, digit => String(digit.charCodeAt(0) - '٠'.charCodeAt(0)));
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

    return {
        formatAmount: formatAmount,
        normalizeAmount: normalizeAmount,
        normalizeAmountInput: normalizeAmountInput
    };
})();
