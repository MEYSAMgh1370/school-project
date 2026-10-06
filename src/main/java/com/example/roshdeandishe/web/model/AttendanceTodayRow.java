package com.example.roshdeandishe.web.model;

/**
 * «حضور و غیاب امروز» یک مدرسه برای پیشخوانِ مدیر (openspec/changes/attendance.md، «The admin figure»).
 * <p>
 * فقط عددهای خلاصه است، نه فهرستِ دانش‌آموزان؛ نام‌ها در صفحه‌ی گزارش می‌مانند. «امروز» را
 * {@link AttendanceTodayAssembler} به وقتِ مدرسه می‌سازد.
 *
 * @param schoolDay      آیا امروز روزِ درسیِ مدرسه است (روزِ تعطیلِ تقویم یا آخرِ هفته نیست)
 * @param absent         شمارِ غایب‌ها فقط در کلاس‌هایی که حضور و غیابشان گرفته شده
 * @param presentPercent عددِ صحیحِ ۰ تا ۱۰۰ (گردشده به پایین)؛ {@code null} یعنی رقمی نیست
 *                       (روزِ غیردرسی یا هنوز هیچ کلاسی ثبت نشده)
 * @param classesTaken   کلاس‌هایی که امروز حضور و غیابشان گرفته شده
 * @param classesTotal   همه‌ی کلاس‌های سالِ جاری
 */
public record AttendanceTodayRow(Long schoolId, String schoolName, boolean schoolDay, int absent,
                                 Integer presentPercent, int classesTaken, int classesTotal) {

    /** روزِ درسی است و دست‌کم یک کلاس ثبت شده: درصد و تعدادِ غایب معنا دارد. */
    public boolean hasRoll() {
        return schoolDay && presentPercent != null;
    }

    /** کلاس‌های بدونِ حضور و غیابِ امروز (هرگز منفی نیست). */
    public int classesWithoutRoll() {
        return Math.max(0, classesTotal - classesTaken);
    }

    /** مقدارِ ۰ تا ۱۰۰ برای {@code <progress>}؛ بدونِ رقم ۰. */
    public int percentValue() {
        return presentPercent == null ? 0 : Math.max(0, Math.min(100, presentPercent));
    }
}
