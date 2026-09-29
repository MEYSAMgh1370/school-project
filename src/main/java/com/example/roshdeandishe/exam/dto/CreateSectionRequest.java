package com.example.roshdeandishe.exam.dto;

import java.util.List;

public record CreateSectionRequest(Long topicId, String name, List<Long> questionIds) {
}
