/**
 * ماژول StudyPlan - برنامه‌ی مطالعاتی هر مدرسه (وزن‌دهی درس‌ها) + الگوریتم
 * پیشنهاد خودکارِ زمان مطالعه. پورت‌شده از moshavere
 * (agency-panel-master/plan, exam-maker-master/plan+study_table) طبق
 * MOSHAVERE_PORT_SPEC.md §6 - محتوای هر مدرسه مال خودش است، نه یک کتابخانه‌ی
 * مشترک پلتفرمی (تصمیم §2 همان سند).
 * تنها راه ورود سایر ماژول‌ها (مثل exam)، اینترفیس {@code StudyPlanFacade} است.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Study Plan"
)
package com.example.roshdeandishe.studyplan;
