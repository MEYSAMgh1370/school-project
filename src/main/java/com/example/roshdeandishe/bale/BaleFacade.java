package com.example.roshdeandishe.bale;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Bale.
 * <p>
 * برخلاف بقیه‌ی ماژول‌ها، اینجا عمداً هیچ {@code shared/BaleError} سلطه‌ای
 * ساخته نشده: تنها متد این Facade هیچ حالت خطای دامنه‌محورِ واقعی‌ای ندارد
 * که نیاز به پرتاب Exception داشته باشد - «کاربر لینک نشده» یک حالت
 * عادی/منتظره است (دقیقاً مثل IamFacade.issueInviteToken که برای حالت
 * no-op idempotent، Optional.empty برمی‌گرداند نه Exception)، پس با یک
 * boolean مدل شده، نه با یک ساختار sealed error که چیزی برای exhaustive
 * کردن ندارد.
 */
public interface BaleFacade {

    /**
     * اگر این کاربر حساب بله‌اش را لینک کرده باشد، پیام برایش ارسال
     * می‌شود. Best-effort است: false یعنی یا لینک نشده یا ارسال شکست
     * خورده - تشخیص این دو برای فراخوان‌کننده (مثلاً یک event listener)
     * اهمیتی ندارد، چون در هر دو حالت کاری از دستش برنمی‌آید.
     */
    boolean sendMessageToUser(Long userId, String text);

    /**
     * پردازش یک Update خام (بدنه‌ی JSON) از وب‌هوک بله. عمداً یک
     * {@code String} خام می‌گیرد، نه یک نوع ساختاریافته - شکل دقیق Update
     * (BaleUpdate) کاملاً داخلی همین ماژول است تا BaleWebhookController
     * (که طبق قرارداد پروژه در ریشه‌ی ماژول است، نه internal/) هرگز نیازی
     * به وارد کردن یک نوع internal نداشته باشد.
     */
    void processIncomingUpdate(String rawJson);
}
