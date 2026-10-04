package com.example.roshdeandishe.exam.dto.request;

import java.util.List;

public record CreateSectionRequest(Long topicId, String name, List<Long> questionIds) {
}
