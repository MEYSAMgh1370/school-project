package com.example.roshdeandishe.school;

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
     * فهرست سراسریِ سال‌های تحصیلی (مشترک بین همه‌ی مدرسه‌ها، فقط‌خواندنی در
     * برنامه) از قدیم به جدید - openspec/changes/academic-year.md. شاملِ سال‌های
     * بسته هم هست ({@code open = false}: اپراتور هنوز برای انتخاب بازشان نکرده).
     */
    List<AcademicYearInfo> listAcademicYears();

    /**
     * «سال تحصیلی جاریِ» یک مدرسه؛ خالی یعنی هنوز انتخاب نشده (هیچ سالی حدس
     * زده نمی‌شود). SchoolError.SchoolNotFound اگر مدرسه وجود نداشته باشد.
     */
    Optional<AcademicYearInfo> findCurrentAcademicYear(SchoolId schoolId);

    /**
     * انتخاب سال تحصیلی جاریِ مدرسه از سال‌های بازِ فهرست سراسری. فقط همین یک
     * مقدار عوض می‌شود - هیچ داده‌ای حذف یا تغییر نمی‌کند، و شروع/پایانِ سال
     * همین انتخابِ مدیرِ مدرسه است (نه تاریخِ ثابت). SchoolError.AcademicYearNotFound
     * اگر سال در فهرست نباشد؛ SchoolError.AcademicYearNotOpen اگر هنوز باز نشده؛
     * SchoolError.SchoolNotFound اگر مدرسه نباشد.
     */
    void setCurrentAcademicYear(SchoolId schoolId, AcademicYearId academicYearId);

    record SchoolId(Long value) {
    }

    record AcademicYearId(Long value) {
    }

    record AcademicYearInfo(AcademicYearId id, String code, int startYear, boolean open) {
    }

    record SchoolInfo(SchoolId id, String name, SchoolStatus status, String timezone, String locale, SchoolLevel level,
                      String slug) {
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
