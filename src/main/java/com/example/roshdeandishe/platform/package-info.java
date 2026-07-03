/**
 * ماژول Platform: فقط زیرساخت کاملاً عمومی و بدون منطق دامنه (مثل ساخت
 * ProblemDetail) را نگه می‌دارد. هیچ ماژول دیگری (iam, academic, finance)
 * را import نمی‌کند - فقط در جهت معکوس استفاده می‌شود.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Platform Shared Infrastructure"
)
package com.example.roshdeandishe.platform;
