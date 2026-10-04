package com.example.roshdeandishe.curriculum.dto.response;

import java.util.List;

public record TopicTreeResponse(Long id, String name, int sortOrder, List<TopicTreeResponse> children) {
}
