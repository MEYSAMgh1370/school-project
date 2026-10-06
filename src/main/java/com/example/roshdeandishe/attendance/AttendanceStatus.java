package com.example.roshdeandishe.attendance;

/**
 * فقط استثناها ذخیره می‌شوند؛ دانش‌آموزِ یک حضور و غیابِ ثبت‌شده که مُهری ندارد «حاضر» است. {@code LATE}
 * در درصدِ حضور حاضر حساب می‌شود و در گزارش جدا نشان داده می‌شود.
 */
public enum AttendanceStatus {
    ABSENT, LATE
}
