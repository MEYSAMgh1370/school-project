package com.example.roshdeandishe.school;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول School.
 * <p>
 * این Facade مسئول ثبت و مدیریت خود مدرسه‌هاست - نه عضویت‌ها یا
 * Permissionها (آن‌ها همچنان متعلق به IamFacade هستند). تفکیک عمدی:
 * "این مدرسه چیست؟" در اینجا، "چه کسی در این مدرسه چه نقشی دارد؟" در iam.
 */
public interface SchoolFacade {

    /**
     * ثبت یک مدرسه‌ی جدید در پلتفرم. این اولین قدم برای هر فرآیند
     * onboarding مدرسه‌ی جدید است؛ تا این متد صدا زده نشود، schoolId ای
     * که در IamFacade استفاده می‌شود معنای واقعی ندارد.
     * <p>
     * {@code slug} اختیاری است (null/خالی = بدون نشانی اختصاصی): نشانی
     * ورودیِ برنددارِ مدرسه در /s/{slug}. قالب مجاز عمداً قالب یک DNS label
     * است (SchoolError.InvalidSlug) تا در فاز «دامنه‌ی اختصاصی» همین مقدار
     * ساب‌دامین مدرسه شود - تصمیم Tier 1، CLAUDE.md §11.
     */
    SchoolId createSchool(String name, String timezone, String locale, SchoolLevel level, String slug);

    /**
     * مثل {@link #createSchool(String, String, String, SchoolLevel, String)} با روز شروع
     * هفته‌ی صریح. نسخه‌ی بدون آن همیشه {@link WeekStart#SATURDAY} (هفته‌ی ایرانی) می‌سازد؛
     * مدرسه‌ی غیرایرانی {@link WeekStart#MONDAY} را در لحظه‌ی ساخت انتخاب می‌کند.
     */
    SchoolId createSchool(String name, String timezone, String locale, SchoolLevel level, String slug,
                          WeekStart weekStart);

    Optional<SchoolInfo> findSchool(SchoolId schoolId);

    /**
     * یافتن مدرسه از روی نشانی اختصاصی‌اش - پشتوانه‌ی صفحه‌ی عمومی
     * /s/{slug} (web.SchoolEntryPageController). وضعیت مدرسه (ACTIVE یا
     * SUSPENDED) فیلتر نمی‌شود؛ تصمیمِ «مدرسه‌ی معلق را تبلیغ نکن» یک
     * قاعده‌ی presentation است و در خود صفحه اعمال می‌شود، نه اینجا.
     */
    Optional<SchoolInfo> findSchoolBySlug(String slug);

    /**
     * برای پنل مدیریت پلتفرم (PLATFORM_ADMIN) - لیست همه‌ی مدرسه‌های ثبت‌شده.
     */
    List<SchoolInfo> listSchools();

    /**
     * تغییر وضعیت یک مدرسه (مثلاً تعلیق یک مدرسه به دلیل عدم پرداخت).
     * توجه: این عملیات کاملاً مجزا از iam.setMembershipActive است - آن یکی
     * دسترسی *یک نفر* را در یک مدرسه قطع می‌کند، این یکی کل مدرسه را.
     */
    void setSchoolStatus(SchoolId schoolId, SchoolStatus status);

    /**
     * فهرست سراسریِ سال‌های تحصیلی (مشترک بین همه‌ی مدرسه‌ها، فقط‌خواندنی برای
     * مدرسه‌ها) از قدیم به جدید - openspec/changes/academic-year.md. هر سال را خودِ
     * برنامه در تاریخِ شمسیِ کانفیگ می‌سازد؛ سالِ آینده تا آن تاریخ در فهرست نیست.
     */
    List<AcademicYearInfo> listAcademicYears();

    /**
     * «سال تحصیلی جاریِ» یک مدرسه؛ خالی یعنی هنوز انتخاب نشده (هیچ سالی حدس
     * زده نمی‌شود). SchoolError.SchoolNotFound اگر مدرسه وجود نداشته باشد.
     */
    Optional<AcademicYearInfo> findCurrentAcademicYear(SchoolId schoolId);

    /**
     * تاریخِ شمسی برای متنِ پیام‌های سرور، مثل «۱۴ مهر ۱۴۰۵» (رقم فارسی). صفحه‌ها تاریخ را سمت کلاینت
     * با jalali.js نشان می‌دهند؛ این فقط برای پیام‌هایی است که روی سرور ساخته می‌شود (مثلاً اطلاع‌رسانی غیبت).
     */
    String formatSolarDate(java.time.LocalDate date);

    /**
     * افزودنِ {@code months} ماهِ شمسی به یک تاریخِ میلادی: همان روزِ ماه در ماهِ مقصد، و اگر آن ماه آن روز را
     * ندارد آخرین روزِ ماه (۳۱ شهریور + ۱ ماه = ۳۰ مهر). نتیجه میلادی است (اقساطِ شهریه).
     */
    LocalDate addSolarMonths(LocalDate gregorian, int months);

    /**
     * انتخاب سال تحصیلی جاریِ مدرسه از فهرست سراسری. فقط همین یک مقدار عوض
     * می‌شود - هیچ داده‌ای حذف یا تغییر نمی‌کند، و شروع/پایانِ سال همین انتخابِ
     * مدیرِ مدرسه است (نه تاریخِ ثابت). SchoolError.AcademicYearNotFound اگر سال
     * در فهرست نباشد (مثلاً سالِ آینده‌ای که هنوز ساخته نشده)؛
     * SchoolError.SchoolNotFound اگر مدرسه نباشد.
     * <p>
     * {@code expectedCurrent} همان سالِ جاریِ «مورد انتظار» است ({@code null} =
     * «هنوز انتخاب نشده»): فقط اگر سالِ جاریِ مدرسه هنوز همین باشد عوض می‌شود،
     * وگرنه SchoolError.AcademicYearChanged - تا درخواستِ کهنه (تأخیری یا از
     * مدیرِ دیگر) انتخابِ جدیدتر را بی‌صدا برنگرداند. بررسی و نوشتن اتمیک است.
     */
    void setCurrentAcademicYear(SchoolId schoolId, AcademicYearId academicYearId, AcademicYearId expectedCurrent);

    /**
     * آیا با تعویضِ سالِ جاری به یک سالِ بعدی، دانش‌آموزان خودکار ارتقا می‌یابند
     * (academic-year.md فاز ۳)؛ پیش‌فرض خاموش. SchoolError.SchoolNotFound اگر مدرسه نباشد.
     */
    boolean isAutoPromotion(SchoolId schoolId);

    void setAutoPromotion(SchoolId schoolId, boolean enabled);

    /** آیا اقساطِ شهریه برای مدرسه روشن است (tuition-installments.md)؛ پیش‌فرض خاموش. SchoolNotFound اگر مدرسه نباشد. */
    boolean isInstallmentsEnabled(SchoolId schoolId);

    void setInstallmentsEnabled(SchoolId schoolId, boolean enabled);

    /** تنظیمات ربات: {@code roles} خالی یعنی همه‌ی نقش‌ها. */
    record BotSettings(boolean enabled, Set<String> roles) {
    }

    /** تنظیمات ربات مدرسه؛ پیش‌فرض روشن برای همه‌ی نقش‌ها. SchoolNotFound اگر مدرسه نباشد. */
    BotSettings getBotSettings(SchoolId schoolId);

    /**
     * جایگزینیِ تنظیمات ربات. {@code roles} خالی/null = همه‌ی نقش‌ها؛ کدِ خالی SchoolError.InvalidSetting.
     * اعتبارِ خودِ کدها با فراخواننده است (نقش‌ها متعلق به iam هستند). SchoolNotFound اگر مدرسه نباشد.
     */
    void setBotSettings(SchoolId schoolId, boolean enabled, Set<String> roles);

    /** آیا ربات روشن است و (نقش‌های مجاز خالی است یا با {@code roleCodes} اشتراک دارد). */
    boolean isBotAllowed(SchoolId schoolId, Set<String> roleCodes);

    /**
     * ورودی‌های تقویم مدرسه در بازه‌ی بسته‌ی [from, to] به ترتیب روز. هر دو الزامی‌اند و
     * {@code from} نباید بعد از {@code to} باشد (SchoolError.InvalidCalendarEntry).
     * SchoolError.SchoolNotFound اگر مدرسه نباشد.
     */
    List<CalendarEntryInfo> listCalendar(SchoolId schoolId, LocalDate from, LocalDate to);

    /**
     * افزودنِ ورودی به تقویم مدرسه. عنوان غیرخالی و حداکثر ۱۲۸ نویسه (پس از trim)؛
     * SchoolError.InvalidCalendarEntry برای روز/نوع/عنوانِ نامعتبر، SchoolError.SchoolNotFound
     * اگر مدرسه نباشد. یک روز می‌تواند چند ورودی داشته باشد.
     */
    CalendarEntryInfo addCalendarEntry(SchoolId schoolId, LocalDate day, CalendarKind kind, String title);

    /**
     * حذفِ ورودی. ورودیِ مدرسه‌ی دیگر همان SchoolError.CalendarEntryNotFound است (وجودش لو نمی‌رود).
     */
    void removeCalendarEntry(SchoolId schoolId, Long entryId);

    /**
     * آیا این روز برای مدرسه تعطیل است؟ یعنی دست‌کم یک ورودیِ {@link CalendarKind#CLOSED} در همین
     * مدرسه هست، یا یک تعطیلیِ سراسریِ پلتفرم روی همان روز (مدرسه نمی‌تواند آن را لغو کند).
     * {@link CalendarKind#EVENT} روز را تعطیل نمی‌کند. (حضور و غیاب فقط همین را می‌پرسد.)
     */
    boolean isClosedDay(SchoolId schoolId, LocalDate day);

    /**
     * برنامه‌ی سالانه‌ی یک مدرسه در یک سال تحصیلی (openspec/changes/yearly-plan.md): رویدادهای
     * {@link CalendarKind#EVENT} همین مدرسه در بازه‌ی سال، به‌علاوه‌ی تعطیلی‌های سراسری همان سال،
     * به ترتیب روز. SchoolError.SchoolNotFound / SchoolError.AcademicYearNotFound.
     */
    YearlyPlan yearlyPlan(SchoolId schoolId, AcademicYearId academicYearId);

    /**
     * افزودنِ رویداد (EVENT) به برنامه‌ی سالانه. قاعده‌ی عنوان مثل {@link #addCalendarEntry}؛ روز باید
     * در بازه‌ی همان سال باشد (SchoolError.InvalidCalendarEntry). SchoolError.AcademicYearNotFound اگر
     * سال نباشد، SchoolError.SchoolNotFound اگر مدرسه نباشد.
     * <p>
     * {@code remindDaysBefore}: چند روز پیش از رویداد یادآوری شروع شود (null = بدون یادآوری، ۰ = همان روز،
     * حداکثر ۳۰)؛ بیرون از ۰..۳۰ SchoolError.InvalidCalendarEntry
     * (openspec/changes/event-reminders.md). رویدادِ ساخته‌شده (source = SCHOOL) برگردانده می‌شود.
     */
    YearlyPlanItem addYearlyPlanEvent(SchoolId schoolId, AcademicYearId academicYearId, LocalDate day,
                                      String title, Integer remindDaysBefore);

    /**
     * رویدادهای EVENT این مدرسه که در روزِ محلیِ {@code today} «سررسید یادآوری» هستند:
     * {@code remindDaysBefore != null} و {@code day - remindDaysBefore <= today <= day}، به ترتیب روز و شناسه.
     * CLOSED و رویدادِ بدون زمانِ یادآوری نمی‌آید. SchoolError.SchoolNotFound اگر مدرسه نباشد.
     */
    List<ReminderEvent> dueReminderEvents(SchoolId schoolId, LocalDate today);

    /**
     * نقش‌هایی که مدیر برای دریافت یادآوری انتخاب کرده؛ {@link Optional#empty()} = انتخابی ذخیره نشده =
     * همه‌ی نقش‌ها (پیش‌فرض). SchoolError.SchoolNotFound اگر مدرسه نباشد.
     */
    Optional<Set<String>> reminderRoleCodes(SchoolId schoolId);

    /**
     * جایگزینیِ انتخابِ نقش‌های یادآوری. مجموعه‌ی تهی/null یا هر کدِ خالی SchoolError.InvalidSetting است
     * (مدیری که یادآوری نمی‌خواهد، زمانِ یادآوری را روی رویدادها نمی‌گذارد). اعتبارِ خودِ کدها با فراخواننده است
     * (نقش‌ها متعلق به iam هستند). SchoolError.SchoolNotFound اگر مدرسه نباشد.
     */
    void setReminderRoleCodes(SchoolId schoolId, Set<String> roleCodes);

    /**
     * تنظیم بازه‌ی برنامه‌ی سالانه‌ی مدرسه برای یک سال. بازه‌ی پیش‌فرض ({@link AcademicYearInfo}) حداقل است:
     * مدرسه فقط می‌تواند آن را گسترش دهد (شروع در یا پیش از شروعِ پیش‌فرض، پایان در یا پس از پایانِ پیش‌فرض).
     * تاریخِ تهی، پایانِ پیش از شروع، یا کوچک‌کردنِ بازه SchoolError.InvalidYearRange است. ثبتِ دوباره‌ی
     * تاریخ‌های پیش‌فرض، بازه‌ی اختصاصی را پاک می‌کند. بازه‌ی مؤثر را برمی‌گرداند.
     * SchoolError.SchoolNotFound / SchoolError.AcademicYearNotFound.
     */
    YearlyPlanRange setYearlyPlanRange(SchoolId schoolId, AcademicYearId academicYearId, LocalDate startDate,
                                       LocalDate endDate);

    /**
     * حذفِ رویداد. فقط ورودیِ EVENT همین مدرسه؛ ورودیِ CLOSED یا ورودیِ مدرسه‌ی دیگر همان
     * SchoolError.CalendarEntryNotFound است.
     */
    void removeYearlyPlanEvent(SchoolId schoolId, Long entryId);

    /**
     * ثبت تعطیلیِ سراسری برای یک سال (مسیر مدیر پلتفرم، فاز C). روز باید در بازه‌ی همان سال باشد
     * (SchoolError.InvalidCalendarEntry)، عنوان مثل {@link #addCalendarEntry}؛
     * SchoolError.AcademicYearNotFound اگر سال نباشد.
     */
    PlatformHolidayInfo addPlatformHoliday(AcademicYearId academicYearId, LocalDate day, String title);

    /** SchoolError.PlatformHolidayNotFound اگر شناسه نباشد. */
    void removePlatformHoliday(Long id);

    /** تعطیلی‌های سراسریِ یک سال به ترتیب روز. SchoolError.AcademicYearNotFound اگر سال نباشد. */
    List<PlatformHolidayInfo> listPlatformHolidays(AcademicYearId academicYearId);

    record SchoolId(Long value) {
    }

    /**
     * {@code id} برای SCHOOL شناسه‌ی ورودی تقویم مدرسه و برای PLATFORM شناسه‌ی تعطیلیِ سراسری است.
     * {@code remindDaysBefore}: null برای PLATFORM و برای رویدادِ بدون زمانِ یادآوری.
     */
    record YearlyPlanItem(Long id, LocalDate day, String title, YearlyPlanSource source, Integer remindDaysBefore) {
    }

    /** رویدادِ سررسیدِ یادآوری (فقط EVENT دارای زمانِ یادآوری). */
    record ReminderEvent(Long id, LocalDate day, String title, int remindDaysBefore) {
    }

    /** SCHOOL = رویدادِ خودِ مدرسه (قابل حذف توسط مدرسه)؛ PLATFORM = تعطیلیِ سراسری (مدرسه حذف نمی‌کند). */
    enum YearlyPlanSource {
        SCHOOL, PLATFORM
    }

    /** {@code year} با تاریخ‌های پیش‌فرض؛ {@code range} بازه‌ی مؤثرِ همین مدرسه (رویدادها در آن می‌آیند). */
    record YearlyPlan(AcademicYearInfo year, YearlyPlanRange range, List<YearlyPlanItem> items) {
    }

    /** بازه‌ی مؤثرِ برنامه‌ی سالانه؛ {@code custom} = مدرسه آن را از پیش‌فرض گسترش داده است. */
    record YearlyPlanRange(LocalDate startDate, LocalDate endDate, boolean custom) {
    }

    record PlatformHolidayInfo(Long id, AcademicYearId academicYearId, LocalDate day, String title) {
    }

    record CalendarEntryInfo(Long id, LocalDate day, CalendarKind kind, String title) {
    }

    /** CLOSED = روز تعطیل؛ EVENT = رویدادِ «برنامه‌ی سالانه» (روز را تعطیل نمی‌کند). */
    enum CalendarKind {
        CLOSED, EVENT
    }

    record AcademicYearId(Long value) {
    }

    /** startDate/endDate: ۱ مهرِ startYear تا ۳۱ خردادِ startYear+1 (هر دو شاملِ بازه)، میلادی. */
    record AcademicYearInfo(AcademicYearId id, String code, int startYear, LocalDate startDate, LocalDate endDate) {
    }

    record SchoolInfo(SchoolId id, String name, SchoolStatus status, String timezone, String locale, SchoolLevel level,
                      String slug, WeekStart weekStart) {

        /** منطقه‌ی زمانیِ پیش‌فرض وقتی مدرسه منطقه‌ی معتبر ندارد. */
        public static final java.time.ZoneId DEFAULT_ZONE = java.time.ZoneId.of("Asia/Tehran");

        /**
         * «امروز»ِ مدرسه از اینجا می‌آید (job یادآوری و جعبه‌ی داشبورد هر دو)؛ خالی یا نامعتبر
         * {@link #DEFAULT_ZONE} می‌شود تا دو جا هرگز از هم جدا نشوند.
         */
        public java.time.ZoneId zoneId() {
            if (timezone == null || timezone.isBlank()) {
                return DEFAULT_ZONE;
            }
            try {
                return java.time.ZoneId.of(timezone.strip());
            } catch (java.time.DateTimeException e) {
                return DEFAULT_ZONE;
            }
        }
    }

    /**
     * روز شروع هفته‌ی یک مدرسه و روزهای درسیِ برآمده از آن - تنها مالکِ این قاعده
     * (openspec/changes/eu-market-readiness.md، W9). studyplan، exam و academic روز شروع را
     * از اینجا می‌خوانند و خودشان «شنبه» یا «دوشنبه» را ثابت نمی‌کنند.
     * <p>
     * {@link #code()} همان مقدارِ ذخیره‌شده در school.schools.week_start_day است.
     */
    enum WeekStart {
        SATURDAY("SAT", DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY),
        MONDAY("MON", DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

        private final String code;
        private final DayOfWeek firstDay;
        private final DayOfWeek lastSchoolDay;

        WeekStart(String code, DayOfWeek firstDay, DayOfWeek lastSchoolDay) {
            this.code = code;
            this.firstDay = firstDay;
            this.lastSchoolDay = lastSchoolDay;
        }

        public String code() {
            return code;
        }

        /** روزِ اولِ هفته؛ هفته‌های ذخیره‌شده (week_start) همیشه روی همین روزند. */
        public DayOfWeek firstDay() {
            return firstDay;
        }

        /** روزهای درسیِ هفته به ترتیب: شنبه تا چهارشنبه، یا دوشنبه تا جمعه. */
        public List<DayOfWeek> schoolDays() {
            List<DayOfWeek> days = new ArrayList<>();
            DayOfWeek day = firstDay;
            while (true) {
                days.add(day);
                if (day == lastSchoolDay) {
                    return List.copyOf(days);
                }
                day = day.plus(1);
            }
        }

        /** آغازِ هفته‌ای که {@code date} در آن است. */
        public LocalDate weekStartOf(LocalDate date) {
            return date.with(TemporalAdjusters.previousOrSame(firstDay));
        }

        public static WeekStart fromCode(String code) {
            for (WeekStart value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown week start code: " + code);
        }
    }

    enum SchoolStatus {
        ACTIVE, SUSPENDED
    }

    /**
     * مقطع تحصیلی مدرسه - نظام سه‌دوره‌ای رسمی ایران. هر مدرسه دقیقاً یک
     * مقطع دارد (یک ساختمان دبستان فقط پایه‌های ۱ تا ۶ دارد، نه بیشتر) -
     * academic.ClassEntity.gradeLevel هر کلاس باید در بازه‌ی همین مقطع
     * باشد (AcademicFacadeImpl.requireValidGradeLevel). این فقط طبقه‌بندی
     * است، نه شیوه‌ی نمره‌دهی - ماژول grades (هنوز ساخته نشده، CLAUDE.md
     * §11 Q8) از همین gradeLevel، شیوه‌ی نمره‌دهی (توصیفی برای پایه‌های
     * ۱ تا ۶، عددی برای ۷ تا ۱۲) را با یک قاعده‌ی ثابت مشتق خواهد کرد -
     * چیزی که عمداً در این فاز ساخته نشده چون هنوز مصرف‌کننده‌ای ندارد.
     */
    enum SchoolLevel {
        ELEMENTARY(1, 6),
        LOWER_SECONDARY(7, 9),
        UPPER_SECONDARY(10, 12);

        private final int minGrade;
        private final int maxGrade;

        SchoolLevel(int minGrade, int maxGrade) {
            this.minGrade = minGrade;
            this.maxGrade = maxGrade;
        }

        public int minGrade() {
            return minGrade;
        }

        public int maxGrade() {
            return maxGrade;
        }

        public boolean includesGrade(int gradeLevel) {
            return gradeLevel >= minGrade && gradeLevel <= maxGrade;
        }
    }
}
