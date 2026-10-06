package com.example.roshdeandishe.school.dto.response;

import java.time.LocalDate;

public record AcademicYearResponse(Long id, String code, LocalDate startDate, LocalDate endDate) {
}
