package com.example.roshdeandishe.finance.internal;

import java.util.Locale;

/**
 * تطبیقِ نامِ دانش‌آموز با فیلترهای صفحه‌ی مالی. فقط {@code iam.users.full_name} داریم، پس «نام» اولین کلمه و
 * «نام‌خانوادگی» بقیه‌ی کلمه‌هاست (نامِ تک‌کلمه‌ای: نام‌خانوادگیِ تهی). هر فیلترِ خالی/null همه را می‌پذیرد و
 * فیلترهای پرشده با AND ترکیب می‌شوند؛ هر فیلتر «شاملِ» متنِ هدف است (جستجو هنگام تایپ). {@code q} در کلِ نام می‌گردد.
 * <p>
 * نرمال‌سازی: ي→ی، ك→ک، حذفِ نیم‌فاصله (ZWNJ) و کشیده (تطویل)، فشرده‌کردنِ فاصله‌ها و case-fold.
 */
final class StudentNameMatcher {

    private StudentNameMatcher() {
    }

    static boolean matches(String fullName, String firstName, String lastName, String q) {
        String name = normalise(fullName);
        String first = name;
        String last = "";
        int space = name.indexOf(' ');
        if (space >= 0) {
            first = name.substring(0, space);
            last = name.substring(space + 1);
        }
        return contains(first, firstName) && contains(last, lastName) && contains(name, q);
    }

    private static boolean contains(String target, String filter) {
        String wanted = normalise(filter);
        return wanted.isEmpty() || target.contains(wanted);
    }

    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String mapped = text
                .replace('ي', 'ی')   // ي → ی
                .replace('ك', 'ک')   // ك → ک
                .replace("‌", "")
                .replace("ـ", "");
        return mapped.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
