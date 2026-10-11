package com.example.roshdeandishe.curriculum.dto.request;

public record UpsertPlatformTopicRequest(String sourceCode, String parentSourceCode, String name, Integer sortOrder) {
}
