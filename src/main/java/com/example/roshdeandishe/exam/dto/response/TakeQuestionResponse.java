package com.example.roshdeandishe.exam.dto.response;

public record TakeQuestionResponse(Long questionId, String text, String choice1, String choice2,
                                   String choice3, String choice4, Integer selectedChoice) {
}
