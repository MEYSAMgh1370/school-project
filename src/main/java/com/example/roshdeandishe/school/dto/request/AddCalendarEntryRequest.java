package com.example.roshdeandishe.school.dto.request;

import java.time.LocalDate;

/** {@code day} تاریخ میلادیِ ISO ({@code 2026-10-05})؛ {@code kind} یکی از CLOSED یا EVENT. */
public record AddCalendarEntryRequest(LocalDate day, String kind, String title) {
}
