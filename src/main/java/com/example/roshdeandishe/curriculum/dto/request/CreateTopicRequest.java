package com.example.roshdeandishe.curriculum.dto.request;

public record CreateTopicRequest(Long parentId, String name, Integer sortOrder) {
}
