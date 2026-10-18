package com.example.roshdeandishe.notification;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Notification (کانال فعلی: بله).
 * <p>
 * برخلاف بقیه‌ی ماژول‌ها، اینجا عمداً هیچ {@code shared/BaleError} سلطه‌ای
 * ساخته نشده: تنها متد این Facade هیچ حالت خطای دامنه‌محورِ واقعی‌ای ندارد
 * که نیاز به پرتاب Exception داشته باشد - «کاربر لینک نشده» یک حالت
 * عادی/منتظره است (دقیقاً مثل IamFacade.issueInviteToken که برای حالت
 * no-op idempotent، Optional.empty برمی‌گرداند نه Exception)، پس با یک
 * boolean مدل شده، نه با یک ساختار sealed error که چیزی برای exhaustive
 * کردن ندارد.
 */
public interface NotificationFacade {

    /**
     * اگر این کاربر حساب بله‌اش را لینک کرده باشد، پیام برایش ارسال
     * می‌شود. Best-effort است: false یعنی یا لینک نشده یا ارسال شکست
     * خورده - تشخیص این دو برای فراخوان‌کننده (مثلاً یک event listener)
     * اهمیتی ندارد، چون در هر دو حالت کاری از دستش برنمی‌آید.
     */
    boolean sendMessageToUser(Long userId, String text);

    /**
     * مثل {@link #sendMessageToUser(Long, String)} ولی با مدرسه‌ی مقصد: اگر مدرسه رباتِ فعالِ اختصاصی داشته باشد
     * پیام فقط از همان ربات می‌رود (بدون بازگشت به ربات مشترک)؛ وگرنه مثل قبل از ربات مشترک. {@code schoolId}
     * می‌تواند null باشد (ربات مشترک).
     */
    boolean sendMessageToUser(Long userId, Long schoolId, String text);

    /**
     * مثل {@link #sendMessageToUser} ولی نتیجه را دقیق می‌گوید تا فراخوان‌کننده‌ای که می‌تواند دوباره تلاش کند
     * (مثلاً job یادآوریِ رویدادها) «کاربر لینک نشده» را از «ارسال موقتاً شکست خورد» جدا کند. مثل
     * {@code sendMessageToUser} هرگز exception دامنه‌ای پرتاب نمی‌کند.
     */
    DeliveryResult deliverMessageToUser(Long userId, String text);

    /**
     * نسخه‌ی مدرسه‌آگاهِ {@link #deliverMessageToUser(Long, String)} با همان قواعد مسیریابی {@code sendMessageToUser(userId, schoolId, text)}.
     * اگر {@code schoolId} داده شده و سوئیچِ «اعلان‌ها»ی آن مدرسه خاموش باشد، هیچ کانالی صدا زده نمی‌شود و
     * {@code UNDELIVERABLE} برمی‌گردد (نتیجه‌ی قطعی، بدون تلاش دوباره).
     */
    DeliveryResult deliverMessageToUser(Long userId, Long schoolId, String text);

    /**
     * {@code SENT}: پیام رسید. {@code NOT_LINKED}: کاربر روی هیچ کانالی حساب لینک‌شده ندارد (تلاش دوباره بی‌فایده است
     * تا خودش لینک کند). {@code UNDELIVERABLE}: کانال پیام را قطعاً نمی‌پذیرد یا پیکربندی نشده (مثلاً توکن بله خالی
     * یا پاسخ 4xx غیر از 429) و تلاش دوباره فایده ندارد. {@code TEMPORARY_FAILURE}: خطای موقت (قطعی یا تایم‌اوت شبکه،
     * 5xx، 429) و تلاش دوباره منطقی است.
     */
    enum DeliveryResult {
        SENT, NOT_LINKED, UNDELIVERABLE, TEMPORARY_FAILURE
    }

    /**
     * پردازش یک Update خام (بدنه‌ی JSON) از وب‌هوک بله. عمداً یک
     * {@code String} خام می‌گیرد، نه یک نوع ساختاریافته - شکل دقیق Update
     * (BaleUpdate) کاملاً داخلی همین ماژول است تا BaleWebhookController
     * (که طبق قرارداد پروژه در ریشه‌ی ماژول است، نه internal/) هرگز نیازی
     * به وارد کردن یک نوع internal نداشته باشد.
     * <p>
     * {@code secret} بخش مخفی مسیر وب‌هوک است و ربات مقصد از روی آن تشخیص داده می‌شود. false یعنی راز به
     * هیچ رباتی نمی‌خورد و بدنه اصلاً پارس نشده (کنترلر ۴۰۴ می‌دهد)؛ true یعنی Update پذیرفته شد.
     */
    boolean processIncomingUpdate(String secret, String rawJson);
}
