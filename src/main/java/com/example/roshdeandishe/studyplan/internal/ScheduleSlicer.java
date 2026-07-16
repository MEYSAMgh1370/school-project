package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.DaySlot;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * پورت مستقیم {@code SliceScheduleInterval} در table_suggester.py: هر
 * بازه‌ی زمانیِ آزادِ یک روز را به بلوک‌های مطالعه/استراحتِ ثابت
 * (۹۰/۱۵، ۷۵/۱۵، ۶۰/۱۰، ۴۵/۱۰، ۳۰/۵ دقیقه) برش می‌زند - همیشه بزرگ‌ترین
 * سایز را اول امتحان می‌کند.
 * <p>
 * انحراف عمدی از منبع: در پایتون {@code self.intervals_info = ALLOWED_INTERVALS}
 * دیکشنری سطح ماژول را با رفرنس (نه کپی) می‌گیرد، یعنی همه‌ی
 * instanceهای SliceScheduleInterval یک شمارنده‌ی مشترک و نشتی بین
 * درخواست‌ها دارند - یک باگ واقعی در منبع، نه رفتار عمدی. اینجا هر
 * فراخوانیِ {@link #slice(Map)} یک شمارنده‌ی تازه می‌سازد.
 */
final class ScheduleSlicer {

    private record Duration(int studyMinutes, int restMinutes) {
    }

    private static final List<Duration> ALLOWED = List.of(
            new Duration(90, 15), new Duration(75, 15), new Duration(60, 10),
            new Duration(45, 10), new Duration(30, 5)
    );

    record SlicedInterval(int studyMinutes, LocalTime start, LocalTime end) {
    }

    record SlicedSchedule(Map<String, List<SlicedInterval>> byDay, Map<Integer, Integer> durationCounts) {
    }

    SlicedSchedule slice(Map<String, List<DaySlot>> schedule) {
        Map<Integer, Integer> durationCounts = new LinkedHashMap<>();
        for (Duration d : ALLOWED) {
            durationCounts.put(d.studyMinutes(), 0);
        }

        Map<String, List<SlicedInterval>> byDay = new LinkedHashMap<>();
        for (String day : WeekDays.ORDER) {
            List<DaySlot> daySlots = schedule.getOrDefault(day, List.of());
            List<SlicedInterval> slicedDay = new ArrayList<>();
            for (DaySlot slot : daySlots) {
                slicedDay.addAll(sliceInterval(LocalTime.parse(slot.start()), LocalTime.parse(slot.end()), durationCounts));
            }
            byDay.put(day, slicedDay);
        }
        return new SlicedSchedule(byDay, durationCounts);
    }

    private List<SlicedInterval> sliceInterval(LocalTime start, LocalTime end, Map<Integer, Integer> durationCounts) {
        List<SlicedInterval> result = new ArrayList<>();
        int totalMinutes = (int) java.time.Duration.between(start, end).toMinutes();
        LocalTime t = start;

        for (Duration d : ALLOWED) {
            int rest = d.restMinutes();
            // آخرین بلوک ممکن: اگر دقیقاً هم‌اندازه‌ی زمان مطالعه است، استراحت لازم نیست
            int x = (totalMinutes == d.studyMinutes()) ? 0 : rest;
            int intervalCount = totalMinutes / (d.studyMinutes() + x);

            for (int i = 0; i < intervalCount; i++) {
                LocalTime blockEnd = t.plusMinutes(d.studyMinutes());
                result.add(new SlicedInterval(d.studyMinutes(), t, blockEnd));
                // مطابق منبع، همیشه استراحتِ کامل اضافه می‌شود (نه x)، حتی برای آخرین بلوک
                t = t.plusMinutes(d.studyMinutes()).plusMinutes(rest);
                totalMinutes -= d.studyMinutes();
                totalMinutes -= x;
                durationCounts.merge(d.studyMinutes(), 1, Integer::sum);
            }
        }
        return result;
    }
}
