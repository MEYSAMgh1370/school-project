package com.example.roshdeandishe.academic;

import com.example.roshdeandishe.academic.AcademicFacade.CourseInfo;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

/**
 * برنامهٔ کلاسی (openspec/changes/class-timetable.md): زنگ‌ها، فعالیت‌های
 * فوق‌برنامه و خانه‌های برنامه. جدا از {@link AcademicFacade} است تا آن
 * اینترفیس بزرگ‌تر نشود؛ همان ماژول و همان مرز است. معلم و دانش‌آموز با شناسه‌ی
 * مات ارجاع می‌شوند؛ نامِ معلم را فراخوان از iam می‌گیرد.
 * <p>
 * خطاها همان {@code AcademicException} با {@code AcademicError} هستند.
 */
public interface TimetableFacade {

    // ---- زنگ‌ها (به‌ازای مدرسه و سال تحصیلی) ----

    /** زنگ‌های یک سال به ترتیب ساعتِ شروع. سالِ نامعتبر: {@code InvalidAcademicYear}. */
    List<PeriodInfo> listPeriods(Long schoolId, String academicYear);

    /**
     * زنگِ تازه؛ شماره‌ی ترتیب خودکار (بیشینه + ۱) است. پایان باید بعد از شروع
     * باشد ({@code InvalidPeriod}) و با زنگ‌های دیگرِ همان مدرسه و سال هم‌پوشانی
     * نداشته باشد ({@code PeriodOverlaps}).
     */
    PeriodInfo createPeriod(Long schoolId, String academicYear, String label, LocalTime startTime, LocalTime endTime);

    /** همان اعتبارسنجیِ ساخت؛ سال و شماره‌ی ترتیب تغییر نمی‌کند. */
    PeriodInfo updatePeriod(Long schoolId, Long periodId, String label, LocalTime startTime, LocalTime endTime);

    /** زنگی که خانه‌ی برنامه دارد حذف نمی‌شود ({@code PeriodHasEntries}). */
    void deletePeriod(Long schoolId, Long periodId);

    // ---- فعالیت‌های فوق‌برنامه ----

    /** فعالیت‌های خودِ مدرسه به‌علاوه‌ی پیش‌فرض‌های پلتفرم ({@code platformDefault = true}). */
    List<ActivityInfo> listActivities(Long schoolId);

    ActivityInfo createActivity(Long schoolId, String name);

    /** فقط فعالیتِ خودِ مدرسه؛ پیش‌فرضِ پلتفرم: {@code ActivityNotEditable}. */
    ActivityInfo renameActivity(Long schoolId, Long activityId, String name);

    /** فقط فعالیتِ خودِ مدرسه و بدون خانه‌ی برنامه ({@code ActivityInUse}). */
    void deleteActivity(Long schoolId, Long activityId);

    // ---- خانه‌های برنامه ----

    /** درس‌های هم‌پایه‌ی کلاس، شاملِ غیرنمره‌پذیر - انتخابگرِ برنامه (نه دفتر نمرات). */
    List<CourseInfo> listCoursesForTimetable(Long schoolId, Long classId);

    /** خانه‌های یک کلاس در یک سال. */
    List<EntryInfo> listEntriesForClass(Long schoolId, Long classId, String academicYear);

    /**
     * پر کردن/جایگزینیِ یک خانه (کلاس، زنگ، روز). دقیقاً یکی از
     * {@code courseId}/{@code activityId} باید پر باشد
     * ({@code InvalidTimetableEntry})؛ درس باید هم‌پایه‌ی کلاس باشد
     * ({@code CourseNotForClass})؛ فعالیت باید از خودِ مدرسه یا پیش‌فرضِ پلتفرم
     * باشد؛ {@code teacherUserId} (اختیاری) باید معلمِ فعالِ همین مدرسه باشد
     * ({@code TeacherNotEligible}) و در همان روز و زنگ در کلاس دیگری نباشد
     * ({@code TeacherDoubleBooked}).
     */
    EntryInfo setEntry(Long schoolId, Long classId, Long periodId, TimetableDay weekday,
                       Long courseId, Long activityId, Long teacherUserId);

    /** خالی کردنِ یک خانه؛ خانه‌ی خالی خطا نیست (idempotent). */
    void clearEntry(Long schoolId, Long classId, Long periodId, TimetableDay weekday);

    // ---- خواندن برای خودسرویس (فراخوان، هویت را از iam/academic می‌گیرد) ----

    /** خانه‌های چند کلاس در یک سال؛ سالِ بدون زنگ یا کلاسِ بی‌خانه: خالی، نه خطا. */
    List<EntryInfo> listEntriesForClasses(Long schoolId, String academicYear, Collection<Long> classIds);

    /** خانه‌هایی که این کاربر معلمِ آن‌هاست، در یک سال. */
    List<EntryInfo> listEntriesForTeacher(Long schoolId, String academicYear, Long teacherUserId);

    record PeriodInfo(Long id, Long schoolId, String academicYear, int position, String label,
                      LocalTime startTime, LocalTime endTime) {
    }

    record ActivityInfo(Long id, String name, boolean platformDefault) {
    }

    record EntryInfo(Long id, Long classId, String className, Long periodId, TimetableDay weekday,
                     Long courseId, String courseName, Long activityId, String activityName,
                     Long teacherUserId) {
    }
}
