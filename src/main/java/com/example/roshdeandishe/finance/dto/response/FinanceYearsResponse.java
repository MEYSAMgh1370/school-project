package com.example.roshdeandishe.finance.dto.response;

import java.util.List;

/** سال‌های قابل‌انتخاب در صفحه‌ی مالی و سالِ جاریِ مدرسه (تهی اگر انتخاب نشده). */
public record FinanceYearsResponse(List<String> years, String currentYear) {
}
