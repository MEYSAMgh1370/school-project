package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.school.SchoolFacade.WeekStart;

import java.util.List;
import java.util.Map;

/**
 * مدل ناوبارِ مشترک (سوییچر مدرسه/نقش - CLAUDE.md بند ۱۱ سؤال ۲) که هر
 * صفحه‌ی مدیریتی به template مشترک {@code layout/navbar.jte} می‌دهد.
 * فقط عضویت‌های فعال را شامل می‌شود؛ کاربر بدون عضویت (فرد B2C آینده)
 * لیست خالی می‌گیرد و دراپ‌داون مدرسه اصلاً رندر نمی‌شود.
 *
 * @param roleNames عنوانِ نقش‌های <b>سفارشی</b> (شناسه‌ی مدرسه ← کدِ نقش ← عنوان) برای هر عضویتِ کاربر، فعال یا
 *                  غیرفعال؛ نقش‌های سیستمی در آن نیستند ({@link RoleLabels})
 */
public record NavbarModel(String fullName, List<SchoolOption> schools, Map<Long, Map<String, String>> roleNames) {

    /** بدون عنوانِ نقش‌های سفارشی (صفحه‌هایی که فقط نقش‌های سیستمی دارند و در تست‌ها). */
    public NavbarModel(String fullName, List<SchoolOption> schools) {
        this(fullName, schools, Map.of());
    }

    /**
     * برچسبِ نمایشیِ نقش در یک مدرسه: عنوانِ نقشِ سفارشی که مدیر مدرسه تعریف کرده، یا برچسب فارسیِ نقش
     * سیستمی؛ فقط اگر هیچ‌کدام نبود کدِ خام ({@link RoleLabels#persian}).
     */
    public String roleLabel(Long schoolId, String roleCode) {
        String custom = roleNames.getOrDefault(schoolId, Map.of()).get(roleCode);
        return custom != null ? custom : RoleLabels.persian(roleCode);
    }

    /**
     * روز شروع هفته‌ی هر مدرسه، به‌صورت JSON {@code {"5":"SAT","9":"MON"}} برای صفتِ
     * {@code data-week-starts}؛ calendar.js مدرسه‌ی انتخاب‌شده را از روی آن می‌خواند (W9).
     * فقط شناسه و کدِ ثابت دارد، پس متنِ کاربر به صفت نمی‌رود.
     */
    public String weekStartsJson() {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < schools.size(); i++) {
            SchoolOption school = schools.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(school.id()).append("\":\"").append(school.weekStart().code()).append('"');
        }
        return json.append('}').toString();
    }

    /**
     * @param rolePermissions Permissionهای هر نقشِ کاربر در همین مدرسه (کدِ نقش ← کدهای Permission):
     *                        لینکِ مدیریتی برای نقشِ سفارشیِ دارای Permission هم نمایش داده می‌شود
     *                        (فقط نمایش؛ کنترل دسترسی همیشه سمت سرور است).
     * @param weekStart       روز شروع هفته‌ی همین مدرسه (W9)؛ صفحه‌ها هفته را از آن می‌چرخانند.
     */
    public record SchoolOption(Long id, String name, List<String> roles, Map<String, List<String>> rolePermissions,
                               WeekStart weekStart) {

        /** بدون Permission و با هفته‌ی شنبه‌محور (صفحه‌هایی که فقط نقش‌های سیستمی دارند و در تست‌ها). */
        public SchoolOption(Long id, String name, List<String> roles) {
            this(id, name, roles, Map.of(), WeekStart.SATURDAY);
        }

        /** هفته‌ی شنبه‌محور (تست‌ها). */
        public SchoolOption(Long id, String name, List<String> roles, Map<String, List<String>> rolePermissions) {
            this(id, name, roles, rolePermissions, WeekStart.SATURDAY);
        }
    }
}
