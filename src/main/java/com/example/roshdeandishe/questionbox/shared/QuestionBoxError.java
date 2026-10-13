package com.example.roshdeandishe.questionbox.shared;

/**
 * مدل خطای دامنه‌محور ماژول questionbox (openspec/changes/question-box.md).
 */
public sealed interface QuestionBoxError {

    /** سؤال در این مدرسه نیست یا حذف شده است (سؤالِ مدرسه‌ی دیگر هم همین است، وجودش لو نمی‌رود). */
    record QuestionNotFound(Long questionId) implements QuestionBoxError {
    }

    record SheetNotFound(Long sheetId) implements QuestionBoxError {
    }

    record HeaderNotFound(Long headerId) implements QuestionBoxError {
    }

    record ImageNotFound(Long imageId) implements QuestionBoxError {
    }

    /** سؤالِ نامعتبر: نوع/حالتِ ناسازگار، متنِ خالی، گزینه‌ی کم یا گزینه‌ی صحیحِ بیرون از ۱ تا ۴. */
    record InvalidQuestion(String reason) implements QuestionBoxError {
    }

    /** تصویرِ نامعتبر: نوعِ غیرمجاز، حجمِ زیاد، بیش از سقفِ تعداد. */
    record InvalidImage(String reason) implements QuestionBoxError {
    }

    /** برگه یا سربرگِ نامعتبر. */
    record InvalidSheet(String reason) implements QuestionBoxError {
    }

    /** فقط سازنده، مدیر مدرسه یا (برای سازنده‌ی جداشده) هر دارنده‌ی مجوز می‌تواند ویرایش/حذف کند. */
    record NotAllowed(Long id) implements QuestionBoxError {
    }
}
