package com.example.roshdeandishe.web.model;

import java.util.List;

/** «کلاس‌های من»ِ یک معلم در یک مدرسه؛ فهرستِ خالی یعنی هنوز کلاسی به او واگذار نشده. */
public record TeacherClassesRow(Long schoolId, String schoolName, List<TeacherClassRow> classes) {

    public boolean empty() {
        return classes.isEmpty();
    }
}
