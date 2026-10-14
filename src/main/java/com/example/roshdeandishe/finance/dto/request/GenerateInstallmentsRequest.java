package com.example.roshdeandishe.finance.dto.request;

import java.time.LocalDate;

/** {@code firstDueDate} به شکل ISO (yyyy-MM-dd)؛ {@code intervalMonths} خالی = ۱ ماه. */
public record GenerateInstallmentsRequest(Integer count, LocalDate firstDueDate, Integer intervalMonths) {
}
