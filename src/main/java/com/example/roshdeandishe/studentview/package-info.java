/**
 * ماژول Student view (خلاصهٔ دانش‌آموز و وضعیتِ امروزِ حضور و غیاب). مسئولیت: یک منبعِ واحد برای «خلاصهٔ
 * نمره‌ها/آزمون‌ها/غیبت» و «وضعیتِ امروزِ یک دانش‌آموز» تا پیشخوان و ربات پاسخِ یکسان بدهند. فقط می‌خواند و
 * از academic، exam، attendance، school و iam، همه از طریق facadeهای عمومی، می‌گیرد؛ تنها راه ورود سایر
 * ماژول‌ها، اینترفیس {@code StudentViewFacade} است.
 * <p>
 * بخش‌های ۲ تا ۵ راهنمای new-module اینجا کاربرد ندارند: REST ندارد، خطای خاصِ خودش ندارد (بخش‌ها «بهترین
 * تلاش»اند)، اشتراک‌پذیر نیست و چیزی نمی‌نویسد، پس کنترلِ دسترسیِ خودش، مجوزِ ماژول و audit هم ندارد.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Student view"
)
package com.example.roshdeandishe.studentview;
