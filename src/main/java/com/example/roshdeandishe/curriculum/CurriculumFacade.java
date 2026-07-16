package com.example.roshdeandishe.curriculum;

import java.util.List;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Curriculum.
 * <p>
 * درخت سرفصل‌ها عمداً یک adjacency list ساده است ({@code parentId} خود-ارجاع
 * nullable)، نه بازنمایی دوگانه‌ی moshavere (جدول ltree پستگرس + درخت
 * pickle شده‌ی in-memory) - آن دوگانگی در بازبینی اولیه یک wart تشخیص داده
 * شد، نه الگویی که ارزش حفظ داشته باشد (MOSHAVERE_PORT_SPEC.md §4/§7).
 */
public interface CurriculumFacade {

    /**
     * ساخت یک سرفصل. اگر {@code parentId} داده شود باید سرفصلی موجود در
     * همین مدرسه باشد - وگرنه CurriculumError.TopicNotFound. چون عملیات
     * جابه‌جایی/ویرایش والد وجود ندارد، حلقه (cycle) هم نمی‌تواند ساخته
     * شود: والد همیشه قبل از فرزند وجود دارد.
     *
     * @param order ترتیب نمایش بین هم‌نیاها؛ null یعنی ۰
     */
    TopicId createTopic(Long schoolId, Long parentId, String name, Integer order);

    List<TopicInfo> listTopics(Long schoolId);

    /**
     * درخت کامل سرفصل‌های یک مدرسه: ریشه‌ها با فرزندان تو در تو، مرتب بر
     * اساس order سپس id - جایگزین مسیرِ خواندنِ درخت pickle شده‌ی moshavere.
     */
    List<TopicNode> getTopicTree(Long schoolId);

    /**
     * ثبت یک سوال چهارگزینه‌ای زیر یک سرفصل از همین مدرسه.
     *
     * @throws com.example.roshdeandishe.curriculum.shared.CurriculumException با
     * TopicNotFound اگر سرفصل در این مدرسه نباشد، InvalidQuestion برای متن/
     * گزینه‌ی خالی یا correctChoice خارج از ۱..۴ یا difficulty منفی
     */
    QuestionId createQuestion(Long schoolId, Long topicId, String text,
                              String choice1, String choice2, String choice3, String choice4,
                              int correctChoice, Integer difficulty, Double discriminationIndex);

    /**
     * فهرست سوالات مدرسه با فیلتر اختیاری سرفصل/سختی (پورتِ QuestionFilter
     * مبتنی بر django-filter در moshavere). topicId داده‌شده باید متعلق به
     * همین مدرسه باشد - وگرنه TopicNotFound، نه فهرست خالیِ گمراه‌کننده.
     */
    List<QuestionInfo> listQuestions(Long schoolId, Long topicId, Integer difficulty);

    record TopicId(Long value) {
    }

    record QuestionId(Long value) {
    }

    record TopicInfo(TopicId id, Long schoolId, Long parentId, String name, int order) {
    }

    record TopicNode(TopicId id, String name, int order, List<TopicNode> children) {
    }

    record QuestionInfo(QuestionId id, Long schoolId, TopicId topicId, String text,
                        String choice1, String choice2, String choice3, String choice4,
                        int correctChoice, Integer difficulty, Double discriminationIndex) {
    }
}
