/**
 * ماژول IAM (Identity &amp; Access Management).
 * <p>
 * مسئولیت: احراز هویت، نقش‌ها، مدیریت مدرسه‌ها (Tenant) و کنترل دسترسی به ماژول‌ها
 * (جدول {@code iam.school_modules}).
 * <p>
 * تنها راه ورود سایر ماژول‌ها به این ماژول، اینترفیس {@code IamFacade} است.
 * پکیج {@code internal} (شامل Entity/Repository/ServiceImpl) به‌صورت خودکار توسط
 * Spring Modulith مخفی می‌شود (قرارداد نام‌گذاری internal).
 * پکیج {@code shared} به‌عنوان یک Named Interface علنی شده تا سایر ماژول‌ها
 * (در صورت نیاز واقعی) بتوانند به TenantContext دسترسی داشته باشند.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity & Access Management"
)
package com.example.roshdeandishe.iam;
