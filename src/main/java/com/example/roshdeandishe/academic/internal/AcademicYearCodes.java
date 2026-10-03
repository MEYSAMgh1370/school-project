package com.example.roshdeandishe.academic.internal;

/**
 * شکلِ canonical کدِ سال تحصیلی («1404-1405»، رقم ASCII): سال کلید است، پس یک شکل در سرور
 * (openspec/changes/academic-year.md، فاز ۱). صفحه‌ی نمره‌ها پیش‌تر ارقام را خودش نرمال می‌کرد؛ این
 * قاعده حالا سمت سرور است تا هر مشتری (موبایل، اسکریپت) همان کلید را بسازد.
 */
final class AcademicYearCodes {

    private AcademicYearCodes() {
    }

    /** ارقام فارسی/عربی به ASCII و حذف فاصله‌ی ابتدا/انتها؛ تهی تهی می‌ماند. */
    static String canonical(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        StringBuilder canonical = new StringBuilder(stripped.length());
        for (char character : stripped.toCharArray()) {
            if (character >= '۰' && character <= '۹') {
                canonical.append((char) ('0' + character - '۰'));
            } else if (character >= '٠' && character <= '٩') {
                canonical.append((char) ('0' + character - '٠'));
            } else {
                canonical.append(character);
            }
        }
        return canonical.toString();
    }
}
