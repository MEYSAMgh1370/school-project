// جست‌وجوی متنیِ فهرست‌ها (DESIGN.md §6). یک جا تعریف می‌شود تا همه‌ی فهرست‌ها
// یک قاعده داشته باشند. پیش از این هر صفحه کپی خودش را داشت و هیچ‌کدام با
// حروف عربی (ي و ك که کیبورد عربی می‌دهد) یا نیم‌فاصله و نشانه‌ها کار نمی‌کرد.
//
// نرمال‌سازی روی هر دو طرف (عبارت جست‌وجو و متن ردیف) انجام می‌شود:
//   - ي و ك عربی → ی و ک فارسی (نام‌ها با حروف فارسی ذخیره می‌شوند)
//   - رقم فارسی و عربی → لاتین، تا «۱۴۰۵» و «1405» یکی باشند
//   - نیم‌فاصله (‌)، نشانه‌ها و جهت‌نماها حذف می‌شوند، تا «می‌دهد» و «میدهد» یکی باشند
//   - فاصله‌های اضافه جمع می‌شوند
// این فایل فقط مقایسه را انجام می‌دهد؛ متن نمایش داده‌شده تغییر نمی‌کند.
window.ListSearch = (function () {
    function normalize(value) {
        return String(value == null ? '' : value)
            .replace(/[‌‍‎‏ً-ٰٟ]/g, '')
            .replace(/ي/g, 'ی')
            .replace(/ك/g, 'ک')
            .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
            .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
            .replace(/\s+/g, ' ')
            .trim();
    }

    // true اگر عبارت در هیچ‌یک از متن‌ها پیدا شود؛ عبارت خالی همه‌چیز را می‌پذیرد
    function matches(query, texts) {
        const needle = normalize(query);
        if (!needle) {
            return true;
        }
        return texts.some((text) => normalize(text).includes(needle));
    }

    return { normalize, matches };
})();
