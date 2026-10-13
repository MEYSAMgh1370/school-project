package com.example.roshdeandishe.questionbox.dto.request;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderField;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ItemSource;

import java.util.List;
import java.util.Map;

public record SheetRequest(
        String title,
        Long headerId,
        Map<HeaderField, String> headerValues,
        Boolean showEssayAnswerSpace,
        Boolean includeAnswerKey,
        List<Section> sections
) {

    public record Section(String title, String instruction, List<Item> items) {
    }

    public record Item(ItemSource source, Long questionId) {
    }
}
