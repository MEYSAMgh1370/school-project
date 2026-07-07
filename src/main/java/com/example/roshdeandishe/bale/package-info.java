/**
 * ماژول Bale.
 * مسئولیت: اتصال به ربات پیام‌رسان بله (Bale) - اطلاع‌رسانی خروجی
 * (مثلاً صدور فاکتور) به کاربرانی که حساب بله‌شان را لینک کرده‌اند، و
 * پاسخ‌گویی به پرس‌وجوهای «خودسرویس» ورودی (مثل «فرزندان من»). یک ماژول
 * قابل‌اشتراک (subscribable) نیست - هیچ ردیف Module/Entitlement ندارد و
 * هیچ {@code @RequiresModule} ای روی کنترلرش نیست، دقیقاً مثل
 * MembershipController/SchoolController (رجوع کنید به CLAUDE.md §5).
 * تنها راه ورود سایر ماژول‌ها، اینترفیس {@code BaleFacade} است.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Bale Bot Integration"
)
package com.example.roshdeandishe.bale;
