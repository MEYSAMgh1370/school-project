package com.example.roshdeandishe.curriculum;

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
     * فهرست تختِ همه‌ی سرفصل‌های یک مدرسه (مرتب بر اساس sortOrder سپس id).
     */
    List<TopicInfo> listTopics(Long schoolId);

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
