package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.TimetableFacade.ActivityInfo;

public record ActivityResponse(Long id, String name, boolean platformDefault) {
    public static ActivityResponse from(ActivityInfo a) {
        return new ActivityResponse(a.id(), a.name(), a.platformDefault());
    }
}
