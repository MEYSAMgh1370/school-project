package com.example.roshdeandishe.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Attendance (حضور و غیاب، openspec/changes/attendance.md).
 * <p>
 * واحد، «یک کلاس در یک روزِ درسی» است. «حضور و غیاب گرفته شد» یعنی ردیفِ {@code roll} هست؛ بدون آن کلاس
 * «ثبت نشده» است، که با «هیچ‌کس غایب نبود» یکی نیست. فقط استثناها ({@link AttendanceStatus}) ذخیره می‌شوند.
 * دانش‌آموز و کلاس با شناسه‌ی مات ارجاع می‌شوند؛ نام را فراخوان نمی‌دهد، این ماژول از iam می‌گیرد.
 * <p>
 * خطاها {@code AttendanceException} با {@code AttendanceError} هستند. مجوزها را کنترلر می‌سنجد و
 * {@link #requireMayAccessClass}/{@link #requireMayEditDay} قاعده‌ی «کلاسِ خودِ معلم» و «روزِ گذشته» را
 * (با دو مقدارِ بولیِ مجوز) اعمال می‌کنند؛ خودِ facade هویتِ فراخوان را نمی‌شناسد.
 */
public interface AttendanceFacade {

    /**
     * ثبتِ حضور و غیابِ یک کلاس در یک روز، به‌صورت یک جایگزینیِ idempotent در یک تراکنش: مُهرهای قبلیِ همین
     * (کلاس، روز) حذف و {@code marks} نوشته می‌شود و ردیفِ {@code roll} upsert می‌شود. دانش‌آموزی که در فهرست
     * نیست حاضر است. {@code marks} خالی = «همه حاضر».
     * <p>
     * روز باید روزِ درسیِ مدرسه و نه آینده باشد ({@code InvalidAttendanceDay})؛ کلاس باید از این مدرسه و
     * از سالِ جاری (یا بدون سال) باشد ({@code ClassNotFound}، {@code ClassNotInCurrentYear})؛ هر دانش‌آموز
     * باید عضو همین کلاس باشد ({@code StudentNotInClass}) و فقط یک‌بار بیاید؛ وضعیت فقط
     * ABSENT/LATE؛ {@code excused} فقط برای ABSENT معنا دارد (برای LATE نادیده و false ذخیره می‌شود)؛
     * یادداشت حداکثر ۲۵۵ نویسه ({@code InvalidMark}).
     *
     * @return وضعیتِ ذخیره‌شده‌ی همان روز، مثل {@link #getClassDay}
     */
    ClassDay saveRoll(Long schoolId, Long classId, LocalDate day, Long actorUserId, List<MarkInput> marks);

    /**
     * فهرستِ دانش‌آموزانِ کلاس در آن روز با مُهرِ هر کدام و اینکه حضور و غیاب گرفته شده یا نه. دانش‌آموزی که
     * مُهر دارد ولی دیگر در کلاس نیست (ارتقا/ترک) همچنان می‌آید تا تاریخچه بماند. روز لازم نیست روزِ درسی باشد
     * ({@link ClassDay#recordable()} می‌گوید ثبت ممکن است یا نه). {@code ClassNotFound} اگر کلاس از این مدرسه نباشد.
     */
    ClassDay getClassDay(Long schoolId, Long classId, LocalDate day);

    /**
     * غایبین و دیرآمدگانِ روز برای همه‌ی کلاس‌های سالِ جاری (به‌علاوه‌ی کلاسی از سالِ دیگر که همان روز حضور و
     * غیاب گرفته). هر کلاس: گرفته شده یا «ثبت نشده»، مُهرها و جمع‌ها. روزِ غیردرسی: فهرستِ خالی.
     */
    List<ClassAbsentees> absenteesForDay(Long schoolId, LocalDate day);

    /**
     * «رقمِ مدیر»: فقط روی کلاس‌هایی که همان روز حضور و غیابشان گرفته شده؛ کلاسِ ثبت‌نشده در درصد نمی‌آید
     * و هرگز حاضر شمرده نمی‌شود. {@code presentPercent = floor(100 × (expected − absent) / expected)} (تأخیر حاضر
     * است؛ گردکردن به پایین تا ۹۹٫۹٪ هرگز ۱۰۰ نشان داده نشود). {@code presentPercent == null} یعنی رقمی نیست
     * (روزِ غیردرسی یا هنوز هیچ کلاسی ثبت نشده).
     */
    DaySummary summaryForDay(Long schoolId, LocalDate day);

    /**
     * گزارشِ بازه: یک ردیف به‌ازای هر (دانش‌آموز، کلاس). مبنا = روزهای بازه که حضور و غیابِ همان کلاس گرفته
     * شده؛ {@code presentDays = مبنا − absentDays} (تأخیر حاضر است و جدا هم در {@code lateDays} می‌آید). فقط
     * کلاس‌هایی که دست‌کم یک روزِ ثبت‌شده در بازه دارند. {@code classId} و {@code nameFilter} (شاملِ
     * حروف، بدون حساسیت به بزرگی/کوچکی) اختیاری‌اند. بازه‌ی بسته [from, to] و حداکثر
     * {@link #MAX_REPORT_DAYS} روز ({@code InvalidRange}).
     */
    List<ReportRow> report(Long schoolId, LocalDate from, LocalDate to, Long classId, String nameFilter);

    /**
     * کلاس‌هایی که این کاربر می‌تواند برایشان ثبت کند، در سالِ جاری: با {@code recordAny} همه، وگرنه کلاس‌هایی
     * که خودش در آن‌ها معلم است (teacherId کلاس یا خانه‌ی برنامه). {@code day} تهی = امروز (به وقتِ مدرسه)؛
     * فقط برای نشان‌دادنِ «ثبت شده/نشده».
     */
    List<RecordableClass> listRecordableClasses(Long schoolId, Long actorUserId, boolean recordAny, LocalDate day);

    /**
     * قاعده‌ی «کلاسِ خود»: {@code ClassNotFound} اگر کلاس از این مدرسه نباشد؛ بدون {@code recordAny}
     * فقط معلمِ همان کلاس مجاز است، وگرنه {@code NotYourClass}.
     */
    void requireMayAccessClass(Long schoolId, Long classId, Long actorUserId, boolean recordAny);

    /** بدون {@code editPast} فقط امروز (به وقتِ مدرسه) قابل ثبت است، وگرنه {@code PastEditNotAllowed}. */
    void requireMayEditDay(Long schoolId, LocalDate day, boolean editPast);

    // ---- عذر (attendance.md «Excuses») ----

    /**
     * ثبتِ عذرِ ولی برای غیبتِ فرزند. فراخوان باید ولیِ <b>فعالِ</b> همین دانش‌آموز در همین مدرسه باشد
     * ({@code NotYourChild}) و دانش‌آموز همان روز مُهرِ ABSENT داشته باشد ({@code NoAbsenceToExcuse})؛ یادداشت
     * غیرخالی و حداکثر ۵۰۰ نویسه ({@code InvalidExcuse}). برای هر (دانش‌آموز، روز) یک عذر: ثبتِ دوباره تا وقتی
     * PENDING است یادداشت را عوض می‌کند، پس از تأیید/ردّ {@code ExcuseAlreadyDecided}.
     */
    ExcuseRow submitExcuse(Long schoolId, Long studentId, LocalDate day, Long submitterUserId, String note);

    /**
     * غیبت و دیرآمدنِ دانش‌آموزِ خودِ کاربر (نقشِ فعالِ STUDENT) و فرزندانِ لینک‌شده‌اش (نقشِ فعالِ PARENT) در
     * بازه‌ی بسته‌ی [from, to] (حداکثر {@link #MAX_REPORT_DAYS} روز، {@code InvalidRange}). فقط استثناها می‌آید؛
     * کسی که هیچ‌کدام از دو نقش را ندارد فهرستِ خالی می‌گیرد و دانش‌آموزِ بیگانه هرگز دیده نمی‌شود.
     */
    List<MyStudentAttendance> listMyAttendance(Long schoolId, Long userId, LocalDate from, LocalDate to);

    /**
     * عذرهای مدرسه، تازه‌ترین روز اول. {@code status} و هر دو سرِ بازه اختیاری‌اند؛ اگر هر دو سر آمده باشد
     * مثل گزارش اعتبارسنجی می‌شود ({@code InvalidRange}).
     */
    List<ExcuseRow> listExcuses(Long schoolId, ExcuseStatus status, LocalDate from, LocalDate to);

    /**
     * تأییدِ ({@code APPROVED}) یا ردِّ ({@code REJECTED}) یک عذرِ PENDING. تأیید در همان تراکنش مُهرِ غیبتِ آن روز
     * را «موجه» می‌کند؛ ردّ مُهر را دست‌نخورده می‌گذارد. {@code ExcuseNotFound}، {@code ExcuseAlreadyDecided}،
     * و برای تصمیمِ PENDING یا تهی {@code InvalidExcuse}.
     */
    ExcuseRow decideExcuse(Long schoolId, Long excuseId, ExcuseStatus decision, Long deciderUserId);

    /**
     * گزارشِ هر کلاس در بازه (یک ردیف برای هر کلاسِ دارای حداقل یک roll، به ترتیبِ نام)؛ {@code classId} اختیاری و
     * کلاسِ ناشناخته/مدرسه‌ی دیگر فهرستِ خالی می‌دهد. بازه مثل {@link #report} ({@code InvalidRange}).
     */
    List<ClassReportRow> classReport(Long schoolId, LocalDate from, LocalDate to, Long classId);

    /**
     * گزارشِ هر معلم: کلاس‌های دارای rollِ بازه که معلمِ آن‌هاست ({@code teacherId} کلاس یا معلمِ یک خانه‌ی برنامه).
     * کلاسِ چندمعلمی زیرِ هر معلم می‌آید و جمع‌ها بین معلم‌ها یکی نمی‌شوند. نامِ ناشناخته: تهی.
     */
    List<TeacherReportRow> teacherReport(Long schoolId, LocalDate from, LocalDate to);

    /**
     * برگه‌ی یک دانش‌آموز در بازه: ردیف‌های هر کلاس، درصدِ کل و فهرستِ روزهای غیبت/تأخیر. دانش‌آموزِ ناشناخته یا
     * از مدرسه‌ی دیگر: {@code StudentNotFound}.
     */
    StudentSheet studentSheet(Long schoolId, Long studentId, LocalDate from, LocalDate to);

    /** سقفِ بازه‌ی گزارش (روز، شاملِ دو سر). */
    int MAX_REPORT_DAYS = 366;

    /** یک مُهرِ ورودی؛ {@code excused} فقط برای ABSENT معنا دارد. */
    record MarkInput(Long studentId, AttendanceStatus status, boolean excused, String note) {
    }

    /** {@code status == null} یعنی حاضر. */
    record StudentMark(Long studentId, String studentName, AttendanceStatus status, boolean excused, String note) {
    }

    /**
     * {@code taken}: حضور و غیاب گرفته شده؛ {@code recordable}: روزِ درسی و نه آینده، یعنی ثبت ممکن است.
     */
    record ClassDay(Long classId, String className, LocalDate day, boolean taken, boolean recordable,
                    List<StudentMark> students) {
    }

    record Absentee(Long studentId, String studentName, AttendanceStatus status, boolean excused, String note) {
    }

    /** {@code expected} = اعضای کلاس آن روز؛ {@code absent}/{@code late}/{@code excusedAbsent} شمارِ مُهرها. */
    record ClassAbsentees(Long classId, String className, boolean taken, int expected, int absent, int late,
                          int excusedAbsent, List<Absentee> students) {
    }

    record DaySummary(LocalDate day, boolean schoolDay, int absent, int excusedAbsent, int expected,
                      Integer presentPercent, int classesTaken, int classesTotal) {
    }

    /** {@code presentPercent} = floor(۱۰۰×present/(present+absent))؛ تهی اگر مبنا صفر باشد. */
    record ReportRow(Long studentId, String studentName, Long classId, String className, int absentDays,
                     int lateDays, int presentDays, Integer presentPercent) {
    }

    /** {@code daysTaken} = شمارِ rollهای کلاس در بازه. */
    record ClassReportRow(Long classId, String className, int daysTaken, int absentDays, int lateDays,
                          int presentDays, Integer presentPercent) {
    }

    record TeacherReportRow(Long teacherUserId, String teacherName, int classCount, int daysTaken, int absentDays,
                            int lateDays, int presentDays, Integer presentPercent, List<ClassReportRow> classes) {
    }

    /** {@code status} ABSENT یا LATE؛ {@code excused} فقط برای ABSENT. */
    record SheetDay(LocalDate day, Long classId, String className, AttendanceStatus status, boolean excused,
                    String note) {
    }

    record StudentSheet(Long studentId, String studentName, LocalDate from, LocalDate to, Integer presentPercent,
                        List<ReportRow> rows, List<SheetDay> days) {
    }

    record RecordableClass(Long classId, String name, String section, int gradeLevel, boolean taken) {
    }

    /** یک عذر با نامِ دانش‌آموز و کلاس (نام‌ها ممکن است تهی باشند اگر دیگر در مدرسه نیست). */
    record ExcuseRow(Long id, Long studentId, String studentName, Long classId, String className, LocalDate day,
                     String note, ExcuseStatus status, Instant submittedAt, Instant decidedAt) {
    }

    record MyExcuse(Long id, ExcuseStatus status, String note) {
    }

    /** {@code excuse} تهی = عذری ثبت نشده؛ {@code status} ABSENT یا LATE. */
    record MyDayAttendance(LocalDate day, AttendanceStatus status, boolean excused, MyExcuse excuse) {
    }

    /** {@code own}: دانش‌آموزِ خودِ کاربر (نه فرزند). */
    record MyStudentAttendance(Long studentId, String studentName, boolean own, List<MyDayAttendance> days) {
    }
}
