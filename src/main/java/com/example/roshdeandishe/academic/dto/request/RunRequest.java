package com.example.roshdeandishe.academic.dto.request;

import java.util.List;

/** {@code decisions} اختیاری: دانش‌آموزانِ نیامده قاعده‌ی پیش‌فرض را می‌گیرند. */
public record RunRequest(String fromYear, List<Decision> decisions) {
}
