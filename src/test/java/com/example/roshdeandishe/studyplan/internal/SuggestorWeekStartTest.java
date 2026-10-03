package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.TimeRange;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.Weekday;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W9: کلیدهای برنامه‌ی زمانی «روز واقعی»اند و تاریخ هر بازه از روز شروع هفته‌ی
 * مدرسه می‌آید. مدرسه‌ی شنبه‌محور باید دقیقاً همان خروجی پیش از W9 را بدهد.
 */
class SuggestorWeekStartTest {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    private static Map<Weekday, List<TimeRange>> schedule() {
        Map<Weekday, List<TimeRange>> schedule = new LinkedHashMap<>();
        for (Weekday day : Weekday.values()) {
            schedule.put(day, List.of());
        }
        // دو بازه‌ی سه‌ساعته: یکی شنبه، یکی دوشنبه
        schedule.put(Weekday.SAT, List.of(new TimeRange("09:00", "12:00")));
        schedule.put(Weekday.MON, List.of(new TimeRange("09:00", "12:00")));
        return schedule;
    }

    private static List<CourseWeight> weights() {
        return List.of(new CourseWeight(1L, 1.0, 1));
    }

    private static Map<DayOfWeek, Integer> countByDay(List<Suggestor.ProposedInterval> proposed) {
        Map<DayOfWeek, Integer> counts = new EnumMap<>(DayOfWeek.class);
        for (Suggestor.ProposedInterval interval : proposed) {
            counts.merge(interval.start().getDayOfWeek(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void saturdayWeekPlacesSaturdayAndMondayBlocksOnTheirRealDates() {
        List<Suggestor.ProposedInterval> proposed = Suggestor.suggest(
                SATURDAY, Weekday.weekStartingOn(DayOfWeek.SATURDAY), schedule(), weights(), List.of());

        assertThat(proposed).isNotEmpty();
        assertThat(countByDay(proposed).keySet()).containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.MONDAY);
        assertThat(proposed).allSatisfy(interval -> {
            LocalDate date = interval.start().toLocalDate();
            assertThat(date).isBetween(SATURDAY, SATURDAY.plusDays(6));
        });
        // نخستین بازه روز شروع هفته است
        assertThat(proposed.get(0).start().toLocalDate()).isEqualTo(SATURDAY);
    }

    @Test
    void mondayWeekPlacesTheSameKeysOnTheirRealDatesStartingFromMonday() {
        List<Suggestor.ProposedInterval> proposed = Suggestor.suggest(
                MONDAY, Weekday.weekStartingOn(DayOfWeek.MONDAY), schedule(), weights(), List.of());

        assertThat(proposed).isNotEmpty();
        // «SAT» هنوز شنبه است: در هفته‌ی دوشنبه‌محور پنج روز بعد از دوشنبه
        assertThat(countByDay(proposed).keySet()).containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.MONDAY);
        assertThat(proposed).allSatisfy(interval -> {
            LocalDate date = interval.start().toLocalDate();
            assertThat(date).isBetween(MONDAY, MONDAY.plusDays(6));
        });
        assertThat(proposed.get(0).start().toLocalDate()).isEqualTo(MONDAY);
        assertThat(proposed.stream().filter(i -> i.start().getDayOfWeek() == DayOfWeek.SATURDAY)
                .map(i -> i.start().toLocalDate()).distinct().toList())
                .containsExactly(MONDAY.plusDays(5));
    }

    @Test
    void weekOrderStartsAtTheGivenDayAndCoversAllSeven() {
        assertThat(Weekday.weekStartingOn(DayOfWeek.SATURDAY)).containsExactly(Weekday.values());
        assertThat(Weekday.weekStartingOn(DayOfWeek.MONDAY)).containsExactly(
                Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI, Weekday.SAT, Weekday.SUN);
        for (Weekday day : Weekday.values()) {
            assertThat(Weekday.of(day.dayOfWeek())).isEqualTo(day);
        }
    }
}
