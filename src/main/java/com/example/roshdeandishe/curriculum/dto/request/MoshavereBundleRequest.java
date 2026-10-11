package com.example.roshdeandishe.curriculum.dto.request;

import java.util.List;

/** بسته‌ی مشاوره، قالب ۱ (data/moshavere/bundle_phase1.json). */
public record MoshavereBundleRequest(Integer format, String source, List<Topic> topics, List<Course> courses) {

    public record Topic(String sourceCode, String parentSourceCode, String name, Integer sortOrder) {
    }

    public record Course(Integer gradeLevel, String name, String track, String topicSourceCode) {
    }
}
