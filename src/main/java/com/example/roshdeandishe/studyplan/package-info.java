/**
 * ماژول StudyPlan - برنامه‌ی درسی هر مدرسه (وزن‌دهی درس‌ها)، سرفصل‌های
 * هفتگی، تخصیص برنامه به دانش‌آموز، بازه‌های مطالعه و الگوریتم پیشنهاد
 * خودکار جدول مطالعه. پورت‌شده از moshavere
 * (exam-maker-master/{plan,study_table}) طبق MOSHAVERE_PORT_SPEC.md §6 -
 * محتوای هر مدرسه مال خودش است (تصمیم §2 همان سند).
 * تنها راه ورود سایر ماژول‌ها، اینترفیس {@code StudyPlanFacade} است -
 * از جمله ماژول exam آینده که {@code getRecentTopics} را برای ساخت آزمون
 * هفتگی صدا خواهد زد (§5 همان سند).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "StudyPlan"
)
package com.example.roshdeandishe.studyplan;
