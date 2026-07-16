package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.TimeRange;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.Weekday;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * هماهنگ‌کننده‌ی پیشنهاد خودکار جدول مطالعه‌ی یک هفته - پورت Suggestor
 * موshavere (table_suggester.py): برش برنامه‌ی زمانی
 * ({@link SliceScheduleInterval})، تخصیص وزنی سرفصل‌ها
 * ({@link FillSlices})، سپس ساخت بازه‌های تاریخ‌دار شنبه تا جمعه.
 * زیرسرفصل‌های هر بازه از WeekCourseهای «فعال» برنامه می‌آیند: آن‌هایی که
 * تاریخشان در بازه‌ی (weekStart-۷، weekStart] است - همان فیلتر
 * {@code 0 <= dt < 7} مبدأ.
 * <p>
 * منطق خالص بدون repository - persistence با StudyPlanFacadeImpl است تا
 * الگوریتم بدون Spring قابل unit-test بماند.
 */
final class Suggestor {

    private Suggestor() {
    }

    record WeekCourseData(Long courseTopicId, LocalDate date, List<Long> subTopicIds) {
    }

    record ProposedInterval(Long topicId, List<Long> subTopicIds, LocalDateTime start, LocalDateTime end) {
    }

    static List<ProposedInterval> suggest(LocalDate weekStart,
                                          Map<Weekday, List<TimeRange>> schedule,
                                          List<CourseWeight> priorities,
                                          List<WeekCourseData> weekCourses) {
        SliceScheduleInterval slicer = new SliceScheduleInterval(schedule);
        LinkedHashMap<Weekday, List<StudyBlock>> blocksByDay = slicer.sliceSchedule();
        new FillSlices(blocksByDay, slicer.intervalsInfo(), priorities).fill();

        List<WeekCourseData> active = weekCourses.stream()
                .filter(wc -> {
                    long dt = ChronoUnit.DAYS.between(wc.date(), weekStart);
                    return dt >= 0 && dt < 7;
                })
                .toList();

        List<ProposedInterval> proposed = new ArrayList<>();
        int dayOffset = 0;
        for (List<StudyBlock> dayBlocks : blocksByDay.values()) {
            LocalDate date = weekStart.plusDays(dayOffset++);
            for (StudyBlock block : dayBlocks) {
                if (block.topicId == null) {
                    // صف سرفصل این اندازه زودتر تمام شده - همان try/except pass
                    // مبدأ: بلوک بدون سرفصل persist نمی‌شود
                    continue;
                }
                List<Long> subTopics = active.stream()
                        .filter(wc -> wc.courseTopicId().equals(block.topicId))
                        .findFirst()
                        .map(WeekCourseData::subTopicIds)
                        .orElse(List.of());
                proposed.add(new ProposedInterval(block.topicId, subTopics,
                        toDateTime(date, block.start), toDateTime(date, block.end)));
            }
        }
        return proposed;
    }

    private static LocalDateTime toDateTime(LocalDate date, LocalTime time) {
        return date.atTime(time);
    }
}
