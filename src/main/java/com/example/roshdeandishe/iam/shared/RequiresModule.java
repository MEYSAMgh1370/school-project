package com.example.roshdeandishe.iam.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * با قرار گرفتن روی یک متد Controller/Service، اجرای آن متد مشروط می‌شود به
 * اینکه ماژول {@code value} برای مدرسه‌ی جاری (schoolId از {@link TenantContext})
 * در جدول {@code iam.school_modules} فعال (enabled = true) باشد.
 * <p>
 * مثال استفاده:
 * <pre>{@code
 * @RequiresModule("FINANCE")
 * @GetMapping("/invoices")
 * public List<InvoiceDto> listInvoices() { ... }
 * }</pre>
 * بررسی واقعی توسط {@link ModuleAccessAspect} و از طریق {@code IamFacade}
 * (که به جدول داخلی iam دسترسی دارد) انجام می‌شود.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresModule {

    /**
     * نام ماژول، باید دقیقاً با مقدار ستون {@code module_name} در
     * جدول {@code iam.school_modules} مطابقت داشته باشد (مثلاً "FINANCE", "ACADEMIC").
     */
    String value();
}
