package com.example.roshdeandishe.academic;

/**
 * وضعیتِ یک دانش‌آموز در مدرسه (openspec/changes/academic-year.md، فاز ۳). {@code ACTIVE} در کلاسی است؛
 * {@code PENDING} ارتقا یافته و کلاسش هنوز انتخاب نشده؛ {@code GRADUATED} آخرین پایه را تمام کرده؛
 * {@code LEFT} مدرسه را ترک کرده. در هر چهار حالت هیچ داده‌ای (نمره، فاکتور، ولی) حذف نمی‌شود.
 */
public enum StudentStatus {
    ACTIVE, PENDING, GRADUATED, LEFT
}
