// منطقِ خالصِ صفحه‌ی «بات بله‌ی مدرسه»: حالتِ صفحه از روی پاسخ GET و متنِ فارسیِ خطا از روی status و type
// ProblemDetail (پسوندِ type: bale-bot-token-invalid، bale-bot-already-registered، bale-unavailable،
// bale-bot-encryption-unavailable، bale-bot-too-many-attempts). جدا از bale-bot.js است تا بدون مرورگر تست شود.
(function (root) {
    const BY_TYPE = [
        ['bale-bot-token-invalid', 'توکن واردشده توسط بله پذیرفته نشد. توکن را از BotFather دوباره کپی کنید.'],
        ['bale-bot-already-registered', 'این بات برای مدرسه‌ی دیگری ثبت شده است. برای هر مدرسه باید بات جداگانه بسازید.'],
        // این دو هر دو 503‌اند ولی علتشان فرق دارد: اولی موقتی است، دومی را فقط پشتیبانی درست می‌کند
        ['bale-bot-encryption-unavailable', 'امکان ثبت بات موقتاً فعال نیست؛ با پشتیبانی تماس بگیرید.'],
        ['bale-unavailable', 'بله در دسترس نیست. کمی بعد دوباره تلاش کنید.'],
        ['bale-bot-too-many-attempts', 'در هر ساعت حداکثر ۱۰ بار می‌توانید بات را ثبت کنید. بعداً دوباره تلاش کنید.']
    ];

    // none | active | disabled
    function viewState(bot) {
        if (!bot) {
            return 'none';
        }
        return bot.status === 'DISABLED' ? 'disabled' : 'active';
    }

    // problem = { status, type, detail }
    function errorMessage(problem) {
        const type = problem.type || '';
        for (const [suffix, message] of BY_TYPE) {
            if (type.endsWith(suffix)) {
                return message;
            }
        }
        return problem.detail || ('خطایی رخ داد. (' + problem.status + ')');
    }

    function validateToken(token) {
        return String(token || '').trim() === '' ? 'توکن بات را وارد کنید.' : '';
    }

    const api = { viewState, errorMessage, validateToken };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    } else {
        root.BaleBotLogic = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
