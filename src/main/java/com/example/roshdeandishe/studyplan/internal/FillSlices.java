package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.Weekday;
import com.example.roshdeandishe.studyplan.shared.StudyPlanError;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * تخصیص بلوک‌های خالی به سرفصل‌ها بر اساس وزن هر درس در برنامه - پورت
 * مستقیم FillSlices موshavere (table_suggester.py) با حفظ دقیق رفتار:
 * <ul>
 * <li>سهم زمانی هر درس = کل زمان × وزن؛ تبدیل زمان به بلوک با گرد کردن
 * half-to-even پایتون ({@link Math#rint}) - نه half-up جاوا.</li>
 * <li>بلوک‌های باقیمانده‌ی هر اندازه، یکی (فقط یکی، همان quirk مبدأ) به
 * آخرین درسِ فهرست اضافه و شمارنده صفر می‌شود.</li>
 * <li>صف‌بندی round-robin: هر درس در هر دور یک بلوک از اولین اندازه‌ی
 * باقیمانده‌اش به صف آن اندازه می‌دهد - ترتیب درج (LinkedHashMap) همان
 * ترتیب dict پایتون است.</li>
 * <li>کمتر از ۱۸۰ دقیقه زمان آزاد - ScheduleTooSmall (همان Exception
 * مبدأ).</li>
 * </ul>
 */
final class FillSlices {

    private final LinkedHashMap<Weekday, List<StudyBlock>> emptyIntervals;
    private final LinkedHashMap<Integer, Integer> intervalsInfo;
    private final List<CourseWeight> priority;

    FillSlices(LinkedHashMap<Weekday, List<StudyBlock>> emptyIntervals,
               LinkedHashMap<Integer, Integer> intervalsInfo,
               List<CourseWeight> priority) {
        this.emptyIntervals = emptyIntervals;
        this.intervalsInfo = intervalsInfo;
        this.priority = priority;
    }

    /**
     * سرفصل هر بلوک را در جا set می‌کند؛ بلوکی که صف اندازه‌اش زودتر خالی
     * شود بدون سرفصل می‌ماند (همان try/except pass مبدأ) و بعداً persist
     * نمی‌شود.
     */
    void fill() {
        Map<Integer, Deque<Long>> topicQueue = topicsQueue();
        for (List<StudyBlock> dayBlocks : emptyIntervals.values()) {
            for (StudyBlock block : dayBlocks) {
                Deque<Long> queue = topicQueue.get(block.minutes);
                if (queue != null && !queue.isEmpty()) {
                    block.topicId = queue.pollFirst();
                }
            }
        }
    }

    private LinkedHashMap<Integer, Integer> computeIntervalsInTime(double timeNeeded) {
        LinkedHashMap<Integer, Integer> interval = new LinkedHashMap<>();
        for (Integer size : List.copyOf(intervalsInfo.keySet())) {
            int available = intervalsInfo.get(size);
            if (available == 0) {
                continue;
            }
            int wanted = (int) Math.rint(timeNeeded / size);
            if (wanted <= available) {
                // ممکن است wanted صفر باشد - کلید با شمارش صفر می‌ماند، همان
                // حالتی که topicsQueue برایش چک «تک‌کلید صفر» دارد
                interval.merge(size, wanted, Integer::sum);
                intervalsInfo.put(size, available - wanted);
                break;
            } else {
                interval.merge(size, available, Integer::sum);
                timeNeeded -= (double) available * size;
                intervalsInfo.put(size, 0);
            }
        }
        return interval;
    }

    private List<TopicIntervals> computeIntervalPerTopic() {
        int totalTime = 0;
        for (Map.Entry<Integer, Integer> entry : intervalsInfo.entrySet()) {
            totalTime += entry.getKey() * entry.getValue();
        }
        if (totalTime < 180) {
            throw new StudyPlanException(new StudyPlanError.ScheduleTooSmall(totalTime));
        }

        List<TopicIntervals> topicIntervals = new ArrayList<>();
        for (CourseWeight courseWeight : priority) {
            double timeNeeded = totalTime * courseWeight.weight();
            topicIntervals.add(new TopicIntervals(courseWeight.topicId(), computeIntervalsInTime(timeNeeded)));
        }

        // باقیمانده‌ها به آخرین درس - به‌ازای هر اندازه فقط «یک» بلوک اضافه
        // می‌شود ولی شمارنده کامل صفر می‌شود (quirk مبدأ، عمداً حفظ شده)
        LinkedHashMap<Integer, Integer> lastIntervals = topicIntervals.get(topicIntervals.size() - 1).intervals;
        for (Integer size : intervalsInfo.keySet()) {
            if (intervalsInfo.get(size) > 0) {
                lastIntervals.merge(size, 1, Integer::sum);
                intervalsInfo.put(size, 0);
            }
        }
        return topicIntervals;
    }

    private Map<Integer, Deque<Long>> topicsQueue() {
        List<TopicIntervals> topicIntervals = computeIntervalPerTopic();
        Map<Integer, Deque<Long>> topicQueue = new LinkedHashMap<>();
        for (Integer size : intervalsInfo.keySet()) {
            topicQueue.put(size, new ArrayDeque<>());
        }
        while (true) {
            int done = 0;
            for (TopicIntervals topic : topicIntervals) {
                if (topic.intervals.size() == 1
                        && topic.intervals.values().iterator().next() == 0) {
                    done++;
                    continue;
                }
                if (topic.intervals.isEmpty()) {
                    done++;
                    continue;
                }
                Iterator<Map.Entry<Integer, Integer>> it = topic.intervals.entrySet().iterator();
                Map.Entry<Integer, Integer> first = it.next();
                topicQueue.get(first.getKey()).addLast(topic.topicId);
                int remaining = first.getValue() - 1;
                if (remaining <= 0) {
                    it.remove();
                } else {
                    first.setValue(remaining);
                }
            }
            if (done >= topicIntervals.size()) {
                break;
            }
        }
        return topicQueue;
    }

    private static final class TopicIntervals {
        final Long topicId;
        final LinkedHashMap<Integer, Integer> intervals;

        TopicIntervals(Long topicId, LinkedHashMap<Integer, Integer> intervals) {
            this.topicId = topicId;
            this.intervals = intervals;
        }
    }
}
