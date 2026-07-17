/**
 * ماژول Exam - موتور آزمون: آزمون‌های خودکار هفتگی (از سرفصل‌های
 * مطالعه‌شده‌ی اخیر) و آزمون‌های جمعیِ تألیف‌شده توسط معلم، جلسه‌ی
 * آزمون‌دهیِ زمان‌دار با توکن مخصوص، و امتیازدهی برخط. پورت‌شده از
 * moshavere ({@code exam-maker-master/azmoon}) طبق MOSHAVERE_PORT_SPEC.md
 * §5 - آخرین و عمیق‌ترین قدم پورت (به curriculum + academic + studyplan +
 * iam وابسته است، یگانه جایی در این پورت که یک ماژول تازه به ماژول تازه‌ی
 * دیگری وابسته است).
 * <p>
 * تنها راه ورود سایر ماژول‌ها، اینترفیس {@code ExamFacade} است.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Exam"
)
package com.example.roshdeandishe.exam;
