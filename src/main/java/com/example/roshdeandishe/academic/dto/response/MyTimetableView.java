package com.example.roshdeandishe.academic.dto.response;

import java.util.List;

/** یک «دید» از برنامه برای صفحه‌ی من: کلاسِ خودم، کلاسِ یکی از فرزندان، یا ساعت‌های تدریسِ خودم. */
public record MyTimetableView(String kind, Long studentId, String studentName, Long classId,
                              String className, List<EntryResponse> entries) {
}
