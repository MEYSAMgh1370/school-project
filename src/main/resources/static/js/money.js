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

    return {
        formatAmount: formatAmount,
        normalizeAmount: normalizeAmount,
        normalizeAmountInput: normalizeAmountInput
    };
})();
