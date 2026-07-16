package com.example.roshdeandishe.studyplan.internal;

import java.util.List;
import java.util.Set;

/**
 * ترتیب هفته‌ی ایرانی (شنبه تا جمعه) و کدهای روزِ پورت‌شده مستقیم از
 * منبع (default_schedule در study_table/views.py:
 * {"sat","sun","mon","tue","wed","thur","fri"}). این ترتیب هم برای
 * اعتبارسنجی شمای برنامه و هم برای تعیین تاریخِ واقعیِ هر روز نسبت به
 * weekStartDate (که باید شنبه باشد) استفاده می‌شود.
 */
final class WeekDays {

    static final List<String> ORDER = List.of("sat", "sun", "mon", "tue", "wed", "thur", "fri");
    static final Set<String> CODES = Set.copyOf(ORDER);

    private WeekDays() {
    }
}
