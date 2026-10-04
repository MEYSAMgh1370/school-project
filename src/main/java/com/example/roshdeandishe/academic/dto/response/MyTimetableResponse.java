package com.example.roshdeandishe.academic.dto.response;

import java.util.List;

public record MyTimetableResponse(String academicYear, List<PeriodResponse> periods,
                                  List<MyTimetableView> views) {
}
