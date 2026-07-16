package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.DaySlot;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.WeekCourseInfo;
import com.example.roshdeandishe.studyplan.internal.TopicAllocator.FilledInterval;
import com.example.roshdeandishe.studyplan.internal.TopicAllocator.FilledSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * پورت مستقیم {@code Suggestor} در table_suggester.py: هماهنگ‌کننده‌ی
 * ScheduleSlicer + TopicAllocator + لنگر انداختنِ بلوک‌های تولیدشده روی
 * تاریخ واقعیِ هفته.
 * <p>
 * انحراف عمدی از منبع: {@code Suggestor.create_study_intervals} روزها را
 * با شاخص‌های به‌هم‌ریخته [۲،۳،۱،۴،۵،۶،۰] پردازش می‌کرد که برای هفته‌ای که
 * صراحتاً باید از شنبه شروع شود (همان‌طور که suggest() در views.py
 * اعتبارسنجی می‌کند) ترتیب واقعی روزها را به‌هم می‌زد - این خودِ فایل منبع
 * هم با کامنت‌های "FIXME: code needs cleaning" علامت‌گذاری شده بود. اینجا
 * ترتیبِ درستِ تقویمی پورت شده: روز i-ام در {@link WeekDays#ORDER}
 * (شنبه=۰) دقیقاً {@code weekStartDate.plusDays(i)} است.
 */
final class WeekSuggester {

    record StudyIntervalCreationCommand(Long topicId, List<Long> subTopicIds, LocalDateTime start, LocalDateTime end) {
    }

    private final ScheduleSlicer slicer = new ScheduleSlicer();
    private final TopicAllocator allocator = new TopicAllocator();

    List<StudyIntervalCreationCommand> suggest(
            Map<String, List<DaySlot>> effectiveSchedule,
            List<CourseWeight> courseInfo,
            List<WeekCourseInfo> weekCoursesForWeek,
            LocalDate weekStartDate
    ) {
        FilledSchedule filled = allocator.allocate(slicer.slice(effectiveSchedule), courseInfo);

        List<StudyIntervalCreationCommand> commands = new ArrayList<>();
        for (int i = 0; i < WeekDays.ORDER.size(); i++) {
            String day = WeekDays.ORDER.get(i);
            LocalDate date = weekStartDate.plusDays(i);
            for (FilledInterval interval : filled.byDay().getOrDefault(day, List.of())) {
                if (interval.topicId() == null) {
                    continue;
                }
                List<Long> subTopicIds = weekCoursesForWeek.stream()
                        .filter(wc -> wc.topicId().equals(interval.topicId()))
                        .findFirst()
                        .map(WeekCourseInfo::subTopicIds)
                        .orElse(List.of());
                commands.add(new StudyIntervalCreationCommand(
                        interval.topicId(),
                        subTopicIds,
                        LocalDateTime.of(date, interval.start()),
                        LocalDateTime.of(date, interval.end())
                ));
            }
        }
        return commands;
    }
}
