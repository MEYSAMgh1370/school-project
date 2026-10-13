package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderLayout;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderMode;

/**
 * سربرگ بدون بایت‌های تصویر ({@code hasImage} از {@code image is null} محاسبه می‌شود): فهرست و نمای نهایی تا ۵ مگابایت
 * به‌ازای هر سربرگ نباید بخوانند.
 */
record HeaderRow(Long id, Long schoolId, Long createdByUserId, String name, HeaderMode mode, HeaderLayout layout,
                 boolean showSchoolName, boolean showLogo, String fields, boolean hasImage) {
}
