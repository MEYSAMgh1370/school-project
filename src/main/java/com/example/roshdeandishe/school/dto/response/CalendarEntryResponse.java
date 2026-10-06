package com.example.roshdeandishe.school.dto.response;

import java.time.LocalDate;

public record CalendarEntryResponse(Long id, LocalDate day, String kind, String title) {
}
