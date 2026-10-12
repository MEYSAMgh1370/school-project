package com.example.roshdeandishe.iam.internal;

import java.util.Set;

/**
 * مجموعه‌ی ثابتِ مجوزهای سطح پلتفرم. فقط در {@code PlatformContext} و فقط برای کاربرِ
 * {@code iam.platform_admins} اعطا می‌شوند؛ به هیچ نقشی (سیستمی یا سفارشی) داده نمی‌شوند و
 * از فهرست/ساخت نقش سفارشیِ مدرسه حذف‌اند.
 */
final class PlatformPermissions {

    static final String HOLIDAY_MANAGE = "PLATFORM_HOLIDAY_MANAGE";

    static final String QUESTION_REPORT_MANAGE = "PLATFORM_QUESTION_REPORT_MANAGE";

    static final Set<String> CODES = Set.of(HOLIDAY_MANAGE, QUESTION_REPORT_MANAGE);

    private PlatformPermissions() {
    }
}
