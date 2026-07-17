package com.example.roshdeandishe.exam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Exam - موتور آزمون (پورت
 * {@code exam-maker-master/azmoon} از moshavere، MOSHAVERE_PORT_SPEC.md §5،
 * آخرین و عمیق‌ترین قدم پورت). به curriculum (سرفصل/سؤال)، academic
 * (دانش‌آموز)، studyplan (سرفصل‌های مطالعه‌شده‌ی اخیر برای آزمون خودکار
 * هفتگی) و iam (کاربر جاری برای teacherId) وابسته است.
 * <p>
 * دو خانواده‌ی متد اینجا هستند که مدل اعتماد متفاوتی دارند:
 * <ul>
 *   <li>متدهای «مدیریتی» (create/list/assign/section/result) با
 *   {@code schoolId} صریح فراخوانی می‌شوند - کنترلرهای @RequiresModule("EXAM")
 *   + @RequiresPermission این‌ها را گیت می‌کنند.</li>
 *   <li>متدهای exam-take (getExamForToken/getSectionQuestions/submitAnswer)
 *   رشته‌ی خام توکن جلسه‌ی آزمون را می‌گیرند، نه schoolId/studentId صریح -
 *   خودشان توکن را decode/expire-check می‌کنند (از طریق
 *   exam.internal.ExamTokenService) و schoolId را از خودِ آزمونِ resolve‌شده
 *   می‌گیرند. این دقیقاً همان «مکانیزم مجوز مستقل، نه @RequiresPermission
 *   معمول» است که MOSHAVERE_PORT_SPEC.md §5 برای ExamTakeController خواسته -
 *   ExamTakeController هرگز TenantContext/X-School-ID نمی‌بیند (permitAll +
 *   مستثنا در TenantResolverFilter، همان الگوی BaleWebhookController).</li>
 * </ul>
 * <p>
 * امتیازدهی (score/correct/incorrect/unanswered) در لحظه‌ی درخواست محاسبه
 * می‌شود، نه از یک Materialized View رفرش‌شده با Celery (که moshavere
 * داشت) - این پروژه معادل Celery ندارد و MOSHAVERE_PORT_SPEC.md §5 صریحاً
 * توصیه کرده این زیرساخت را از قبل نسازیم. rank/balance (انحراف معیار بین
 * همه‌ی دانش‌آموزان) هم به همین دلیل عمداً پورت نشده‌اند - مصنوعات همان
 * Materialized View هستند، نه داده‌ی هسته‌ای؛ اگر یک UI واقعاً به آن‌ها
 * نیاز پیدا کرد، به‌عنوان یک کوئری تجمیعیِ دیگر (نه زیرساخت async جدید)
 * اضافه می‌شوند.
 */
public interface ExamFacade {

    // ------------------------------------------------------------ authoring

    /**
     * ساخت یک آزمون COLLECTIVE (تألیف‌شده توسط معلم/ادمین). title نباید
     * خالی باشد؛ end باید بعد از start باشد و بازه‌ی start..end دست‌کم به
     * اندازه‌ی durationMinutes باشد - وگرنه InvalidExam (پورت validate
     * موshavere). برخلاف منبع، «start باید حداقل هم‌اکنون باشد» عمداً
     * اعتبارسنجی نشده - قید UX منبع بود، نه یک ناوردای دامنه‌ای واقعی
     * (بدون آن، ساخت آزمون از طریق seed/تست هم ساده‌تر می‌ماند).
     * teacherId از کاربر احرازشده‌ی جاری برداشته می‌شود و مثل
     * PlanEntity.teacherId ممکن است null باشد (فراخوانی بدون کاربر
     * احرازشده - تست/seed).
     */
    ExamId createCollectiveExam(Long schoolId, String title, LocalDateTime start, LocalDateTime end,
                                int durationMinutes);

    List<ExamInfo> listCollectiveExams(Long schoolId);

    ExamInfo getExam(Long schoolId, Long examId);

    /**
     * تخصیص گروهی دانش‌آموزان به یک آزمون COLLECTIVE - جایگزین
     * {@code partial_update} روی {@code CollectiveExam.students}
     * (ManyToMany جنگو) موshavere. هر studentId باید متعلق به همین مدرسه
     * باشد - وگرنه StudentNotFound. Idempotent برای هر جفت (تخصیص تکراری
     * خطا نمی‌دهد).
     */
    void assignStudents(Long schoolId, Long examId, List<Long> studentIds);

    List<Long> listAssignedStudents(Long schoolId, Long examId);

    /**
     * ساخت بخش با انتخاب دستیِ سؤال - پورت {@code SectionCreateSerializer}
     * موshavere. هر questionId باید متعلق به بانک سؤال همین مدرسه باشد -
     * وگرنه QuestionNotFound. topicId باید متعلق به همین مدرسه باشد -
     * وگرنه TopicNotFound. (examId, topicId) باید یکتا باشد - وگرنه
     * DuplicateSection.
     */
    SectionId createSection(Long schoolId, Long examId, Long topicId, String name, List<Long> questionIds);

    /**
     * ساخت بخش با انتخاب تصادفیِ سؤال از زیردرخت topicId - پورت
     * {@code SectionAutomaticCreateSerializer.pick_random_questions}
     * موshavere. اگر lazy=false و سؤال کافی نباشد - InsufficientQuestions؛
     * اگر lazy=true، همان تعداد موجود (حتی صفر) بدون خطا انتخاب می‌شود -
     * دقیقاً همان رفتار منبع برای ساخت خودکار آزمون هفتگی.
     */
    SectionId createSectionAuto(Long schoolId, Long examId, Long topicId, String name, int count, boolean lazy);

    List<SectionInfo> listSections(Long schoolId, Long examId);

    /**
     * ساخت خودکار آزمون هفتگیِ شخصیِ یک دانش‌آموز - پورت
     * {@code WeeklyExamViewSet.auto_generate/auto_create/auto_update}
     * موshavere. سرفصل‌های اخیر از {@code StudyPlanFacade.getRecentTopics}
     * می‌آیند؛ برای هر سرفصل یک بخش با تا ۵ سؤال تصادفی (lazy=true، مثل
     * منبع) ساخته می‌شود. اگر آزمون هفتگیِ همین (studentId, weekStart) از
     * قبل وجود داشته باشد ولی هنوز تلاشی برایش ثبت نشده - جایگزین می‌شود
     * (حذف کامل و بازساخت، پورت auto_update)؛ اگر تلاشی ثبت شده -
     * InvalidExam (برخلاف منبع که بی‌قید و شرط پاک می‌کرد؛ حذف کار
     * ثبت‌شده‌ی دانش‌آموز یک نگرانی واقعی جامعیت داده است، نه فقط سبک -
     * بهبود کوچک مشابه دقتِ اضافه‌شده در studyplan.copyWeek).
     * weekStart باید شنبه باشد - وگرنه InvalidExam.
     */
    ExamId generateWeeklyExam(Long schoolId, Long studentId, LocalDate weekStart);

    // ------------------------------------------------------- student "my"

    /**
     * آزمون‌های در دسترسِ یک دانش‌آموز مشخص: همه‌ی آزمون‌های WEEKLY خودش +
     * هر آزمون COLLECTIVE که به آن تخصیص داده شده. schoolId/studentId از
     * قبل توسط caller (MyExamsController، از طریق
     * AcademicFacade.findMyEnrollment) resolve شده‌اند - همان قرارداد
     * academic.MyEnrollmentController.
     */
    List<ExamInfo> listExamsForStudent(Long schoolId, Long studentId);

    /**
     * شروع یک تلاش و صدور توکن جلسه‌ی آزمون - پورت
     * {@code ExamTokenObtainSerializer.validate} + {@code Attempt.jwt_token}
     * موshavere. اعتبارسنجی: آزمون شروع شده و هنوز تمام نشده، و دانش‌آموز
     * مجاز است (مالک آزمون WEEKLY یا تخصیص‌یافته‌ی آزمون COLLECTIVE) -
     * وگرنه CannotAttempt.
     * <p>
     * برخلاف منبع (که برای تلاش تکراری همیشه خطا می‌داد)، این متد تا وقتی
     * تلاش موجود منقضی نشده idempotent است - فراخوانی دوباره همان توکن
     * (با انقضای دوباره‌محاسبه‌شده) را برمی‌گرداند، نه CannotAttempt؛ فقط
     * وقتی تلاشِ از قبل موجود واقعاً منقضی شده باشد AttemptExpired پرتاب
     * می‌شود («فقط آزمون تک‌تلاشی پشتیبانی می‌شود» - همان ناوردای منبع،
     * توکن تازه صادر نمی‌شود).
     */
    AttemptResult startAttempt(Long schoolId, Long examId, Long studentId);

    // -------------------------------------------------------- exam-take

    /**
     * جزئیات آزمون از روی توکن جلسه (رجوع به کلاس‌داک - مکانیزم مجوز
     * مستقل). توکن نامعتبر/منقضی - InvalidExamToken.
     */
    ExamTakeInfo getExamForToken(String token);

    /**
     * سؤال‌های یک بخش، همراه با پاسخِ قبلاً انتخاب‌شده (اگر بوده) - پورت
     * {@code SectionQuestionsReadSerializer} موshavere. عمداً
     * correctChoice/answer را حمل نمی‌کند - افشای پاسخ درست حین آزمون‌دهی.
     * بخش باید متعلق به آزمونِ همین توکن باشد - وگرنه SectionNotFound.
     */
    List<TakeQuestionInfo> getSectionQuestions(String token, Long sectionId);

    /**
     * ثبت/به‌روزرسانی/حذف پاسخ یک سؤال - پورت {@code SubmitSerializer}
     * موshavere. choice=null یعنی حذف پاسخ (نه ذخیره‌ی null)؛ در غیر این
     * صورت باید بین ۱ تا ۴ باشد - وگرنه InvalidSubmission. سؤال باید عضو
     * همین بخش باشد - وگرنه QuestionNotFound.
     */
    void submitAnswer(String token, Long sectionId, Long questionId, Integer choice);

    // ------------------------------------------------------------- results

    /**
     * نتیجه‌ی خودِ دانش‌آموز - فقط بعد از پایان زمان آزمون (ExamNotEnded)
     * و فقط اگر تلاشی ثبت کرده باشد (NoAttempt) - پورت
     * {@code StudentsExamResultViewSet} موshavere.
     */
    ExamResultSummary getMyResult(Long schoolId, Long examId, Long studentId);

    /**
     * فهرست دانش‌آموزانی که برای این آزمون تلاش ثبت کرده‌اند - سمت
     * مدرس/ادمین، پورت {@code InstructorsExamResultViewSet.list} موshavere.
     * فقط studentId/زمان شروع برمی‌گرداند - نام از طریق
     * iam.MembershipController سمت کلاینت resolve می‌شود، همان الگوی
     * finance.js/students.js.
     */
    List<AttemptedStudentInfo> listAttemptedStudents(Long schoolId, Long examId);

    /**
     * نتیجه‌ی یک دانش‌آموز مشخص - سمت مدرس/ادمین (بدون محدودیت مالکیت،
     * برخلاف getMyResult). همان قیدهای ExamNotEnded/NoAttempt.
     */
    ExamResultSummary getStudentResult(Long schoolId, Long examId, Long studentId);

    record ExamId(Long value) {
    }

    record SectionId(Long value) {
    }

    record ExamInfo(ExamId id, Long schoolId, ExamType type, String title, Long teacherId, Long studentId,
                    LocalDateTime start, LocalDateTime end, int durationMinutes, LocalDate weekStart,
                    List<SectionInfo> sections) {
    }

    record SectionInfo(SectionId id, ExamId examId, Long topicId, String name, int questionCount) {
    }

    record AttemptResult(String token, Instant expireAt) {
    }

    record ExamTakeInfo(ExamId id, String title, LocalDateTime start, LocalDateTime end, int durationMinutes,
                        List<SectionInfo> sections) {
    }

    /**
     * {@code selectedChoice} پاسخِ قبلاً ثبت‌شده‌ی همین دانش‌آموز است
     * (nullable) - پورت {@code first_choice} موshavere. عمداً بدون
     * correctChoice/answer.
     */
    record TakeQuestionInfo(Long questionId, String text, String choice1, String choice2, String choice3,
                            String choice4, Integer selectedChoice) {
    }

    record SectionResult(SectionId sectionId, String name, Long topicId, int questionCount, int correctCount,
                         int incorrectCount, int unansweredCount, double score) {
    }

    /**
     * {@code overallScore} میانگین ساده‌ی score همه‌ی بخش‌هاست (بدون
     * وزن‌دهی - moshavere هم چیزی جز per-section score نداشت).
     */
    record ExamResultSummary(ExamId examId, Long studentId, List<SectionResult> sections, double overallScore) {
    }

    record AttemptedStudentInfo(Long studentId, Instant startedAt) {
    }
}
