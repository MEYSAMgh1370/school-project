/**
 * ماژول Curriculum - سرفصل‌های درسی (تاکسونومی سلسله‌مراتبی) و بانک سوال
 * چهارگزینه‌ای. پورت‌شده از moshavere (exam-maker-master/exam_generator) طبق
 * MOSHAVERE_PORT_SPEC.md §4 - محتوای هر مدرسه مال خودش است، نه یک کتابخانه‌ی
 * مشترک پلتفرمی (تصمیم §2 همان سند).
 * تنها راه ورود سایر ماژول‌ها، اینترفیس {@code CurriculumFacade} است.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Curriculum"
)
package com.example.roshdeandishe.curriculum;
