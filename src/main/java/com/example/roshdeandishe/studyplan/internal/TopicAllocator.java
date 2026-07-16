package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;
import com.example.roshdeandishe.studyplan.internal.ScheduleSlicer.SlicedInterval;
import com.example.roshdeandishe.studyplan.internal.ScheduleSlicer.SlicedSchedule;
import com.example.roshdeandishe.studyplan.shared.StudyPlanError;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * پورت مستقیم {@code FillSlices} در table_suggester.py: زمان کل بلوک‌های
 * برش‌خورده را متناسب با weight هر درس در course_info تقسیم می‌کند، سپس
 * به‌صورت round-robin بین درس‌ها توزیع می‌کند تا صف نهاییِ «کدام سرفصل در
 * کدام بلوک» ساخته شود.
 * <p>
 * فیلد priority در {@link CourseWeight} عمداً اینجا مصرف نمی‌شود - در
 * منبع هم table_suggester.py هرگز priority را نمی‌خواند (فقط weight)،
 * پس این وفاداری به یک رفتار واقعیِ منبع است، نه یک نقص پورت.
 */
final class TopicAllocator {

    private record TopicAllocation(Long topicId, Map<Integer, Integer> intervals) {
    }

    record FilledInterval(int studyMinutes, LocalTime start, LocalTime end, Long topicId) {
    }

    record FilledSchedule(Map<String, List<FilledInterval>> byDay) {
    }

    FilledSchedule allocate(SlicedSchedule sliced, List<CourseWeight> priority) {
        Map<Integer, Integer> pool = new LinkedHashMap<>(sliced.durationCounts());

        int totalMinutes = 0;
        for (Map.Entry<Integer, Integer> e : pool.entrySet()) {
            totalMinutes += e.getKey() * e.getValue();
        }
        if (totalMinutes < 180) {
            throw new StudyPlanException(new StudyPlanError.ScheduleTooSmall(totalMinutes));
        }

        List<TopicAllocation> topicIntervals = new ArrayList<>();
        for (CourseWeight courseWeight : priority) {
            double timeNeeded = totalMinutes * courseWeight.weight();
            Map<Integer, Integer> intervals = computeIntervalsInTime(timeNeeded, pool);
            topicIntervals.add(new TopicAllocation(courseWeight.topicId(), intervals));
        }

        // باقیمانده‌ی هر سایز (به‌خاطر گرد شدن اعداد) به آخرین درس اضافه می‌شود - پورت مستقیم منبع
        if (!topicIntervals.isEmpty()) {
            TopicAllocation last = topicIntervals.get(topicIntervals.size() - 1);
            for (Map.Entry<Integer, Integer> e : pool.entrySet()) {
                if (e.getValue() > 0) {
                    last.intervals().merge(e.getKey(), e.getValue(), Integer::sum);
                    e.setValue(0);
                }
            }
        }

        Map<Integer, Deque<Long>> queueBySize = topicsQueue(topicIntervals, sliced.durationCounts().keySet());
        return fill(sliced, queueBySize);
    }

    private Map<Integer, Integer> computeIntervalsInTime(double timeNeeded, Map<Integer, Integer> pool) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Integer size : pool.keySet()) {
            int available = pool.get(size);
            if (available == 0) {
                continue;
            }
            long neededCount = Math.round(timeNeeded / size);
            if (neededCount <= available) {
                result.put(size, (int) neededCount);
                pool.put(size, available - (int) neededCount);
                timeNeeded = 0;
                break;
            } else {
                result.put(size, available);
                timeNeeded -= available * (double) size;
                pool.put(size, 0);
            }
        }
        return result;
    }

    private Map<Integer, Deque<Long>> topicsQueue(List<TopicAllocation> topicIntervals, java.util.Set<Integer> sizes) {
        Map<Integer, Deque<Long>> queueBySize = new LinkedHashMap<>();
        for (Integer size : sizes) {
            queueBySize.put(size, new ArrayDeque<>());
        }

        while (true) {
            int done = 0;
            for (TopicAllocation topic : topicIntervals) {
                if (topic.intervals().isEmpty()) {
                    done++;
                    continue;
                }
                Integer firstKey = topic.intervals().keySet().iterator().next();
                queueBySize.get(firstKey).addLast(topic.topicId());
                int remaining = topic.intervals().get(firstKey) - 1;
                if (remaining <= 0) {
                    topic.intervals().remove(firstKey);
                } else {
                    topic.intervals().put(firstKey, remaining);
                }
            }
            if (done >= topicIntervals.size()) {
                break;
            }
        }
        return queueBySize;
    }

    private FilledSchedule fill(SlicedSchedule sliced, Map<Integer, Deque<Long>> queueBySize) {
        Map<String, List<FilledInterval>> byDay = new LinkedHashMap<>();
        for (String day : WeekDays.ORDER) {
            List<FilledInterval> filledDay = new ArrayList<>();
            for (SlicedInterval interval : sliced.byDay().getOrDefault(day, List.of())) {
                Deque<Long> queue = queueBySize.get(interval.studyMinutes());
                Long topicId = (queue != null && !queue.isEmpty()) ? queue.pollFirst() : null;
                filledDay.add(new FilledInterval(interval.studyMinutes(), interval.start(), interval.end(), topicId));
            }
            byDay.put(day, filledDay);
        }
        return new FilledSchedule(byDay);
    }
}
