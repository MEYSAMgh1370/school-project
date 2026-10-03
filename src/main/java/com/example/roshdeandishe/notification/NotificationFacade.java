package com.example.roshdeandishe.notification;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Notification.
 * <p>
 * مثل {@code BaleFacade}، عمداً هیچ Exception/{@code NotificationError} ای
 * ندارد: ارسال best-effort است و «کاربر برای هیچ کانالی قابل‌دسترس نیست»
 * یک حالت عادی است، نه خطا.
 */
public interface NotificationFacade {

    /**
     * پیام را از طریق همه‌ی کانال‌های فعال برای این کاربر می‌فرستد. Best-effort
     * است و هرگز Exception پرتاب نمی‌کند: false یعنی هیچ کانالی پیام را
     * تحویل نداد (کاربر در دسترس نبود یا ارسال شکست خورد) - تشخیص این دو برای
     * فراخوان‌کننده اهمیتی ندارد، چون در هر دو حالت کاری از دستش برنمی‌آید.
     */
    boolean sendToUser(Long userId, String text);
}
