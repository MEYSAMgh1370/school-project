package com.example.roshdeandishe.questionbox.dto.request;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ContentMode;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.QuestionType;

import java.util.List;

public record QuestionRequest(
        QuestionType type,
        ContentMode contentMode,
        Long topicId,
        String text,
        List<String> choices,
        Integer correctChoice,
        String answer,
        Integer suggestedScore
) {
}
