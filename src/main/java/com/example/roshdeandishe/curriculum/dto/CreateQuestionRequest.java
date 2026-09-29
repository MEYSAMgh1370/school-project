package com.example.roshdeandishe.curriculum.dto;

import com.example.roshdeandishe.curriculum.QuestionDifficulty;

public record CreateQuestionRequest(
        Long topicId,
        String text,
        String choice1,
        String choice2,
        String choice3,
        String choice4,
        Integer correctChoice,
        String answer,
        QuestionDifficulty difficulty,
        Double discriminationIndex
) {
}
