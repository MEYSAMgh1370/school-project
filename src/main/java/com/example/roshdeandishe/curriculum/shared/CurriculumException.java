package com.example.roshdeandishe.curriculum.shared;

public final class CurriculumException extends RuntimeException {

    private final CurriculumError error;

    public CurriculumException(CurriculumError error) {
        super(describe(error));
        this.error = error;
    }

    public CurriculumError error() {
        return error;
    }

    private static String describe(CurriculumError error) {
        return switch (error) {
            case CurriculumError.TopicNotFound e -> "سرفصلی با شناسه " + e.topicId() + " یافت نشد.";
            case CurriculumError.InvalidTopic e -> e.detail();
            case CurriculumError.InvalidQuestion e -> e.detail();
        };
    }
}
