package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.RecordableClass;

/** {@code taken}: حضور و غیابِ این کلاس در روزِ خواسته‌شده گرفته شده. */
public record RecordableClassResponse(Long classId, String name, String section, int gradeLevel, boolean taken) {

    public static RecordableClassResponse from(RecordableClass c) {
        return new RecordableClassResponse(c.classId(), c.name(), c.section(), c.gradeLevel(), c.taken());
    }
}
