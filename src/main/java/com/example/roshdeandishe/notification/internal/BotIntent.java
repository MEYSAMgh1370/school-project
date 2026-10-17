package com.example.roshdeandishe.notification.internal;

import java.util.Arrays;
import java.util.Optional;

/**
 * پرسش‌هایی که بات می‌تواند پاسخ دهد. {@code code} همان بخشِ اولِ callback_data است
 * (۱ تا ۲ حرف ASCII) و {@code button()} متنِ دکمه‌ی منو در {@link BotTexts}.
 */
enum BotIntent {
    ATTENDANCE_TODAY("a", TargetKind.STUDENT_TARGET, BotTexts.BTN_ATTENDANCE_TODAY),
    ATTENDANCE_SUMMARY("s", TargetKind.STUDENT_TARGET, BotTexts.BTN_ATTENDANCE_SUMMARY),
    GRADES("g", TargetKind.STUDENT_TARGET, BotTexts.BTN_GRADES),
    EXAMS("e", TargetKind.STUDENT_TARGET, BotTexts.BTN_EXAMS),
    TIMETABLE_TODAY("t", TargetKind.STUDENT_TARGET, BotTexts.BTN_TIMETABLE_TODAY),
    INVOICES("i", TargetKind.WARD_TARGET, BotTexts.BTN_INVOICES),
    TEACHER_TODAY("tt", TargetKind.TEACHER_SCHOOL, BotTexts.BTN_TEACHER_TODAY),
    TEACHER_ROLL("tr", TargetKind.TEACHER_SCHOOL, BotTexts.BTN_TEACHER_ROLL),
    EVENTS("ev", TargetKind.MEMBER_SCHOOL, BotTexts.BTN_EVENTS);

    /** موضوعِ پرسش از چه نوعی است و {@link SubjectResolver} آن را از کجا می‌سازد. */
    enum TargetKind {
        /** فرزندان (والد) به‌علاوه‌ی ثبت‌نامِ خودِ دانش‌آموز. */
        STUDENT_TARGET,
        /** فقط فرزندان؛ دانش‌آموز هرگز هدفِ این نوع نیست (مالی فقط برای والد). */
        WARD_TARGET,
        /** مدرسه‌هایی که کاربر در آن‌ها معلمِ فعال است. */
        TEACHER_SCHOOL,
        /** مدرسه‌هایی که کاربر در آن‌ها هر عضویتِ فعالی دارد. */
        MEMBER_SCHOOL
    }

    private final String code;
    private final TargetKind targetKind;
    private final String button;

    BotIntent(String code, TargetKind targetKind, String button) {
        this.code = code;
        this.targetKind = targetKind;
        this.button = button;
    }

    String code() {
        return code;
    }

    TargetKind targetKind() {
        return targetKind;
    }

    String button() {
        return button;
    }

    static Optional<BotIntent> byButton(String text) {
        return Arrays.stream(values()).filter(i -> i.button.equals(text)).findFirst();
    }

    static Optional<BotIntent> byCode(String code) {
        return Arrays.stream(values()).filter(i -> i.code.equals(code)).findFirst();
    }
}
