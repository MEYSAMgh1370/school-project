package com.example.roshdeandishe.iam.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * در حالی که {@link RequiresModule} فقط بررسی می‌کند مدرسه ماژول را خریده
 * است یا نه، این انوتیشن یک پله دقیق‌تر است: "آیا نقش فعال کاربر در این
 * مدرسه، Permission مشخص را دارد؟"
 * <p>
 * مثال: حتی اگر مدرسه ماژول FINANCE را فعال کرده باشد،
 * {@code @RequiresPermission("FINANCE_MANAGE")} روی متد صدور فاکتور یعنی
 * فقط کاربری با نقشی که این Permission را دارد (مثلاً SCHOOL_ADMIN یا
 * FINANCE_ADMIN) می‌تواند آن متد را اجرا کند - یک دانش‌آموز با همان مدرسه
 * نمی‌تواند، حتی اگر ماژول فعال باشد.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

    /**
     * نام Permission، باید دقیقاً با مقدار ستون {@code code} در جدول
     * {@code iam.permissions} مطابقت داشته باشد (مثلاً "FINANCE_MANAGE").
     */
    String value();
}
