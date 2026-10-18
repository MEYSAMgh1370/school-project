package com.example.roshdeandishe.finance.dto.response;

import java.util.List;
import java.util.Map;

/** counts کلیدهای all/current/previous دارد (همان واژگانِ پارامتر scope). */
public record StudentFinancePageResponse(List<StudentFinanceRowResponse> items, long total,
                                         Map<String, Long> counts, int page, int size) {
}
