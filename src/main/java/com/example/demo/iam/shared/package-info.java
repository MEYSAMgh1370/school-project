/**
 * این پکیج به‌عنوان یک {@code NamedInterface} با نام "shared" علنی شده است.
 * یعنی برخلاف internal، سایر ماژول‌ها (academic, finance) اجازه دارند
 * صریحاً به کلاس‌های این پکیج (مثل {@code TenantContext}) وابسته شوند،
 * بدون اینکه Spring Modulith این کار را نقض معماری (Architecture Violation) تشخیص دهد.
 */
@org.springframework.modulith.NamedInterface("shared")
package com.example.roshdeandishe.iam.shared;
