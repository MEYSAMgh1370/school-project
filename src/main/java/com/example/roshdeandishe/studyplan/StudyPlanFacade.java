package com.example.roshdeandishe.studyplan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول StudyPlan: برنامه‌ی مطالعاتیِ هر مدرسه
 * (وزن‌دهی درس‌ها به تفکیک هفته) + الگوریتم پیشنهاد خودکارِ زمان آزاد
 * دانش‌آموز - پورت plan/models.py + study_table (exam-maker-master و
 * agency-panel-master) از moshavere (MOSHAVERE_PORT_SPEC.md §6).
 * <p>
 * محتوا per-school است (هر مدرسه برنامه‌های خودش را دارد)، همان تصمیم
 * صریح curriculum (MOSHAVERE_PORT_SPEC.md §2). {@code studentId} در همه‌ی
 * متدها همان قرارداد ارجاع مبهم بدون FK است که در جاهای دیگر این پروژه
 * هم به‌کار رفته (StudentGuardianEntity.guardianUserId، ClassEntity.teacherId) -
 * تا academic لازم نباشد از وجود studyplan خبر داشته باشد؛ اعتبارسنجیِ
 * تعلق studentId به این مدرسه از طریق AcademicFacade انجام می‌شود، نه FK.
 */
public interface StudyPlanFacade {

    /**
     * ساخت یک برنامه‌ی مطالعاتی. {@code courseInfo} پورتِ course_info در
     * منبع است (COURSE_INFO_SCHEMA) - هر آیتم باید به یک Topic واقعیِ همین
     * مدرسه اشاره کند (وگرنه StudyPlanError.TopicNotFound)، weight باید
     * >=0 و priority باید >=1 باشد (وگرنه StudyPlanError.InvalidPlan).
     * فیلد priority مثل منبع توسط الگوریتم پیشنهاد مصرف نمی‌شود (فقط در
     * table_suggester.py هرگز خوانده نشده)؛ همچنان برای وفاداری به شمای
     * منبع نگه‌داشته شده.
     */
    PlanId createPlan(Long schoolId, Long teacherId, String title, List<CourseWeight> courseInfo);

    PlanInfo getPlan(Long schoolId, Long planId);

    List<PlanInfo> listPlans(Long schoolId);

    /**
     * تخصیص یک برنامه به یک دانش‌آموز. Idempotent-به-معنای-جایگزینی: اگر
     * این دانش‌آموز از قبل برنامه‌ای در این مدرسه داشته باشد، جایگزین
     * می‌شود (همان الگوی enrollStudent در academic برای جابه‌جایی کلاس) -
     * برخلاف منبع که Student.plan یک FK تکی بود، اینجا یک جدول تخصیص
     * جداگانه با unique(schoolId, studentId) است.
     */
    StudentPlanAssignmentId assignPlanToStudent(Long schoolId, Long studentId, Long planId);

    Optional<PlanId> findAssignedPlan(Long schoolId, Long studentId);

    /**
     * ثبت اینکه یک سرفصل ("course") در یک هفته‌ی مشخص (باید شنبه باشد -
     * وگرنه StudyPlanError.InvalidWeekCourse) برای این برنامه در نظر
     * گرفته شده، به‌همراه حداکثر ۱۰ زیر-سرفصل (پورتِ ArrayField سایز ۱۰
     * در منبع، اینجا به‌صورت جدول join - MOSHAVERE_PORT_SPEC.md §6).
     */
    WeekCourseId createWeekCourse(Long schoolId, Long planId, LocalDate weekStartDate, Long topicId, List<Long> subTopicIds);

    List<WeekCourseInfo> listWeekCourses(Long schoolId, Long planId);

    /**
     * برنامه‌ی هفتگیِ ثابتِ زمان آزاد یک دانش‌آموز برای مطالعه - در صورت
     * نبود، یک برنامه‌ی خالی ساخته می‌شود (پورتِ
     * get_or_create_interval_schedule در منبع).
     */
    ScheduleInfo getOrCreateSchedule(Long schoolId, Long studentId);

    /**
     * جایگزینیِ برنامه‌ی «این هفته»ی دانش‌آموز (current_schedule در منبع).
     * برنامه‌ی پیش‌فرض (default_schedule) از این مسیر قابل تغییر نیست -
     * برای آن باید یک متد جدا اضافه شود، اگر/وقتی واقعاً لازم شد.
     */
    ScheduleInfo updateCurrentSchedule(Long schoolId, Long studentId, Map<String, List<DaySlot>> currentSchedule);

    /**
     * تولید خودکار بازه‌های مطالعه‌ی یک هفته (باید از شنبه شروع شود) روی
     * زمان‌های آزادِ برنامه‌ی مؤثرِ دانش‌آموز (current_schedule اگر به‌تازگی
     * ویرایش شده، وگرنه default_schedule) - پورت مستقیم
     * table_suggester.py (SliceScheduleInterval + FillSlices + Suggestor).
     * اگر آن هفته از قبل بازه‌ای داشته باشد، StudyPlanError.WeekNotEmpty.
     */
    List<StudyIntervalInfo> suggestWeek(Long schoolId, Long studentId, LocalDate weekStartDate);

    List<StudyIntervalInfo> getWeek(Long schoolId, Long studentId, LocalDate weekStartDate);

    void clearWeek(Long schoolId, Long studentId, LocalDate weekStartDate);

    /**
     * کپیِ بازه‌های یک هفته از یک دانش‌آموز به چند دانش‌آموز دیگر - عمل
     * معلم/ادمین (پورتِ StudyViewSet.copy_week). Best-effort مثل منبع: هر
     * دانش‌آموز مقصد نتیجه‌ی خودش را می‌گیرد، شکست روی یکی مانع بقیه نیست.
     */
    List<CopyWeekResult> copyWeek(Long schoolId, Long sourceStudentId, LocalDate weekStartDate, List<Long> targetStudentIds);

    StudyIntervalId updateStudyInterval(Long schoolId, Long intervalId, Boolean done, Integer testCount);

    /**
     * سرفصل‌های (+ زیر-سرفصل‌ها) مطالعه‌شده در هفته‌ی جاریِ تقویمی - پورتِ
     * get_week_intervals_topics، متدی که ماژول exam (پورت گام ۴) برای
     * تولید خودکار آزمون هفتگی به آن نیاز دارد (MOSHAVERE_PORT_SPEC.md §5/§6).
     */
    List<RecentTopic> getRecentTopics(Long schoolId, Long studentId);

    record PlanId(Long value) {
    }

    record StudentPlanAssignmentId(Long value) {
    }

    record WeekCourseId(Long value) {
    }

    record StudyIntervalId(Long value) {
    }

    /**
     * یک آیتمِ course_info: {@code topicId} همان "course" در منبع (اشاره
     * به یک Topic در curriculum)، {@code weight} سهم این درس از کل زمان
     * آزاد هفته (۰ تا ۱)، {@code priority} نگه‌داشته‌شده برای وفاداری به
     * شمای منبع ولی توسط الگوریتم پیشنهاد مصرف نمی‌شود (رجوع به createPlan).
     */
    record CourseWeight(Long topicId, double weight, int priority) {
    }

    record PlanInfo(PlanId id, Long schoolId, Long teacherId, String title, List<CourseWeight> courseInfo) {
    }

    record WeekCourseInfo(
            WeekCourseId id, Long schoolId, Long planId, LocalDate weekStartDate, Long topicId, List<Long> subTopicIds
    ) {
    }

    /**
     * یک بازه‌ی زمانیِ آزاد در یک روز، به‌صورت رشته‌ی "HH:mm" (همان قالب
     * منبع) - عمداً نه LocalTime، تا ذخیره‌سازی JSON بدون نیاز به ماژول
     * زمانِ Jackson ساده بماند (رجوع به توضیح PlanEntity.courseInfoJson).
     */
    record DaySlot(String start, String end) {
    }

    /**
     * {@code effectiveSchedule} مشتق‌شده در لحظه‌ی خواندن است (هرگز
     * ذخیره نمی‌شود): اگر current_schedule وجود دارد و در ۷ روز گذشته
     * ویرایش شده، همان؛ وگرنه default_schedule - جایگزینِ ساده‌ترِ
     * property محاسباتیِ منبع (رجوع به توضیح IntervalScheduleEntity).
     */
    record ScheduleInfo(
            Long schoolId,
            Long studentId,
            Map<String, List<DaySlot>> defaultSchedule,
            Map<String, List<DaySlot>> currentSchedule,
            Map<String, List<DaySlot>> effectiveSchedule,
            LocalDateTime lastEditedAt
    ) {
    }

    record StudyIntervalInfo(
            StudyIntervalId id,
            Long schoolId,
            Long studentId,
            Long topicId,
            List<Long> subTopicIds,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean done,
            int testCount
    ) {
    }

    record CopyWeekResult(Long targetStudentId, boolean success, String errorMessage) {
    }

    record RecentTopic(Long topicId, List<Long> subTopicIds) {
    }
}
