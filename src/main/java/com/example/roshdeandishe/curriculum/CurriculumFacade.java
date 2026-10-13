package com.example.roshdeandishe.curriculum;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Curriculum: تاکسونومی سلسله‌مراتبی
 * سرفصل‌ها (Topic) + بانک سؤال چهارگزینه‌ای (Question) - پورت
 * exam_generator از moshavere (MOSHAVERE_PORT_SPEC.md §4).
 * <p>
 * محتوا per-school است، نه کتابخانه‌ی مشترک پلتفرمی - تصمیم صریح
 * MOSHAVERE_PORT_SPEC.md §2. درخت Topic یک adjacency list ساده است
 * (parentId)، عمداً نه بازنمایی دوگانه‌ی moshavere (ltree + درخت pickle
 * شده در حافظه) - یک منبع حقیقت.
 */
public interface CurriculumFacade {

    /**
     * ساخت یک سرفصل. {@code parentId} nullable است (null = سرفصل ریشه).
     * والد باید وجود داشته باشد و متعلق به همین مدرسه باشد - وگرنه
     * CurriculumError.TopicNotFound (والدِ مدرسه‌ی دیگر هم از دید این
     * مدرسه «یافت نشد» است، همان قرارداد requireClassExists در academic).
     * چون والد باید از قبل موجود باشد و عملیات جابه‌جایی (تغییر والد)
     * وجود ندارد، حلقه در درخت ساختاراً ناممکن است.
     */
    TopicId createTopic(Long schoolId, Long parentId, String name, Integer sortOrder);

    /**
     * ثبت/به‌روزرسانیِ یک سرفصلِ پلتفرمی (school_id تهی) با کلیدِ {@code sourceCode}: موجود → نام، والد و ترتیب
     * عوض می‌شود؛ وگرنه ساخته می‌شود. {@code parentSourceCode} تهی = ریشه؛ والد باید سرفصلِ پلتفرمی با همان کدِ منبع
     * باشد (ParentTopicNotFound وگرنه) و نباید خودِ سرفصل یا یکی از نوادگانش باشد (InvalidTopic). کد یا نامِ خالی
     * InvalidTopic.
     */
    TopicInfo upsertPlatformTopic(String sourceCode, String parentSourceCode, String name, int sortOrder);

    /**
     * سؤال پلتفرمی را با کلید {@code sourceCode} upsert می‌کند و برمی‌گرداند آیا تازه ساخته شد. سرفصل باید پلتفرمی
     * باشد (InvalidQuestion وگرنه)؛ {@code correctChoice} از ۱ تا ۴ است و هر چهار گزینه باید متن داشته باشند.
     */
    boolean upsertPlatformQuestion(String sourceCode, String topicSourceCode, String text, List<String> choices,
                                   int correctChoice, String answer);

    /**
     * فهرست تختِ همه‌ی سرفصل‌های یک مدرسه (مرتب بر اساس sortOrder سپس id).
     */
    List<TopicInfo> listTopics(Long schoolId);

    /**
     * کدهای منبع (source_code) → شناسه‌ی سرفصل، فقط برای سرفصل‌های قابل‌مشاهده برای مدرسه (پلتفرمی یا خودِ
     * مدرسه). کدِ ناشناخته یا نامرئی در نتیجه نمی‌آید؛ کدها strip می‌شوند.
     */
    java.util.Map<String, Long> resolveTopicIdsBySourceCode(Long schoolId, java.util.Collection<String> sourceCodes);

    /**
     * درخت کامل سرفصل‌های یک مدرسه - جایگزین مسیرخوانی درخت pickle شده‌ی
     * moshavere. برخلاف moshavere (که یک ریشه‌ی سراسری داشت)، هر مدرسه
     * می‌تواند چند سرفصل ریشه داشته باشد، پس خروجی فهرست ریشه‌هاست.
     */
    List<TopicTreeNode> getTopicTree(Long schoolId);

    /**
     * سرفصل + همه‌ی نوادگانش به‌صورت یک گره‌ی درختی. سرفصل باید برای مدرسه
     * قابل‌مشاهده باشد (پلتفرمی یا متعلق به همین مدرسه) - وگرنه TopicNotFound.
     * نوادگان هم فقط از میان سرفصل‌های قابل‌مشاهده برای مدرسه‌اند.
     */
    TopicTreeNode getTopicSubtree(Long schoolId, Long topicId);

    /**
     * ثبت یک سؤال چهارگزینه‌ای روی یک سرفصل. اعتبارسنجی: متن و هر چهار
     * گزینه غیرخالی، correctChoice در بازه‌ی ۱ تا ۴ - وگرنه
     * CurriculumError.InvalidQuestion. سرفصل باید متعلق به همین مدرسه
     * باشد - وگرنه TopicNotFound.
     */
    QuestionId createQuestion(Long schoolId, NewQuestion question);

    /**
     * فهرست سؤال‌های یک مدرسه با فیلتر اختیاری. سمنتیک مهمِ پورت‌شده از
     * QuestionFilter در moshavere: فیلترِ سرفصل شامل «کل زیر درخت» آن
     * سرفصل است (descendants)، نه فقط خودش - فیلتر دشواری فقط سؤال‌هایی
     * را برمی‌گرداند که difficulty آن‌ها در مجموعه باشد (سؤال بدون
     * difficulty با فیلترِ فعال حذف می‌شود، همان رفتار IN در منبع).
     */
    List<QuestionInfo> listQuestions(Long schoolId, QuestionFilter filter);

    /**
     * سؤال‌های این شناسه‌ها که برای مدرسه قابل‌مشاهده‌اند (پلتفرمی یا خودِ مدرسه). شناسه‌ی ناشناخته یا مالِ مدرسه‌ی
     * دیگر در نتیجه نمی‌آید. خواننده‌ی عمومی برای ماژول‌های دیگر (مثل questionbox).
     */
    List<QuestionInfo> findQuestionsVisibleToSchool(Long schoolId, java.util.Collection<Long> questionIds);

    /**
     * ثبت گزارش مشکل یک سؤال. سؤال باید برای مدرسه قابل‌مشاهده باشد (پلتفرمی یا متعلق به همین مدرسه) - وگرنه
     * QuestionNotFound. نوع گزارش الزامی است و توضیح حداکثر ۱۰۰۰ نویسه (InvalidReport وگرنه). خودِ سؤال
     * هرگز تغییر نمی‌کند (D10)؛ گزارش تکراری مجاز است.
     */
    ReportId createReport(Long schoolId, Long reporterUserId, Long questionId, ReportType reportType, String comment);

    /** صف مدرسه: همه‌ی گزارش‌هایی که کاربران این مدرسه ثبت کرده‌اند؛ {@code status} تهی = همه. جدیدترین اول. */
    List<QuestionReportInfo> listReportsForSchool(Long schoolId, ReportStatus status);

    /** گزارش‌هایی که خودِ این کاربر در این مدرسه ثبت کرده است (دید دانش‌آموز). جدیدترین اول. */
    List<QuestionReportInfo> listReportsByReporter(Long schoolId, Long userId);

    /**
     * تغییر وضعیت یک گزارشِ سؤالِ اختصاصیِ همین مدرسه (question.school_id == schoolId). گزارشِ سؤال پلتفرمی
     * یا مدرسه‌ی دیگر ReportNotFound است. فقط OPEN→REVIEWED، REVIEWED→FIXED و REVIEWED→CLOSED مجاز است
     * (InvalidReportTransition وگرنه). {@code note} اختیاری و حداکثر ۱۰۰۰ نویسه است.
     */
    QuestionReportInfo updateSchoolReport(Long schoolId, Long reportId, ReportStatus newStatus, String note);

    /** صف پلتفرم: گزارش‌های سؤال‌های پلتفرمی از هر مدرسه (schoolId = مدرسه‌ی گزارش‌دهنده). */
    List<QuestionReportInfo> listPlatformReports(ReportStatus status);

    /** مثل {@link #updateSchoolReport} ولی فقط برای گزارش‌های سؤال پلتفرمی؛ وگرنه ReportNotFound. */
    QuestionReportInfo updatePlatformReport(Long reportId, ReportStatus newStatus, String note);

    enum ReportType {
        WRONG_ANSWER, TYPO, UNCLEAR_TEXT, OTHER
    }

    /** گردش: OPEN → REVIEWED → (FIXED | CLOSED). */
    enum ReportStatus {
        OPEN, REVIEWED, FIXED, CLOSED
    }

    record ReportId(Long value) {
    }

    /**
     * {@code platformQuestion} = سؤال پلتفرمی است (صف پلتفرم)؛ {@code schoolId} مدرسه‌ی گزارش‌دهنده است.
     */
    record QuestionReportInfo(
            ReportId id,
            Long questionId,
            String questionText,
            boolean platformQuestion,
            Long reporterUserId,
            Long schoolId,
            ReportType reportType,
            String comment,
            ReportStatus status,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record TopicId(Long value) {
    }

    record QuestionId(Long value) {
    }

    record TopicInfo(TopicId id, Long schoolId, Long parentId, String name, int sortOrder) {
    }

    record TopicTreeNode(TopicId id, String name, int sortOrder, List<TopicTreeNode> children) {
    }

    /**
     * {@code answer} پاسخ تشریحی/توضیح گزینه‌ی درست است (اختیاری) -
     * فیلدی که در منبع moshavere وجود داشت ولی در خلاصه‌ی spec از قلم
     * افتاده بود؛ عمداً پورت شده چون بدون آن بانک سؤال برای مرور بعد از
     * آزمون ناقص است.
     */
    record NewQuestion(
            Long topicId,
            String text,
            String choice1,
            String choice2,
            String choice3,
            String choice4,
            int correctChoice,
            String answer,
            QuestionDifficulty difficulty,
            Double discriminationIndex
    ) {
    }

    record QuestionInfo(
            QuestionId id,
            Long schoolId,
            Long topicId,
            String text,
            String choice1,
            String choice2,
            String choice3,
            String choice4,
            int correctChoice,
            String answer,
            QuestionDifficulty difficulty,
            Double discriminationIndex
    ) {
    }

    /**
     * هر دو فیلد اختیاری‌اند: topicId null یعنی همه‌ی سرفصل‌ها،
     * difficulties خالی/null یعنی همه‌ی سطوح دشواری.
     */
    record QuestionFilter(Long topicId, Set<QuestionDifficulty> difficulties) {

        public static QuestionFilter none() {
            return new QuestionFilter(null, null);
        }
    }
}
