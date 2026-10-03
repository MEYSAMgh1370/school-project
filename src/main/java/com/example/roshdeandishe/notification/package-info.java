/**
 * ماژول Notification.
 * مسئولیت: اطلاع‌رسانی خروجیِ best-effort به یک کاربر (با شناسه‌ی داخلی
 * userId) از طریق هر کانالی که در این استقرار فعال است - فعلاً Bale (فقط
 * اگر {@code roshdeandishe.bale.bot-token} تنظیم شده باشد) و ایمیل. این ماژول
 * هیچ داده‌ای ذخیره نمی‌کند، هیچ سطح HTTP ای ندارد و یک ماژول
 * قابل‌اشتراک (subscribable) نیست. ایمیل دعوت (iam) و OTP پیامکی (iam)
 * عمداً کانال این ماژول نیستند. تنها راه ورود سایر ماژول‌ها،
 * اینترفیس {@code NotificationFacade} است.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Notification"
)
package com.example.roshdeandishe.notification;
