package com.example.roshdeandishe.questionbox.shared;

public final class QuestionBoxException extends RuntimeException {

    private final QuestionBoxError error;

    public QuestionBoxException(QuestionBoxError error) {
        super(describe(error));
        this.error = error;
    }

    public QuestionBoxError error() {
        return error;
    }

    private static String describe(QuestionBoxError error) {
        return switch (error) {
            case QuestionBoxError.QuestionNotFound e -> "سؤالی با شناسه " + e.questionId() + " یافت نشد.";
            case QuestionBoxError.SheetNotFound e -> "برگه‌ای با شناسه " + e.sheetId() + " یافت نشد.";
            case QuestionBoxError.HeaderNotFound e -> "سربرگی با شناسه " + e.headerId() + " یافت نشد.";
            case QuestionBoxError.ImageNotFound e -> "تصویری با شناسه " + e.imageId() + " یافت نشد.";
            case QuestionBoxError.InvalidQuestion e -> e.reason();
            case QuestionBoxError.InvalidImage e -> e.reason();
            case QuestionBoxError.InvalidSheet e -> e.reason();
            case QuestionBoxError.NotAllowed e -> "شما اجازه‌ی تغییر مورد " + e.id() + " را ندارید.";
        };
    }
}
