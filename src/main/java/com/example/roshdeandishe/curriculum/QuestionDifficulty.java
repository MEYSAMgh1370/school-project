package com.example.roshdeandishe.curriculum;

/**
 * سطح دشواری یک سؤال - پورت مستقیم پنج سطحِ moshavere
 * (0/10/20/30/40 = educational/easy/medium/hard/veryhard در
 * {@code QuestionFilter.DIFFICULTY_CHOICES}) به‌صورت enum، مطابق ترجیح
 * این codebase برای enum صریح به‌جای عدد جادویی (مثل ExpenseStatus).
 * روی سؤال nullable است، همان‌طور که در منبع بود (difficulty هنوز
 * ارزیابی نشده).
 */
public enum QuestionDifficulty {
    EDUCATIONAL,
    EASY,
    MEDIUM,
    HARD,
    VERY_HARD
}
