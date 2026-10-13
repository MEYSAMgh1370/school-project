/**
 * ماژول QuestionBox (باکس سؤالات، openspec/changes/question-box.md).
 * مسئولیت: سؤال‌های معلم (چهارگزینه‌ای و تشریحی، متنی یا فقط‌تصویر) با تصویر، سربرگ‌های قابل‌استفاده‌ی مجدد و برگه‌های
 * چاپیِ ذخیره‌شده. سؤال‌های بانک را فقط از راه {@code CurriculumFacade} و نقش‌های فعالِ سازنده را فقط از راه
 * {@code IamFacade} می‌خواند. تنها راه ورود سایر ماژول‌ها، اینترفیس {@code QuestionBoxFacade} است.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Question Box"
)
package com.example.roshdeandishe.questionbox;
