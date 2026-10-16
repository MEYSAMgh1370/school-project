package com.example.roshdeandishe.notification.internal;

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

    /** برچسب فرمان‌های متنی (علاوه بر BTN_HELP). */
    static final String CMD_MY_SCHOOLS = "مدرسه‌های من";
    static final String CMD_MY_WARDS = "فرزندان من";

    static final String CONTACT_NOT_MATCHED =
            "شماره‌ی به‌اشتراک‌گذاشته‌شده با هیچ حسابی در سامانه مطابقت ندارد. " +
            "ابتدا از طریق پروفایل حساب کاربری‌تان در سایت، همین شماره را ثبت کنید.";

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
}
