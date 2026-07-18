package com.example.roshdeandishe.academic;

/**
 * شِمای نمره‌دهیِ یک کلاس - در سطح ClassEntity تعیین می‌شود (نه مدرسه یا پایه‌ی
 * تحصیلی)، چون یک مدرسه‌ی واحد هم‌زمان کلاس‌های ابتدایی (توصیفی) و کلاس‌های
 * بالاتر (عددی) دارد و ClassEntity فعلاً هیچ فیلد gradeLevel ای ندارد که
 * بشود این تصمیم را از روی آن مشتق کرد (CLAUDE.md §11 Q8).
 */
public enum GradingScheme {
    DESCRIPTIVE,
    NUMERIC
}
