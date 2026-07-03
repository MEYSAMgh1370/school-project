/**
 * ماژول School: مالک داده‌ی توصیفی خود مدرسه (نام، وضعیت، timezone، locale).
 * <p>
 * تا قبل از این فاز، schoolId در همه‌جا (از جمله iam.SchoolMembershipEntity،
 * iam.SchoolEntitlementEntity، TenantContext) یک Long مات (opaque) بود -
 * یعنی هیچ جایی نمی‌دانست "مدرسه‌ی ۴۲ اصلاً اسمش چیست یا فعال است یا نه".
 * <p>
 * این ماژول عمداً جدا از iam است، نه بخشی از آن: iam فقط به schoolId به
 * عنوان یک شناسه نیاز دارد (برای membership/entitlement)، نه به اطلاعات
 * توصیفی مدرسه. اگر School را داخل iam می‌گذاشتیم، iam مجبور می‌شد چیزی
 * را owns کند که academic و finance هم به همان اندازه به آن نیاز دارند -
 * دقیقاً همان مشکلی که platform برای حل آن جدا شد.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "School Registry"
)
package com.example.roshdeandishe.school;
