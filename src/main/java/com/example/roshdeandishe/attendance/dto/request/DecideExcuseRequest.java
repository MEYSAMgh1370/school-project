package com.example.roshdeandishe.attendance.dto.request;

/** بدنه‌ی {@code POST .../excuses/{excuseId}/decision}؛ {@code decision}: APPROVED یا REJECTED. */
public record DecideExcuseRequest(String decision) {
}
