package com.example.roshdeandishe.questionbox;

import java.util.List;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول QuestionBox (openspec/changes/question-box.md).
 * <p>
 * قاعده‌ی ویرایش/حذف: سازنده، مدیر مدرسه ({@code SCHOOL_ADMIN})، یا هر دارنده‌ی مجوز اگر سازنده دیگر هیچ
 * نقش فعالِ معلم یا مدیر در مدرسه ندارد (سؤالِ بی‌صاحب). سؤالِ مدرسه‌ی دیگر یا حذف‌شده «یافت نشد» است.
 */
public interface QuestionBoxFacade {

    QuestionInfo createQuestion(Long schoolId, Long actorUserId, NewQuestion question);

    QuestionInfo updateQuestion(Long schoolId, Long actorUserId, Long questionId, NewQuestion question);

    /** حذف نرم: از باکس ناپدید می‌شود، برگه‌های ذخیره‌شده همچنان آن را چاپ می‌کنند. */
    void deleteQuestion(Long schoolId, Long actorUserId, Long questionId);

    QuestionInfo getQuestion(Long schoolId, Long actorUserId, Long questionId);

    List<QuestionInfo> listQuestions(Long schoolId, Long actorUserId, QuestionListFilter filter);

    enum QuestionType {MULTIPLE_CHOICE, ESSAY}

    enum ContentMode {TEXT, IMAGE_ONLY}

    enum ImageRole {STEM, CHOICE_1, CHOICE_2, CHOICE_3, CHOICE_4, ANSWER}

    /**
     * {@code choices} فقط برای چهارگزینه‌ایِ متنی (چهار متن)؛ برای تشریحی و فقط‌تصویر خالی/null.
     * {@code correctChoice} (۱ تا ۴) برای هر چهارگزینه‌ای لازم است و برای تشریحی باید null باشد.
     */
    record NewQuestion(
            QuestionType type,
            ContentMode contentMode,
            Long topicId,
            String text,
            List<String> choices,
            Integer correctChoice,
            String answer,
            Integer suggestedScore
    ) {
    }

    record ImageRef(Long id, ImageRole role) {
    }

    /**
     * {@code editable} نسبت به کاربرِ درخواست‌کننده محاسبه می‌شود. {@code complete}: سؤالِ فقط‌تصویر تا وقتی
     * تصویرِ STEM ندارد ناقص است و در برگه پذیرفته نمی‌شود.
     */
    record QuestionInfo(
            Long id,
            Long schoolId,
            Long createdByUserId,
            boolean editable,
            boolean complete,
            QuestionType type,
            ContentMode contentMode,
            Long topicId,
            String text,
            List<String> choices,
            Integer correctChoice,
            String answer,
            Integer suggestedScore,
            List<ImageRef> images
    ) {
    }

    record QuestionListFilter(String search, QuestionType type, Long topicId) {

        public static QuestionListFilter none() {
            return new QuestionListFilter(null, null, null);
        }
    }
}
