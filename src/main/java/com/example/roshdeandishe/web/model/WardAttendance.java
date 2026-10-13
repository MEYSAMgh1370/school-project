package com.example.roshdeandishe.web.model;

/**
 * وضعیتِ امروزِ یک فرزند روی کارتِ «فرزندان من». {@code UNAVAILABLE}: مدرسه ماژولِ حضور و غیاب را ندارد یا فرزند
 * کلاس ندارد (چیزی نشان داده نمی‌شود)؛ {@code NOT_SCHOOL_DAY}: امروز روزِ درسی نیست؛ {@code PENDING}: حضور و غیابِ
 * کلاس هنوز گرفته نشده، پس «حاضر» نیست و حاضر هم شمرده نمی‌شود.
 */
public enum WardAttendance {
    UNAVAILABLE, NOT_SCHOOL_DAY, PENDING, PRESENT, LATE, ABSENT, ABSENT_EXCUSED
}
