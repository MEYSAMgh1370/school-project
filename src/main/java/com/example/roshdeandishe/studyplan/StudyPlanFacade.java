package com.example.roshdeandishe.studyplan;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول StudyPlan (پورت moshavere،
 * MOSHAVERE_PORT_SPEC.md §6).
 * <p>
 * وزن‌دهی درس‌های برنامه ({@code CourseWeight}) به‌جای ستون JSON موshavere
 * (course_info) یک جدول فرزند واقعی است - همان ترجیح join table بر ستون
 * آرایه/JSON که خود spec برای WeekCourseTopics به آن استناد می‌کند
 * (الگوی RolePermissionEntity)؛ برنامه‌های زمانی هفتگی
 * (default/currentSchedule) اما ساختار تو در توی نامنظم دارند و طبق spec
 * jsonb می‌مانند. ترتیب پیمایش وزن‌ها در الگوریتم پیشنهاد، priority صعودی
 * سپس id است - جایگزینِ قطعیِ «ترتیب آرایه‌ی JSON» موshavere که با join
 * table دیگر وجود ندارد.
 * <p>
 * هفته‌ی درسی از شنبه شروع می‌شود (تقویم میلادی، DayOfWeek.SATURDAY) -
 * چک «weekday پارسی» موshavere فقط محاسبه‌ی همین شنبه بود و به تقویم جلالی
 * نیازی ندارد؛ سرور طبق قرارداد موجود Gregorian-only می‌ماند.
 */
public interface StudyPlanFacade {

    /**
     * ساخت برنامه با وزن‌دهی درس‌ها. هر topicId باید سرفصلی از همین مدرسه
     * باشد (اعتبارسنجی از طریق CurriculumFacade - وگرنه TopicNotFound).
     * عنوان خالی، فهرست وزن خالی، وزن منفی، priority کمتر از ۱ یا سرفصل
     * تکراری - InvalidPlan. teacherId (سازنده) از کاربر احرازشده‌ی جاری
     * برداشته می‌شود و مثل ClassEntity.teacherId ممکن است null باشد.
     */
    PlanId createPlan(Long schoolId, String title, List<CourseWeight> weights);

    List<PlanInfo> listPlans(Long schoolId);

    /**
     * ثبت سرفصل‌های یک هفته‌ی مشخص از برنامه. weekStart باید شنبه باشد
     * (InvalidWeekStart - همان قید DB موshavere)؛ حداکثر ۱۰ زیرسرفصل
     * (سقف ArrayField موshavere)؛ (plan, weekStart, courseTopic) یکتاست
     * (DuplicateWeekCourse).
     */
    WeekCourseId createWeekCourse(Long schoolId, Long planId, LocalDate weekStart,
                                  Long courseTopicId, List<Long> subTopicIds);

    List<WeekCourseInfo> listWeekCourses(Long schoolId, Long planId);

    /**
     * تخصیص برنامه به دانش‌آموز - upsert: هر دانش‌آموز در هر لحظه دقیقاً
     * یک برنامه دارد (همان FK تکی Student.plan در moshavere)؛ تخصیص جدید
     * جایگزین قبلی می‌شود، نه رکورد تکراری.
     */
    void assignPlanToStudent(Long schoolId, Long planId, Long studentId);

    List<AssignmentInfo> listAssignments(Long schoolId, Long planId);

    /**
     * برنامه‌ی زمانی هفتگی دانش‌آموز. اگر هنوز ثبت نشده باشد، نمایش
     * پیش‌فرضِ خالی برمی‌گردد (بدون persist - برخلاف get_or_create
     * موshavere؛ GET نباید بنویسد). {@code currentActive} یعنی
     * currentSchedule در هفته‌ی جاری (از شنبه‌ی اخیر) ویرایش شده و همان
     * ملاک پیشنهاد خودکار است - پورت property «schedule» موshavere با
     * مقایسه‌ی timestamp ساده.
     */
    ScheduleInfo getSchedule(Long schoolId, Long studentId);

    void setDefaultSchedule(Long schoolId, Long studentId, Map<Weekday, List<TimeRange>> schedule);

    /**
     * schedule=null یعنی پاک کردن برنامه‌ی موقت (بازگشت به پیش‌فرض).
     */
    void setCurrentSchedule(Long schoolId, Long studentId, Map<Weekday, List<TimeRange>> schedule);

    /**
     * پیشنهاد خودکار جدول مطالعه‌ی یک هفته - پورت مستقیم table_suggester
     * موshavere (SliceScheduleInterval/FillSlices/Suggestor). weekStart باید
     * شنبه باشد، هفته باید خالی باشد (WeekNotEmpty)، دانش‌آموز باید برنامه
     * داشته باشد (PlanNotAssigned) و مجموع زمان آزاد دست‌کم ۱۸۰ دقیقه
     * (ScheduleTooSmall). بازه‌های ساخته‌شده برمی‌گردند.
     */
    List<IntervalInfo> suggestWeek(Long schoolId, Long studentId, LocalDate weekStart);

    /**
     * بازه‌های هفته‌ی شامل {@code date} (نرمال‌شده به هفته‌ی شنبه-محور،
     * همان get_start_end_week موshavere - لازم نیست خودِ date شنبه باشد).
     */
    List<IntervalInfo> getWeek(Long schoolId, Long studentId, LocalDate date);

    /**
     * حذف همه‌ی بازه‌های هفته‌ی weekStart (باید شنبه باشد).
     */
    void clearWeek(Long schoolId, Long studentId, LocalDate weekStart);

    /**
     * کپی بازه‌های یک هفته‌ی دانش‌آموز مبدأ برای چند دانش‌آموز دیگر
     * (عملیات گروهی معلم در moshavere). به‌ازای هر مقصد نتیجه‌ی جدا
     * برمی‌گردد؛ شکست یک مقصد (مثلاً هم‌پوشانی) بقیه را متوقف نمی‌کند.
     * برای هر مقصد اول همه‌ی بازه‌ها چک می‌شوند بعد ثبت - کپی نیمه‌کاره
     * برای یک مقصد پیش نمی‌آید (بهبود کوچک نسبت به ثبت ردیف‌به‌ردیف
     * موshavere). زیرسرفصل‌ها مثل مبدأ کپی نمی‌شوند (همان رفتار
     * copy_week موshavere که فقط topic را می‌برد).
     */
    List<CopyResult> copyWeek(Long schoolId, Long sourceStudentId, LocalDate weekStart,
                              List<Long> targetStudentIds);

    /**
     * ثبت دستی یک بازه‌ی مطالعه - با چک هم‌پوشانی با بازه‌های موجود
     * (IntervalOverlaps، پورت check_time_overlap موshavere).
     */
    IntervalId createInterval(Long schoolId, Long studentId, Long topicId, List<Long> subTopicIds,
                              LocalDateTime start, LocalDateTime end);

    /**
     * فقط انجام‌شدن/تعداد تست قابل ویرایش‌اند (همان
     * StudyIntervalUpdateSerializer موshavere - زمان و سرفصل read-only).
     * پارامتر null یعنی «بدون تغییر».
     */
    IntervalInfo updateInterval(Long schoolId, Long intervalId, Boolean done, Integer testCount);

    /**
     * همان {@link #updateInterval}، به‌علاوه‌ی چک مالکیت: بازه باید متعلق
     * به همین {@code studentId} باشد، وگرنه IntervalNotFound (نه یک خطای
     * دسترسیِ جدا - همان قرارداد «مال مدرسه‌ی دیگر هم یافت نشد است» که
     * requireIntervalInSchool از قبل برای schoolId اعمال می‌کند). لازم
     * برای مسیر خودسرویسِ دانش‌آموز (MyStudyIntervalController) که برخلاف
     * updateInterval معمولی (پشت STUDYPLAN_MANAGE، فقط برای مشاور/معلم)
     * هیچ اجازه‌ی سطح-مدرسه‌ای پشت سرش نیست - بدون این چک، یک دانش‌آموز
     * می‌توانست با حدس‌زدن intervalId بازه‌ی دانش‌آموز دیگری را ویرایش کند.
     */
    IntervalInfo updateOwnInterval(Long schoolId, Long studentId, Long intervalId, Boolean done, Integer testCount);

    void deleteInterval(Long schoolId, Long intervalId);

    /**
     * سرفصل‌های مطالعه‌شده‌ی هفته‌ی جاری دانش‌آموز (متمایز، با
     * زیرسرفصل‌های تجمیع‌شده) - پورت get_week_intervals_topics؛ متدی که
     * ساخت خودکار آزمون هفتگی در ماژول exam به آن وابسته خواهد بود
     * (MOSHAVERE_PORT_SPEC.md §5). studentId به‌تنهایی کافی است چون
     * شناسه‌ی StudentEntity خودش مدرسه-محدود است.
     */
    List<RecentTopic> getRecentTopics(Long studentId);

    /**
     * روزهای هفته. هر کلید یک «روز واقعی» است (SAT همیشه شنبه)، نه جایگاه در هفته؛
     * کلیدهای JSON برنامه‌های زمانی همین‌اند، پس برنامه‌ی ذخیره‌شده‌ی مدرسه‌ی شنبه‌محور
     * با آمدن مدرسه‌ی دوشنبه‌محور (W9) معنایش عوض نمی‌شود. ترتیبِ برش/تخصیص الگوریتم
     * پیشنهاد از روز شروع هفته‌ی مدرسه می‌آید ({@link #weekStartingOn}): برای مدرسه‌ی
     * شنبه‌محور همان ترتیب قدیمی شنبه تا جمعه - ترتیب fri-اول موshavere فقط عارضه‌ی
     * مرتب‌سازی کلیدهای jsonb پستگرس بود، نه تصمیم.
     */
    enum Weekday {
        SAT(java.time.DayOfWeek.SATURDAY), SUN(java.time.DayOfWeek.SUNDAY), MON(java.time.DayOfWeek.MONDAY),
        TUE(java.time.DayOfWeek.TUESDAY), WED(java.time.DayOfWeek.WEDNESDAY), THU(java.time.DayOfWeek.THURSDAY),
        FRI(java.time.DayOfWeek.FRIDAY);

        private final java.time.DayOfWeek dayOfWeek;

        Weekday(java.time.DayOfWeek dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public java.time.DayOfWeek dayOfWeek() {
            return dayOfWeek;
        }

        /** هر ۷ روز به ترتیبِ هفته‌ای که از {@code first} شروع می‌شود. */
        public static java.util.List<Weekday> weekStartingOn(java.time.DayOfWeek first) {
            java.util.List<Weekday> days = new java.util.ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                days.add(of(first.plus(i)));
            }
            return java.util.List.copyOf(days);
        }

        public static Weekday of(java.time.DayOfWeek dayOfWeek) {
            for (Weekday day : values()) {
                if (day.dayOfWeek == dayOfWeek) {
                    return day;
                }
            }
            throw new IllegalArgumentException("Unknown day: " + dayOfWeek);
        }
    }

    record PlanId(Long value) {
    }

    record WeekCourseId(Long value) {
    }

    record IntervalId(Long value) {
    }

    /**
     * وزن‌دهی یک درس در برنامه - همان {course, weight, priority} اسکیمای
     * course_info موshavere (weight >= 0, priority >= 1).
     */
    record CourseWeight(Long topicId, double weight, int priority) {
    }

    record PlanInfo(PlanId id, Long schoolId, Long teacherId, String title, List<CourseWeight> weights) {
    }

    record WeekCourseInfo(WeekCourseId id, PlanId planId, LocalDate weekStart,
                          Long courseTopicId, List<Long> subTopicIds) {
    }

    record AssignmentInfo(Long studentId, PlanId planId, Instant assignedAt) {
    }

    /**
     * یک بازه‌ی زمان آزاد در روز، ساعت دیواری "HH:mm" - همان قالب رشته‌ای
     * موshavere؛ اعتبارسنجی (قالب، start<end، عدم هم‌پوشانی) در facade.
     */
    record TimeRange(String start, String end) {
    }

    record ScheduleInfo(Long studentId,
                        Map<Weekday, List<TimeRange>> defaultSchedule,
                        Map<Weekday, List<TimeRange>> currentSchedule,
                        Instant lastEditedAt,
                        boolean currentActive) {
    }

    record IntervalInfo(IntervalId id, Long studentId, Long topicId, List<Long> subTopicIds,
                        LocalDateTime start, LocalDateTime end, boolean done, int testCount) {
    }

    record CopyResult(Long studentId, boolean ok, String message) {
    }

    record RecentTopic(Long topicId, List<Long> subTopicIds) {
    }
}
