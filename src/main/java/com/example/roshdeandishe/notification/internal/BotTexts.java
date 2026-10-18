package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.studentview.TodayAttendance;

import java.util.Map;

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

    static final String UNKNOWN_TEXT = "متوجه نشدم. لطفاً از دکمه‌های پایین انتخاب کنید.";
    static final String NOT_AVAILABLE = "این مورد برای شما در دسترس نیست.";

    /** کاربر عضویت فعال دارد ولی تنظیمات بات مدرسه‌هایش (خاموش یا نقش مجاز) اجازه نمی‌دهد. */
    static final String BOT_DISABLED_FOR_SCHOOL = "بات برای مدرسه‌ی شما فعال نیست.";
    static final String CHOOSE_STUDENT = "برای کدام دانش‌آموز؟";
    static final String CHOOSE_SCHOOL = "برای کدام مدرسه؟";

    static final String WELCOME_TEXT = """
            به ربات رشد اندیشه خوش آمدید!

            برای اتصال حساب کاربری‌تان، لطفاً شماره تلفنی که در پروفایل حساب \
            کاربری‌تان (در سایت) ثبت کرده‌اید را با دکمه‌ی زیر به اشتراک بگذارید.""";

    static final String NOT_LINKED_TEXT = "حساب شما هنوز متصل نشده است. لطفاً ابتدا /start را ارسال کنید.";

    static final String HELP_TEXT = """
            از دکمه‌های پایین انتخاب کنید؛ فقط مواردی که برای شما در دسترس است نمایش داده می‌شود:
            «حضور امروز»، «غیبت‌ها»، «نمره‌ها»، «امتحان‌ها» و «برنامه‌ی امروز» - وضعیت دانش‌آموز
            «شهریه» - وضعیت مالی فرزندان (فقط برای والد)
            «کلاس‌های امروز من» و «حضور و غیاب مانده» - ویژه‌ی معلم
            «رویدادهای نزدیک» - رویدادهای مدرسه
            «مدرسه‌های من» - فهرست مدرسه‌ها و نقش‌های شما
            «فرزندان من» - فهرست فرزندان لینک‌شده و وضعیت مالی هرکدام""";

    /** برچسب دکمه‌ی انتخابِ دانش‌آموز وقتی چند گزینه هست: «نام — مدرسه». */
    static String studentChoice(String name, String schoolName) {
        return name + " — " + schoolName;
    }

    /** برچسب فرمان‌های متنی (علاوه بر BTN_HELP). */
    static final String CMD_MY_SCHOOLS = "مدرسه‌های من";
    static final String CMD_MY_WARDS = "فرزندان من";

    static final String CONTACT_NOT_MATCHED =
            "شماره‌ی به‌اشتراک‌گذاشته‌شده با هیچ حسابی در سامانه مطابقت ندارد. " +
            "ابتدا از طریق پروفایل حساب کاربری‌تان در سایت، همین شماره را ثبت کنید.";

    static final String CONTACT_NOT_OWN =
            "لطفاً شماره‌ی خودتان را با دکمه‌ی «اشتراک‌گذاری شماره تلفن» بفرستید؛ " +
            "شماره‌ی شخص دیگری پذیرفته نمی‌شود.";
    static final String PRIVATE_CHAT_ONLY = "این بات فقط در گفتگوی خصوصی کار می‌کند.";

    static final String NO_MEMBERSHIPS = "شما در حال حاضر عضو هیچ مدرسه‌ای نیستید.";
    static final String MEMBERSHIPS_HEADER = "مدرسه‌ها و نقش‌های شما:\n";
    static final String NOT_A_PARENT = "شما در هیچ مدرسه‌ای نقش والد ندارید.";
    static final String NO_WARDS = "هیچ دانش‌آموزی به شما لینک نشده است.";

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "STUDENT", "دانش‌آموز",
            "TEACHER", "معلم",
            "PARENT", "والد",
            "SCHOOL_ADMIN", "مدیر مدرسه",
            "FINANCE_ADMIN", "مدیر مالی"
    );

    /** پیام موفقیتِ اتصال حساب. */
    static String linkSuccess(String fullName) {
        return fullName + " عزیز، حساب شما با موفقیت متصل شد. برای راهنما «راهنما» را ارسال کنید.";
    }

    /** نام جایگزین وقتی مدرسه پیدا نشود. */
    static String schoolFallback(Long schoolId) {
        return "مدرسه #" + schoolId;
    }

    /** نام جایگزین وقتی نام دانش‌آموز پیدا نشود. */
    static String studentFallback(Long userId) {
        return "دانش‌آموز #" + userId;
    }

    /** برچسب نقش؛ کد ناشناخته همان‌طور برمی‌گردد. */
    static String roleLabel(String roleCode) {
        return ROLE_LABELS.getOrDefault(roleCode, roleCode);
    }

    /** یک خط از فهرست مدرسه‌ها و نقش‌ها (با newline پایانی). */
    static String membershipLine(String schoolName, String roleCode) {
        return "- " + schoolName + " (" + roleLabel(roleCode) + ")\n";
    }

    /** یک خط از فهرست فرزندان (با newline پایانی). */
    static String wardLine(String studentName, String schoolName, long invoiceCount) {
        return "- " + studentName + " (" + schoolName + "): " + invoiceCount + " فاکتور صادرشده\n";
    }

    // ---- پاسخ‌ها ----

    static final String ATTENDANCE_UNAVAILABLE = "اطلاعات حضور و غیاب در دسترس نیست.";
    static final String ATTENDANCE_PENDING = "حضور و غیاب امروز هنوز ثبت نشده است.";
    static final String NOT_SCHOOL_DAY = "امروز روز درسی نیست.";
    static final String ATTENDANCE_SUMMARY_NOTE = " (امسال، فقط روزهای ثبت‌شده)";
    static final String GRADES_EMPTY = "هنوز نمره‌ای ثبت نشده است.";
    static final String GRADES_UNAVAILABLE = "نمره‌ها در دسترس نیست.";
    static final String EXAMS_EMPTY = "امتحان پیش‌رویی ندارد.";
    static final String EXAMS_UNAVAILABLE = "امتحان‌ها در دسترس نیست.";
    static final String HOLIDAY_TODAY = "امروز تعطیل است.";
    static final String TIMETABLE_EMPTY = "امروز کلاسی ثبت نشده است.";
    static final String TIMETABLE_NO_CLASS = "کلاسی برای این دانش‌آموز ثبت نشده است.";
    static final String TIMETABLE_UNAVAILABLE = "برنامه‌ی امروز در دسترس نیست.";
    static final String INVOICES_NONE = "فاکتور بازی ندارد.";
    static final String INVOICES_UNAVAILABLE = "اطلاعات شهریه در دسترس نیست.";
    static final String ROLL_ALL_DONE = "حضور و غیاب همه‌ی کلاس‌های امروز ثبت شده است.";
    static final String ROLL_UNAVAILABLE = "وضعیت حضور و غیاب کلاس‌ها در دسترس نیست.";
    static final String EVENTS_NONE = "رویداد نزدیکی نیست.";
    static final String EVENTS_UNAVAILABLE = "رویدادهای مدرسه در دسترس نیست.";
    static final String NO_SUBJECT = "—";
    private static final String DASH = " — ";

    /** خط اولِ پاسخ دانش‌آموز: «نام — مدرسه». */
    static String studentHeader(String studentName, String schoolName) {
        return "«" + studentName + DASH + schoolName + "»";
    }

    /** خط اولِ پاسخ مدرسه‌محور: «مدرسه». */
    static String schoolHeader(String schoolName) {
        return "«" + schoolName + "»";
    }

    /** پاسخ وضعیت حضور امروز؛ {@code own} یعنی ثبت‌نام خودِ کاربر (دوم‌شخص مفرد). */
    static String attendanceToday(TodayAttendance state, boolean own) {
        return switch (state) {
            case PRESENT -> own ? "امروز حاضر بودی." : "امروز حاضر بود.";
            case LATE -> own ? "امروز با تأخیر آمدی." : "امروز با تأخیر آمد.";
            case ABSENT -> own ? "امروز غایب بودی." : "امروز غایب بود.";
            case ABSENT_EXCUSED -> own ? "امروز غایب بودی (موجه)." : "امروز غایب بود (موجه).";
            case PENDING -> ATTENDANCE_PENDING;
            case NOT_SCHOOL_DAY -> NOT_SCHOOL_DAY;
            default -> ATTENDANCE_UNAVAILABLE;
        };
    }

    /** خلاصه‌ی غیبت‌ها. */
    static String attendanceSummary(int absent, int excused, int late) {
        return "غیبت بدون عذر: " + PersianText.digits(absent) + DASH + "موجه: " + PersianText.digits(excused)
                + DASH + "تأخیر: " + PersianText.digits(late) + ATTENDANCE_SUMMARY_NOTE;
    }

    /** یک خط نمره: «ریاضی — نوبت اول: ۱۸٫۵» (نوبت خالی حذف می‌شود). */
    static String gradeLine(String subject, String term, String value) {
        String head = term == null || term.isBlank() ? subject : subject + DASH + term;
        return head + ": " + PersianText.localize(value);
    }

    /** یک خط امتحان: «آزمون هفتگی — ۱۴ مهر ۱۴۰۵». */
    static String examLine(String title, String when) {
        return title + DASH + when;
    }

    /** ساعت به‌صورت «۸:۰۰». */
    static String clock(java.time.LocalTime time) {
        return PersianText.digits(time.getHour()) + ":" + PersianText.digits(time.getMinute() / 10)
                + PersianText.digits(time.getMinute() % 10);
    }

    /** یک خط برنامه: «زنگ ۱ (۸:۰۰ تا ۸:۴۵): ریاضی» و برای معلم «… — کلاس». */
    static String timetableLine(int position, java.time.LocalTime start, java.time.LocalTime end,
                                String subject, String className) {
        String line = "زنگ " + PersianText.digits(position) + " (" + clock(start) + " تا " + clock(end) + "): " + subject;
        return className == null || className.isBlank() ? line : line + DASH + className;
    }

    /** «مانده‌ی کل: X». */
    static String invoiceTotal(String amount) {
        return "مانده‌ی کل: " + amount;
    }

    /** «سررسید گذشته: N فاکتور». */
    static String invoicesOverdue(long count) {
        return "سررسید گذشته: " + PersianText.digits(count) + " فاکتور";
    }

    /** «قسط بعدی: Y تا ۲۰ آبان ۱۴۰۵». */
    static String nextInstallment(String amount, String date) {
        return "قسط بعدی: " + amount + " تا " + date;
    }

    /** «حضور و غیاب این کلاس‌ها هنوز ثبت نشده: …». */
    static String rollPending(java.util.List<String> classNames) {
        return "حضور و غیاب این کلاس‌ها هنوز ثبت نشده: " + String.join("، ", classNames);
    }

    /** یک خط رویداد: «جشن شروع سال — ۱ مهر ۱۴۰۵ (۳ روز دیگر)». */
    static String eventLine(String title, String date, long daysLeft) {
        String when = daysLeft <= 0 ? "امروز" : daysLeft == 1 ? "فردا" : PersianText.digits(daysLeft) + " روز دیگر";
        return title + DASH + date + " (" + when + ")";
    }
}
