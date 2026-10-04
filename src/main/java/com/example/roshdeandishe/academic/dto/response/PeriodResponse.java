package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.TimetableFacade.PeriodInfo;

public record PeriodResponse(Long id, String academicYear, int position, String label,
                             String startTime, String endTime) {
    public static PeriodResponse from(PeriodInfo p) {
        return new PeriodResponse(p.id(), p.academicYear(), p.position(), p.label(),
                p.startTime().toString(), p.endTime().toString());
    }
}
