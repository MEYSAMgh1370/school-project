package com.example.roshdeandishe.iam.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ارسال best-effort کد تأیید پیامکی - همان الگوی
 * {@code notification.internal.BaleClient}/{@code InviteEmailSender}: یک کلاس
 * concrete (نه یک interface با چند پیاده‌سازی فرضی - هیچ ارائه‌دهنده‌ی
 * دومی هنوز درخواست نشده، ساختن انتزاع برایش خلاف YAGNI است). در dev/test
 * می‌تواند کد را در لاگ چاپ کند، ولی production باید fail-closed باشد: اگر
 * پیامک واقعاً ارسال نشود، کد معتبری هم ذخیره نمی‌شود.
 * <p>
 * roshdeandishe.sms.api-key پیش‌فرض خالی دارد، ولی پیکربندی‌اش هم delivery
 * را فعال نمی‌کند: اتصال به ارائه‌دهنده هنوز نوشته نشده (رجوع به
 * {@link #sendOtp}) - این کد گم‌شده است، نه کانفیگ زمان استقرار، و برخلاف
 * roshdeandishe.bale.bot-token که با توکن واقعی تماس واقعی برقرار می‌کند.
 * تا آن موقع ورود با پیامک فقط در dev و از طریق
 * roshdeandishe.sms.dev-log-codes کار می‌کند.
 */
@Component
class SmsDriver {

    private static final Logger log = LoggerFactory.getLogger(SmsDriver.class);

    private final String apiKey;
    private final boolean devLogCodes;

    SmsDriver(@Value("${roshdeandishe.sms.api-key}") String apiKey,
              @Value("${roshdeandishe.sms.dev-log-codes:false}") boolean devLogCodes) {
        this.apiKey = apiKey;
        this.devLogCodes = devLogCodes;
    }

    /**
     * آیا {@link #sendOtp} الان می‌تواند کدی را تحویل دهد؟ فقط وقتی که کلید پیکربندی نشده و {@code dev-log-codes}
     * روشن است (کد در لاگ چاپ می‌شود)؛ با کلیدِ واقعی هم نه، چون اتصال به ارائه‌دهنده هنوز نوشته نشده. صفحه‌ی ورود
     * از همین برای نشان‌دادنِ تبِ «کد پیامکی» استفاده می‌کند، تا تبی که در production همیشه شکست می‌خورد دیده نشود.
     * با نوشته‌شدنِ اتصال واقعی، این شرط باید همراهش تغییر کند.
     */
    boolean deliveryAvailable() {
        return (apiKey == null || apiKey.isBlank()) && devLogCodes;
    }

    boolean sendOtp(String phoneNumber, String code) {
        if (apiKey == null || apiKey.isBlank()) {
            if (devLogCodes) {
                log.warn("roshdeandishe.sms.api-key پیکربندی نشده - کد تأیید به {} ارسال نشد (کد: {}).", phoneNumber, code);
                return true;
            }
            log.warn("roshdeandishe.sms.api-key پیکربندی نشده - کد تأیید به {} ارسال نشد.", phoneNumber);
            return false;
        }

        // یک ارائه‌دهنده‌ی واقعی پیامک هنوز انتخاب نشده - وقتی apiKey
        // واقعی پیکربندی شود، فراخوانی HTTP واقعی همین‌جا اضافه می‌شود
        // (همان جایی که BaleClient.sendMessage تماس واقعی را انجام می‌دهد).
        log.error("roshdeandishe.sms.api-key تنظیم شده اما اتصال به ارائه‌دهنده‌ی پیامک هنوز پیاده‌سازی نشده است - کد تأیید به {} ارسال نشد.", phoneNumber);
        return false;
    }
}
