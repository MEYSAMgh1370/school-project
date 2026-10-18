package com.example.roshdeandishe.notification.internal;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * یک پرسشِ متن آزاد که {@link BaleUpdateHandler#handle} درون تراکنش آماده کرده و {@link FreeTextResponder}
 * بیرون از تراکنش به مدل می‌سپارد. {@code text} از پیش شبه‌نام شده است (بدون نام، نام مدرسه یا رشته‌ی
 * عددی بلند). فقط کلیدهای {@code labelToStudentId} (مثل «[فرزند_۱]») به مدل می‌روند؛ مقدارها شناسه‌ی
 * دانش‌آموزند و هرگز از سرور بیرون نمی‌روند، و پیش از پاسخ دوباره با هدف‌های تازه‌ی کاربر سنجیده می‌شوند.
 * ترتیب برچسب‌ها همان ترتیب {@link SubjectResolver} است.
 *
 * @param allowed intentهایی که منوی همین کاربر دارد
 */
record FreeTextRequest(BotRef bot, long chatId, long userId, List<String> activeRoleCodes, String text,
                       Set<BotIntent> allowed, Map<String, Long> labelToStudentId) {

    FreeTextRequest {
        activeRoleCodes = List.copyOf(activeRoleCodes);
        allowed = allowed.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(BotIntent.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(allowed));
        labelToStudentId = Collections.unmodifiableMap(new LinkedHashMap<>(labelToStudentId));
    }

    /** بدون متن و برچسب‌ها، تا اگر جایی لاگ شد چیزی از پرسش بیرون نرود. */
    @Override
    public String toString() {
        return "FreeTextRequest[bot=" + bot.botId() + ", chatId=" + chatId + ", userId=" + userId
                + ", allowed=" + allowed + ", labels=" + labelToStudentId.size() + "]";
    }
}
