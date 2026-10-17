package com.example.roshdeandishe.notification.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ابزار متن فارسی برای پیام‌های ربات: تبدیل رقم لاتین به فارسی و گروه‌بندی مبلغ با جداکننده‌ی هزارگان فارسی
 * ({@code ٬}، U+066C).
 */
final class PersianText {

    private static final char GROUP_SEPARATOR = '٬';

    private PersianText() {
    }

    /**
     * بله متن همه‌ی پیام‌های ارسالی را Markdown تفسیر می‌کند ({@code *پررنگ*}، {@code _کج_}،
     * {@code [متن](آدرس)}، {@code ```نمایش آنی```})؛ بات هیچ‌جا قالب‌بندی نمی‌خواهد، پس این چهار نویسه با
     * هم‌شکل بی‌اثرشان عوض می‌شوند تا نامی که کاربر وارد کرده (مثلاً «a_b_c») کج یا پیوند نشود. مستندات بله
     * راه escape تعریف نکرده، پس جایگزینی تنها راه مطمئن است. null همان null می‌ماند.
     */
    static String withoutMarkdown(String text) {
        if (text == null) {
            return null;
        }
        return text.replace('*', '∗')   // U+2217
                .replace('_', 'ˍ')      // U+02CD
                .replace('[', '［')     // U+FF3B
                .replace('`', 'ˋ');     // U+02CB
    }

    /** ۱۴۰۵ به‌جای 1405؛ علامت منفی حفظ می‌شود. */
    static String digits(long n) {
        return toPersianDigits(Long.toString(n));
    }

    /** مبلغ بدون اعشار (ریال عدد صحیح است) با جداکننده‌ی هزارگان فارسی: 45000000 به «۴۵٬۰۰۰٬۰۰۰». */
    static String grouped(BigDecimal amount) {
        String plain = amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
        boolean negative = plain.startsWith("-");
        String abs = negative ? plain.substring(1) : plain;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < abs.length(); i++) {
            if (i > 0 && (abs.length() - i) % 3 == 0) {
                out.append(GROUP_SEPARATOR);
            }
            out.append(abs.charAt(i));
        }
        return (negative ? "-" : "") + toPersianDigits(out.toString());
    }

    /** رقم لاتین به فارسی و ممیز اعشار به «٫» (U+066B)، برای مقدارهایی مثل «18.5». */
    static String localize(String s) {
        return toPersianDigits(s).replace('.', '٫');
    }

    private static String toPersianDigits(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }
}
