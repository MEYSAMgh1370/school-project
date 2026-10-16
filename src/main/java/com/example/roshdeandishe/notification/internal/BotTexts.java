package com.example.roshdeandishe.notification.internal;

/**
 * همه‌ی متن‌های رو به کاربرِ ربات بله (دکمه‌ها و پیام‌ها) یک‌جا، تا بعد از آزمون کاربری یا A/B بدون دست‌زدن به
 * منطق قابل تنظیم باشند. فقط ثابت یا ساخت رشته؛ بدون منطق.
 */
final class BotTexts {

    private BotTexts() {
    }

    static final String BTN_ATTENDANCE_TODAY = "حضور امروز";
    static final String BTN_ATTENDANCE_SUMMARY = "غیبت‌ها";
    static final String BTN_GRADES = "نمره‌ها";
    static final String BTN_EXAMS = "امتحان‌ها";
    static final String BTN_TIMETABLE_TODAY = "برنامه‌ی امروز";
    static final String BTN_INVOICES = "شهریه";
    static final String BTN_TEACHER_TODAY = "کلاس‌های امروز من";
    static final String BTN_TEACHER_ROLL = "حضور و غیاب مانده";
    static final String BTN_EVENTS = "رویدادهای نزدیک";
    static final String BTN_HELP = "راهنما";

    static final String MENU_PROMPT = "از دکمه‌های پایین انتخاب کنید.";
    static final String UNKNOWN_TEXT = "متوجه نشدم. لطفاً از دکمه‌های پایین انتخاب کنید.";
    static final String NOT_AVAILABLE = "این مورد برای شما در دسترس نیست.";
    static final String CHOOSE_STUDENT = "برای کدام دانش‌آموز؟";
    static final String CHOOSE_SCHOOL = "برای کدام مدرسه؟";
    static final String NO_STUDENT = "دانش‌آموزی برای نمایش پیدا نشد.";

    static final String WELCOME_TEXT = """
            به ربات رشد اندیشه خوش آمدید!

            برای اتصال حساب کاربری‌تان، لطفاً شماره تلفنی که در پروفایل حساب \
            کاربری‌تان (در سایت) ثبت کرده‌اید را با دکمه‌ی زیر به اشتراک بگذارید.""";

    static final String NOT_LINKED_TEXT = "حساب شما هنوز متصل نشده است. لطفاً ابتدا /start را ارسال کنید.";

    static final String UNKNOWN_COMMAND_TEXT =
            "دستور نامعتبر است. برای راهنما، «راهنما» را ارسال کنید.";

    static final String HELP_TEXT = """
            دستورهای قابل استفاده:
            «مدرسه‌های من» - فهرست مدرسه‌ها و نقش‌های شما
            «فرزندان من» - فهرست فرزندان لینک‌شده و وضعیت مالی هرکدام""";
}
