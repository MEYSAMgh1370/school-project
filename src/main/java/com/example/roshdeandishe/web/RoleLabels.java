package com.example.roshdeandishe.web;

import java.util.Map;

/**
 * برچسب فارسی نقش‌ها برای رندر سمت سرور (navbar مشترک، جدول داشبورد) -
 * کد خام نقش (SCHOOL_ADMIN و ...) هرگز نباید به UI برسد (UX_ROADMAP.md §2).
 * نسخه‌ی سمت کلاینتِ همین نگاشت در people.js است (صفحه‌های client-rendered
 * داده‌ی API را با کد نقش می‌گیرند) - این دو را با هم هماهنگ نگه دارید.
 * PLATFORM_ADMIN عمداً غایب است: مسیر بررسی مجوز ندارد (CLAUDE.md §7 مورد ۱)
 * و نباید در هیچ UI ای ظاهر شود؛ اگر روزی کدی ناشناخته برسد، خودِ کد
 * برگردانده می‌شود تا صفحه نشکند.
 */
public final class RoleLabels {

    private static final Map<String, String> PERSIAN = Map.of(
            "STUDENT", "دانش‌آموز",
            "TEACHER", "معلم",
            "PARENT", "والد",
            "SCHOOL_ADMIN", "مدیر مدرسه",
            "FINANCE_ADMIN", "مدیر مالی"
    );

    private RoleLabels() {
    }

    public static String persian(String roleCode) {
        return PERSIAN.getOrDefault(roleCode, roleCode);
    }
}
