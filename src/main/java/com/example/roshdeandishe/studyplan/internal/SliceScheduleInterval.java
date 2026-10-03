package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.TimeRange;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.Weekday;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * برش زمان‌های آزاد هفته به بلوک‌های مطالعه/استراحت با اندازه‌های ثابت -
 * پورت مستقیم SliceScheduleInterval موshavere (table_suggester.py):
 * جفت‌های ۹۰/۱۵، ۷۵/۱۵، ۶۰/۱۰، ۴۵/۱۰، ۳۰/۵ دقیقه (مطالعه/استراحت)، به
 * همین ترتیب حریصانه، با همان قاعده‌ی «بازه‌ی آخر بدون استراحت» (وقتی
 * باقیمانده دقیقاً برابر طول مطالعه است).
 * <p>
 * دو نرمال‌سازی عمدی نسبت به مبدأ: شمارنده‌ی بلوک‌ها ({@code intervalsInfo})
 * per-run است نه دیکشنری سطح ماژول (در moshavere آن دیکشنری global بین
 * اجراها نشت می‌کرد - باگ، نه رفتار)، و روزها به ترتیب هفته‌ی مدرسه (شنبه‌محور:
 * شنبه تا جمعه) پیموده می‌شوند (ترتیب fri-اول مبدأ فقط عارضه‌ی مرتب‌سازی کلیدهای jsonb پستگرس
 * بود - رجوع به Weekday در StudyPlanFacade).
 */
final class SliceScheduleInterval {

    static final int[][] ALLOWED_INTERVAL_TIME = {{90, 15}, {75, 15}, {60, 10}, {45, 10}, {30, 5}};

    private final Map<Weekday, List<TimeRange>> schedule;
    private final List<Weekday> weekOrder;
    private final LinkedHashMap<Integer, Integer> intervalsInfo = new LinkedHashMap<>();

    /**
     * @param weekOrder ترتیبِ پیمایش روزها: روزِ اولِ هفته‌ی مدرسه تا آخر
     *                  ({@link Weekday#weekStartingOn})
     */
    SliceScheduleInterval(Map<Weekday, List<TimeRange>> schedule, List<Weekday> weekOrder) {
        this.schedule = schedule;
        this.weekOrder = weekOrder;
        for (int[] pair : ALLOWED_INTERVAL_TIME) {
            intervalsInfo.put(pair[0], 0);
        }
    }

    /**
     * برش کل هفته - بلوک‌های خالی هر روز به ترتیب هفته‌ی مدرسه (شنبه‌محور: شنبه تا جمعه).
     */
    LinkedHashMap<Weekday, List<StudyBlock>> sliceSchedule() {
        LinkedHashMap<Weekday, List<StudyBlock>> byDay = new LinkedHashMap<>();
        for (Weekday day : weekOrder) {
            List<StudyBlock> blocks = new ArrayList<>();
            for (TimeRange range : schedule.getOrDefault(day, List.of())) {
                blocks.addAll(sliceInterval(range));
            }
            byDay.put(day, blocks);
        }
        return byDay;
    }

    /**
     * تعداد بلوک‌های ساخته‌شده به تفکیک اندازه (۹۰..۳۰، به همین ترتیب) -
     * ترتیب پیمایش این map در FillSlices معنادار است.
     */
    LinkedHashMap<Integer, Integer> intervalsInfo() {
        return intervalsInfo;
    }

    private List<StudyBlock> sliceInterval(TimeRange range) {
        LocalTime start = parse(range.start());
        LocalTime end = parse(range.end());
        int totalTime = (end.getHour() * 60 + end.getMinute()) - (start.getHour() * 60 + start.getMinute());

        LocalTime t = start;
        List<StudyBlock> blocks = new ArrayList<>();
        for (int[] pair : ALLOWED_INTERVAL_TIME) {
            int study = pair[0];
            int rest = pair[1];
            // قاعده‌ی «بازه‌ی آخر»: اگر باقیمانده دقیقاً یک بلوک مطالعه باشد،
            // استراحت در محاسبه صفر می‌شود تا همان بلوک هم جا شود
            int x = (totalTime == study) ? 0 : rest;
            int intervalCount = totalTime / (study + x);
            for (int i = 0; i < intervalCount; i++) {
                blocks.add(new StudyBlock(study, t, t.plusMinutes(study)));
                t = t.plusMinutes(study + rest);
                totalTime -= study + x;
                intervalsInfo.merge(study, 1, Integer::sum);
            }
        }
        return blocks;
    }

    private static LocalTime parse(String hhmm) {
        String[] parts = hhmm.split(":");
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
