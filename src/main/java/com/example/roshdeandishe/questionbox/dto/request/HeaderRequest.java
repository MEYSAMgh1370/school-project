package com.example.roshdeandishe.questionbox.dto.request;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderField;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderLayout;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderMode;

import java.util.Set;

public record HeaderRequest(
        String name,
        HeaderMode mode,
        HeaderLayout layout,
        Boolean showSchoolName,
        Boolean showLogo,
        Set<HeaderField> fields
) {
}
