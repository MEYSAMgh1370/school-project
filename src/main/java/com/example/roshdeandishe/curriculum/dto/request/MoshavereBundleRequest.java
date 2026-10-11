package com.example.roshdeandishe.curriculum.dto.request;

import java.util.List;

/**
 * بسته‌ی مشاوره، قالب ۱ (data/moshavere/bundle_phase1.json). {@code questions} اختیاری است؛ بسته‌ی بدون آن
 * فقط سرفصل و درس وارد می‌کند.
 */
public record MoshavereBundleRequest(Integer format, String source, List<Topic> topics, List<Course> courses,
                                     List<Question> questions) {

    public record Topic(String sourceCode, String parentSourceCode, String name, Integer sortOrder) {
    }

    public record Course(Integer gradeLevel, String name, String track, String topicSourceCode) {
    }

    public record Question(String sourceCode, String topicSourceCode, String text, List<String> choices,
                           Integer correctChoice, String answer) {
    }
}
