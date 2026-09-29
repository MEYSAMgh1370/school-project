package com.example.roshdeandishe.curriculum.dto;

public record CreateTopicRequest(Long parentId, String name, Integer sortOrder) {
}
