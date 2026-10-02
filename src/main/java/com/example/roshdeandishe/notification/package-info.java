/**
 * ماژول Notification (اطلاع‌رسانی).
 * مسئولیت: اطلاع‌رسانی خروجی (مثلاً صدور فاکتور) به کاربران از طریق کانال‌های
 * پیام‌رسان. تنها کانال فعلی ربات پیام‌رسان بله (Bale) است: اطلاع‌رسانی به
 * کاربرانی که حساب بله‌شان را لینک کرده‌اند، و پاسخ‌گویی به پرس‌وجوهای
 * «خودسرویس» ورودی (مثل «فرزندان من»). کلاس‌های Bale* داخل internal/ همان
 * کانال بله‌اند؛ آدرس وب‌هوک (/api/bale/webhook)، کلیدهای roshdeandishe.bale.*،
 * schema/جدول bale.bale_links و متغیرهای BALE_* عمداً بدون تغییر ماندند چون
 * قراردادهای بیرونی‌اند (OpenSpec: changes/rename-bale-module-to-notification.md). یک ماژول
 * قابل‌اشتراک (subscribable) نیست - هیچ ردیف Module/Entitlement ندارد و
 * هیچ {@code @RequiresModule} ای روی کنترلرش نیست، دقیقاً مثل
 * MembershipController/SchoolController (رجوع کنید به CLAUDE.md §5).
 * تنها راه ورود سایر ماژول‌ها، اینترفیس {@code NotificationFacade} است.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Notification"
)
package com.example.roshdeandishe.notification;
