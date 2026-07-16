package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.DaySlot;
import com.example.roshdeandishe.studyplan.shared.StudyPlanError;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * پورت schedule_schema_validator + schedule_overlap_validator از
 * study_table/serializers.py: هر ۷ کد روز باید حاضر باشند، هر بازه باید
 * start&lt;end باشد و در قالب "HH:mm" پارس شود، و بازه‌های یک روز نباید
 * هم‌پوشانی داشته باشند.
 */
final class ScheduleValidator {

    private ScheduleValidator() {
    }

    static void validate(Map<String, List<DaySlot>> schedule) {
        if (schedule == null || !schedule.keySet().equals(WeekDays.CODES)) {
            throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                    "برنامه باید دقیقاً شامل هفت روز " + WeekDays.CODES + " باشد."
            ));
        }
        for (String day : WeekDays.ORDER) {
            List<DaySlot> slots = schedule.get(day);
            if (slots == null) {
                throw new StudyPlanException(new StudyPlanError.InvalidSchedule("روز " + day + " فهرست ندارد."));
            }
            List<LocalTime[]> parsed = new java.util.ArrayList<>();
            for (DaySlot slot : slots) {
                LocalTime start;
                LocalTime end;
                try {
                    start = LocalTime.parse(slot.start());
                    end = LocalTime.parse(slot.end());
                } catch (DateTimeParseException | NullPointerException e) {
                    throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                            "بازه‌ی زمانیِ نامعتبر در روز " + day + ": " + slot
                    ));
                }
                if (!start.isBefore(end)) {
                    throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                            "زمان شروع باید قبل از زمان پایان باشد (روز " + day + ")."
                    ));
                }
                for (LocalTime[] other : parsed) {
                    if (overlaps(start, end, other[0], other[1])) {
                        throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                                "بازه‌های زمانی روز " + day + " با هم هم‌پوشانی دارند."
                        ));
                    }
                }
                parsed.add(new LocalTime[]{start, end});
            }
        }
    }

    private static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return s2.isBefore(e1) && s1.isBefore(e2);
    }
}
