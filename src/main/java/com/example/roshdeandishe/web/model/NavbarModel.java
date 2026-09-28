package com.example.roshdeandishe.web.model;

import java.util.List;

/**
 * مدل ناوبارِ مشترک (سوییچر مدرسه/نقش - CLAUDE.md بند ۱۱ سؤال ۲) که هر
 * صفحه‌ی مدیریتی به template مشترک {@code layout/navbar.jte} می‌دهد.
 * فقط عضویت‌های فعال را شامل می‌شود؛ کاربر بدون عضویت (فرد B2C آینده)
 * لیست خالی می‌گیرد و دراپ‌داون مدرسه اصلاً رندر نمی‌شود.
 */
public record NavbarModel(String fullName, List<SchoolOption> schools) {

    public record SchoolOption(Long id, String name, List<String> roles) {
    }
}
