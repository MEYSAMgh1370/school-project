package com.example.roshdeandishe.web.model;

import java.util.List;
import java.util.Map;

/**
 * مدل ناوبارِ مشترک (سوییچر مدرسه/نقش - CLAUDE.md بند ۱۱ سؤال ۲) که هر
 * صفحه‌ی مدیریتی به template مشترک {@code layout/navbar.jte} می‌دهد.
 * فقط عضویت‌های فعال را شامل می‌شود؛ کاربر بدون عضویت (فرد B2C آینده)
 * لیست خالی می‌گیرد و دراپ‌داون مدرسه اصلاً رندر نمی‌شود.
 */
public record NavbarModel(String fullName, List<SchoolOption> schools) {

    /**
     * @param rolePermissions Permissionهای هر نقشِ کاربر در همین مدرسه (کدِ نقش ← کدهای Permission):
     *                        لینکِ مدیریتی برای نقشِ سفارشیِ دارای Permission هم نمایش داده می‌شود
     *                        (فقط نمایش؛ کنترل دسترسی همیشه سمت سرور است).
     */
    public record SchoolOption(Long id, String name, List<String> roles, Map<String, List<String>> rolePermissions) {

        /** بدون Permission (صفحه‌هایی که فقط نقش‌های سیستمی دارند و در تست‌ها). */
        public SchoolOption(Long id, String name, List<String> roles) {
            this(id, name, roles, Map.of());
        }
    }
}
