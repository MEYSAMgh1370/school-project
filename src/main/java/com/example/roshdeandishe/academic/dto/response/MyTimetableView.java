package com.example.roshdeandishe.academic.dto.response;

import java.util.List;

public record MyTimetableView(String kind, Long studentId, String studentName, Long classId,
                              String className, List<EntryResponse> entries) {
}
