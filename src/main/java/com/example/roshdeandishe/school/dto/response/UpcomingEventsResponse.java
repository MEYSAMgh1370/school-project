package com.example.roshdeandishe.school.dto.response;

import java.time.LocalDate;
import java.util.List;

/** {@code daysLeft}: روز تا رویداد در تقویمِ محلیِ مدرسه (۰ = امروز). */
public record UpcomingEventsResponse(List<Event> events) {

    public record Event(Long id, LocalDate day, String title, int daysLeft) {
    }
}
