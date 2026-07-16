package com.example.roshdeandishe.studyplan.internal;

import java.time.LocalTime;

/**
 * یک بلوک خالی مطالعه که از برش زمان آزاد درمی‌آید و بعداً FillSlices به
 * آن سرفصل تخصیص می‌دهد - عمداً کلاس قابل‌تغییر، نه record: همان دیکشنری
 * قابل‌تغییری که در moshavere بین SliceScheduleInterval و FillSlices دست
 * به دست می‌شد.
 */
final class StudyBlock {

    final int minutes;
    final LocalTime start;
    final LocalTime end;
    Long topicId;

    StudyBlock(int minutes, LocalTime start, LocalTime end) {
        this.minutes = minutes;
        this.start = start;
        this.end = end;
    }
}
