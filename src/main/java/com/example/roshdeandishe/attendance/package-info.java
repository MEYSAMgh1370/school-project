/**
 * ماژول Attendance (حضور و غیاب، openspec/changes/attendance.md).
 * مسئولیت: یک «حضور و غیاب» به‌ازای هر کلاس در هر روزِ درسی؛ فقط استثناها (غایب/تأخیر) ذخیره
 * می‌شوند. تنها راه ورود سایر ماژول‌ها، اینترفیس {@code AttendanceFacade} است. کلاس و دانش‌آموز را از
 * academic، روزِ درسی/تقویم/سال جاری را از school و نام و مجوز را از iam، همه از طریق facadeهای عمومی، می‌گیرد.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Attendance"
)
package com.example.roshdeandishe.attendance;
