package com.example.roshdeandishe.school.dto.response;

import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;

public record SchoolResponse(Long id, String name, String status, String timezone, String locale, String level,
                             String slug, String weekStart) {
    public static SchoolResponse from(SchoolInfo info) {
        return new SchoolResponse(
                info.id().value(), info.name(), info.status().name(), info.timezone(), info.locale(),
                info.level().name(), info.slug(), info.weekStart().name()
        );
    }
}
