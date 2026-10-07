// تبدیل رقم فارسی و عربی‌–هندی به لاتین (DESIGN.md §8). یک جا تعریف می‌شود: پیش از این
// money.js، grades.js و list-search.js هرکدام جدول خودشان را داشتند و با تغییر یکی، بقیه
// متفاوت رفتار می‌کردند. فقط تبدیلِ رقم است؛ جداکننده‌ها هر صفحه خودش تصمیم می‌گیرد.
window.Digits = (function () {
    function toAscii(value) {
        return String(value ?? '')
            .replace(/[۰-۹]/g, (digit) => String(digit.charCodeAt(0) - 0x06F0))
            .replace(/[٠-٩]/g, (digit) => String(digit.charCodeAt(0) - 0x0660));
    }
    return { toAscii };
})();
