package com.example.roshdeandishe.attendance;

import java.time.Instant;
import java.time.LocalDate;

/**
 * منتشر می‌شود وقتی {@code AttendanceFacade.saveRoll} موفق شد، برای هر دانش‌آموزی که در همین ذخیره
 * <b>تازه</b> غایب ثبت شده (نه ذخیره‌ی دوباره‌ی بی‌تغییر، نه دیرآمده). تنها مصرف‌کننده‌ی فعلی ماژول
 * notification است که به ولی‌های لینک‌شده پیام می‌دهد؛ چون در پکیج بیرونی است هر ماژول دیگری هم
 * می‌تواند بدون تغییر آن را بشنود (الگوی InvoiceIssuedEvent). {@code actorUserId} می‌تواند تهی باشد.
 */
public record AbsenceRecordedEvent(
        Long schoolId,
        Long classId,
        Long studentId,
        LocalDate day,
        Long actorUserId,
        Instant occurredAt
) {
}
