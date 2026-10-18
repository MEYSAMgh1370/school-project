// منطقِ خالصِ صفحه‌ی تنظیمات مدرسه (بدون مرورگر و Alpine): تبدیل پاسخ GET /settings به حالت فرم،
// «فهرست خالیِ نقش = همه‌ی نقش‌ها» و ساختِ بدنه‌ی PUTها. سرور مرجعِ نهایی است.
window.SettingsLogic = (function () {
    const REMINDER_ROLES_REQUIRED = 'دست‌کم یک نقش را برای دریافت یادآوری انتخاب کنید.';

    const present = (value) => value !== null && value !== undefined;

    // هر بخش فقط وقتی نشان داده می‌شود که سرور آن را پر کرده باشد (null = مجوز ندارد). بخشِ سال را autoPromotion
    // تعیین می‌کند نه currentYear: مدیری که هنوز سالِ جاری ندارد هم باید بتواند اولین سال را انتخاب کند.
    function fromResponse(response) {
        const r = response || {};
        return {
            hasYear: present(r.autoPromotion),
            hasFinance: present(r.installmentsEnabled),
            hasReminders: present(r.reminderRoles),
            hasBot: present(r.bot),
            current: r.currentYear || null,
            autoPromotion: !!r.autoPromotion,
            installmentsEnabled: !!r.installmentsEnabled,
            reminderRoles: r.reminderRoles ? [...r.reminderRoles] : [],
            botEnabled: !!(r.bot && r.bot.enabled),
            botRoles: r.bot && r.bot.roles ? [...r.bot.roles] : []
        };
    }

    // فهرستِ خالی = انتخابی ذخیره نشده = همه‌ی نقش‌ها
    function isAllRoles(selected) {
        return !selected || selected.length === 0;
    }

    // کدهای ناشناخته (نقشِ حذف‌شده) کنار گذاشته می‌شوند؛ اگر فهرستِ نقش‌ها بارگذاری نشده، انتخابِ ذخیره‌شده دست‌نخورده می‌ماند
    function knownSelected(selected, roles) {
        if (!roles || roles.length === 0) {
            return [...(selected || [])];
        }
        const known = new Set(roles.map((role) => role.code));
        return (selected || []).filter((code) => known.has(code));
    }

    function orderedByRoles(selected, roles) {
        const chosen = new Set(selected || []);
        return roles.map((role) => role.code).filter((code) => chosen.has(code));
    }

    // یادآوری‌ها: سرور انتخابِ خالی را رد می‌کند؛ '' = درست
    function rolesError(selected) {
        return selected && selected.length > 0 ? '' : REMINDER_ROLES_REQUIRED;
    }

    function reminderBody(selected, roles) {
        return { roleCodes: orderedByRoles(selected, roles) };
    }

    // بات: انتخابِ خالی = همه‌ی نقش‌ها و مجاز است
    function botBody(enabled, selected, roles) {
        const list = roles && roles.length > 0 ? orderedByRoles(selected, roles) : [...(selected || [])];
        return { enabled: !!enabled, roles: list };
    }

    // سالِ جاریِ دیده‌شده هم می‌رود تا سرور فقط وقتی بنویسد که هنوز همان است
    function yearBody(selectedId, current) {
        return {
            academicYearId: parseInt(selectedId, 10),
            expectedCurrentAcademicYearId: current ? current.id : null
        };
    }

    // سال‌هایی که داده‌ی موجود را می‌شود به آن‌ها نسبت داد: تا سالِ جاری (نه بعد از آن)
    function attributableYears(years, current) {
        if (!current) {
            return [];
        }
        const index = years.findIndex((year) => year.id === current.id);
        return index < 0 ? [] : years.slice(0, index + 1);
    }

    return { fromResponse, isAllRoles, knownSelected, rolesError, reminderBody, botBody, yearBody, attributableYears };
})();
