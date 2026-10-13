package com.example.roshdeandishe.questionbox.dto.response;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ContentMode;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageRef;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.QuestionInfo;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.QuestionType;

import java.util.List;

public record QuestionResponse(
        Long id,
        Long schoolId,
        Long createdByUserId,
        boolean editable,
        boolean complete,
        QuestionType type,
        ContentMode contentMode,
        Long topicId,
        String text,
        List<String> choices,
        Integer correctChoice,
        String answer,
        Integer suggestedScore,
        List<ImageRef> images
) {

    public static QuestionResponse from(QuestionInfo q) {
        return new QuestionResponse(q.id(), q.schoolId(), q.createdByUserId(), q.editable(), q.complete(),
                q.type(), q.contentMode(), q.topicId(), q.text(), q.choices(), q.correctChoice(), q.answer(),
                q.suggestedScore(), q.images());
    }
}
