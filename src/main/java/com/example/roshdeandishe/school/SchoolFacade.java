package com.example.roshdeandishe.school;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    record SchoolId(Long value) {
    }

    record AcademicYearId(Long value) {
    }

    record AcademicYearInfo(AcademicYearId id, String code, int startYear) {
    }

    record SchoolInfo(SchoolId id, String name, SchoolStatus status, String timezone, String locale, SchoolLevel level,
                      String slug, WeekStart weekStart) {
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
