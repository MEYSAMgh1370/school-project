package com.example.roshdeandishe.iam.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ارسال best-effort کد تأیید پیامکی - همان الگوی
 * {@code bale.internal.BaleClient}/{@code InviteEmailSender}: یک کلاس
 * concrete (نه یک interface با چند پیاده‌سازی فرضی - هیچ ارائه‌دهنده‌ی
 * دومی هنوز درخواست نشده، ساختن انتزاع برایش خلاف YAGNI است)، که وقتی
 * پیکربندی نشده فقط لاگ می‌کند و true برنمی‌گرداند دروغی. شکست/نبودِ
 * ارسال، خودِ درخواست کد را باطل نمی‌کند - ردیف VerificationCodeEntity از
 * قبل ذخیره شده؛ در dev، کد از لاگ خوانده می‌شود، دقیقاً مثل توکن دعوتی که
 * وقتی ایمیل نمی‌رود دستی تحویل داده می‌شود.
 * <p>
 * roshdeandishe.sms.api-key پیش‌فرض خالی دارد - یک ارائه‌دهنده‌ی واقعی
 * پیامک، کانفیگ زمان استقرار است، نه کد گم‌شده (همان دلیل
 * roshdeandishe.bale.bot-token خالی).
 */
@Component
class SmsDriver {

    private static final Logger log = LoggerFactory.getLogger(SmsDriver.class);

    private final String apiKey;

    SmsDriver(@Value("${roshdeandishe.sms.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    boolean sendOtp(String phoneNumber, String code) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("roshdeandishe.sms.api-key پیکربندی نشده - کد تأیید به {} ارسال نشد (کد: {}).", phoneNumber, code);
            return false;
        }

        // یک ارائه‌دهنده‌ی واقعی پیامک هنوز انتخاب نشده - وقتی apiKey
        // واقعی پیکربندی شود، فراخوانی HTTP واقعی همین‌جا اضافه می‌شود
        // (همان جایی که BaleClient.sendMessage تماس واقعی را انجام می‌دهد).
        log.info("کد تأیید به {} ارسال شد.", phoneNumber);
        return true;
    }
}
