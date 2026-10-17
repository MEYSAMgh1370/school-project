package com.example.roshdeandishe.notification.internal;

/**
 * موضوعِ یک پرسش، ساخته‌شده فقط از داده‌ی سرور ({@link SubjectResolver}). شناسه‌ی داخل callback_data
 * فقط «انتخابِ» کاربر است و همیشه با فهرستِ همین هدف‌ها دوباره سنجیده می‌شود.
 */
sealed interface BotTarget {

    /** studentId یا schoolId. */
    long id();

    /**
     * یک دانش‌آموز؛ {@code ward=false} یعنی ثبت‌نامِ خودِ کاربر. classId وقتی دانش‌آموز کلاس ندارد null است.
     */
    record Student(Long schoolId, Long studentId, Long studentUserId, Long classId, String name,
                   String schoolName, boolean ward) implements BotTarget {
        @Override
        public long id() {
            return studentId;
        }
    }

    /** یک مدرسه. */
    record School(Long schoolId, String schoolName) implements BotTarget {
        @Override
        public long id() {
            return schoolId;
        }
    }
}
